package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * معاش السعودية (§69) — حالات مرجعية محسوبة على الورق من نص النظام (م/273 ومرسومه، وصفحة التأمينات للنظام القديم). أرقام مخترعة.
 * الأجر 10,000 ر.س = 1,000,000 هللة في أغلب الحالات.
 */
class RetirementSaudiTest {
    @Test fun newLawAboveTheMinimum() {
        // 10,000 × 2.25% × 300 ÷ 12 = 5,625 · الحد الأدنى 4,000 × 300 ÷ 480 = 2,500 ⇒ المعاش 5,625
        val p = saudiNewPension(1_000_000, 300, 0, 0)
        assertEquals(562_500L, p.accruedMinor)
        assertEquals(250_000L, p.minimumMinor)
        assertEquals(562_500L, p.pensionMinor)
    }

    @Test fun newLawBelowTheMinimumUsesTheFloor() {
        // 3,000 × 2.25% × 180 ÷ 12 = 1,012.50 · الحد الأدنى 4,000 × 180 ÷ 480 = 1,500 < الأرضية 2,000 ⇒ 2,000
        val p = saudiNewPension(300_000, 180, 0, 0)
        assertEquals(101_250L, p.accruedMinor)
        assertEquals(200_000L, p.minimumMinor)
        assertEquals(200_000L, p.pensionMinor)
        // بين الأرضية والكامل: 4,000 × 400 ÷ 480 = 3,333.33 > المحسوب 4,000 × 2.25% × 400 ÷ 12 = 3,000 ⇒ 3,333.33
        assertEquals(333_333L, saudiNewPension(400_000, 400, 0, 0).pensionMinor)
        // 480 شهر أو أكتر ⇒ الحد الأدنى 4,000 كامل (مش أكتر)
        assertEquals(400_000L, saudiNewPension(100_000, 600, 0, 0).minimumMinor)
    }

    @Test fun newLawCapsAtTheAverageAndAtTheWageCeiling() {
        // 10,000 × 2.25% × 600 ÷ 12 = 11,250 > 100% من المتوسط ⇒ 10,000
        val p = saudiNewPension(1_000_000, 600, 0, 0)
        assertEquals(1_125_000L, p.accruedMinor)
        assertEquals(1_000_000L, p.pensionMinor)
        // أجر 60,000 ⇒ بيتقص عند 45,000 (م8(2)): 45,000 × 2.25% × 240 ÷ 12 = 20,250
        val high = saudiNewPension(6_000_000, 240, 0, 0)
        assertEquals(4_500_000L, high.averageWageMinor)
        assertEquals(2_025_000L, high.pensionMinor)
        // بالظبط 100%: 10,000 × 2.25% × 533.33… — 533 شهر = 9,993.75 (تحت السقف) و534 = 10,012.50 ⇒ 10,000
        assertEquals(999_375L, saudiNewPension(1_000_000, 533, 0, 0).pensionMinor)
        assertEquals(1_000_000L, saudiNewPension(1_000_000, 534, 0, 0).pensionMinor)
    }

    @Test fun newLawEarlyReductionUsesTheSmallerOfMonthsEarlyAndMonthsShortOf480() {
        // 380 شهر، 36 شهر بدري: min(36, 480−380=100) = 36 ⇒ 9%. الأساس 10,000 × 2.25% × 380 ÷ 12 = 7,125 ⇒ −641.25 ⇒ 6,483.75
        val a = saudiNewPension(1_000_000, 380, 36, 0)
        assertEquals(712_500L, a.baseMinor)
        assertEquals(36, a.reductionMonths)
        assertEquals(64_125L, a.reductionMinor)
        assertEquals(648_375L, a.pensionMinor)
        // 460 شهر، 60 بدري: min(60, 20) = 20 ⇒ 5%. الأساس 8,625 ⇒ −431.25 ⇒ 8,193.75
        val b = saudiNewPension(1_000_000, 460, 60, 0)
        assertEquals(20, b.reductionMonths)
        assertEquals(819_375L, b.pensionMinor)
        // 480 شهر ⇒ مفيش تخفيض حتى لو بدري
        assertEquals(0L, saudiNewPension(1_000_000, 480, 120, 0).reductionMinor)
    }

