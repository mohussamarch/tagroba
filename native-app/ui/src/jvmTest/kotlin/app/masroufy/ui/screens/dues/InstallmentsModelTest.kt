package app.masroufy.ui.screens.dues

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.dayMonth
import app.masroufy.core.dueProgress
import app.masroufy.core.installmentSchedule
import app.masroufy.core.monthName
import app.masroufy.core.sentenceNumber
import app.masroufy.usecase.InstallmentView
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الأقساط» و«تفاصيل القسط» و«خطة جديدة»: `ManageInstallments.list` (الخطة + `DueProgress`) ⇒ البطاقات والجدول، وفحص الخانات قبل `save`.
 * التقدم والمتبقي من حالة الاستخدام (`dueProgress`)؛ الشاشة بتختار الكلام بس.
 */
class InstallmentsModelTest {
    private val phone = InstallmentPlan(
        "i1", "تقسيط الجوال", "متجر الأجهزة", InstallmentKind.PURCHASE_PLAN, Currency.SAR,
        principalMinor = 360_000, totalMinor = 360_000, installmentMinor = 30_000, cycleMonths = 1, firstDueAt = "2026-05-27",
    )
    private val car = InstallmentPlan(
        "i2", "تمويل السيارة", "البنك", InstallmentKind.FINANCING, Currency.SAR,
        principalMinor = 1_000_000, totalMinor = 1_200_000, installmentMinor = 50_000, cycleMonths = 1, firstDueAt = "2026-01-27", hasInterest = true,
    )

    private fun view(p: InstallmentPlan, paid: Halalas, received: Halalas? = null) =
        InstallmentView(p, dueProgress(installmentSchedule(p), paid, TODAY), received)

    @BeforeTest fun arabic() = useArabic()

    @AfterTest fun reset() = resetTexts()

    @Test fun cardsCarryTheUseCaseProgressAndPickTheRightWords() {
        val ui = installmentsUi(listOf(view(phone, 120_000), view(car, 450_000, received = 1_000_000)), totals(installments = 990_000), TODAY, Currency.SAR)
        assertEquals(990_000L, ui.leftMinor, "المتبقي من `DuesTotals` زي ما هو")
        assertEquals("خطتان", ui.plansCount)
        val (p, c) = ui.cards
        assertEquals("تقسيط مشتريات، متجر الأجهزة", p.kindText)
        assertEquals("دفعت " + sentenceNumber(4) + " من " + sentenceNumber(12), p.progressText)
        assertEquals("متأخر منذ " + dayMonth("2026-09-27"), p.nextText)
        assertTrue(p.late)
        assertNull(p.receivedMinor, "تقسيط مشتريات ما فيهوش مبلغ مستلم")
        assertEquals("تمويل بنكي، بأرباح، البنك", c.kindText)
        assertEquals("القادم " + dayMonth("2026-10-27"), c.nextText)
        assertFalse(c.late)
        assertEquals(1_000_000L, c.receivedMinor)
        assertTrue(c.financing)
    }

    @Test fun detailBuildsTheScheduleFromTheUseCaseProgress() {
        val d = planDetailUi(view(phone, 120_000), TODAY)
        assertEquals(240_000L, d.leftMinor, "المتبقي من `DueProgress`")
        assertEquals("المتبقي عليك", d.leftLabel)
        assertEquals(1, d.years.size)
        assertEquals(monthName(5) + " " + sentenceNumber(2026) + " – " + monthName(4) + " " + sentenceNumber(2027), d.years.single().label)
        assertEquals(List(4) { Cell.PAID } + Cell.LATE + List(7) { Cell.UP }, d.years.single().cells)
        assertEquals("مدفوع " + sentenceNumber(4) + " من " + sentenceNumber(12) + "، ومتأخر " + sentenceNumber(1), d.schedAria)
        assertEquals("يُحسب القسط كاملًا مصروفًا في «المستحقات ← تقسيط مشتريات».", d.expenseNote)
        val f = planDetailUi(view(car, 450_000), TODAY)
        assertEquals(2, f.years.size, "أكتر من ١٢ قسط ⇒ سطر لكل سنة")
        assertEquals("السنة " + sentenceNumber(1) + "، " + monthName(1) + " " + sentenceNumber(2026) + " – " + monthName(12) + " " + sentenceNumber(2026), f.years[0].label)
        assertEquals(Cell.NEXT, f.years[0].cells[9], "القسط العاشر هو الجاي")
        assertEquals("القادم " + dayMonth("2026-10-27") + "، القسط " + sentenceNumber(10), f.nextText)
        assertTrue(f.expenseNote.contains("«المستحقات ← تمويل»"))
    }

    @Test fun aFinishedPlanSaysSo() {
        val d = planDetailUi(view(phone, 360_000), TODAY)
        assertTrue(d.done)
        assertEquals("اكتملت الأقساط", d.leftLabel)
        assertEquals("اكتملت الأقساط", d.nextText)
        assertEquals(0L, d.leftMinor)
    }

    @Test fun emptyFormShowsAnErrorNextToEachField() {
        val check = checkPlanForm(PlanForm(), null, Currency.SAR)
        assertNull(check.input)
        assertEquals(setOf(PlanField.NAME, PlanField.PRINCIPAL, PlanField.TOTAL, PlanField.INSTALLMENT, PlanField.FIRST), check.errors.keys)
        assertEquals("اكتب المبلغ بالأرقام، مثل 1200.50", check.errors[PlanField.TOTAL])
        val zero = checkPlanForm(PlanForm(name = "x", principal = "0", total = "10", installment = "1", firstDueAt = "2026-11-01"), null, Currency.SAR)
        assertEquals("اكتب مبلغًا أكبر من صفر.", zero.errors[PlanField.PRINCIPAL])
    }

    @Test fun aSavedPlanRoundTripsThroughTheForm() {
        val v = view(car, 450_000)
        val check = checkPlanForm(PlanForm.of(v), car.id, Currency.SAR)
        assertTrue(check.errors.isEmpty())
        val input = assertNotNull(check.input)
        assertEquals(car.id, input.id)
        assertEquals(listOf(1_000_000L, 1_200_000L, 50_000L), listOf(input.principalMinor, input.totalMinor, input.installmentMinor))
        assertEquals(car.firstDueAt, input.firstDueAt)
        assertEquals(true, input.hasInterest)
        assertTrue(v.locked(), "فيه أقساط مدفوعة ⇒ النوع والعملة مقفولين")
        assertFalse(view(car, 0).locked())
    }

    @Test fun egyptWording() {
        useArabic(ArabicVariant.EGYPTIAN)
        val ui = installmentsUi(listOf(view(phone, 120_000)), totals(installments = 240_000), TODAY, Currency.EGP)
        assertEquals("خطة واحدة", ui.plansCount)
        assertEquals("متأخر من " + dayMonth("2026-09-27"), ui.cards.single().nextText)
        assertEquals("الباقي عليك", planDetailUi(view(phone, 120_000), TODAY).leftLabel)
    }
}
