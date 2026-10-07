package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * حساب «هتوصل لكام؟» (OVERRIDES §69.6) — كل رقم متوقع محسوب **مرتين**: على الورق (التعليق جنبه) وبنسخة مستقلة بـ`BigInt` في
 * جافاسكربت بنفس قاعدة التقريب (`scratchpad/grow/replica.mjs` عند المنفّذ). commonTest ⇒ بيشتغل على الآيفون كمان.
 */
class GrowthMathTest {
    @Test
    fun monthlyRateIsTheNearestEquivalentOfTheYearlyCompoundRate() {
        assertEquals(0L, monthlyRateFromAnnual(0))
        assertEquals(797_414L, monthlyRateFromAnnual(1000)) // 1.1^(1/12) − 1 = 0.0079741404
        assertEquals(1_162_690L, monthlyRateFromAnnual(1488)) // 1.1488^(1/12) − 1 = 0.0116268996
        assertEquals(-426_532L, monthlyRateFromAnnual(-500)) // 0.95^(1/12) − 1 = −0.0042653188
        assertEquals(22_118_855L, monthlyRateFromAnnual(MAX_ANNUAL_RATE_BP)) // 11^(1/12) − 1 ⇒ حد الأمان ما بيطفحش
        assertTrue(monthlyRateFromAnnual(MIN_ANNUAL_RATE_BP) in -53_584_300L..-53_584_200L) // 0.0001^(1/12) − 1 ≈ −0.535842
        assertFailsWith<GrowthError> { monthlyRateFromAnnual(MAX_ANNUAL_RATE_BP + 1) }
        assertFailsWith<GrowthError> { monthlyRateFromAnnual(MIN_ANNUAL_RATE_BP - 1) }
    }

    @Test
    fun growthAtZeroIsTheSumAndTenPercentForAYearIsTenPercent() {
        assertEquals(1_000_000L + 100_000L * 120, projectGrowth(100_000, 120, 0, 1_000_000))
        // 10,000 بـ10% سنة كاملة = 11,000 على الورق؛ التقريب الشهري للهللة بينقّص هللة واحدة
        assertEquals(1_099_999L, projectGrowth(0, 12, 1000, 1_000_000))
        assertEquals(0L, projectGrowth(0, 0, 1488, 0))
    }

    /** ليه دالة تانية: 14.88% «متوسط مركّب» في `projectMonthly` (÷ 12) بيطلع 15.9% فعليًا ⇒ 1,000 في الشهر 10 سنين بتزيد 14,890 ر.س غلط. */
    @Test
    fun compoundAverageIsNotTheBankStyleNominalRate() {
        assertEquals(25_832_688L, projectGrowth(100_000, 120, 1488)) // 258,326.88 ر.س
        assertEquals(27_321_750L, projectMonthly(100_000, 120, 1488)) // 273,217.50 ر.س (الطريقة البنكية)
        assertEquals(13_087_780L, projectGrowth(100_000, 120, 174))
        assertEquals(17_965_843L, projectGrowth(100_000, 120, 795))
    }

    @Test
    fun wholeYearsCompoundAndDeflateExactly() {
        // مثال المالك: 240 متر × 40,000 = 9,600,000 — بغلاء السعودية 1.74% لحد 2030 (4 سنين): 9,600,000 × 1.0174^4 = 10,285,802.15
        assertEquals(1_028_580_216L, compoundYears(960_000_000, 4, 174))
        assertEquals(960_000_000L, compoundYears(960_000_000, 0, 174))
        assertEquals(1_000_000L, deflate(1_100_000, 12, 1000)) // 11,000 بعد سنة بتضخم 10% = 10,000 النهارده
        assertEquals(1_000_000L, deflate(1_210_000, 24, 1000))
        assertEquals(866_783L, deflate(1_000_000, 18, 1000)) // 10,000 ÷ 1.1^1.5 = 8,667.83
        assertEquals(10_098_664L, deflate(12_000_000, 120, 174)) // 120,000 ÷ 1.0174^10 = 100,986.64
        assertFailsWith<MoneyError> { compoundYears(MAX_SAFE_HALALAS, 1, 1000) }
        assertFailsWith<GrowthError> { compoundYears(100, 101, 100) }
    }

    @Test
    fun theCurrencyPartAndPercentFormat() {
        // (1.3422 ÷ 1.1148) − 1 = 20.398% ⇒ 2040 = نفس متوسط نزول الجنيه من سعر الصرف الرسمي
        assertEquals(2040, devaluationPartBp(3422, 1148))
        // البورصة 19.42% بالجنيه والجنيه نزل 20.40% ⇒ بالدولار (1.1942 ÷ 1.2040) − 1 = −0.81%
        assertEquals(-81, inUsdTermsBp(1942, 2040))
        assertEquals("14.88%", formatBp(1488))
        assertEquals("-0.81%", formatBp(-81))
        assertEquals("0.05%", formatBp(5))
        assertEquals("100.00%", formatBp(10_000))
    }

    @Test
    fun mulDivWideRoundsHalfAwayFromZero() {
        assertEquals(2L, mulDivWide(3, 1, 2)) // 1.5 ⇒ 2
        assertEquals(-2L, mulDivWide(-3, 1, 2)) // −1.5 ⇒ −2
        assertEquals(1L, mulDivWide(5, 1, 4)) // 1.25 ⇒ 1
        assertEquals(MAX_SAFE_HALALAS, mulDivWide(MAX_SAFE_HALALAS, 1_000_000_000, 1_000_000_000))
    }
}
