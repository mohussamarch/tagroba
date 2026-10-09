package app.masroufy.device

import android.Manifest
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §77-A «وضع التعلّم» و§75-2 «ده راتبك؟» على الجوال (S1): أشكال الرسايل اللي المالك أكّدها ورده على «ده راتبك؟» محفوظين على الجهاز
 * (`sms-inbox-settings`) — بيفضلوا بعد ما الصندوق يتفتح من جديد، **لكل صاحب حساب لوحده ولكل بلد لوحدها**، والمرسل بأي حالة حروف.
 * البصمات بس اللي بتتخزن (ولا حرف من نص رسالة). بيانات وهمية.
 */
@RunWith(AndroidJUnit4::class)
class SmsLearnedShapesOnDeviceTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("sms-inbox-settings", Context.MODE_PRIVATE)

    @Test fun learnedLayoutsSurviveANewInboxPerOwnerAndCountry() = runBlocking<Unit> {
        val uid = "kt-shapes-" + System.nanoTime()
        AndroidSmsInbox(context, uid).learnShapes("sa", "BankA ", setOf("AAAA1111BBBB2222", "CCCC3333DDDD4444"))
        AndroidSmsInbox(context, uid).learnShapes("eg", "banka", setOf("EEEE5555FFFF6666"))

        val again = AndroidSmsInbox(context, uid)
        assertEquals(mapOf("banka" to setOf("AAAA1111BBBB2222", "CCCC3333DDDD4444")), again.learnedShapes("sa"), "بعد صندوق جديد")
        assertEquals(mapOf("banka" to setOf("EEEE5555FFFF6666")), again.learnedShapes("eg"), "كل بلد لوحدها")
        assertEquals(emptyMap(), AndroidSmsInbox(context, "kt-other-" + System.nanoTime()).learnedShapes("sa"), "كل حساب لوحده")
        assertTrue(prefs.all.keys.filter { uid in it }.all { it.startsWith("smsShape|$uid|") }, "مفاتيح البصمات بس")

        again.forgetShapes("sa", "BANKA")
        assertEquals(emptyMap(), again.learnedShapes("sa"), "نسيان المرسل ⇒ رسالته الجاية تستنى تاني")
        assertEquals(mapOf("banka" to setOf("EEEE5555FFFF6666")), again.learnedShapes("eg"), "النسيان في بلد واحدة بس")
        again.forgetShapes("eg", "banka")
    }

    @Test fun theSalaryAnswerIsKeptPerOwnerCountryAndSender() = runBlocking<Unit> {
        val uid = "kt-salary-" + System.nanoTime()
        val inbox = AndroidSmsInbox(context, uid)
        assertNull(inbox.salaryAnswer("sa", "BANKA"), "لسه ما اتسألش")
        inbox.setSalaryAnswer("sa", "BankA", true)
        inbox.setSalaryAnswer("eg", "BANKA", false)
        assertEquals(true, AndroidSmsInbox(context, uid).salaryAnswer("sa", "banka"))
        assertEquals(false, AndroidSmsInbox(context, uid).salaryAnswer("eg", "banka"))
        assertNull(AndroidSmsInbox(context, "kt-other-" + System.nanoTime()).salaryAnswer("sa", "banka"))
        inbox.setSalaryAnswer("sa", "BANKA", null)
        assertNull(inbox.salaryAnswer("sa", "BANKA"))
        inbox.setSalaryAnswer("eg", "BANKA", null)
    }
}
