package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.emptyProfile
import app.masroufy.core.parseStoredProfile
import app.masroufy.core.salaryState
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «المرتب الشهري» مبلغ بالظبط (قرار المالك 2026-10-16) على مستودعات الذاكرة — أسماء ومبالغ مخترعة. */
class ProfileSalaryTest {
    private val repo = MemoryIncomeSourceRepository()
    private val profiles = MemoryProfileRepository(emptyProfile().copy(payday = 28))
    private val clock = FixedClock("2026-10-16T10:00:00.000Z")
    private val profile = ManageProfile(ManageProfileDeps(profiles, MemoryAccount(), clock, repo))
    private val incomes = ManageIncomeSources(ManageIncomeSourcesDeps(repo, profile, MemoryUnitOfWork(listOf(repo)), SequentialIdGenerator(), clock))
    private fun salary(currency: Currency = Currency.SAR) = ProfileSalary(ProfileSalaryDeps(profile, incomes, currency) { "2026-10-16" })

    @Test fun anExactAmountIsSavedOnTheProfileAndReadBack() = runBlocking<Unit> {
        assertFalse(salary().current().answered)
        val saved = assertIs<SalarySaved.Ok>(salary().save(1_234_550))
        assertEquals(1_234_550L, saved.state.amountMinor)
        assertNull(saved.state.employer)
        assertEquals(1_234_550L, profile.load().salaryMinor)
        assertEquals(Currency.SAR, profile.load().salaryCurrency)
        assertNull(salary(Currency.EGP).current().amountMinor, "المبلغ بالريال مش رقم في بلد بالجنيه")
        assertIs<SalarySaved.Invalid>(salary().save(0))
        assertIs<SalarySaved.Invalid>(salary().save(null))
        assertEquals(1_234_550L, profile.load().salaryMinor, "المرفوض ما بيغيّرش حاجة")
    }

    @Test fun aStoredRangeShowsUnansweredUntilTheExactAmountIsTyped() = runBlocking<Unit> {
        profiles.save(parseStoredProfile(mapOf("payday" to 28L, "salaryRange" to "R2")))
        val before = salary().current()
        assertTrue(before.wasRange)
        assertNull(before.amountMinor)
        assertFalse(before.answered)
        salary().save(950_000)
        val after = salary().current()
        assertFalse(after.wasRange)
        assertEquals(950_000L, after.amountMinor)
        assertNull(profile.load().salaryRangeLegacy)
    }

    @Test fun skipIsRememberedAndClearsTheAmount() = runBlocking<Unit> {
        salary().save(700_000)
        val s = assertIs<SalarySaved.Ok>(salary().decline()).state
        assertTrue(s.declined)
        assertTrue(s.answered)
        assertNull(s.amountMinor)
        assertNull(profile.load().salaryMinor)
        salary().save(710_000)
        assertNull(profile.load().salaryDeclined, "كتابة المبلغ بعد كده بتلغي «أفضّل ما أقولش»")
    }

    @Test fun theCurrentEmployerIsTheSingleSourceOfTheAmount() = runBlocking<Unit> {
        salary().save(600_000)
        val job = incomes.add(IncomeSourceInput("شركة وهمية للتجربة", "2026-01-01"))
        val use = salary(job.currency)
        assertEquals(job.id, use.current().employer?.id)
        assertEquals(600_000L, use.current().amountMinor, "الجهة لسه مالهاش مبلغ ⇒ مبلغ الملف بيتعرض")
        use.save(1_100_000)
        assertEquals(1_100_000L, repo.listAll().single().expectedMinor, "الرقم اتكتب على الجهة نفسها")
        assertNull(profile.load().salaryMinor, "ومش متكرر في الملف")
        incomes.answerExpectedSalary(job.id, 1_150_000)
        assertEquals(1_150_000L, use.current().amountMinor, "تعديل «المرتب المتوقع» في مصادر الدخل هو نفسه رقم «ملفك»")
        use.decline()
        assertNull(repo.listAll().single().expectedMinor)
        assertTrue(use.current().declined)
    }

    @Test fun backupRoundTripKeepsTheAmountAndRestoresOldRangesAsUnanswered() = runBlocking<Unit> {
        val exact = mapOf<String, Any?>("payday" to 28L, "salaryMinor" to 1_250_000L, "salaryCurrency" to "SAR")
        val restoredExact = roundTrip(exact)
        assertEquals(1_250_000L, salaryState(restoredExact, emptyList(), Currency.SAR, "2026-10-16").amountMinor)
        val oldRange = mapOf<String, Any?>("payday" to 28L, "salaryRange" to "R2", "salaryMinor" to null)
        val restoredRange = roundTrip(oldRange)
        val s = salaryState(restoredRange, emptyList(), Currency.SAR, "2026-10-16")
        assertNull(s.amountMinor, "النطاق عمره ما بيبقى رقم")
        assertTrue(s.wasRange)
    }

    private suspend fun roundTrip(row: Map<String, Any?>) = run {
        val file = FullBackup(MemoryFullBackup(profile = row)).create("2026-10-16T10:00:00.000Z")
        val target = MemoryFullBackup()
        FullBackup(target).apply(file)
        parseStoredProfile(target.readProfile())
    }
}
