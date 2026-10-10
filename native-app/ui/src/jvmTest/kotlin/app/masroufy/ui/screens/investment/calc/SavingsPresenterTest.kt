package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.Language
import app.masroufy.core.Texts
import app.masroufy.core.averageSavingMinor
import app.masroufy.core.compareWithActual
import app.masroufy.core.periodForDate
import app.masroufy.core.savingsPerMonth
import app.masroufy.core.savingsReach
import app.masroufy.usecase.ActualSaving
import app.masroufy.usecase.MonthSaving
import app.masroufy.usecase.SavingsCalculator
import app.masroufy.usecase.SavingsCalculatorDeps
import app.masroufy.usecase.SavingsReachOutcome
import app.masroufy.usecase.SavingsTargetOutcome
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «حاسبة الادخار» (`SavingsCalculator`): الخانات ⇒ الطلب، ونتيجة حالة الاستخدام ⇒ حالة الشاشة. كل رقم جاي من `SavingsCalculator`
 * (هنا الكلام والحكم والأعمدة بس) — والمجهول «غير معروف»/«غير متاح» عمره ما يبقى صفر (القاعدة 10).
 */
class SavingsPresenterTest {
    private val today = "2026-10-07"

    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun theFieldsBecomeARequestOnlyWhenTheyAreValid() {
        val empty = checkSavings(SavingsMode.TARGET, emptyMap(), Currency.SAR, today)
        assertNull(empty.request)
        assertEquals(setOf(SavingsFields.TARGET, SavingsFields.DATE), empty.errors.keys, "«كم معك» فاضي = صفر، مش خطأ")

        val ok = checkSavings(SavingsMode.TARGET, mapOf(SavingsFields.TARGET to "60,000", SavingsFields.DATE to "2028-10-07"), Currency.SAR, today)
        assertEquals(SavingsRequest.Target(6_000_000, 0, "2028-10-07"), ok.request)
        assertTrue(ok.errors.isEmpty())

        val past = checkSavings(SavingsMode.TARGET, mapOf(SavingsFields.TARGET to "100", SavingsFields.DATE to today), Currency.SAR, today)
        assertNull(past.request, "التاريخ لازم يبقى بعد النهارده")
        assertEquals(setOf(SavingsFields.DATE), past.errors.keys)

        val reach = checkSavings(SavingsMode.MONTHLY, mapOf(SavingsFields.MONTHLY to "٢٠٠٠", SavingsFields.MONTHS to "٣٦", SavingsFields.HAVE to "12000"), Currency.SAR, today)
        assertEquals(SavingsRequest.Reach(200_000, 36, 1_200_000), reach.request, "الأرقام العربية بتتقري")

        val bad = checkSavings(SavingsMode.MONTHLY, mapOf(SavingsFields.MONTHLY to "2000", SavingsFields.MONTHS to "0", SavingsFields.HAVE to "-5"), Currency.SAR, today)
        assertNull(bad.request)
        assertEquals(setOf(SavingsFields.MONTHS, SavingsFields.HAVE), bad.errors.keys)
    }

    @Test
    fun aTargetWithAKnownAverageGivesTheVerdictAndTheBars() {
        val months = actual(235_000, 112_050, 298_000)
        val plan = savingsPerMonth(6_000_000, 1_200_000, today, "2028-10-07")
        val outcome = SavingsOutcome.Target(SavingsTargetOutcome(plan, months, compareWithActual(plan.perMonthMinor, months.averageMinor)))
        val ui = savingsResultUi(outcome, Currency.SAR)

        assertEquals(plan.perMonthMinor, ui.heroAmountMinor, "الرقم الكبير من حالة الاستخدام بالظبط")
        assertEquals("تحتاج أن تدّخر شهريًا", ui.heroLabel)
        assertTrue(ui.heroSub.startsWith("لمدة 24 شهرًا حتى 7 أكتوبر 2028، المتبقي 48,000.00 ر.س"), ui.heroSub)
        assertEquals(VerdictTone.ENOUGH, ui.verdictTone)
        assertEquals("كافٍ", ui.verdict)
        assertEquals(listOf("يوليو", "أغسطس", "سبتمبر"), ui.bars.map { it.label }, "الشهر المالي باسم الشهر اللي بيخلص فيه")
        assertEquals(48, ui.bars.maxOf { it.heightDp }, "أعلى عمود 48")
        assertFalse(ui.compareUnknown)
        assertTrue(ui.compareLine.startsWith("متوسط ادخارك: 2,150.17 ر.س شهريًا، المطلوب 2,000.00 ر.س"), ui.compareLine)
        assertTrue(ui.compareNote.contains("تبدأ يوم 28"), "يوم الراتب من الفترات نفسها")
        assertEquals(plan.perMonthMinor, ui.growthMonthlyMinor)
        assertEquals(1_200_000, ui.growthStartMinor, "«لو وضعتها في…» بتبدأ باللي معاك")
    }

