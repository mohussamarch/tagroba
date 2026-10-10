package app.masroufy.device

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.masroufy.port.SmsInboxState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * §83 «يقرا كل التاريخ ويكمّل الناقص لوحده» على محاكي أندرويد — **رسايل وهمية**: `scripts/device/sendTestSms.sh` بيبعت من `5550001` قبل الاختبارات
 * (شراء «TEST-DEMAND» + رمز OTP)، فهي **أقدم** من تفعيل الصندوق في الاختبار ده:
 * - الصندوق الجديد بيبدأ من دلوقتي (الرسالة القديمة مش فيه) · أقدم رسالة على الجوال معروفة · `readFrom` بيجيبها (والرمز لأ) · الأرضية بتنزل.
 * - بعد ما تخلص (اتسجلت/اتشالت) ورجعنا نقرا من نفس النقطة ⇒ **ما بتدخلش تاني**.
 * - الإيقاف وبعده التفعيل بيكمّل من نفس المؤشر (الفترة اللي القراية كانت واقفة فيها بتتقري).
 * من غير السكربت الاختبار بيفشل — مش بيتخطّى.
 */
@RunWith(AndroidJUnit4::class)
class SmsHistoryOnDeviceTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun AndroidSmsInbox.drain(): SmsInboxState {
        var state = sync()
        repeat(100) { if (state.more) state = sync() }
        return state
    }

    @Test fun readsTheOlderMessagesFromAPointAndNeverTwice() = runBlocking<Unit> {
        val inbox = AndroidSmsInbox(context, "kt-history-" + System.nanoTime())
        inbox.enable(listOf("5550001"))
        val marks = assertNotNull(inbox.readMarks())
        val oldest = assertNotNull(inbox.oldestMessageAt(listOf("5550001")), "the script sends 5550001 before the tests")
        assertTrue(Instant.parse(oldest) < Instant.parse(marks.floor), "older than the link")
        assertTrue(inbox.drain().messages.none { "TEST-DEMAND" in it.body }, "a new inbox starts from now")

        inbox.readFrom(oldest)
        val state = inbox.drain()
        val demand = state.messages.filter { "TEST-DEMAND" in it.body }
        assertTrue(demand.isNotEmpty(), "the history is read")
        assertTrue(state.messages.none { "OTP" in it.body }, "codes never enter")
        assertEquals(oldest, assertNotNull(inbox.readMarks()).floor)
        assertNotNull(state.oldestQueuedAt)

        // خلصت ⇒ نفس النقطة تاني ما بتدخلهاش
        inbox.acknowledge(state.messages.map { it.id })
        inbox.readFrom(oldest)
        assertTrue(inbox.drain().messages.none { "TEST-DEMAND" in it.body }, "never twice")

        // الإيقاف والتفعيل ما بيرجّعوش المؤشر لدلوقتي
        val point = assertNotNull(inbox.readMarks()).point
        inbox.disable()
        inbox.enable(listOf("5550001"))
        assertEquals(point, assertNotNull(inbox.readMarks()).point)
        inbox.disable()
    }
}