    @Test fun newLawLateIncrease() {
        // 400 شهر و24 شهر بعد 65: الأساس 7,500 × (1 + 3% × 2) = 7,950
        val p = saudiNewPension(1_000_000, 400, 0, 24)
        assertEquals(45_000L, p.increaseMinor)
        assertEquals(795_000L, p.pensionMinor)
    }

    private fun input(birth: String, before2024: Boolean, soFar: Int, wage: Long?, atEff: Int? = null, retireAt: Int? = null, pre1422: Int = 0) =
        SaudiPensionInput(birth, "2026-10-05", before2024, soFar, atEff, pre1422, wage, retireAt)

    @Test fun newLawEstimateFromDatesAssumesContributionContinues() {
        // مولود 1990-03-15 ⇒ 65 يوم 2055-03-15. من 2026-10-05: 341 شهر كامل (2055-03-05 ≤ 03-15، والـ342 = 04-05 بعدها)
        // 24 + 341 = 365 شهر. 8,000 × 2.25% × 365 ÷ 12 = 5,475
        val e = saudiPensionEstimate(input("1990-03-15", false, 24, 800_000))
        assertEquals(PensionLaw.SA_NEW, e.law)
        assertEquals(PensionStatus.ESTIMATED, e.status)
        assertEquals("2055-03-15", e.retirementDate)
        assertEquals(341, e.monthsUntilRetirement)
        assertEquals(365, e.contributionMonths)
        assertEquals(547_500L, e.pensionMinor)
        // من غير أجر ⇒ «غير متاح» بالسبب، مش صفر
        val noWage = saudiPensionEstimate(input("1990-03-15", false, 24, null))
        assertEquals(PensionStatus.UNAVAILABLE, noWage.status)
        assertEquals(CalcReason.NEED_WAGE, noWage.reason)
        assertNull(noWage.pensionMinor)
    }

    @Test fun newLawEligibilityBoundaries() {
        // مولود 2000-01-01، اشتراك صفر لحد النهارده ⇒ 65 يوم 2065-01-01: 458 شهر (الـ459 = 2065-01-05 بعد 01-01) ⇒ يستحق
        assertEquals(PensionStatus.ESTIMATED, saudiPensionEstimate(input("2000-01-01", false, 0, 500_000)).status)
        // تقاعد عند 45 (2045-01-01): 219 شهر ⇒ 240 شهر قبل السن > 120 ⇒ مبكر مش مسموح
        val tooEarly = saudiPensionEstimate(input("2000-01-01", false, 0, 500_000, retireAt = 45 * 12))
        assertEquals(PensionStatus.NOT_ELIGIBLE, tooEarly.status)
        assertEquals(CalcReason.EARLY_NOT_ALLOWED, tooEarly.reason)
        // 179 شهر عند 65 ⇒ أقل من 180 (قرار 1022) ⇒ مش مستحق؛ 180 ⇒ مستحق
        val born = "1961-10-05" // 65 يوم 2026-10-05 = النهارده ⇒ مفيش شهور جاية
        assertEquals(CalcReason.TOO_FEW_MONTHS, saudiPensionEstimate(input(born, false, 179, 500_000)).reason)
        assertEquals(PensionStatus.ESTIMATED, saudiPensionEstimate(input(born, false, 180, 500_000)).status)
        // مبكر 120 شهر بالظبط بـ360 مسموح، و359 لأ
        val early55 = "1971-10-05" // 55 النهارده
        assertEquals(PensionStatus.ESTIMATED, saudiPensionEstimate(input(early55, false, 360, 500_000, retireAt = 55 * 12)).status)
        assertEquals(CalcReason.EARLY_NOT_ALLOWED, saudiPensionEstimate(input(early55, false, 359, 500_000, retireAt = 55 * 12)).reason)
        assertEquals(CalcReason.EARLY_NOT_ALLOWED, saudiPensionEstimate(input("1971-09-05", false, 400, 500_000, retireAt = 55 * 12 - 1)).reason, "121 شهر بدري")
        assertFailsWith<RetirementCalcError> { saudiPensionEstimate(input("2026-10-05", false, 0, 1)) }
        assertFailsWith<RetirementCalcError> { saudiPensionEstimate(input("1990-01-01", true, 10, 1, atEff = 11)) }
    }

