package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * معاش مصر (قانون 148/2019 — OVERRIDES §69.5). حالات مرجعية محسوبة على الورق، وكل واحدة بتقول سطر جدول 5 اللي اتاخد منه المعامل.
 * أرقام وتواريخ مخترعة. الأجر 10,000 جنيه = 1,000,000 قرش في أغلب الحالات. النهارده 2026-10-05.
 */
class RetirementEgyptTest {
    /**
     * جدول 5 **بالقراية التانية** (صورة الصفحة 153 من ملف NOSI، عمود عمود) — مكتوب هنا بشكل تاني (نص) عشان أي غلطة كتابة في
     * `EG_TABLE5_TENTHS` أو هنا تبان. القراية الأولى والتانية اتطابقوا خانة خانة (81 خانة).
     */
    private val secondReading = """
        65: 150.0 132.4 118.4 107.1 97.8 90.0 81.8 75.0 69.2 64.3 60.0 56.3 52.9 50.0 47.4 45.0
        64: 128.6 115.4 104.7 95.7 88.2 81.8 75.0 69.2 64.3 60.0 56.3 52.9 50.0 47.4 45.0
        63: 112.5 102.3 93.8 86.5 80.4 75.0 69.2 64.3 60.0 56.3 52.9 50.0 47.4 45.0
        62: 100.0 91.8 84.9 78.9 73.8 69.2 64.3 60.0 56.3 52.9 50.0 47.4 45.0
        61: 90.0 83.3 77.6 72.6 68.2 64.3 60.0 56.3 52.9 50.0 47.4 45.0
        60: 81.8 76.3 71.4 67.2 63.4 60.0 56.3 52.9 50.0 47.4 45.0
    """.trimIndent()

    @Test fun table5MatchesTheSecondReadingCellByCell() {
        var cells = 0
        for (line in secondReading.lines()) {
            val (legal, values) = line.split(":")
            val read = values.trim().split(" ").map { it.replace(".", "").toInt() }
            assertEquals(read, EG_TABLE5_TENTHS.getValue(legal.trim().toInt()), "سن الشيخوخة $legal")
            assertEquals(legal.trim().toInt() - 50 + 1, read.size, "من «50 فأقل» لحد سن الشيخوخة")
            cells += read.size
        }
        assertEquals(81, cells)
        assertEquals((60..65).toSet(), EG_TABLE5_TENTHS.keys)
    }

    /**
     * فحص اتساق بس (**مش مصدر**): كل خانة = 45 ÷ r مقرّبة لرقم عشري واحد، و r بتنزل 5% عن كل سنة قبل سن الشيخوخة لحد 55،
     * و4% عن كل سنة تحت 55. لو أي رقم اتقرا غلط كان هيبان هنا.
     */
    @Test fun table5IsInternallyConsistent() {
        for ((legal, column) in EG_TABLE5_TENTHS) for ((i, tenths) in column.withIndex()) {
            val age = 50 + i
            val rBp = if (age >= 55) 10_000 - 500 * (legal - age) else 10_000 - 500 * (legal - 55) - 400 * (55 - age)
            val expected = (4_500_000L * 2 + rBp) / (2L * rBp) // 450 × 10,000 ÷ r بتقريب النص لفوق
            assertEquals(expected.toInt(), tenths, "سن الشيخوخة $legal والسن $age")
        }
    }

    @Test fun coefficientLookup() {
        assertEquals(450, egyptCoefficientTenths(65, 65), "عند سن الشيخوخة 45.0 في كل الأعمدة")
        assertEquals(600, egyptCoefficientTenths(65, 60), "سطر 60 في عمود 65 = 60.0")
        assertEquals(818, egyptCoefficientTenths(60, 50), "«50 فأقل» في عمود 60 = 81.8")
        assertEquals(1500, egyptCoefficientTenths(65, 40), "أقل من 50 ⇒ سطر «50 فأقل» = 150.0")
        assertEquals(634, egyptCoefficientTenths(60, 54), "سطر 54 في عمود 60 = 63.4")
        assertNull(egyptCoefficientTenths(60, 61), "بعد سن الشيخوخة الجدول فاضي")
        assertNull(egyptCoefficientTenths(66, 60))
        assertNull(egyptCoefficientTenths(59, 50))
    }

    private fun eg(birth: String, soFar: Int, wage: Long?, retireAt: Int? = null, legal: Int? = null, minWage: Long? = null, pre2020: Int = 0,
                   category: EgyptInsuredCategory = EgyptInsuredCategory.EMPLOYEE, today: String = "2026-10-05") =
        egyptPensionEstimate(EgyptPensionInput(birth, today, soFar, pre2020, wage, category, legal, minWage, retireAt))

