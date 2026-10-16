package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.PROFILE_SALARY_KEY
import app.masroufy.core.emptyProfile
import app.masroufy.core.parseStoredProfile
import app.masroufy.core.salaryProfilePoints
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

/** «المرتب الشهري» مبلغ بالظبط (قرار المالك 2026-10-16 وردوده 18:15) على مستودعات الذاكرة — أسماء ومبالغ مخترعة. */
class ProfileSalaryTest {
    private val repo = MemoryIncomeSourceRepository()
    private val profiles = MemoryProfileRepository(emptyProfile().copy(payday = 28))
    private val clock = FixedClock("2026-10-16T10:00:00.000Z")
    private val profile = ManageProfile(ManageProfileDeps(profiles, MemoryAccount(), clock, repo))
    private val incomes = ManageIncomeSources(ManageIncomeSourcesDeps(repo, profile, MemoryUnitOfWork(listOf(repo)), SequentialIdGenerator(), clock))
    private fun salary(currency: Currency = Currency.SAR) = ProfileSalary(ProfileSalaryDeps(profile, incomes, currency) { "2026-10-16" })

    @Test fun anExactAmountIsSavedOnTheProfileAndReadBack() = runBlocking<Unit> {
        assertFalse(salary().current().answered)
        val saved = assertIs<SalarySaved.Ok>(salary().saveAll(mapOf(PROFILE_SALARY_KEY to 1_234_550)))
        assertEquals(1_234_550L, saved.state.amountMinor)
        assertTrue(saved.state.employers.isEmpty())
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

    @Test fun skipIsRememberedClearsTheAmountAndStillCountsInTheProfilePercent() = runBlocking<Unit> {
        salary().save(700_000)
        val s = assertIs<SalarySaved.Ok>(salary().decline()).state
        assertTrue(s.declined)
        assertTrue(s.answered)
        assertNull(s.amountMinor)
        assertNull(profile.load().salaryMinor)
        assertEquals(12, salaryProfilePoints(s), "«مش عايز أقول» بتاخد الـ12 (رد المالك)")
        salary().save(710_000)
        assertNull(profile.load().salaryDeclined, "كتابة المبلغ بعد كده بتلغي «مش عايز أقول»")
        assertEquals(12, salaryProfilePoints(salary().current()))
    }

    @Test fun theCurrentEmployerIsTheSingleSourceOfTheAmount() = runBlocking<Unit> {
        salary().save(600_000)
        val job = incomes.add(IncomeSourceInput("شركة وهمية للتجربة", "2026-01-01"))
        val use = salary(job.currency)
        assertEquals(job.id, use.current().employer?.id)
        assertNull(use.current().amountMinor, "فيه جهة ⇒ مبلغ الملف ما بيتستعملش (رد المالك)")
        use.save(1_100_000)
        assertEquals(1_100_000L, repo.listAll().single().expectedMinor, "الرقم اتكتب على الجهة نفسها")
        assertNull(profile.load().salaryMinor, "ومش متكرر في الملف")
        incomes.answerExpectedSalary(job.id, 1_150_000)
        assertEquals(1_150_000L, use.current().amountMinor, "تعديل «المرتب المتوقع» في مصادر الدخل هو نفسه رقم «ملفك»")
        use.decline()
        assertNull(repo.listAll().single().expectedMinor)
        assertTrue(use.current().declined)
    }

    @Test fun twoCurrentJobsGetOneAmountEachAndTheProfileShowsTheSum() = runBlocking<Unit> {
        val a = incomes.add(IncomeSourceInput("شركة وهمية أولى", "2025-03-01"))
        val b = incomes.add(IncomeSourceInput("شركة وهمية تانية", "2026-02-01"))
        val use = salary(a.currency)
        assertEquals(listOf(a.id, b.id), use.current().employers.map { it.id })
        assertIs<SalarySaved.Invalid>(use.save(900_000), "مبلغ واحد لوظيفتين ⇒ مرفوض (لازم لكل واحدة)")
        assertIs<SalarySaved.Invalid>(use.saveEach(mapOf(a.id to 900_000)), "جهة ناقصة ⇒ مرفوض")
        assertIs<SalarySaved.Invalid>(use.saveEach(mapOf(a.id to 900_000, b.id to 0)))
        val ok = assertIs<SalarySaved.Ok>(use.saveAll(mapOf(a.id to 900_000, b.id to 250_050))).state
        assertEquals(1_150_050L, ok.amountMinor, "المجموع = 9,000.00 + 2,500.50")
        assertEquals(mapOf(a.id to 900_000L, b.id to 250_050L), repo.listAll().associate { it.id to it.expectedMinor })
        assertNull(profile.load().salaryMinor)
        use.decline()
        assertTrue(repo.listAll().all { it.expectedMinor == null }, "التخطي بيمسح مبلغ الاتنين")
    }

    @Test fun retirementUsesTheProfileSalaryOnlyWhenNoEmployerHasAnAmount() = runBlocking<Unit> {
        val calc = RetirementCalculator(RetirementCalculatorDeps(repo, profiles))
        val none = calc.defaults("SA", "2026-10-16")
        assertNull(none.salaryMinor)
        assertEquals(RetirementSalaryFrom.NONE, none.salaryFrom, "ولا جهة ولا ملف ⇒ «غير متاح»")
        salary().save(800_000)
        val fromProfile = calc.defaults("SA", "2026-10-16")
        assertEquals(800_000L, fromProfile.salaryMinor)
        assertEquals(RetirementSalaryFrom.PROFILE, fromProfile.salaryFrom, "«من البروفايل»")
        val job = incomes.add(IncomeSourceInput("شركة وهمية للمعاش", "2024-01-01"))
        assertEquals(RetirementSalaryFrom.PROFILE, calc.defaults("SA", "2026-10-16").salaryFrom, "جهة من غير مبلغ ⇒ لسه من البروفايل")
        incomes.answerExpectedSalary(job.id, 1_200_000)
        val fromJob = calc.defaults("SA", "2026-10-16")
        assertEquals(1_200_000L, fromJob.salaryMinor)
        assertEquals(RetirementSalaryFrom.EMPLOYER, fromJob.salaryFrom)
    }

    @Test fun backupRoundTripKeepsTheAmountAndRestoresOldRangesAsUnanswered() = runBlocking<Unit> {
        val exact = mapOf<String, Any?>("payday" to 28L, "salaryMinor" to 1_250_000L, "salaryCurrency" to "SAR")
        assertEquals(1_250_000L, salaryState(roundTrip(exact), emptyList(), Currency.SAR, "2026-10-16").amountMinor)
        val oldRange = mapOf<String, Any?>("payday" to 28L, "salaryRange" to "R2", "salaryMinor" to null)
        val s = salaryState(roundTrip(oldRange), emptyList(), Currency.SAR, "2026-10-16")
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
