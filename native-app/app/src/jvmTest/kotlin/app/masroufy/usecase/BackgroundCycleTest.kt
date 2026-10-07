package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.LocalMoment
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

    @Test fun mutedQuestionsStayOnThePageOnly() = runBlocking<Unit> {
        world.enable()
        settings.setGroupEnabled(AlertGroup.QUESTIONS, false)
        world.receive(sms("m1", UNCLEAR))
        cycle().run(now)
        assertTrue(notifier.posted.isEmpty())
        assertTrue(inbox.listAll().single().decision.factors.isNotEmpty())
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
