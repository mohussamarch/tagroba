package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * مكافأة نهاية الخدمة (نظام العمل م84–87 و80) وفجوة التقاعد (§69) — أرقام مخترعة والحسبة على الورق.
 * الأجر الأخير 12,000 ر.س = 1,200,000 هللة، والبداية 2020-01-01.
 */
class EndOfServiceTest {
    private val wage = 1_200_000L
    private val start = "2020-01-01"
    private fun eos(end: String, reason: EosEnd = EosEnd.EMPLOYER_OR_CONTRACT_END, art87: Boolean = false, w: Long = wage) =
        saudiEndOfService(w, start, end, reason, art87)

    @Test fun goldenYears1_3_7_12() {
        // سنة: نص شهر = 6,000
        assertEquals(600_000L, eos("2021-01-01").payableMinor)
        // 3 سنين: 3 × نص شهر = 18,000
        assertEquals(1_800_000L, eos("2023-01-01").payableMinor)
        // 7 سنين: 5 × نص + 2 × شهر = 4.5 شهر = 54,000
        assertEquals(5_400_000L, eos("2027-01-01").payableMinor)
        // 12 سنة: 5 × نص + 7 × شهر = 9.5 شهر = 114,000
        assertEquals(11_400_000L, eos("2032-01-01").payableMinor)
    }

    @Test fun resignationThirds() {
        assertEquals(0L, eos("2021-01-01", EosEnd.RESIGNATION).payableMinor, "سنة ⇒ أقل من سنتين ⇒ صفر")
        assertEquals(0L, eos("2021-12-31", EosEnd.RESIGNATION).payableMinor, "سنتين ناقص يوم ⇒ صفر")
        val two = eos("2022-01-01", EosEnd.RESIGNATION) // سنتين بالظبط ⇒ الثلث من شهر = 4,000
        assertEquals(EosShare.THIRD, two.share)
        assertEquals(400_000L, two.payableMinor)
        assertEquals(600_000L, eos("2023-01-01", EosEnd.RESIGNATION).payableMinor, "3 سنين: ثلث 18,000 = 6,000")
        val five = eos("2025-01-01", EosEnd.RESIGNATION) // 5 بالظبط «لا تزيد على خمس» ⇒ الثلث من 2.5 شهر = 10,000
        assertEquals(EosShare.THIRD, five.share)
        assertEquals(1_000_000L, five.payableMinor)
        // 5 سنين ويوم ⇒ الثلثين: الكاملة = 12,000 × (5×365 + 2×1) ÷ 730 = 30,032.88 · الثلثين = 20,021.92
        val fivePlus = eos("2025-01-02", EosEnd.RESIGNATION)
        assertEquals(EosShare.TWO_THIRDS, fivePlus.share)
        assertEquals(3_003_288L, fivePlus.fullAwardMinor)
        assertEquals(2_002_192L, fivePlus.payableMinor)
        assertEquals(3_600_000L, eos("2027-01-01", EosEnd.RESIGNATION).payableMinor, "7 سنين: ثلثين 54,000 = 36,000")
        assertEquals(EosShare.TWO_THIRDS, eos("2029-12-31", EosEnd.RESIGNATION).share, "10 ناقص يوم")
        val ten = eos("2030-01-01", EosEnd.RESIGNATION) // 10 بالظبط ⇒ كاملة: 7.5 شهر = 90,000
        assertEquals(EosShare.FULL, ten.share)
        assertEquals(9_000_000L, ten.payableMinor)
        assertEquals(11_400_000L, eos("2032-01-01", EosEnd.RESIGNATION).payableMinor)
    }

    @Test fun article87AndArticle80() {
        assertEquals(600_000L, eos("2021-01-01", EosEnd.RESIGNATION, art87 = true).payableMinor, "م87: كاملة حتى بعد سنة")
        val fired = eos("2032-01-01", EosEnd.DISMISSED_ART80)
        assertEquals(EosShare.NONE, fired.share)
        assertEquals(0L, fired.payableMinor)
        assertEquals(11_400_000L, fired.fullAwardMinor, "الكاملة بتبان للشرح، والمستحق صفر")
    }