    /** جدول المرسوم م/273 بند «خامسًا» (2) — منقول سطر سطر من النص الرسمي: العمر يوم السريان ⇒ السن (سنين، شهور). */
    private val decreeTable = listOf(
        29 to (64 to 8), 30 to (64 to 4), 31 to (64 to 0), 32 to (63 to 8), 33 to (63 to 4), 34 to (63 to 0), 35 to (62 to 8),
        36 to (62 to 4), 37 to (62 to 0), 38 to (61 to 8), 39 to (61 to 4), 40 to (61 to 0), 41 to (60 to 8), 42 to (60 to 4),
        43 to (60 to 0), 44 to (59 to 8), 45 to (59 to 4), 46 to (59 to 0), 47 to (58 to 8), 48 to (58 to 4),
    )

    @Test fun oldLawAgeFollowsTheDecreeTableRowByRow() {
        for ((age, legal) in decreeTable) {
            val birth = addMonthsClamped(SA_NEW_LAW_EFFECTIVE, -age * 12) // بالظبط [age] سنة يوم السريان
            assertEquals(legal.first * 12 + legal.second, saudiOldLawLegalAgeMonths(birth, 0), "عمره $age")
            val almost = addMonthsClamped(SA_NEW_LAW_EFFECTIVE, -(age * 12 + 5)) // نفس السنة + 5 شهور
            assertEquals(legal.first * 12 + legal.second, saudiOldLawLegalAgeMonths(almost, 239), "عمره $age و5 شهور")
        }
        assertEquals(65 * 12, saudiOldLawLegalAgeMonths("1996-07-04", 0), "27 سنة و11 شهر ⇒ أقل من 29 ⇒ 65")
        assertEquals(64 * 12 + 8, saudiOldLawLegalAgeMonths("1995-07-03", 0), "29 بالظبط")
        assertEquals(58 * 12 + 4, saudiOldLawLegalAgeMonths("1976-01-04", 0), "48 و5 شهور و29 يوم")
        assertEquals(60 * 12, saudiOldLawLegalAgeMonths("1976-01-03", 0), "48 و6 شهور = 50 هجري ⇒ البند ما ينطبقش ⇒ 60")
        assertEquals(60 * 12, saudiOldLawLegalAgeMonths("1996-07-04", 240), "240 شهر يوم السريان ⇒ البند ما ينطبقش")
    }

    @Test fun oldLawEarlyQualifyingPeriod() {
        val young = "1990-01-01"
        assertEquals(360, saudiOldLawEarlyMonths(young, 179))
        assertEquals(348, saudiOldLawEarlyMonths(young, 180))
        assertEquals(348, saudiOldLawEarlyMonths(young, 191))
        assertEquals(336, saudiOldLawEarlyMonths(young, 192))
        assertEquals(324, saudiOldLawEarlyMonths(young, 215))
        assertEquals(312, saudiOldLawEarlyMonths(young, 216))
        assertEquals(300, saudiOldLawEarlyMonths(young, 239))
        assertEquals(300, saudiOldLawEarlyMonths(young, 240), "البند ما ينطبقش ⇒ 300 (صفحة التأمينات)")
        assertEquals(300, saudiOldLawEarlyMonths("1970-01-01", 100))
    }

