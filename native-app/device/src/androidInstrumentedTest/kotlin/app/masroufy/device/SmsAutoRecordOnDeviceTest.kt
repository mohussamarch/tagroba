package app.masroufy.device

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.masroufy.core.AlertKind
import app.masroufy.core.isLockSafe
import app.masroufy.core.systemNoticeFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * من الشبكة لحد العملية (OVERRIDES §72) على محاكي أندرويد — **رسايل وهمية** بيبعتها الكمبيوتر (`adb emu sms send`) لما الاختبار يكتب
 * «READY» تحت `MasroufySmsAuto` (`scripts/device/sendTestSms.sh`): من `5550003` شراء **بشكل معروف** + شراء مفهوم من كلمات عامة بس
 * (الجولة الرابعة) + رسالة اتجاهها مش واضح. المتوقع: الاستقبال ⇒ WorkManager ⇒ العامل ⇒ الشراء المعروف **اتسجل لوحده** ومن غير
 * إشعار، والاتنين التانيين **فضلوا في الصندوق** وطلّعوا إشعار واحد عام (شاشة القفل من غير مبلغ ولا محل). من غير الكمبيوتر الاختبار
 * بيفشل (مش بيتخطّى). النصوص نفسها متجربة على الكمبيوتر في `SmsDeviceScriptTest`.
 */
@RunWith(AndroidJUnit4::class)
class SmsAutoRecordOnDeviceTest {
    @get:Rule val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After fun cleanUp() {
        MasroufyBackground.uninstall()
        manager.cancelAll()
    }

    @Test fun bankMessageIsRecordedInTheBackgroundAndOnlyTheUnclearOneNotifies() = runBlocking<Unit> {
        manager.cancelAll()
        val inbox = AndroidSmsInbox(context, "kt-auto-" + System.nanoTime())
        inbox.enable(listOf("5550003"))
        val graph = TestBackgroundGraph(inbox, AndroidDeviceNotifier(context))
        MasroufyBackground.install(context, graph)
        Log.i("MasroufySmsAuto", "READY")

        val lockText = systemNoticeFor(AlertKind.SMS_CONFIRM).body
        fun ours(): List<Notification> = manager.activeNotifications.filter { it.notification.channelId == AndroidDeviceNotifier.CHANNEL_ALERTS }.map { it.notification }
        withTimeout(180_000) {
            while (graph.txns.listByDateRange("0000-01-01", "9999-12-31").isEmpty() || ours().none { it.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == lockText }) {
                delay(1_000)
            }
        }
        delay(3_000) // لو دورة تانية اشتغلت (رسالة تانية) لازم ما تسجّلش تاني ولا تبعت تاني

        val txn = graph.txns.listByDateRange("0000-01-01", "9999-12-31").single()
        assertEquals(2_500L, txn.amountMinor, "الشراء اتسجل لوحده")
        assertEquals("TEST CAFE", txn.rawMerchantName)
        val waiting = inbox.sync().messages
        assertEquals(2, waiting.size, "اللي ما اتفهمش واللي اتفهم من كلمات عامة بس فضلوا في الصندوق")
        assertTrue(waiting.any { "TEST-WAIT" in it.body } && waiting.any { "TEST-FALLBACK" in it.body }, "$waiting")

        val notes = ours()
        // للمستني بس، والمسجّل مالوش إشعار: رسالتين مستنيين ممكن يوصلوا في دورتين ⇒ إشعار لكل «أحدث مستنية» (§72 اختيار ٤) — بالكتير 2
        assertTrue(notes.size in 1..2, "إشعار للمستني بس: ${notes.size}")
        for (n in notes) {
            assertEquals(NotificationCompat.VISIBILITY_PRIVATE, n.visibility)
            for (shown in listOf(n, n.publicVersion!!)) {
                val text = "${shown.extras.getCharSequence(Notification.EXTRA_TITLE)} ${shown.extras.getCharSequence(Notification.EXTRA_TEXT)}"
                assertTrue(isLockSafe(text) && "CAFE" !in text && "SHOP" !in text, "شاشة القفل: $text")
            }
        }
        val runs = WorkManager.getInstance(context).getWorkInfosForUniqueWork(MasroufyBackground.SMS_WORK).get()
        assertTrue(runs.any { it.state == WorkInfo.State.SUCCEEDED }, "الاستقبال طلب دورة والعامل خلّصها: $runs")
        inbox.disable()
    }
}
