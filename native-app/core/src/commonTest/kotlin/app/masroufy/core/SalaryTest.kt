package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** المرتب مبلغ بالظبط (قرار المالك 2026-10-16) — مكانه · النطاق القديم · العملة · «أفضّل ما أقولش». أسماء ومبالغ مخترعة. */
class SalaryTest {
    private val today = "2026-10-16"
    private val blank = emptyProfile()

    private fun job(id: String, kind: IncomeSourceKind = IncomeSourceKind.JOB, currency: Currency = Currency.SAR, end: IsoDate? = null, expected: Halalas? = null) =
        IncomeSource(id, "جهة وهمية $id", "جهه وهميه $id", kind, currency, "2025-01-01", end, expectedMinor = expected)

    @Test fun anExactAmountOnTheProfileIsReadBackInItsCurrencyOnly() {
        val p = profileAfterSalary(blank, 1_234_567, Currency.SAR, onEmployer = false)
        assertEquals(1_234_567L, p.salaryMinor)
        assertEquals(Currency.SAR, p.salaryCurrency)
        assertEquals(1_234_567L, salaryState(p, emptyList(), Currency.SAR, today).amountMinor)
        assertNull(salaryState(p, emptyList(), Currency.EGP, today).amountMinor, "ريال ما يتعرضش في بلد بالجنيه")
        assertEquals(800_000L, salaryState(blank.copy(salaryMinor = 800_000), emptyList(), Currency.EGP, today).amountMinor, "مبلغ قديم من غير عملة بيتعرض زي ما كان")
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
        assertNull(p.salaryMinor)
        assertNull(p.salaryRangeLegacy)
        val s = salaryState(p, emptyList(), Currency.SAR, today)
        assertTrue(s.declined)
        assertTrue(s.answered)
        assertNull(s.amountMinor)
        val reparsed = parseStoredProfile(mapOf("salaryDeclined" to true, "salaryCurrency" to "EGP", "salaryMinor" to 100L))
        assertEquals(true, reparsed.salaryDeclined)
        assertEquals(Currency.EGP, reparsed.salaryCurrency)
    }

    @Test fun theMainEmployerHoldsTheAmountAndTheProfileCopyIsCleared() {
        val sources = listOf(job("a", expected = 1_500_000), job("old", end = "2025-12-31", expected = 900_000))
        val s = salaryState(blank, sources, Currency.SAR, today)
        assertEquals("a", s.employer?.id)
        assertEquals(1_500_000L, s.amountMinor)
        val p = profileAfterSalary(blank.copy(salaryMinor = 700_000, salaryCurrency = Currency.SAR), 1_600_000, Currency.SAR, onEmployer = true)
        assertNull(p.salaryMinor, "الرقم على الجهة بس — ما يفضلش رقمين")
        // الجهة لسه مالهاش مبلغ ⇒ مبلغ الملف بيتعرض لحد ما يتكتب من جديد
        assertEquals(700_000L, salaryState(blank.copy(salaryMinor = 700_000, salaryCurrency = Currency.SAR), listOf(job("b")), Currency.SAR, today).amountMinor)
    }

    @Test fun noMainEmployerWhenItIsAmbiguousOrInAnotherCurrency() {
        assertEquals("j", salaryEmployer(listOf(job("j"), job("p", IncomeSourceKind.PART_TIME)), Currency.SAR, today)?.id, "الوظيفة قبل البارت تايم")
        assertNull(salaryEmployer(listOf(job("x"), job("y")), Currency.SAR, today), "وظيفتين ⇒ ما بنختارش")
        assertNull(salaryEmployer(listOf(job("e", currency = Currency.EGP)), Currency.SAR, today))
        assertFalse(validSalaryAmount(0))
        assertFalse(validSalaryAmount(-5))
        assertTrue(validSalaryAmount(1))
    }

    @Test fun oldBackupsWithARangeStillPassTheProfileCheck() {
        checkBackupProfile(mapOf("payday" to 28L, "salaryRange" to "R3"))
        checkBackupProfile(mapOf("payday" to 28L, "salaryRange" to 2L, "salaryMinor" to null))
        checkBackupProfile(mapOf("salaryMinor" to 1_250_000L, "salaryCurrency" to "SAR", "salaryDeclined" to false))
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("salaryCurrency" to "XYZ")) }
        assertFailsWith<BackupError> { checkBackupProfile(mapOf("salaryDeclined" to "yes")) }
        assertEquals("2", parseStoredProfile(mapOf("salaryRange" to 2L)).salaryRangeLegacy)
    }
}
