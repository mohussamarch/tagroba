package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** المرتب مبلغ بالظبط (قرار المالك 2026-10-16 وردوده 18:15) — مكانه · مجموع الوظايف · النطاق القديم · العملة · التخطي · النسبة. أسماء ومبالغ مخترعة. */
class SalaryTest {
    private val today = "2026-10-16"
    private val blank = emptyProfile()

    private fun job(id: String, kind: IncomeSourceKind = IncomeSourceKind.JOB, currency: Currency = Currency.SAR, end: IsoDate? = null, expected: Halalas? = null, start: IsoDate = "2025-01-01") =
        IncomeSource(id, "جهة وهمية $id", "جهه وهميه $id", kind, currency, start, end, expectedMinor = expected)

    @Test fun anExactAmountOnTheProfileIsReadBackInItsCurrencyOnly() {
        val p = profileAfterSalary(blank, 1_234_567, Currency.SAR, onEmployer = false)
        assertEquals(mapOf(Currency.SAR to 1_234_567L), p.salaries)
        assertEquals(1_234_567L, salaryState(p, emptyList(), Currency.SAR, today).amountMinor)
        assertNull(salaryState(p, emptyList(), Currency.EGP, today).amountMinor, "ريال ما يتعرضش في بلد بالجنيه")
        assertNull(salaryState(blank.copy(salaryMinor = 800_000), emptyList(), Currency.EGP, today).amountMinor, "cty-4: مبلغ قديم من غير عملة بيتحسب للسعودية بس")
        assertEquals(800_000L, salaryState(blank.copy(salaryMinor = 800_000), emptyList(), Currency.SAR, today).amountMinor)
    }

    @Test fun aStoredRangeIsNeverAGuessedNumberAndStaysUnanswered() {
        val p = parseStoredProfile(mapOf("payday" to 28L, "salaryRange" to "R2"))
        assertEquals("R2", p.salaryRangeLegacy)
        assertNull(p.salaryMinor)
        val s = salaryState(p, emptyList(), Currency.SAR, today)
        assertNull(s.amountMinor)
        assertTrue(s.wasRange)
        assertFalse(s.answered)
        val typed = profileAfterSalary(p, 900_000, Currency.SAR, onEmployer = false)
        assertNull(typed.salaryRangeLegacy, "أول ما يكتب المبلغ النطاق القديم بيتمسح")
        assertFalse(salaryState(typed, emptyList(), Currency.SAR, today).wasRange)
    }

    @Test fun declineAnswersTheQuestionWithoutANumber() {
        val p = profileAfterDecline(blank.copy(salaryMinor = 500_000, salaryCurrency = Currency.SAR, salaryRangeLegacy = "R1"), Currency.SAR)
        assertNull(p.salaries[Currency.SAR])
        assertNull(p.salaryRangeLegacy)
        val s = salaryState(p, emptyList(), Currency.SAR, today)
        assertTrue(s.declined)
        assertTrue(s.answered)
        assertNull(s.amountMinor)
        val reparsed = parseStoredProfile(mapOf("salaryDeclined" to true, "salaryCurrency" to "EGP", "salaryMinor" to 100L)).withSalaryMigrated()
        assertEquals(mapOf(Currency.EGP to 100L), reparsed.salaries, "cty-4: المبلغ القديم بيتنقل لبلد عملته")
        assertEquals(setOf(Currency.SAR), reparsed.salaryDeclinedIn, "«أفضّل ما أقولش» القديم ⇒ السعودية")
        assertNull(reparsed.salaryDeclined)
    }

    @Test fun theEmployerHoldsTheAmountAndTheProfileAmountIsUsedOnlyWithoutEmployers() {
        val sources = listOf(job("a", expected = 1_500_000), job("old", end = "2025-12-31", expected = 900_000))
        val s = salaryState(blank, sources, Currency.SAR, today)
        assertEquals(listOf("a"), s.employers.map { it.id })
        assertEquals(1_500_000L, s.amountMinor)
        val p = profileAfterSalary(blank.copy(salaryMinor = 700_000, salaryCurrency = Currency.SAR), 1_600_000, Currency.SAR, onEmployer = true)
        assertNull(p.salaries[Currency.SAR], "الرقم على الجهة بس — ما يفضلش رقمين")
        val withProfile = blank.copy(salaryMinor = 700_000, salaryCurrency = Currency.SAR)
        assertNull(salaryState(withProfile, listOf(job("b")), Currency.SAR, today).amountMinor, "فيه جهة ⇒ مبلغ الملف ما بيتستعملش (رد المالك)")
        assertEquals(700_000L, salaryState(withProfile, listOf(job("e", currency = Currency.EGP)), Currency.SAR, today).amountMinor, "جهة بعملة تانية مش جهة هنا")
    }