    @Test fun partialYearsCountByTheRealYearLength() {
        // 3 سنين + 182 يوم من 365: 12,000 × (3×365 + 182) ÷ 730 = 20,991.78
        val p = eos("2023-07-02")
        assertEquals(ServiceLength(3, 182, 365), p.service)
        assertEquals(2_099_178L, p.payableMinor)
        // سنة كبيسة: 2023-01-01 ⇒ 2024-07-01 = سنة + 182 يوم من 366 ⇒ 12,000 × (366 + 182) ÷ 732 = 8,983.61
        val leap = saudiEndOfService(wage, "2023-01-01", "2024-07-01", EosEnd.EMPLOYER_OR_CONTRACT_END)
        assertEquals(ServiceLength(1, 182, 366), leap.service)
        assertEquals(898_361L, leap.payableMinor)
        // تقريب واحد للثلث: 10,000.01 × 3 سنين ⇒ الكاملة 15,000.015 ⇒ 15,000.02 · الثلث 5,000.005 ⇒ 5,000.01
        val odd = eos("2023-01-01", EosEnd.RESIGNATION, w = 1_000_001)
        assertEquals(1_500_002L, odd.fullAwardMinor)
        assertEquals(500_001L, odd.payableMinor)
        assertEquals(0L, eos(start).payableMinor, "نفس اليوم ⇒ صفر")
        assertFailsWith<EndOfServiceError> { eos("2019-12-31") }
        assertFailsWith<EndOfServiceError> { eos("2021-01-01", w = 0) }
    }

    private fun pension(minor: Long?, status: PensionStatus = PensionStatus.ESTIMATED, reason: CalcReason? = null, monthsLeft: Int = 341) =
        PensionEstimate(PensionLaw.SA_NEW, status, reason, minor, 780, 780, "2055-03-15", 365, monthsLeft)

    @Test fun gapWithYearsEntered() {
        // المعاش 5,475 والمطلوب 9,000 ⇒ ناقص 3,525 × 12 × 20 سنة = 846,000. ناقص المكافأة 95,000 واللي معاك 50,000 ⇒ 701,000
        // ÷ 341 شهر لحد التقاعد = 2,055.718… ⇒ لفوق 2,055.72
        val eosPart = EosPart.Known(saudiEndOfService(1_000_000, "2010-01-01", "2022-01-01", EosEnd.EMPLOYER_OR_CONTRACT_END))
        assertEquals(9_500_000L, eosPart.result.payableMinor)
        val g = retirementGap(900_000, pension(547_500), eosPart, 5_000_000, 20, null).gap!!
        assertEquals(352_500L, g.shortfallPerMonthMinor)
        assertEquals(240, g.lastingMonths)
        assertEquals(84_600_000L, g.totalNeedMinor)
        assertEquals(70_100_000L, g.remainingMinor)
        assertEquals(205_572L, g.perMonthMinor)
        // «لحد سن 80» بدل السنين: 960 − 780 = 180 شهر
        assertEquals(180, retirementGap(900_000, pension(547_500), eosPart, 0, null, 80 * 12).gap!!.lastingMonths)
        // المعاش ≥ المطلوب ⇒ صفر · اللي معاك يغطي ⇒ صفر
        assertEquals(0L, retirementGap(500_000, pension(547_500), eosPart, 0, 20, null).gap!!.perMonthMinor)
        assertEquals(0L, retirementGap(900_000, pension(547_500), EosPart.NotApplicable(TextKey.CALC_EOS_NO_JOB), 84_600_000, 20, null).gap!!.perMonthMinor)
    }

    @Test fun gapIsUnavailableWithItsReason() {
        val known = EosPart.NotApplicable(TextKey.CALC_EOS_NO_JOB)
        val noYears = retirementGap(900_000, pension(547_500), known, 0, null, null)
        assertNull(noYears.gap)
        assertEquals(CalcReason.NEED_YEARS, noYears.reason, "مفيش مدة افتراضية للتقاعد")
        assertEquals(CalcReason.TOO_FEW_MONTHS, retirementGap(900_000, pension(null, PensionStatus.NOT_ELIGIBLE, CalcReason.TOO_FEW_MONTHS), known, 0, 20, null).reason)
        // مصر من غير أجر التسوية ⇒ الفجوة «غير متاح» بنفس السبب (مولود 1985 ⇒ يتم 60 بعد يوليو 2040 ⇒ سن الشيخوخة 65 تلقائي)
        val egypt = egyptPensionEstimate(EgyptPensionInput("1985-06-01", "2026-10-05", 60, settlementWageMinor = null))
        assertEquals(CalcReason.NEED_SETTLEMENT_WAGE, retirementGap(900_000, egypt, known, 0, 20, null).reason)
        assertEquals(CalcReason.NEED_JOB_START, retirementGap(900_000, pension(547_500), EosPart.Unavailable(CalcReason.NEED_JOB_START), 0, 20, null).reason)
        assertEquals(CalcReason.NO_MONTHS_TO_SAVE, retirementGap(900_000, pension(547_500, monthsLeft = 0), known, 0, 20, null).reason)
        assertFailsWith<RetirementCalcError> { retirementGap(0, pension(547_500), known, 0, 20, null) }
        assertFailsWith<RetirementCalcError> { retirementGap(900_000, pension(547_500), known, 0, 0, null) }
        assertFailsWith<RetirementCalcError>("لحد سن قبل التقاعد") { retirementGap(900_000, pension(547_500), known, 0, null, 780) }
    }
}
