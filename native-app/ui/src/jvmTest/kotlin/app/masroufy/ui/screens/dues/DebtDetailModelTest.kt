package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.ObligationKind
import app.masroufy.core.dayMonth
import app.masroufy.ui.components.amountLabel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «تفاصيل الدين» ولوحتا «سداد/تحصيل» و«دين قديم»: الالتزام من `listWithBalances` + ميعاده ⇒ حالة الصفحة، وفحص المبلغ قبل `settle`
 * (مقارنة بالمتبقي بس — الزيادة ممنوعة)، و`addOpeningDebt` مقفول في عملة غير الريال.
 */
class DebtDetailModelTest {
    private val people = listOf(
        personRow("p1", "فهد", obligation("o1", "p1", ObligationKind.RECEIVABLE, 150_000) to 120_000),
        personRow(
            "p2", "عمر",
            obligation("o2", "p2", ObligationKind.LOAN_PAYABLE, 200_000, opening = true) to 200_000,
            obligation("o3", "p2", ObligationKind.CUSTODY_PAYABLE, 30_000) to 30_000,
            obligation("o5", "p2", ObligationKind.LOAN_PAYABLE, 10_000) to 4_000,
        ),
    )
    private val dues = listOf(debtDue("o1", "2026-11-15", 120_000, receive = true))

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun aDebtOwedToYouWithADueDate() {
        val ui = assertNotNull(debtDetailUi(people, dues, "o1", TODAY))
        assertTrue(ui.forYou)
        assertEquals("فهد", ui.personName)
        assertEquals("ف", ui.initial)
        assertEquals("المتبقي لك عنده", ui.remainLabel)
        assertEquals(120_000L, ui.remainingMinor, "المتبقي من حالة الاستخدام زي ما هو")
        assertEquals("من أصل " + amountLabel(150_000, Currency.SAR, showCurrency = false), ui.ofOriginal, "اتسدد جزء ⇒ «من أصل» بس (المسدَّد مش متاح)")
        assertEquals("عملية مسجّلة", ui.originValue)
        assertNull(ui.originHint)
        assertEquals("دين لك", ui.typeText)
        assertEquals("منفصل عن الأمانات — وبلا مقاصة مع «عليك»", ui.typeHint)
        assertEquals("2026-11-15", ui.dueAt)
        assertEquals("موعدها " + dayMonth("2026-11-15") + "، دفعة واحدة", ui.termsValue)
        assertFalse(ui.done)
    }

    @Test fun anOldDebtYouOweNothingPaidYet() {
        val ui = assertNotNull(debtDetailUi(people, dues, "o2", TODAY))
        assertFalse(ui.forYou)
        assertTrue(ui.opening)
        assertEquals("المتبقي عليك له", ui.remainLabel)
        assertEquals("من أصل " + amountLabel(200_000, Currency.SAR, showCurrency = false) + "، لم يُسدَّد شيء بعد", ui.ofOriginal, "المتبقي = الأصل (مقارنة)")
        assertEquals("من قبل التطبيق", ui.originValue)
        assertEquals("بلا عملية — لا يدخل المصروف ولا الدخل ولا الكاش", ui.originHint)
        assertEquals("دين قديم عليك", ui.typeText)
        assertEquals("بلا موعد", ui.termsValue)
        assertNull(ui.signal)
    }

    @Test fun custodyAndLoanTypes() {
        assertEquals("أمانة عندك", debtDetailUi(people, dues, "o3", TODAY)?.typeText)
        assertEquals("دين عليك", debtDetailUi(people, dues, "o5", TODAY)?.typeText)
    }

    @Test fun aMissingOrSettledDebtIsNullAndSettledFullyShowsDone() {
        assertNull(debtDetailUi(people, dues, "gone", TODAY), "`listWithBalances` بيشيل المسدَّد بالكامل")
        val done = assertNotNull(debtDetailUi(people, dues, "o1", TODAY)).settledFully()
        assertTrue(done.done)
        assertEquals(0L, done.remainingMinor)
        assertTrue(done.termOptions.isEmpty())
    }

    @Test fun termOptionsAreTheFirstOfTheNextThreeMonths() {
        assertEquals(listOf("2026-11-01", "2026-12-01", "2027-01-01"), termOptions(TODAY).map { it.date })
        assertEquals(dayMonth("2026-11-01"), termOptions(TODAY).first().label)
    }

    @Test fun settleAmountIsComparedWithTheRemainingOnly() {
        assertEquals(SettleInput.Empty, settleInput(" ", 120_000, Currency.SAR))
        assertEquals(SettleInput.BadFormat, settleInput("abc", 120_000, Currency.SAR))
        assertEquals(SettleInput.NotPositive, settleInput("0", 120_000, Currency.SAR))
        assertEquals(SettleInput.OverRemaining, settleInput("1200.01", 120_000, Currency.SAR), "الزيادة ممنوعة — ما بتتبلعش")
        assertEquals(SettleInput.Ok(30_000, full = false), settleInput("300", 120_000, Currency.SAR))
        assertEquals(SettleInput.Ok(120_000, full = true), settleInput("1200", 120_000, Currency.SAR))
        assertEquals("يجب ألا يتجاوز المبلغ المتبقي (" + amountLabel(120_000, Currency.SAR) + ").", SettleInput.OverRemaining.errorText(120_000, Currency.SAR))
        assertNull(SettleInput.Empty.errorText(120_000, Currency.SAR), "فاضي ⇒ من غير خطأ (الزرار بس معطّل)")
    }

    @Test fun toastAfterSaving() {
        val target = assertNotNull(debtDetailUi(people, dues, "o1", TODAY)).settleTarget()
        assertEquals("سُجّل التحصيل من فهد", settledToast(SettleInput.Ok(100, false), target))
        assertEquals("سُدّد الدين بالكامل", settledToast(SettleInput.Ok(120_000, true), target))
        val onYou = assertNotNull(debtDetailUi(people, dues, "o5", TODAY)).settleTarget()
        assertEquals("سُجّل السداد لـعمر", settledToast(SettleInput.Ok(100, false), onYou))
        useArabic(ArabicVariant.EGYPTIAN)
        assertEquals("اتسجّل التحصيل من فهد", settledToast(SettleInput.Ok(100, false), target))
    }

    @Test fun openingDebtNeedsASideAndRiyals() {
        assertEquals(OpeningInput.NoSide, openingInput(null, "100", Currency.SAR))
        assertEquals(OpeningInput.BadAmount, openingInput(ObligationKind.RECEIVABLE, "x", Currency.SAR))
        assertEquals(OpeningInput.NotPositive, openingInput(ObligationKind.RECEIVABLE, "0", Currency.SAR))
        assertEquals(OpeningInput.Ok(ObligationKind.LOAN_PAYABLE, 10_050), openingInput(ObligationKind.LOAN_PAYABLE, "100.50", Currency.SAR))
        val egypt = openingInput(ObligationKind.RECEIVABLE, "100", Currency.EGP)
        assertIs<OpeningInput.CurrencyNotReady>(egypt, "`addOpeningDebt` بيحفظ بالريال ⇒ مصر مقفولة لحد ما حالة الاستخدام تاخد العملة")
        useArabic(ArabicVariant.EGYPTIAN)
        assertEquals("تسجيل الدين القديم بالجنيه لسه مش متاح.", egypt.errorText())
        assertEquals("اختار: ليك عنده ولا عليك ليه.", OpeningInput.NoSide.errorText())
    }
}
