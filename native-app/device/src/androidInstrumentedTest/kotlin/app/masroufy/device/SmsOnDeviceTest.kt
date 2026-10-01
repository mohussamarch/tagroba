package app.masroufy.device

import android.Manifest
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * رسايل البنك على محاكي أندرويد — **رسايل وهمية** بتتبعت للمحاكي من الكمبيوتر (`adb emu sms send`) زي ما الشبكة بتبعتها:
 * - [readsOnDemand]: الكمبيوتر بيبعت قبل الاختبار من `5550001` (شراء + رمز OTP) ⇒ القراية بالطلب بتجيب الشراء بس.
 * - [inboxCatchesNewMessages]: الاختبار بيفعّل الصندوق لـ`5550002` ويكتب «READY» في السجل ⇒ الكمبيوتر بيبعت وقتها
 *   (شراء فيه رقم حساب + رمز OTP) ⇒ الصندوق فيه الشراء **مرة واحدة** ورقم الحساب متقص، والـOTP مش موجود خالص.
 * من غير الكمبيوتر (`scripts/device/sendTestSms.sh`) الاختبارين بيفشلوا — مش بيتخطّوا.
 */
@RunWith(AndroidJUnit4::class)
class SmsOnDeviceTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Africa/Cairo")

    @Test fun readsOnDemand() = runBlocking<Unit> {
        val today = LocalDate.now(zone)
        val read = AndroidBankSms(context, zone).read(today.minusDays(1).toString(), today.plusDays(1).toString(), listOf("5550001"))
        assertTrue(read.messages.any { "TEST-DEMAND" in it.body }, "رسالة الشراء اتقرت")
        assertTrue(read.messages.none { "OTP" in it.body }, "رسالة الرمز ما بتطلعش")
    }

    @Test fun inboxCatchesNewMessages() = runBlocking<Unit> {
        val inbox = AndroidSmsInbox(context, "kt-sms-" + System.nanoTime())
        inbox.enable(listOf("5550002"))
        Log.i("MasroufySms", "READY")
        val state = withTimeout(120_000) {
            while (true) {
                val s = inbox.sync()
                if (s.messages.any { "TEST-INBOX" in it.body }) {
                    // نستنى ثانيتين كمان: لو الاستقبال والمزامنة الاتنين لقطوها، لازم تفضل مرة واحدة
                    delay(2_000)
                    return@withTimeout inbox.sync()
                }
                delay(1_000)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        val purchases = state.messages.filter { "TEST-INBOX" in it.body }
        assertEquals(1, purchases.size, "الرسالة مرة واحدة")
        assertTrue("9876543210" !in purchases.single().body && "••••3210" in purchases.single().body, "رقم الحساب اتقص قبل الحفظ")
        assertTrue(state.messages.none { "OTP" in it.body }, "رسالة الرمز ما اتحفظتش")
        assertEquals(0, inbox.acknowledge(purchases.map { it.id }).count, "اللي خلص بيطلع من الصندوق")
        assertTrue(!inbox.disable().enabled)
    }
}
