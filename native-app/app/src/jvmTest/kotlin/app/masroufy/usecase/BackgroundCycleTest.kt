package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.budgetAlertCandidates
import app.masroufy.core.budgetStatus
import app.masroufy.core.buildBudgetNotifications
import app.masroufy.core.muted
import app.masroufy.core.isLockSafe
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryDeviceNotifier
import app.masroufy.memory.MemoryUsualHours
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** دورة الخلفية (OVERRIDES §72 · §61): التسجيل لوحده ⇒ المحرك ⇒ إشعار الجوال **للمستني بس**. كل الرسايل مخترعة. */
class BackgroundCycleTest {
    private val now = LocalMoment("2026-10-07", 12)
    private val world = SmsWorld()
    private val notifier = MemoryDeviceNotifier()
    private val inbox = MemoryAlertInbox()
    private val settings = MemoryAlertSettings()
    private val engine = RunAlertEngine(
        AlertEngineDeps(settings, MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), inbox, FixedClock("2026-10-07T12:00:00.000Z")),
    )
    private var others: List<AlertCandidate> = emptyList()

    private fun cycle(withCandidates: Boolean = true) = RunBackgroundCycle(
        BackgroundCycleDeps(world.auto(), if (withCandidates) ({ others }) else null, engine, notifier),
    )

    @Test fun autoRecordedMessagesNeverNotify() = runBlocking<Unit> {
        world.enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        val result = cycle().run(now)
        assertEquals(2, result.sms!!.recorded)
        assertTrue(notifier.posted.isEmpty(), "رد المالك ٢: المسجّل لوحده ما بيطلّعش إشعار")
        assertTrue(inbox.listAll().none { it.kind == AlertKind.SMS_CONFIRM })
        assertNull(result.delivery)
    }

    @Test fun waitingMessagesNotifyOnceWithLockSafeText() = runBlocking<Unit> {
        world.enable()
        world.receive(sms("m1", CAFE), sms("m2", UNCLEAR))
        cycle().run(now)
        val post = notifier.posted.single()
        assertTrue(isLockSafe(post.notice.title) && isLockSafe(post.notice.body), "${post.notice}")
        assertFalse(post.notice.body.contains("TEST CAFE") || post.notice.body.contains("25"))
        assertTrue(post.tag.startsWith("alert-") && "sms" !in post.tag, "المعرّف ببصمة")
        val line = inbox.listAll().single { it.kind == AlertKind.SMS_CONFIRM }
        assertTrue(line.title.contains("1"), "العدد في الصفحة جوه التطبيق")

        notifier.posted.clear()
        cycle().run(now)
        assertTrue(notifier.posted.isEmpty(), "نفس الرسالة المستنية ما بتتبعتش مرتين")

        world.receive(sms("m3", "رسالة\nبـSR 30\n26/10/07"))
        cycle().run(now)
        assertEquals(1, notifier.posted.size, "رسالة جديدة بتستنى ⇒ إشعار جديد")
        assertEquals(1, inbox.listAll().count { it.kind == AlertKind.SMS_CONFIRM }, "سطر واحد في الصفحة")
    }

    @Test fun confirmedMessagesLeaveThePage() = runBlocking<Unit> {
        world.enable()
        world.receive(sms("m1", UNCLEAR))
        cycle().run(now)
        world.memory.acknowledge(listOf("m1")) // المالك شالها من الشاشة
        val result = cycle().run(now)
        assertTrue(inbox.listAll().none { it.kind == AlertKind.SMS_CONFIRM })
        assertEquals(1, result.alerts!!.resolved.size)
    }

    @Test fun blockedNotificationsAreReportedNotThrown() = runBlocking<Unit> {
        world.enable()
        notifier.allowed = false
        world.receive(sms("m1", UNCLEAR))
        val result = cycle().run(now)
        assertEquals(true, result.delivery?.blocked)
        assertTrue(notifier.posted.isEmpty())
        assertTrue(inbox.listAll().any { it.kind == AlertKind.SMS_CONFIRM }, "الصفحة جوه التطبيق لسه فيها السطر")
    }

    @Test fun questionsOffDoesNotSilenceBankMessages() = runBlocking<Unit> {
        world.enable()
        settings.setGroupEnabled(AlertGroup.QUESTIONS, false)
        world.receive(sms("m1", UNCLEAR))
        cycle().run(now)
        assertEquals(1, notifier.posted.size, "رد المالك ٣: «رسايل البنك» مجموعة لوحدها مش جوه «الأسئلة»")
        assertFalse(inbox.listAll().single().decision.muted)
    }

    @Test fun bankMessagesOffStaysOnThePageOnly() = runBlocking<Unit> {
        world.enable()
        settings.setGroupEnabled(AlertGroup.BANK_SMS, false)
        world.receive(sms("m1", UNCLEAR))
        cycle().run(now)
        assertTrue(notifier.posted.isEmpty(), "مقفولة ⇒ مفيش إشعار جوال")
        val line = inbox.listAll().single()
        assertEquals(AlertKind.SMS_CONFIRM, line.kind)
        assertTrue(line.decision.muted, "§61: بتفضل في صفحة الإشعارات معلّمة «مقفولة»")
        assertTrue(engine.inbox().single().muted)
    }

    @Test fun unmappedBankWithTwoAccountsCountsAsWaitingAndNotifies() = runBlocking<Unit> {
        val multi = SmsWorld(listOf(SmsSpace(wallets = listOf(BANK, BANK2)))).enable()
        multi.receive(sms("m1", CAFE))
        val result = RunBackgroundCycle(BackgroundCycleDeps(multi.auto(), { others }, engine, notifier)).run(now)
        assertEquals(0, result.sms!!.recorded)
        assertEquals(1, notifier.posted.size, "مستنية اختيار المحفظة ⇒ «رسايل محتاجة تأكيدك»")
        assertTrue(inbox.listAll().single().title.contains("1"))
    }

    @Test fun otherAlertsCausedByAnAutoRecordedTransactionStillFire() = runBlocking<Unit> {
        // رد المالك ٢: السكوت عن العملية نفسها بس — «عدّيت الميزانية» بسببها بيشتغل عادي
        world.enable()
        world.receive(sms("m1", CAFE), sms("m2", MART))
        val space = world.spaces.single()
        val withBudget = RunBackgroundCycle(
            BackgroundCycleDeps(world.auto(), {
                val spent = space.all().sumOf { it.amountMinor }
                budgetAlertCandidates(buildBudgetNotifications("2026-09-28", budgetStatus(5_000, spent, 80), 80, emptyList(), spentKnown = true))
            }, engine, notifier),
        )
        val result = withBudget.run(now)
        assertEquals(2, result.sms!!.recorded)
        assertEquals(listOf(AlertKind.BUDGET_EXCEEDED), inbox.listAll().map { it.kind }, "الميزانية من العمليات اللي اتسجلت لوحدها")
        assertEquals(1, notifier.posted.size, "إشعار الميزانية بس — مفيش إشعار للتسجيل نفسه")
    }

    @Test fun engineDoesNotRunWithoutTheFullCandidateList() = runBlocking<Unit> {
        world.enable()
        world.receive(sms("m1", UNCLEAR))
        val result = cycle(withCandidates = false).run(now)
        assertNull(result.alerts, "من غير باقي المرشحين المحرك كان هيمسح الصفحة ⇒ ما بيشتغلش")
        assertTrue(notifier.posted.isEmpty())
        assertEquals(listOf("m1"), result.sms!!.waiting)
    }

    @Test fun smsFailureDoesNotStopTheOtherAlerts() = runBlocking<Unit> {
        world.enable()
        world.receive(sms("m1", CAFE))
        world.inbox.failAcks = 1
        others = listOf(AlertCandidate(AlertKind.DUE_TODAY, "due|x", "قسط وهمي", "تفاصيل"))
        val result = cycle().run(now)
        assertTrue(result.smsFailed)
        assertEquals(1, notifier.posted.size, "التنبيهات التانية اتبعتت")
    }
}