    @Test fun atTheLegalAge() {
        // مولود 1985-06-01 ⇒ يتم 60 في 2045 (بعد يوليو 2040) ⇒ سن الشيخوخة 65 (م41). التقاعد 2050-06-01: 283 شهر جاي ⇒ 60 + 283 = 343
        // جدول 5 عمود 65 سطر 65 = 45.0 ⇒ 10,000 × (343 ÷ 12) ÷ 45 = 6,351.85 (80% = 8,000 ⇒ تحت السقف)
        val e = eg("1985-06-01", 60, 1_000_000)
        assertEquals(PensionLaw.EG_148_2019, e.law)
        assertEquals(PensionStatus.ESTIMATED, e.status)
        assertEquals(65 * 12, e.legalAgeMonths)
        assertEquals("2050-06-01", e.retirementDate)
        assertEquals(283, e.monthsUntilRetirement)
        assertEquals(343, e.contributionMonths)
        val d = e.egyptDetail!!
        assertEquals(450, d.coefficientTenths)
        assertEquals(635_185L, d.accruedMinor)
        assertEquals(800_000L, d.capMinor)
        assertNull(d.floorMinor, "الحد الأدنى لأجر الاشتراك من اللائحة ⇒ مش معروف")
        assertEquals(635_185L, e.pensionMinor)
        // نفس السطر بس 65 و11 شهر ⇒ السن 65 (كسر السنة بيتهمل) · 294 شهر جاي ⇒ 354: 10,000 × 29.5 ÷ 45 = 6,555.56
        assertEquals(655_556L, eg("1985-06-01", 60, 1_000_000, retireAt = 65 * 12 + 11).pensionMinor)
    }

    @Test fun capAt80PercentOfTheSettlementWage() {
        // مولود 2000-01-01، صفر شهور ⇒ 458 شهر لحد 65: 10,000 × (458 ÷ 12) ÷ 45 (عمود 65 سطر 65) = 8,481.48 > 80% = 8,000 ⇒ 8,000
        val e = eg("2000-01-01", 0, 1_000_000)
        assertEquals(848_148L, e.egyptDetail!!.accruedMinor)
        assertEquals(800_000L, e.pensionMinor)
    }

    @Test fun floorRaisesTheOldAgePensionOnlyWhenTheMinimumWageIsKnown() {
        // 3,000 × (283 ÷ 12) ÷ 45 (عمود 65 سطر 65) = 1,572.22 · الحد الأدنى لأجر الاشتراك 3,000 (مكتوب) ⇒ 65% = 1,950 ⇒ يترفع لـ1,950 (م24)
        assertEquals(157_222L, eg("1985-06-01", 0, 300_000).pensionMinor)
        val raised = eg("1985-06-01", 0, 300_000, minWage = 300_000)
        assertEquals(195_000L, raised.egyptDetail!!.floorMinor)
        assertEquals(195_000L, raised.pensionMinor)
    }

    @Test fun earlyPensionNeeds300MonthsAndHalfTheWage() {
        // مولود 1990-03-15 (سن الشيخوخة 65) يتقاعد عند 60 = 2050-03-15: 281 شهر جاي. عمود 65 سطر 60 = 60.0
        // 70 + 281 = 351 ⇒ 10,000 × 29.25 ÷ 60 = 4,875 < 50% (5,000) ⇒ مش مستحق (م21(6)(أ))
        assertEquals(CalcReason.EGYPT_EARLY_BELOW_HALF, eg("1990-03-15", 70, 1_000_000, retireAt = 60 * 12).reason)
        // 100 + 281 = 381 ⇒ 10,000 × 31.75 ÷ 60 = 5,291.67 ≥ 5,000 ⇒ مستحق
        val ok = eg("1990-03-15", 100, 1_000_000, retireAt = 60 * 12)
        assertEquals(PensionStatus.ESTIMATED, ok.status)
        assertEquals(600, ok.egyptDetail!!.coefficientTenths)
        assertTrue(ok.egyptDetail.early)
        assertEquals(529_167L, ok.pensionMinor)
        // 10 + 281 = 291 < 300 (بعد 2025 — م21(6)(ب)) ⇒ مش مسموح
        assertEquals(CalcReason.EARLY_NOT_ALLOWED, eg("1990-03-15", 10, 1_000_000, retireAt = 60 * 12).reason)
        // الحد الأدنى لأجر الاشتراك 9,000 ⇒ 65% = 5,850 > 5,291.67 ⇒ مش مستحق؛ 2,000 ⇒ 1,300 ⇒ مستحق والمبكر ما بيترفعش
        assertEquals(CalcReason.EGYPT_EARLY_BELOW_MINIMUM, eg("1990-03-15", 100, 1_000_000, retireAt = 60 * 12, minWage = 900_000).reason)
        val low = eg("1990-03-15", 100, 1_000_000, retireAt = 60 * 12, minWage = 200_000)
        assertEquals(130_000L, low.egyptDetail!!.floorMinor)
        assertEquals(529_167L, low.pensionMinor)
        // مولود 1995-01-01 يتقاعد عند 50: 82 + 218 = 300 · عمود 65 سطر «50 فأقل» = 150.0 ⇒ 10,000 × 25 ÷ 150 = 1,666.67 < 5,000
        assertEquals(CalcReason.EGYPT_EARLY_BELOW_HALF, eg("1995-01-01", 82, 1_000_000, retireAt = 50 * 12).reason)
    }

