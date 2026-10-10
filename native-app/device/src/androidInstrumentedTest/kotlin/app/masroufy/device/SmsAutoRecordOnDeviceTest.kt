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
 * (الجولة الرابعة) + رسالة اتجاهها مش واضح. **§77-A «وضع التعلّم» (S1):** الشراء المعروف **أول رسالة من شكله** ⇒ التلاتة بيستنوا
 * (الاستقبال ⇒ WorkManager ⇒ العامل) ومعاهم إشعار عام (شاشة القفل من غير مبلغ ولا محل). الاختبار بيأكد الشراء المعروف (زي المالك)
 * ويكتب «LEARNED» ⇒ السكربت بيبعت شراء **تاني بنفس الشكل** ⇒ لازم **يتسجل لوحده** من غير إشعار. من غير الكمبيوتر الاختبار بيفشل
 * (مش بيتخطّى). النصوص نفسها متجربة على الكمبيوتر في `SmsDeviceScriptTest`.
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
        suspend fun all() = graph.txns.listByDateRange("0000-01-01", "9999-12-31")
        // ١) التلاتة وصلوا وبيستنوا (الشراء المعروف شكله جديد — §77-A) ومعاهم إشعار
        withTimeout(180_000) {
            while (inbox.sync().messages.size < 3 || ours().none { it.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == lockText }) {
                delay(1_000)
            }
        }
        delay(3_000) // لو دورة تانية اشتغلت لازم ما تسجّلش حاجة
        assertTrue(all().isEmpty(), "أول رسالة من الشكل ما بتتسجلش لوحدها")

        // ٢) المالك أكّد الشراء المعروف ⇒ اتسجل واتعلّم شكله ⇒ السكربت بيبعت شراء تاني بنفس الشكل
        val known = inbox.sync().messages.single { "TEST CAFE" in it.body }
        assertEquals(1, graph.auto.confirm(listOf(known.id)))
        Log.i("MasroufySmsAuto", "LEARNED")
        withTimeout(180_000) { while (all().size < 2) delay(1_000) }
        delay(3_000)

        val txns = all()
        assertEquals(mapOf(2_500L to "TEST CAFE", 4_000L to "TEST MART"), txns.associate { it.amountMinor to it.rawMerchantName }, "التاني بنفس الشكل اتسجل لوحده")
        val waiting = inbox.sync().messages
        assertEquals(2, waiting.size, "اللي ما اتفهمش واللي اتفهم من كلمات عامة بس فضلوا في الصندوق")
        assertTrue(waiting.any { "TEST-WAIT" in it.body } && waiting.any { "TEST-FALLBACK" in it.body }, "$waiting")

        val notes = ours()
        // للمستني بس، والمسجّل مالوش إشعار: تلات رسايل مستنية ممكن يوصلوا في تلات دورات ⇒ إشعار لكل «أحدث مستنية» (§72 اختيار ٤) — بالكتير 3
        assertTrue(notes.size in 1..3, "إشعار للمستني بس: ${notes.size}")
        for (n in notes) {
            assertEquals(NotificationCompat.VISIBILITY_PRIVATE, n.visibility)
            for (shown in listOf(n, n.publicVersion!!)) {
                val text = "${shown.extras.getCharSequence(Notification.EXTRA_TITLE)} ${shown.extras.getCharSequence(Notification.EXTRA_TEXT)}"
                assertTrue(isLockSafe(text) && "CAFE" !in text && "SHOP" !in text && "MART" !in text, "شاشة القفل: $text")
            }
        }
        val runs = WorkManager.getInstance(context).getWorkInfosForUniqueWork(MasroufyBackground.SMS_WORK).get()
        assertTrue(runs.any { it.state == WorkInfo.State.SUCCEEDED }, "الاستقبال طلب دورة والعامل خلّصها: $runs")
        inbox.disable()
    }
}
