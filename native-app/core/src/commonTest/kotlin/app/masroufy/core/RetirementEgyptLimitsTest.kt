package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * معاش مصر — أرقام اللائحة التنفيذية (قرار رئيس الوزراء 2437/2021) وأخبار الهيئة الرسمية (OVERRIDES §69.8). كل رقم مرجعي هنا
 * جنبه مصدره. الأشخاص والأجور مخترعة. 1 جنيه = 100 قرش.
 */
class RetirementEgyptLimitsTest {
    /**
     * **القراية التانية** (مكتوبة نص بالجنيه، لوحدها عن الجدول في الكود) — سنة: أدنى أقصى · المصدر:
     * 2020 اللائحة م53 ص46 (الجريدة الرسمية 38 مكرر ب 2021-09-28 — A−) · 2021 nosi.gov.eg/ar/News/Pages/27-11-2021 + 4-1-2022 «بدلاً من» ·
     * 2022 4-1-2022 · 2023 2023-12-17 «بدلاً من» · 2024 2023-12-17 · 2025 2025-11-30 «من» · 2026 2025-11-30 (كلها A).
     */
    private val secondReading = """
        2020: 1000 7000
        2021: 1200 8100
        2022: 1400 9400
        2023: 1700 10900
        2024: 2000 12600
        2025: 2300 14500
        2026: 2700 16700
    """.trimIndent()

    @Test fun officialTableMatchesTheSecondReading() {
        val read = secondReading.lines().map { line ->
            val (year, values) = line.split(":")
            val (min, max) = values.trim().split(" ").map { it.toLong() * 100 }
            EgyptContributionWageLimits(year.trim().toInt(), min, max)
        }
        assertEquals(read, EG_CONTRIBUTION_WAGE_LIMITS)
        assertEquals((2020..2026).toList(), EG_CONTRIBUTION_WAGE_LIMITS.map { it.year }, "سنين ورا بعض من غير فراغ")
    }

    /**
     * فحص اتساق (**مش مصدر**): اللائحة م53 — +15% أول يناير على رقم ديسمبر و«جبر» لأقرب 100 جنيه. «جبر» = **لفوق** بيطلّع أرقام الهيئة
     * 2021–2026 بالظبط؛ التقريب لأقرب 100 بيغلط من 2022 (9,300 بدل 9,400). وده اللي خلّانا نكتب 2027 (3,200 / 19,300) في الوثايق «حساب
     * مش رقم معلن» ومش في الجدول.
     */
    @Test fun art53RuleWithRoundingUpReproducesTheAnnouncedFigures() {
        fun ceilStep(v: Long) = (v * 115 + 999_999) / 1_000_000 * 10_000
        fun nearestStep(v: Long) = (v * 115 + 500_000) / 1_000_000 * 10_000
        var (min, max) = 100_000L to 700_000L
        var (nMin, nMax) = min to max
        var nearestMatches = true
        for (row in EG_CONTRIBUTION_WAGE_LIMITS.drop(1)) {
            min = ceilStep(min); max = ceilStep(max); nMin = nearestStep(nMin); nMax = nearestStep(nMax)
            assertEquals(row, EgyptContributionWageLimits(row.year, min, max), "قاعدة م53 لسنة ${row.year}")
            if (nMin != row.minMinor || nMax != row.maxMinor) nearestMatches = false
        }
        assertFalse(nearestMatches, "التقريب لأقرب 100 ما يطابقش ⇒ «جبر» = لفوق")
        assertEquals(320_000L to 1_930_000L, ceilStep(min) to ceilStep(max), "2027 بالقاعدة — مش في الجدول لحد ما يتعلن")
    }

    /** أخبار الهيئة بتقول الحد الأدنى/الأقصى **للمعاش** كمان — لازم = 65% / 80% من الحدين (م24 + م105). */
    @Test fun announcedPensionLimitsAre65And80PercentOfTheWageLimits() {
        val announced = mapOf( // NOSI: 2022-01-04 · 2023-12-17 (2023 «بدلاً من» و2024) · 2025-11-30 (2025 «من» و2026)
            2022 to (91_000L to 752_000L), 2023 to (110_500L to 872_000L), 2024 to (130_000L to 1_008_000L),
            2025 to (149_500L to 1_160_000L), 2026 to (175_500L to 1_336_000L),
        )
        for ((year, pair) in announced) {
            val l = EG_CONTRIBUTION_WAGE_LIMITS.first { it.year == year }
            assertEquals(pair.first, l.minMinor * EG_FLOOR_OF_MIN_WAGE_BP / BASIS_POINTS, "الحد الأدنى للمعاش $year")
            assertEquals(pair.second, l.maxMinor * EG_MAX_PENSION_OF_MAX_WAGE_BP / BASIS_POINTS, "الحد الأقصى للمعاش $year")
        }
    }