    @Test fun minimumPeriodsSwitchFiveYearsAfter2020() {
        // التقاعد 2026-12-01 (بعد 2025) ⇒ 180: 130 + 1 = 131 ⇒ أقل من المدة
        assertEquals(CalcReason.TOO_FEW_MONTHS, eg("1966-12-01", 130, 1_000_000, legal = 60).reason)
        // نفس المدة والتقاعد 2024-07-01 (قبل 2025) ⇒ 120 ⇒ مستحق — وبعدين مدد قبل 2020 ⇒ «غير متاح» (م156)
        val before = eg("1964-07-01", 130, 1_000_000, legal = 60, pre2020 = 77, today = "2024-06-01")
        assertEquals(CalcReason.EGYPT_PRE_2020, before.reason)
        assertEquals(PensionStatus.UNAVAILABLE, before.status)
        // المبكر: 246 شهر عند 54 في 2024 ⇒ ≥ 240 ⇒ يعدّي المدة؛ نفس الشيء في 2026 ⇒ أقل من 300
        assertEquals(CalcReason.EGYPT_PRE_2020, eg("1970-07-01", 245, 1_000_000, retireAt = 54 * 12, legal = 60, pre2020 = 197, today = "2024-06-01").reason)
        assertEquals(CalcReason.EARLY_NOT_ALLOWED, eg("1972-11-01", 245, 1_000_000, retireAt = 54 * 12, legal = 60, pre2020 = 197).reason)
    }

    @Test fun legalAgeIsAutomaticOnlyWhenTheLawMakesItCertain() {
        assertNull(egyptLegalAgeYears("1975-01-01", EgyptInsuredCategory.EMPLOYEE, null), "يتم 60 في 2035 ⇒ جدول التدرّج مش في القانون")
        assertEquals(65, egyptLegalAgeYears("1975-01-01", EgyptInsuredCategory.SELF_EMPLOYED_OR_ABROAD, null), "م1(10)")
        assertEquals(65, egyptLegalAgeYears("1980-07-01", EgyptInsuredCategory.EMPLOYEE, null), "يتم 60 يوم 2040-07-01 بالظبط")
        assertNull(egyptLegalAgeYears("1980-06-30", EgyptInsuredCategory.EMPLOYEE, null), "يوم قبلها")
        assertEquals(62, egyptLegalAgeYears("1975-01-01", EgyptInsuredCategory.EMPLOYEE, 62), "اللي المستخدم كتبه")
        val e = eg("1975-01-01", 80, 1_000_000)
        assertEquals(CalcReason.NEED_EGYPT_LEGAL_AGE, e.reason)
        assertNull(e.pensionMinor)
    }

    @Test fun unavailableWithReasonsNotZero() {
        assertEquals(CalcReason.EGYPT_AFTER_LEGAL_AGE, eg("1985-06-01", 60, 1_000_000, retireAt = 66 * 12).reason)
        assertEquals(CalcReason.EGYPT_PRE_2020, eg("1985-06-01", 80, 1_000_000, pre2020 = 20).reason)
        val noWage = eg("1985-06-01", 60, null)
        assertEquals(CalcReason.NEED_SETTLEMENT_WAGE, noWage.reason)
        assertNull(noWage.pensionMinor)
        assertEquals(283, noWage.monthsUntilRetirement, "التواريخ معروفة حتى من غير الأجر")
        assertFailsWith<RetirementCalcError> { eg("1985-06-01", 10, 1_000_000, pre2020 = 11) }
        assertFailsWith<RetirementCalcError> { eg("1985-06-01", 10, 1_000_000, legal = 59) }
        assertFailsWith<RetirementCalcError> { eg("1985-06-01", 10, 0) }
        assertFailsWith<RetirementCalcError> { eg("1985-06-01", 10, 1_000_000, minWage = 0) }
        assertFailsWith<RetirementCalcError> { eg("2026-10-05", 0, 1_000_000) }
    }
}
