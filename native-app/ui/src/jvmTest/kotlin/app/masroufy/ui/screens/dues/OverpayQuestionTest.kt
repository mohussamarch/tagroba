package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Overpay
import app.masroufy.core.OverpayChoice
import app.masroufy.ui.components.amountLabel
import app.masroufy.usecase.OverpayOutcome
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** §82-19 في لوحة السداد: الزيادة على دين **عليك** سؤال مش خطأ، والأرقام في الجملة والرسالة من حالة الاستخدام زي ما هي. */
class OverpayQuestionTest {
    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun overOnADebtYouOweIsAQuestionNotAnError() {
        assertEquals(SettleInput.Over(150_000), settleInput("1500", 120_000, Currency.SAR, allowOver = true))
        assertNull(SettleInput.Over(150_000).errorText(120_000, Currency.SAR))
        assertEquals(SettleInput.OverRemaining, settleInput("1500", 120_000, Currency.SAR), "الدين ليك ⇒ الزيادة ممنوعة زي الأول")
        assertEquals(SettleInput.Ok(120_000, full = true), settleInput("1200", 120_000, Currency.SAR, allowOver = true))
    }

    @Test fun theFourChoicesInTheOwnersOrder() {
        assertEquals(listOf("أمانة لك عنده", "دعم", "سلفة جديدة له", "خطأ في المبلغ"), OVERPAY_CHOICES.map(::overpayLabel))
        useArabic(ArabicVariant.EGYPTIAN)
        assertEquals(listOf("أمانة ليك عنده", "دعم", "سلفة جديدة ليه", "غلطة في المبلغ"), OVERPAY_CHOICES.map(::overpayLabel))
    }

    @Test fun bodyAndToastShowTheUseCaseNumbers() {
        val o = Overpay(50_000, 70_000, 20_000)
        val c = Currency.SAR
        assertEquals("عليك ${amountLabel(50_000, c)} فقط ودفعت ${amountLabel(70_000, c)} — الزيادة ${amountLabel(20_000, c)}. ماذا تكون؟", overpayBody(o, c))
        val out = OverpayOutcome(emptyList(), null, o)
        assertEquals("سُدّد الدين، و${amountLabel(20_000, c)} أمانة لك عند عمر", overpaySavedToast(out, OverpayChoice.HELD_FOR_YOU, "عمر", c))
        assertNull(overpaySavedToast(out.copy(editAmount = true), OverpayChoice.WRONG_AMOUNT, "عمر", c))
    }
}
