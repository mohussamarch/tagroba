package app.masroufy.device

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.masroufy.core.AlertKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.isLockSafe
import app.masroufy.core.parseBankSms
import app.masroufy.core.systemNoticeFor
import app.masroufy.memory.MemoryDeviceNotifier
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.port.NoticeOutcome
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * الشغل في الخلفية والإشعارات على محاكي أندرويد (OVERRIDES §72) — من غير رسايل حقيقية: الصندوق في الذاكرة، والعامل والإشعار والـWorkManager
 * بتوع أندرويد الحقيقيين. الرسالة الحقيقية من الشبكة لحد العملية في `SmsAutoRecordOnDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundWorkTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After fun cleanUp() {
        MasroufyBackground.uninstall()
        manager.cancelAll()
    }

    private fun worker(attempt: Int = 0) = TestListenableWorkerBuilder<MasroufyCycleWorker>(context).setRunAttemptCount(attempt).build()

    @Test fun withNothingRegisteredTheWorkerSucceedsAndDoesNothing() = runBlocking<Unit> {
        MasroufyBackground.uninstall()
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test fun workerRunsTheRegisteredCycleAndRecordsWithoutNotifying() = runBlocking<Unit> {
        val inbox = MemorySmsInbox(listOf(QueuedSms("m1", "TESTBANK", "2026-10-07T10:00:00Z", "شراء\nبـSR 25\nلدى:TEST CAFE\n26/10/07")), available = true)
        inbox.enable(listOf("TESTBANK"))
        // §77-A (S1): المالك أكّد قبل كده رسالة بنفس الشكل — من غيره أول رسالة من الشكل بتستنى
        inbox.preLearn("sa", ::parseBankSms, inbox.sync().messages.single())
        val notifier = MemoryDeviceNotifier()
        val graph = TestBackgroundGraph(inbox, notifier)
        MasroufyBackground.install(context, graph)
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, graph.runs)
        assertEquals(2_500L, graph.txns.listByDateRange("0000-01-01", "9999-12-31").single().amountMinor)
        assertTrue(notifier.posted.isEmpty(), "المسجّل لوحده ما بيطلّعش إشعار")
        val periodic = WorkManager.getInstance(context).getWorkInfosForUniqueWork(MasroufyBackground.PERIODIC_WORK).get()
        assertTrue(periodic.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }, "الدورة الدورية اتسجلت: $periodic")
    }

    @Test fun aFailingCycleIsRetriedAFewTimesThenLeftToThePeriodicRun() = runBlocking<Unit> {
        val broken = object : SmsInboxPort by MemorySmsInbox(available = true) {
            override suspend fun sync(): SmsInboxState = throw IllegalStateException("storage unavailable")
        }
        MasroufyBackground.install(context, TestBackgroundGraph(broken, MemoryDeviceNotifier()))
        assertEquals(ListenableWorker.Result.retry(), worker(0).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(MasroufyBackground.MAX_ATTEMPTS - 1).doWork())
    }

    private fun shown(tag: String): Notification? = manager.activeNotifications.firstOrNull { it.tag == tag }?.notification

    /** النظام بيضيف الإشعار بعد `notify` بشوية (مش في نفس اللحظة) ⇒ نستنى لحد 5 ثواني. */
    private suspend fun awaitShown(tag: String): Notification? {
        repeat(50) {
            shown(tag)?.let { return it }
            delay(100)
        }
        return null
    }

    @Test fun phoneNotificationIsPrivateAndTheLockScreenVersionHasNoAmountsOrNames() = runBlocking<Unit> {
        val notice = systemNoticeFor(AlertKind.SMS_CONFIRM)
        assertEquals(NoticeOutcome.SHOWN, AndroidDeviceNotifier(context).post("alert-test-now", notice, null))
        val n = assertNotNull(awaitShown("alert-test-now"), "الإشعار ظهر فعلًا في النظام")
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, n.visibility)
        assertEquals(AndroidDeviceNotifier.CHANNEL_ALERTS, n.channelId)
        val public = assertNotNull(n.publicVersion, "نسخة شاشة القفل موجودة")
        for (text in listOf(n, public).flatMap { listOf(it.extras.getCharSequence(Notification.EXTRA_TITLE), it.extras.getCharSequence(Notification.EXTRA_TEXT)) }) {
            assertTrue(text != null && isLockSafe(text.toString()), "شاشة القفل: $text")
        }
        assertEquals(notice.body, public.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun textWithAnAmountIsNeverShown() = runBlocking<Unit> {
        assertTrue(!AndroidDeviceNotifier.show(context, "alert-test-unsafe", "عندك عملية", "شراء 25 ر.س من محل"), "الفحص لحظة العرض")
        assertEquals(null, awaitShown("alert-test-unsafe"))
    }

    @Test fun laterNoticesWaitInWorkManager() = runBlocking<Unit> {
        val later = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 3) }
        val at = LocalMoment("%04d-%02d-%02d".format(later.get(Calendar.YEAR), later.get(Calendar.MONTH) + 1, later.get(Calendar.DAY_OF_MONTH)), later.get(Calendar.HOUR_OF_DAY))
        assertEquals(NoticeOutcome.SCHEDULED, AndroidDeviceNotifier(context).post("alert-test-later", systemNoticeFor(AlertKind.SMS_CONFIRM), at))
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("notice-alert-test-later").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(null, shown("alert-test-later"), "لسه ما ظهرش")
        WorkManager.getInstance(context).cancelUniqueWork("notice-alert-test-later")
    }
}
