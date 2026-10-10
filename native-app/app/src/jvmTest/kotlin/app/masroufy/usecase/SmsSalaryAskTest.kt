package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.EconomicKind
import app.masroufy.core.PendingAsk
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §75-2 (قرار المالك 2026-10-08): «رسالة راتب من غير اسم جهة ⇒ يسأل مرة «ده راتبك؟» وبعدها لوحده». السؤال جوه التطبيق على الرسالة
 * ([SmsReviewLine.question]) + سطر في عدّ «محتاجة تأكيد» ([SmsAskSource]) — مفيش إشعار جديد. كل الرسايل والأسامي مخترعة.
 */
class SmsSalaryAskTest {
    private val salary = "راتب\nبـSR 9,500\n26/10/01"
    private val nextSalary = "راتب\nبـSR 9,750\n26/10/06"
    private val withPayer = "حوالة راتب\nمن: ACME SAMPLE CO\nبـSR 9,500\n26/10/01"

    private fun world() = SmsWorld(listOf(SmsSpace()), learnOnReceive = false)

    @Test fun aSalaryWithoutAPayerAsksOnceInTheAppAndInTheNeedsConfirmationCount() = runBlocking<Unit> {
        val w = world().enable()
        w.learn(sms("seed", salary)) // حتى لو الشكل اتعلّم، السؤال بيوقّفها
        w.receive(sms("s1", salary))
        val r = w.auto().run()
        assertEquals(0, r.recorded)
        assertEquals(listOf("s1"), r.waiting)
        val line = w.spaces.single().screen(w.inbox).load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertEquals(AskKind.IS_SALARY, line.question)
        assertEquals(uiText(TextKey.SMS_WAIT_IS_SALARY), line.confirmReason)
        assertEquals(listOf(PendingAsk(AskKind.IS_SALARY, "sa", messageId = "s1", date = "2026-10-01")), SmsAskSource(w.auto()).pending("2026-01-01", "2026-01-02"))
    }

    @Test fun yesMakesItAConfirmedSalaryAndTheNextOneRecordsItselfWithoutAsking() = runBlocking<Unit> {
        val w = world().enable()
        val space = w.spaces.single()
        w.receive(sms("s1", salary))
        w.auto().answerSalary("sa", "TestBank", true)
        assertEquals(1, w.auto().confirm(listOf("s1")))
        val first = space.all().single()
        assertEquals(Triple(EconomicKind.SALARY, true, ReviewState.CONFIRMED), Triple(first.economicKind, first.economicKindConfirmed, first.reviewState))

        w.receive(sms("s2", nextSalary))
        val r = w.auto().run()
        assertEquals(1, r.recorded, "نفس الشكل ونفس المرسل ⇒ لوحده ومن غير سؤال")
        assertTrue(SmsAskSource(w.auto()).pending("2026-10-01", "2026-10-31").isEmpty())
        val second = space.all().single { it.amountMinor == 975_000L }
        assertEquals(EconomicKind.SALARY to true, second.economicKind to second.economicKindConfirmed)
    }

    @Test fun noRecordsItUnclassifiedAndItIsNeverAskedAgain() = runBlocking<Unit> {
        val w = world().enable()
        val space = w.spaces.single()
        w.receive(sms("s1", salary))
        w.auto().answerSalary("sa", "TESTBANK", false)
        assertEquals(1, w.auto().confirm(listOf("s1")))
        assertEquals(EconomicKind.UNCLASSIFIED to false, space.all().single().let { it.economicKind to it.economicKindConfirmed })

        w.receive(sms("s2", nextSalary))
        assertEquals(1, w.auto().run().recorded, "ما بيتسألش تاني")
        assertTrue(space.all().none { it.economicKindConfirmed })
    }

    /** سؤال لكل رسالة مستنية (أي سبب) — «ده راتبك؟» بس على اللي عليها، والباقي «مستنية تأكيدك» — ونافذة الأيام ما بتشيلش حاجة. */
    @Test fun theAskSourceGivesOneAskPerWaitingMessageWhateverTheDateWindow() = runBlocking<Unit> {
        val w = world().enable()
        w.receive(sms("s1", salary), sms("k1", KEYWORD_ONLY), sms("u1", UNCLEAR), sms("n1", CAFE))
        val asks = SmsAskSource(w.auto()).pending("2030-01-01", "2030-01-02")
        assertEquals(
            listOf(
                PendingAsk(AskKind.IS_SALARY, "sa", messageId = "s1", date = "2026-10-01"),
                PendingAsk(AskKind.SMS_WAITING, "sa", messageId = "k1", date = "2026-10-07"),
                PendingAsk(AskKind.SMS_WAITING, "sa", messageId = "u1"),
                PendingAsk(AskKind.SMS_WAITING, "sa", messageId = "n1", date = "2026-10-07"),
            ),
            asks,
            "الراتب · الكلمات العامة · اللي ما اتفهمش · الشكل الجديد",
        )
    }

    @Test fun aSalaryWithAPayerKeepsThePayerQuestionAndNoIsSalary() = runBlocking<Unit> {
        val w = world().enable()
        val space = w.spaces.single()
        w.receive(sms("p1", withPayer))
        val line = space.screen(w.inbox).load(SmsReviewTarget(BANK.id, BANK.name)).ready.single()
        assertNull(line.question, "الجهة مكتوبة ⇒ سؤال «ده مرتب من …؟» (§64) مش «ده راتبك؟»")
        assertEquals(TextKey.SMS_WAIT_NEW_SHAPE, line.waitReason)
        // حتى لو المرسل اترد عليه «أيوه» قبل كده، الرسالة اللي فيها جهة ما بتتلمسش هنا
        w.auto().answerSalary("sa", "TESTBANK", true)
        assertEquals(1, w.auto().confirm(listOf("p1")))
        assertFalse(space.all().single().economicKindConfirmed)
    }
}