    @Test fun twoCurrentJobsShowTheSumOnlyWhenEveryJobHasAnAmount() {
        val both = listOf(job("y", expected = 1_000_050, start = "2026-02-01"), job("x", IncomeSourceKind.PART_TIME, expected = 300_000))
        val s = salaryState(blank, both, Currency.SAR, today)
        assertEquals(listOf("x", "y"), s.employers.map { it.id }, "الأقدم الأول")
        assertEquals(1_300_050L, s.amountMinor, "المجموع بالهللة")
        val partial = salaryState(blank.copy(salaryMinor = 9_999), listOf(job("x", expected = 300_000), job("y")), Currency.SAR, today)
        assertNull(partial.amountMinor, "جهة من غير مبلغ ⇒ المجموع مش معروف (مش مجموع ناقص ولا مبلغ الملف)")
        assertFalse(partial.answered)
        assertNull(partial.employer, "أكتر من جهة ⇒ مفيش جهة لوحدها")
        assertFalse(validSalaryAmount(0))
        assertFalse(validSalaryAmount(-5))
        assertTrue(validSalaryAmount(1))
    }

    @Test fun profileCompletionCountsTwelveForAnAmountOrForDeclining() {
        assertEquals(12, salaryProfilePoints(salaryState(blank.copy(salaryMinor = 100), emptyList(), Currency.SAR, today)))
        assertEquals(12, salaryProfilePoints(salaryState(blank.copy(salaryDeclined = true), emptyList(), Currency.SAR, today)), "«مش عايز أقول» بتتحسب")
        assertEquals(0, salaryProfilePoints(salaryState(blank.copy(salaryRangeLegacy = "R2"), emptyList(), Currency.SAR, today)), "النطاق القديم ما بيتحسبش")
        assertEquals(0, salaryProfilePoints(salaryState(blank, emptyList(), Currency.SAR, today)))
        assertEquals(0, salaryProfilePoints(null))
    }

    @Test fun oldBackupsWithARangeStillPassTheProfileCheck() {
        checkBackupProfile(mapOf("payday" to 28L, "salaryRange" to "R3"))
        checkBackupProfile(mapOf("payday" to 28L, "salaryRange" to 2L, "salaryMinor" to null))
        checkBackupProfile(mapOf("salaryMinor" to 1_250_000L, "salaryCurrency" to "SAR", "salaryDeclined" to false))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("salaryCurrency" to "XYZ")) }
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("salaryDeclined" to "yes")) }
        assertEquals("2", parseStoredProfile(mapOf("salaryRange" to 2L)).salaryRangeLegacy)
    }

    /** cty-4: مرتب مصر ما بيمسحش مرتب السعودية، و«أفضّل ما أقولش» في مصر ما بيوصلش للسعودية. */
    @Test fun eachCountryKeepsItsOwnSalaryAndItsOwnDecline() {
        val sa = profileAfterSalary(blank, 1_000_000, Currency.SAR, onEmployer = false)
        val both = profileAfterSalary(sa, 2_500_000, Currency.EGP, onEmployer = false)
        assertEquals(1_000_000L, salaryState(both, emptyList(), Currency.SAR, today).amountMinor)
        assertEquals(2_500_000L, salaryState(both, emptyList(), Currency.EGP, today).amountMinor)
        val egDeclined = profileAfterDecline(both, Currency.EGP)
        assertTrue(salaryState(egDeclined, emptyList(), Currency.EGP, today).declined)
        assertEquals(1_000_000L, salaryState(egDeclined, emptyList(), Currency.SAR, today).amountMinor)
        assertFalse(salaryState(profileAfterDecline(blank, Currency.EGP), emptyList(), Currency.SAR, today).declined, "رفض مصر مش رفض السعودية")
    }

    @Test fun theOldSingleSlotMovesToItsCountryOnRead() {
        val old = parseStoredProfile(mapOf("payday" to 28L, "salaryMinor" to 1_250_000L)).withSalaryMigrated()
        assertEquals(mapOf(Currency.SAR to 1_250_000L), old.salaries, "من غير عملة ⇒ السعودية (البلد الأصلية)")
        assertNull(old.salaryMinor)
        val fresh = parseStoredProfile(mapOf("payday" to 28L, "salaries" to mapOf("SAR" to 900_000L, "EGP" to 3_000_000L), "salaryDeclinedIn" to listOf("EGP")))
        assertEquals(mapOf(Currency.SAR to 900_000L, Currency.EGP to 3_000_000L), fresh.salaries)
        assertEquals(setOf(Currency.EGP), fresh.salaryDeclinedIn)
        checkBackupProfile(mapOf("salaries" to mapOf("SAR" to 900_000L), "salaryDeclinedIn" to listOf("EGP")))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("salaries" to mapOf("XYZ" to 1L))) }
    }
}