    @Test
    fun anUnknownMonthMeansNoVerdictAndAReviewLinkNeverAZero() {
        val months = actual(235_000, null, 298_000)
        val r = savingsReach(200_000, 36, 0, today)
        val outcome = SavingsOutcome.Reach(SavingsReachOutcome(r, months, compareWithActual(r.monthlyMinor, months.averageMinor)))
        val ui = savingsResultUi(outcome, Currency.SAR)

        assertEquals(r.reachedMinor, ui.heroAmountMinor)
        assertEquals("ستصل إلى", ui.heroLabel)
        assertEquals(VerdictTone.UNKNOWN, ui.verdictTone)
        assertEquals("غير معروف", ui.verdict)
        assertTrue(ui.compareUnknown, "«حدّد نوع هذه العمليات» بيظهر")
        val unknownBar = ui.bars[1]
        assertTrue(unknownBar.unknown)
        assertEquals("غير معروف", unknownBar.valueText)
        assertEquals(40, unknownBar.heightDp, "عمود منقّط ثابت، مش صفر")
        assertTrue(ui.compareLine.contains("غير متاح"), ui.compareLine)
    }

    @Test
    fun aShortfallShowsHowMuchIsMissing() {
        val months = actual(100_000, 100_000, 100_000)
        val plan = savingsPerMonth(6_000_000, 0, today, "2028-10-07")
        val ui = savingsResultUi(SavingsOutcome.Target(SavingsTargetOutcome(plan, months, compareWithActual(plan.perMonthMinor, months.averageMinor))), Currency.SAR)
        assertEquals(VerdictTone.SHORT, ui.verdictTone)
        assertEquals("ينقصك 1,500.00 ر.س", ui.verdict)
    }

    @Test
    fun egyptReadsTheEgyptianWording() {
        Texts.followCountry("EG")
        val plan = savingsPerMonth(18_000_000, 18_000_000, today, "2028-10-07")
        val outcome = SavingsOutcome.Target(SavingsTargetOutcome(plan, null, compareWithActual(plan.perMonthMinor, null)))
        val ui = savingsResultUi(outcome, Currency.EGP)
        assertEquals("محتاج تحوّش في الشهر", ui.heroLabel)
        assertEquals("معاك المبلغ كله خلاص.", ui.heroSub)
        assertEquals("مش معروف", ui.verdict)
        assertEquals(0, ui.growthMonthlyMinor, "معاك كله ⇒ «لو حطّيتها في…» ما بتظهرش")
        assertTrue(ui.bars.isEmpty(), "المقارنة مش متوصلة ⇒ من غير أعمدة مخترعة")
    }

    @Test
    fun theUseCaseResultAndTheGoalToastSayTheSameNumbers() = runBlocking {
        // المقارنة مش متوصلة ⇒ الحكم «غير معروف» من حالة الاستخدام نفسها، والرسالة بعد «حوّلها لخطة» بنفس الرقم والتاريخ
        val calc = SavingsCalculator(SavingsCalculatorDeps())
        val outcome = calc.perMonth(6_000_000, 0, "2028-10-07", Currency.SAR, today)
        assertEquals(compareWithActual(outcome.plan.perMonthMinor, null), outcome.comparison)
        val ui = savingsResultUi(SavingsOutcome.Target(outcome), Currency.SAR)
        assertEquals("أُنشئت خطة «ادخار 60,000»، 2,500.00 ر.س شهريًا حتى 7 أكتوبر 2028", goalToast("ادخار 60,000", ui, Currency.SAR))
    }

    private fun actual(vararg saved: Long?): ActualSaving {
        val periods = listOf("2026-07-01", "2026-08-01", "2026-09-01").map { periodForDate(it, 28) }
        val months = periods.zip(saved.toList()).map { (p, v) -> MonthSaving(p, v) }
        return ActualSaving(months, averageSavingMinor(months.map { it.savedMinor }))
    }
}