    @Test fun lookupIsByCalendarYearAndNullOutsideTheTable() {
        assertEquals(EgyptContributionWageLimits(2026, 270_000L, 1_670_000L), egyptContributionWageLimits("2026-03-15"))
        assertEquals(2020, egyptContributionWageLimits("2020-01-01")?.year)
        assertEquals(2025, egyptContributionWageLimits("2025-12-31")?.year)
        assertNull(egyptContributionWageLimits("2019-12-31"), "قبل القانون")
        assertNull(egyptContributionWageLimits("2027-01-01"), "لسه ما اتعلنش")
        assertEquals(EgyptContributionWageLimits(2026, 270_000L, 1_670_000L), egyptLatestContributionWageLimits())
    }

    private fun eg(birth: String, soFar: Int, wage: Long, today: String, legal: Int? = null, retireAt: Int? = null, minWage: Long? = null, maxWage: Long? = null) =
        egyptPensionEstimate(EgyptPensionInput(birth, today, soFar, 0, wage, EgyptInsuredCategory.EMPLOYEE, legal, minWage, retireAt, maxWage))

    /**
     * التقاعد 2026-03-15 (مولود 1966-03-15، سن 60 مكتوب) ⇒ الحدين من الجدول الرسمي 2026. النهارده 2025-06-01 ⇒ 9 شهور جاية.
     * (أرقام مخترعة — الشهور مش لازم تكون واقعية عشان نختبر القاعدة.) جدول 5 عمود 60 سطر 60 = 45.0.
     */
    @Test fun inATableYearTheSettlementWageAndPensionAreCappedAtTheAnnouncedMaximum() {
        // 500 + 9 = 509 شهر · أجر مكتوب 20,000 > الحد الأقصى 16,700 ⇒ 16,700 (م22) · 16,700 × (509 ÷ 12) ÷ 45 = 15,741.30
        // ⇒ سقف 80% = 13,360 — نفس «الحد الأقصى للمعاش» اللي الهيئة أعلنته لسنة 2026
        val e = eg("1966-03-15", 500, 2_000_000, "2025-06-01", legal = 60)
        assertEquals("2026-03-15", e.retirementDate)
        val d = e.egyptDetail!!
        assertEquals(1_670_000L, d.settlementWageMinor)
        assertTrue(d.settlementCappedToMax)
        assertEquals(2026, d.limitsYear)
        assertEquals(270_000L, d.minContributionWageMinor)
        assertEquals(1_670_000L, d.maxContributionWageMinor)
        assertEquals(1_574_130L, d.accruedMinor)
        assertEquals(1_336_000L, d.capMinor)
        assertEquals(1_336_000L, d.maxWageCapMinor)
        assertEquals(1_336_000L, e.pensionMinor)
    }

    @Test fun inATableYearTheFloorIsTheAnnouncedMinimumPension() {
        // 200 + 9 = 209 · 1,000 × (209 ÷ 12) ÷ 45 = 387.04 ⇒ الأرضية 65% × 2,700 = 1,755 (= الحد الأدنى للمعاش المعلن 2026)
        val e = eg("1966-03-15", 200, 100_000, "2025-06-01", legal = 60)
        assertEquals(38_704L, e.egyptDetail!!.accruedMinor)
        assertFalse(e.egyptDetail.settlementCappedToMax)
        assertEquals(175_500L, e.egyptDetail.floorMinor)
        assertEquals(175_500L, e.pensionMinor)
        // المكتوب بيعلى على الجدول: الحد الأدنى 3,000 ⇒ 1,950 · والأقصى لسه من الجدول ⇒ سنة الجدول فاضلة
        val typedMin = eg("1966-03-15", 200, 100_000, "2025-06-01", legal = 60, minWage = 300_000)
        assertEquals(195_000L, typedMin.pensionMinor)
        assertEquals(2026, typedMin.egyptDetail!!.limitsYear)
        // الاتنين مكتوبين ⇒ مفيش حاجة من الجدول · الأجر 20,000 = الحد الأقصى المكتوب ⇒ مش متقص
        val both = eg("1966-03-15", 500, 2_000_000, "2025-06-01", legal = 60, minWage = 300_000, maxWage = 2_000_000)
        assertNull(both.egyptDetail!!.limitsYear)
        assertFalse(both.egyptDetail.settlementCappedToMax)
        assertEquals(1_600_000L, both.egyptDetail.maxWageCapMinor)
        assertEquals(1_600_000L, both.pensionMinor, "80% من 20,000")
    }

