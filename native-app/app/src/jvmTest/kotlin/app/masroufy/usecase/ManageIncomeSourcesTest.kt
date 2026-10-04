package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.PayFrequency
import app.masroufy.core.ProfileCheck
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مصادر الدخل و«غيّرت شغلي» و«هتروح بيها الشغل؟» على مستودعات الذاكرة (OVERRIDES §48 · §64) — أسماء ومبالغ مخترعة. */
class ManageIncomeSourcesTest {
    private val repo = MemoryIncomeSourceRepository()
    private val profiles = MemoryProfileRepository(emptyProfile().copy(payday = 28, hasCar = false))
    private val clock = FixedClock("2026-10-04T10:00:00.000Z")
    private val profile = ManageProfile(ManageProfileDeps(profiles, MemoryAccount(), clock, repo))
    private val manage = ManageIncomeSources(ManageIncomeSourcesDeps(repo, profile, MemoryUnitOfWork(listOf(repo)), SequentialIdGenerator(), clock))

    @Test fun onlyNameAndStartAreNeededAndAClosedSourceIsNeverDeleted() = runBlocking<Unit> {
        val a = manage.add(IncomeSourceInput("  شركة   النجمة الوهمية ", "2024-01-01"))
        assertEquals("شركة النجمة الوهمية", a.name)
        assertEquals(IncomeSourceKind.JOB, a.kind)
        assertNull(a.expectedDayOfMonth)
        assertNull(a.expectedMinor)
        val edited = manage.edit(a.id, IncomeSourceInput("شركة النجمة الوهمية", "2024-02-01", expectedDayOfMonth = 27))
        assertEquals(27, edited.expectedDayOfMonth)
        assertFailsWith<IncomeSourceError> { manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2025-01-01")) }
        assertFailsWith<IncomeSourceError> { manage.add(IncomeSourceInput("", "2025-01-01")) }
        manage.close(a.id, "2026-09-30")
        val kept = repo.listAll().single()
        assertEquals("2026-09-30", kept.endedAt, "اتقفل بتاريخ — ما اتمسحش")
        assertFailsWith<IncomeSourceError> { manage.close(a.id, "2026-10-01") }
        assertFailsWith<IncomeSourceError> { manage.close("مش موجود", "2026-10-01") }
        // النهاية قبل البداية
        val b = manage.add(IncomeSourceInput("عميل وهمي", "2026-05-01", IncomeSourceKind.CLIENT))
        assertFailsWith<IncomeSourceError> { manage.close(b.id, "2026-04-30") }
        assertEquals(listOf(b.id, a.id), manage.list().map { it.id }, "الشغال الأول")
    }

    @Test fun endingAloneSaysNothingAndAsksNothing() = runBlocking<Unit> {
        // رد المالك §64-٧: سؤال مكافأة نهاية الخدمة اتشال من القفل
        val a = manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01"))
        val r = manage.close(a.id, "2026-09-30")
        assertFalse(r.congratulate)
        assertNull(r.opened)
        assertEquals(emptyList(), r.followUps)
    }

    @Test fun changingJobsClosesAndOpensTogetherCongratulatesAndNeverAsksTransport() = runBlocking<Unit> {
        // عنده عربية من زمان ولسه ما اتسألش «هتروح بيها؟» — تغيير الشغل برضه ما بيسألش (المواصلات بتتستنتج من قبل)
        profiles.save(emptyProfile().copy(payday = 28, hasCar = true))
        val old = manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01"))
        val r = manage.changeJob(JobChange(old.id, "2026-09-30", IncomeSourceInput("شركة القمر الوهمية", "2026-10-01")))
        assertTrue(r.congratulate)
        assertEquals("2026-09-30", r.closed!!.endedAt)
        val new = r.opened!!
        assertEquals(listOf(IncomeFollowUp.AskPayday(new.id), IncomeFollowUp.AskExpectedSalary(new.id)), r.followUps)
        assertTrue(r.followUps.none { it == IncomeFollowUp.CarToWork })
        assertEquals(2, repo.listAll().size)
        assertFailsWith<IncomeSourceError> { manage.changeJob(JobChange()) }
    }

    @Test fun aRejectedNewJobLeavesTheOldOneOpen() = runBlocking<Unit> {
        val old = manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01"))
        // نفس الاسم بفترة بتقابل القديمة (بيبدأ قبل ما القديم يتقفل) ⇒ مرفوض
        assertFailsWith<IncomeSourceError> { manage.changeJob(JobChange(old.id, "2026-09-30", IncomeSourceInput("شركة النجمة الوهمية", "2026-09-15"))) }
        assertNull(repo.listAll().single().endedAt, "الاتنين مع بعض أو ولا واحد")
        assertFailsWith<IncomeSourceError> { manage.changeJob(JobChange(old.id, null, IncomeSourceInput("شركة القمر الوهمية", "2026-10-01"))) }
        assertNull(repo.listAll().single().endedAt)
    }

    @Test fun rejoiningTheSameCompanyIsANewPeriodWithTheSameName() = runBlocking<Unit> {
        // رد المالك §64: الرجوع لشركة قديمة = فترة جديدة بنفس الاسم، والقديمة بتفضل بتاريخها
        val old = manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01"))
        manage.changeJob(JobChange(old.id, "2025-06-30", IncomeSourceInput("شركة القمر الوهمية", "2025-07-01")))
        val back = manage.changeJob(JobChange(repo.listAll().single { it.endedAt == null }.id, "2025-12-31", IncomeSourceInput(" شركة  النجمة الوهمية", "2026-01-01")))
        assertTrue(back.congratulate)
        val sameName = repo.listAll().filter { it.normalizedName == old.normalizedName }.sortedBy { it.startedAt }
        assertEquals(listOf("2024-01-01" to "2025-06-30", "2026-01-01" to null), sameName.map { it.startedAt to it.endedAt })
        // فترة بتقابل واحدة منهم ⇒ مرفوض (حتى المقفولة)
        assertFailsWith<IncomeSourceError> { manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2025-03-01")) }
        assertFailsWith<IncomeSourceError> { manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2026-05-01")) }
        // يوم القفل نفسه جوه الفترة (الحدود شاملة)
        assertFailsWith<IncomeSourceError> { manage.edit(back.opened!!.id, IncomeSourceInput("شركة النجمة الوهمية", "2025-06-30")) }
        assertEquals(3, repo.listAll().size)
    }

    @Test fun partTimeAsksPayFrequencySeparatelyAndPensionAsksItsDay() = runBlocking<Unit> {
        val started = manage.changeJob(JobChange(starting = IncomeSourceInput("محل وهمي", "2026-10-01", IncomeSourceKind.PART_TIME)))
        val pt = started.opened!!
        assertEquals(listOf(IncomeFollowUp.AskPayFrequency(pt.id), IncomeFollowUp.AskExpectedSalary(pt.id)), started.followUps, "مش «بينزل يوم كام؟» — شهري ولا أسبوعي الأول")
        val weekly = manage.answerPayFrequency(pt.id, PayFrequency.WEEKLY, 4)
        assertEquals(Triple(PayFrequency.WEEKLY, 4, null as Int?), Triple(weekly.payFrequency, weekly.payWeekday, weekly.expectedDayOfMonth))
        assertEquals(28, profile.load().payday, "البارت تايم ما بيحرّكش بداية الشهر")
        val monthly = manage.answerPayFrequency(pt.id, PayFrequency.MONTHLY, 15)
        assertEquals(Triple(PayFrequency.MONTHLY, null as Int?, 15), Triple(monthly.payFrequency, monthly.payWeekday, monthly.expectedDayOfMonth))
        assertFailsWith<IncomeSourceError> { manage.answerPayFrequency(pt.id, PayFrequency.WEEKLY, 8) }
        // رد المالك §64-٤: الوظيفة ينفع تكون بقبض أسبوعي — يومها معروف ⇒ لا «بينزل يوم كام؟» ولا «تغيّر بداية شهرك؟»
        val weeklyJob = manage.changeJob(JobChange(starting = IncomeSourceInput("شركة وهمية", "2026-10-01", payFrequency = PayFrequency.WEEKLY, payWeekday = 2)))
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.AskExpectedSalary(weeklyJob.opened!!.id)), weeklyJob.followUps)
        assertEquals(28, profile.load().payday)
        val pension = manage.changeJob(JobChange(starting = IncomeSourceInput("معاش وهمي", "2026-10-01", IncomeSourceKind.PENSION)))
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.AskPayday(pension.opened!!.id)), pension.followUps)
        assertTrue(manage.answerPayday(pension.opened!!.id, 1).isEmpty(), "المعاش ما بيسألش عن بداية الشهر")
    }

    @Test fun aNewPaydayOffersToMoveTheFinancialMonthThroughTheProfile() = runBlocking<Unit> {
        val new = manage.changeJob(JobChange(starting = IncomeSourceInput("شركة القمر الوهمية", "2026-10-01"))).opened!!
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.ChangeMonthStart(new.id, 25)), manage.answerPayday(new.id, 25))
        assertEquals(25, repo.listAll().single().expectedDayOfMonth)
        assertTrue(manage.applyMonthStart(25) is ProfileCheck.Ok)
        assertEquals(25, profile.load().payday)
        assertTrue(manage.answerPayday(new.id, 25).isEmpty(), "نفس بداية الشهر ⇒ مفيش سؤال")
        assertFailsWith<IncomeSourceError> { manage.answerPayday(new.id, 32) }
        assertTrue(manage.applyMonthStart(40) is ProfileCheck.Invalid)
        assertEquals(25, profile.load().payday)
        assertEquals(1_200_000, manage.answerExpectedSalary(new.id, 1_200_000).expectedMinor)
        assertFailsWith<IncomeSourceError> { manage.answerExpectedSalary(new.id, 0) }
    }

    @Test fun buyingACarAsksOnceAndOnlyWithARunningJob() = runBlocking<Unit> {
        // من غير شغل: مفيش سؤال
        assertTrue(profile.saveWithQuestions(profile.load().copy(hasCar = true)).followUps.isEmpty())
        profile.save(profile.load().copy(hasCar = false))
        manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01"))
        val saved = profile.saveWithQuestions(profile.load().copy(hasCar = true))
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.CarToWork), saved.followUps)
        assertTrue(profile.saveWithQuestions(profile.load().copy(displayName = "اسم وهمي")).followUps.isEmpty(), "مرة واحدة لكل تغيير")
        assertTrue(profile.answerCarToWork(true) is ProfileCheck.Ok)
        assertEquals(true, profile.load().carToWork)
        assertTrue(profile.saveWithQuestions(profile.load()).followUps.isEmpty())
        // باع العربية ⇒ الرد بيتمسح، والعربية الجاية بتتسأل من جديد
        profile.save(profile.load().copy(hasCar = false))
        assertNull(profile.load().carToWork)
        assertEquals(listOf<IncomeFollowUp>(IncomeFollowUp.CarToWork), profile.saveWithQuestions(profile.load().copy(hasCar = true)).followUps)
    }

    @Test fun aClosedJobOrAClientDoesNotTriggerTheCarQuestion() = runBlocking<Unit> {
        val old = manage.add(IncomeSourceInput("شركة النجمة الوهمية", "2024-01-01"))
        manage.close(old.id, "2026-09-30")
        manage.add(IncomeSourceInput("عميل وهمي", "2026-01-01", IncomeSourceKind.CLIENT, Currency.SAR))
        assertTrue(profile.saveWithQuestions(profile.load().copy(hasCar = true)).followUps.isEmpty())
    }
}