    @Test fun oldLawFormula480And600() {
        // 10,000 × 300 ÷ 480 = 6,250
        assertEquals(625_000L, saudiOldPension(1_000_000, 300, 0).pensionMinor)
        // 10,000 × 240 ÷ 480 + 10,000 × 120 ÷ 600 = 5,000 + 2,000 = 7,000
        assertEquals(700_000L, saudiOldPension(1_000_000, 240, 120).pensionMinor)
        // 480 شهر ⇒ 100% بالظبط · 600 قبل 1422 ⇒ 100% · أكتر ⇒ السقف
        assertEquals(1_000_000L, saudiOldPension(1_000_000, 480, 0).pensionMinor)
        assertEquals(1_000_000L, saudiOldPension(1_000_000, 0, 600).pensionMinor)
        assertEquals(1_000_000L, saudiOldPension(1_000_000, 500, 0).pensionMinor)
        assertEquals(1_041_667L, saudiOldPension(1_000_000, 500, 0).accruedMinor)
        // 3,000 × 150 ÷ 480 = 937.50 < 1,983.75 ⇒ الحد الأدنى
        assertEquals(198_375L, saudiOldPension(300_000, 150, 0).pensionMinor)
    }

    @Test fun oldLawEstimateNeedsMonthsOnEffectiveDate() {
        // مولود 1985-01-10 ⇒ 39 سنة و5 شهور يوم السريان ⇒ 61 و4 شهور = 2046-05-10. من النهارده 235 شهر ⇒ 127 + 235 = 362
        // 9,000 × 362 ÷ 480 = 6,787.50
        val e = saudiPensionEstimate(input("1985-01-10", true, 127, 900_000, atEff = 100))
        assertEquals(PensionLaw.SA_OLD, e.law)
        assertEquals(61 * 12 + 4, e.legalAgeMonths)
        assertEquals("2046-05-10", e.retirementDate)
        assertEquals(362, e.contributionMonths)
        assertEquals(678_750L, e.pensionMinor)
        assertEquals(CalcReason.NEED_MONTHS_AT_2024, saudiPensionEstimate(input("1985-01-10", true, 127, 900_000)).reason)
        // عمره يوم السريان 54 (فوق 48 و6 شهور) ⇒ البند ما ينطبقش ⇒ مش محتاجين شهور 2024: سن 60 = 2030-01-01، 38 شهر جاي ⇒ 300 + 38 = 338
        // 10,000 × 338 ÷ 480 = 7,041.666… ⇒ 7,041.67
        val older = saudiPensionEstimate(input("1970-01-01", true, 300, 1_000_000))
        assertEquals(60 * 12, older.legalAgeMonths)
        assertEquals(338, older.contributionMonths)
        assertEquals(704_167L, older.pensionMinor)
        // نفس الشخص يتقاعد بدري عند 55 بـ362 شهر؟ محتاج 360 (أقل من 180 يوم السريان) ⇒ هنا 127 + (2040-01-10 − النهارده = 159) = 286 ⇒ لأ
        val early = saudiPensionEstimate(input("1985-01-10", true, 127, 900_000, atEff = 100, retireAt = 55 * 12))
        assertEquals(CalcReason.EARLY_NOT_ALLOWED, early.reason)
    }

    @Test fun highestWagesAverage() {
        assertNull(averageOfHighestWages(List(179) { 100L }), "أقل من 180 شهر ⇒ مش معروف")
        // 180 شهر بـ5,000 + 20 شهر بـ3,000 ⇒ أعلى 180 = 5,000؛ وشهر بـ50,000 بيتقص لـ45,000
        assertEquals(500_000L, averageOfHighestWages(List(180) { 500_000L } + List(20) { 300_000L }))
        assertEquals(522_222L, averageOfHighestWages(List(179) { 500_000L } + listOf(5_000_000L)), "(179 × 5,000 + 45,000) ÷ 180 = 5,222.22")
    }

    @Test fun fullMonthsBetweenClampsMonthEnds() {
        assertEquals(1, fullMonthsBetween("2026-01-31", "2026-02-28"))
        assertEquals(0, fullMonthsBetween("2026-01-31", "2026-02-27"))
        assertEquals(12, fullMonthsBetween("2024-02-29", "2025-02-28"))
        assertEquals(0, fullMonthsBetween("2026-10-05", "2026-10-05"))
        assertEquals(0, fullMonthsBetween("2026-10-05", "2020-01-01"))
    }
}