    @Test fun outsideTheTableNothingIsAssumedButThe900PoundMinimumHolds() {
        // التقاعد 2050 (مولود 1985-06-01 ⇒ سن 65 · 343 شهر) — سنة مش معلنة ⇒ الحدين null، وسقفهم مش متطبّق
        // 500 × (343 ÷ 12) ÷ 45 = 317.59 ⇒ يترفع لـ900 جنيه (اللائحة م105 ثالثًا(4) — حتى لو الحد الأدنى مش معروف)
        val e = eg("1985-06-01", 60, 50_000, "2026-10-05")
        val d = e.egyptDetail!!
        assertNull(d.minContributionWageMinor)
        assertNull(d.maxContributionWageMinor)
        assertNull(d.limitsYear)
        assertNull(d.floorMinor)
        assertNull(d.maxWageCapMinor)
        assertEquals(31_759L, d.accruedMinor)
        assertEquals(EG_MIN_PENSION_NUMERIC_MINOR, e.pensionMinor)
        // الحد الأدنى المكتوب 1,000 ⇒ 65% = 650 < 900 ⇒ الأرضية 900
        assertEquals(90_000L, eg("1985-06-01", 60, 50_000, "2026-10-05", minWage = 100_000).egyptDetail!!.floorMinor)
    }

    @Test fun theEarlyConditionUses65PercentOnlyAndTheEarlyPensionIsNotRaisedTo900() {
        // مولود 1990-03-15 يتقاعد عند 60 (سن الشيخوخة 65) ⇒ 381 شهر · عمود 65 سطر 60 = 60.0 · الحد الأدنى المكتوب 1,000 ⇒ 65% = 650
        // 1,400 × 31.75 ÷ 60 = 740.83 ≥ 700 (نص الأجر) و≥ 650 ⇒ مستحق، ويفضل 740.83 (المبكر ما بيترفعش للـ900)
        val ok = eg("1990-03-15", 100, 140_000, "2026-10-05", retireAt = 60 * 12, minWage = 100_000)
        assertEquals(PensionStatus.ESTIMATED, ok.status)
        assertTrue(ok.egyptDetail!!.early)
        assertEquals(90_000L, ok.egyptDetail.floorMinor)
        assertEquals(74_083L, ok.pensionMinor)
        // 1,200 × 31.75 ÷ 60 = 635 ≥ 600 بس < 650 ⇒ مش مستحق (اللائحة م102(7)(ب))
        assertEquals(CalcReason.EGYPT_EARLY_BELOW_MINIMUM, eg("1990-03-15", 100, 120_000, "2026-10-05", retireAt = 60 * 12, minWage = 100_000).reason)
    }

    @Test fun typedLimitsAreValidated() {
        assertFailsWith<RetirementCalcError> { eg("1985-06-01", 60, 100_000, "2026-10-05", maxWage = 0) }
        assertFailsWith<RetirementCalcError> { eg("1985-06-01", 60, 100_000, "2026-10-05", minWage = 300_000, maxWage = 200_000) }
        assertNotEquals(null, eg("1985-06-01", 60, 100_000, "2026-10-05", minWage = 300_000, maxWage = 300_000).pensionMinor)
    }

    @Test fun theNewTextsExistInAllThreeTables() {
        val keys = listOf(TextKey.CALC_EGYPT_MAX_BELOW_MIN, TextKey.CALC_EGYPT_MAX_CAP_UNKNOWN, TextKey.CALC_EGYPT_WAGE_CAPPED, TextKey.CALC_EGYPT_ART163_NOTE)
        for (k in keys) for (table in listOf(MSA_RETIRE_TEXTS, EGYPTIAN_RETIRE_TEXTS, ENGLISH_RETIRE_TEXTS)) assertTrue(table[k]?.isNotBlank() == true, k.name)
        for (table in listOf(MSA_RETIRE_TEXTS, EGYPTIAN_RETIRE_TEXTS, ENGLISH_RETIRE_TEXTS)) {
            assertTrue("{1}" in table.getValue(TextKey.CALC_EGYPT_FLOOR_UNKNOWN), "آخر رقم رسمي + سنته")
            assertTrue("163" in table.getValue(TextKey.CALC_EGYPT_ART163_NOTE))
        }
    }
}
