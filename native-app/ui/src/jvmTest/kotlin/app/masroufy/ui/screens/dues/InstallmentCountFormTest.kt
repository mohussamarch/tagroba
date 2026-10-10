package app.masroufy.ui.screens.dues

import app.masroufy.core.Currency
import app.masroufy.core.InstallmentKind
import app.masroufy.core.installmentCountPreview
import app.masroufy.ui.components.amountLabel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §82-20 في «خطة جديدة»: «عدد الأقساط» بيغني عن الإجمالي (الحساب في حالة الاستخدام)، و«ادفع بعدين» من غير سعر. كل الأرقام مخترعة. */
class InstallmentCountFormTest {
    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    private val mattress = PlanForm(
        name = "مرتبة وهمية", kind = InstallmentKind.PURCHASE_PLAN, installment = "250", count = "4", firstDueAt = "2026-10-10", payLater = true,
    )

    @Test fun withACountTheTotalIsNotAskedAndThePriceIsOptionalForPayLater() {
        val check = checkPlanForm(mattress, null, Currency.SAR)
        assertTrue(check.errors.isEmpty(), check.errors.toString())
        assertEquals(4, check.count)
        val input = assertNotNull(check.input)
        assertEquals(listOf(25_000L, 0L, 0L), listOf(input.installmentMinor, input.totalMinor, input.principalMinor), "الإجمالي والسعر من حالة الاستخدام")
        assertEquals(Triple(25_000L, 4, true), mattress.previewInputs(Currency.SAR))
    }

    @Test fun aBadCountIsAnErrorNextToTheField() {
        for (bad in listOf("0", "361", "x")) {
            val check = checkPlanForm(mattress.copy(count = bad), null, Currency.SAR)
            assertNotNull(check.errors[PlanField.COUNT], bad)
            assertNull(check.count)
        }
        val noCount = checkPlanForm(mattress.copy(count = "", payLater = false), null, Currency.SAR)
        assertNotNull(noCount.errors[PlanField.TOTAL], "من غير عدد ⇒ الإجمالي مطلوب زي الأول")
        assertEquals(4, checkPlanForm(mattress.copy(count = "٤"), null, Currency.SAR).count, "الأرقام العربي بتتقري 0-9")
    }

    @Test fun payLaterIsOnlyForANewPurchasePlan() {
        assertTrue(mattress.payLaterShown(isNew = true))
        assertFalse(mattress.payLaterShown(isNew = false))
        assertFalse(mattress.copy(kind = InstallmentKind.FINANCING).payLaterShown(isNew = true))
    }

    @Test fun previewLinesShowTheUseCaseNumbers() {
        val c = Currency.SAR
        val lines = countPreviewLines(installmentCountPreview(25_000, 4, payLater = true), c)
        assertEquals("الإجمالي ${amountLabel(100_000, c)} = 4 × ${amountLabel(25_000, c, showCurrency = false)}", lines[0])
        assertEquals("دفعت ${amountLabel(25_000, c)} وقت الشراء، والباقي ${amountLabel(75_000, c)} على 3 أقساط قادمة", lines[1])
        assertEquals(1, countPreviewLines(installmentCountPreview(25_000, 4, payLater = false), c).size)
    }
}
