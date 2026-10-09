package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.RoscaAnswer
import app.masroufy.core.RoscaFrequency
import app.masroufy.core.RoscaQuestion
import app.masroufy.core.RoscaShare
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.amountLabel
import app.masroufy.usecase.RoscaSetup
import app.masroufy.usecase.RoscaSetupState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * معالج «جمعية جديدة» فوق `RoscaSetup` الحقيقية (من غير تخزين): الخانات ⇒ الإجابة، اختيار الأدوار، ترتيب الأسئلة، والتحليل قبل الحفظ
 * من `preview` حالة الاستخدام (الشاشة ما بتحسبش قيمة الدور ولا المكسب).
 */
class RoscaWizardModelTest {
    private val setup = RoscaSetup()

    private fun answered(turns: List<Int>): RoscaSetupState {
        var s = setup.begin(Currency.SAR)
        val answers = listOf(
            RoscaAnswer.Name("جمعية الدوام"), RoscaAnswer.TurnsCount(10), RoscaAnswer.ShareAmount(100_000), RoscaAnswer.Frequency(RoscaFrequency.MONTHLY),
            RoscaAnswer.FirstDate("2026-11-01"), RoscaAnswer.Share(RoscaShare.ONE), RoscaAnswer.MyTurns(turns),
        )
        for (a in answers) s = setup.answer(s.draft, a)
        if (turns.isNotEmpty()) s = setup.answer(s.draft, RoscaAnswer.Payout(null))
        return s
    }

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun fieldsBecomeAnswersOrAnErrorBesideTheQuestion() {
        val i = WizardInputs(amount = "1000", first = null, turns = listOf(3))
        assertEquals(RoscaAnswer.ShareAmount(100_000) to null, answerOf(RoscaQuestion.SHARE_AMOUNT, i, Currency.SAR))
        val bad = answerOf(RoscaQuestion.SHARE_AMOUNT, i.copy(amount = "abc"), Currency.SAR)
        assertNull(bad.first)
        assertNotNull(bad.second)
        assertEquals(null to "اختر يوم أول دفعة.", answerOf(RoscaQuestion.FIRST_DATE, i, Currency.SAR))
        assertEquals(RoscaAnswer.MyTurns(listOf(3)) to null, answerOf(RoscaQuestion.MY_TURN, i, Currency.SAR))
        assertEquals(RoscaAnswer.MyTurns(emptyList()) to null, answerOf(RoscaQuestion.MY_TURN, i.copy(turns = emptyList(), unknownTurn = true), Currency.SAR), "«غير معروف بعد»")
        assertEquals(null to "اختر دورك، أو «غير معروف بعد».", answerOf(RoscaQuestion.MY_TURN, i.copy(turns = emptyList()), Currency.SAR))
        assertEquals(RoscaAnswer.Payout(null) to null, answerOf(RoscaQuestion.PAYOUT, i, Currency.SAR), "فاضية ⇒ موافق على المحسوب")
    }

    @Test fun pickingTurnsDropsTheOldestWhenFull() {
        assertEquals(emptyList(), toggleTurn(listOf(1), 1, 1))
        assertEquals(listOf(2), toggleTurn(listOf(1), 2, 1))
        assertEquals(listOf(1, 2), toggleTurn(listOf(1), 2, 2))
        assertEquals(listOf(2, 3), toggleTurn(listOf(1, 2), 3, 2))
        assertEquals(2, stepCount(2, -1), "أقل من دورين ممنوع")
        assertEquals(60, stepCount(60, 1), "أكتر من ٦٠ ممنوع")
        assertEquals(11, stepCount(10, 1))
    }

    @Test fun questionOrderSkipsThePayoutWhenTheTurnIsUnknown() {
        val unknown = answered(emptyList())
        assertTrue(RoscaQuestion.PAYOUT !in wizardOrder(unknown.draft))
        assertEquals(RoscaQuestion.MY_TURN, previousQuestion(unknown.draft, null), "من التحليل ⇒ آخر سؤال")
        assertEquals(7 to 7, wizardStep(unknown, null))
        val known = answered(listOf(3))
        assertEquals(RoscaQuestion.PAYOUT, previousQuestion(known.draft, null))
        assertEquals(3 to 8, wizardStep(known, RoscaQuestion.SHARE_AMOUNT))
        assertNull(previousQuestion(known.draft, RoscaQuestion.NAME))
    }

    @Test fun summaryComesFromTheUseCasePreview() {
        val s = answered(listOf(3))
        val payout = assertNotNull(s.draft.payoutMinor, "قيمة الدور المحسوبة من حالة الاستخدام")
        val ui = assertNotNull(wizardSummary(s, "2026-10-09"))
        assertEquals("ستقبض", ui.heroLabel)
        assertEquals(dayMonth("2027-01-01") + " " + sentenceNumber(2027), ui.heroValue, "الدور التالت بعد شهرين من أول دفعة — والسنة لأنها مش السنة دي")
        assertEquals(amountLabel(payout, Currency.SAR), ui.heroSub)
        assertEquals(StatValue.Money(s.preview!!.totalPayMinor), ui.facts[0].value)
        assertEquals(8, ui.answers.size)
        assertEquals(sentenceNumber(3), ui.answers.first { it.question == RoscaQuestion.MY_TURN }.value)
        assertEquals("جمعية الدوام", ui.answers.first().value)
    }

    @Test fun unknownTurnSummaryHidesTheDateAndGain() {
        val ui = assertNotNull(wizardSummary(answered(emptyList()), "2026-10-09"))
        assertEquals("موعد قبضك", ui.heroLabel)
        assertNull(ui.heroValue)
        assertEquals("يظهر الموعد والربح حين يُعرف دورك.", ui.heroSub)
        assertEquals(StatValue.NA, ui.facts[2].value, "المكسب غير متاح")
        assertEquals(7, ui.answers.size, "من غير «قيمة الدور»")
        assertEquals("غير معروف بعد", ui.answers.last().value)
        useArabic(ArabicVariant.EGYPTIAN)
        assertEquals("لما دورك يتعرف هيبان الميعاد والمكسب.", wizardSummary(answered(emptyList()), "2026-10-09")?.heroSub)
        assertNull(wizardSummary(setup.begin(Currency.SAR), "2026-10-09"), "قبل ما الأسئلة تخلص مفيش تحليل")
    }
}
