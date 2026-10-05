package app.masroufy.usecase

import app.masroufy.core.AlertKind
import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.GoalContribution
import app.masroufy.core.ReviewState
import app.masroufy.core.SavingsGoal
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryGoalContributionRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySavingsGoalRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * capPace وhabitVsGoal من البيانات للمرشحين (OVERRIDES §68) على مستودعات الذاكرة — كل الأسامي والمبالغ مخترعة.
 * أمثلة المالك: 800 أول يوم على سقف 1000 في عملية واحدة ⇒ ساكت · نفس الـ800 على 4 مرات في 5 أيام ⇒ تنبيه ·
 * قهوة مرتين أعلى بكتير ⇒ ساكت · 3 مرات ⇒ تنبيه.
 */
class AdvisorPaceFlowTest {
    private val period = buildPeriod(2026, 9, 28) // 28 سبتمبر ⇒ 27 أكتوبر
    private val fun_ = "cat-ترفيه"
    private val coffee = "cat-مطاعم-وقهوه--قهوه-ومشروبات"
    private val grocery = "cat-بقاله-وسوبرماركت"
    private val categories = listOf(
        Category(fun_, null, "ترفيه", "i", "#000", "#fff", true, 1),
        Category("cat-مطاعم-وقهوه", null, "مطاعم وقهوة", "i", "#000", "#fff", true, 2),
        Category(coffee, "cat-مطاعم-وقهوه", "قهوة ومشروبات", "i", "#000", "#fff", true, 3),
        Category(grocery, null, "بقالة وسوبرماركت", "i", "#000", "#fff", true, 4),
    )
    private var seq = 0

    private fun txn(date: String, amount: Long, category: String, kind: EconomicKind = EconomicKind.PURCHASE) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category,
    )

    /** آخر 3 شهور مالية: ترفيه 300 وقهوة 400 وبقالة في كل شهر. */
    private fun history() = listOf("2026-06-28", "2026-07-28", "2026-08-28").flatMap { d ->
        listOf(txn(d, 30_000, fun_), txn(d, 40_000, coffee), txn(d, 90_000, grocery))
    }

    // خطة: 12,000 آخر السنة، و10,000 اتحوشوا من فبراير ⇒ ماشي على الخطة وبيوصل فوق الهدف
    private val goal = SavingsGoal("g-1", "خطة وهمية", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "c", updatedAt = "c")
    private val goals = LoadGoalsOverview(
        LoadGoalsOverviewDeps(MemorySavingsGoalRepository(listOf(goal)), MemoryGoalContributionRepository(listOf(GoalContribution("c-1", "g-1", "2026-02-01", 1_000_000, "x")))),
    )

    private suspend fun candidates(
        current: List<Transaction>,
        today: String,
        past: List<Transaction> = history(),
        withGoal: Boolean = true,
        withBudget: Boolean = true,
        overview: LoadGoalsOverview = goals,
    ): List<app.masroufy.core.AlertCandidate> {
        val txns = MemoryTransactionRepository(past + current)
        val cats = MemoryCategoryRepository(categories)
        val allocations = MemoryAllocationRepository()
        val budgets = MemoryBudgetRepository(
            listOf(Budget("2026-09", "2026-09", period.start, period.end, null, null, "c", "c")),
            listOf(CategoryBudget("cb-1", "2026-09", fun_, 100_000, true, 80)),
        )
        val budget = if (!withBudget) null else LoadBudgetScreen(LoadBudgetScreenDeps(txns, cats, allocations, budgets)).load(LoadBudgetScreenRequest(period, today, 28))
        val advisor = AdvisorSignals(
            AdvisorSignalsDeps(txns, allocations, cats, MemoryProfileRepository(emptyProfile().copy(payday = 28)), goals = if (withGoal) overview else null),
        )
        val dues = LoadDues(
            LoadDuesDeps(
                MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(), MemoryDebtTermsRepository(),
                MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(), MemoryRecurringRepository(), txns,
            ),
        )
        // من نفس خط التنبيهات (GatherAlerts) — المساعد مصدر زي الباقي
        return GatherAlerts(GatherAlertsDeps(dues, advisor = advisor)).gather(AlertGatherInput(today, period, Currency.SAR, budget))
            // القواعد التلاتة الأولى بس — unusual وbigOne والملخص الأسبوعي ليهم اختباراتهم (`AdvisorMoreFlowTest`)
            .filter { it.kind in setOf(AlertKind.CAP_PACE, AlertKind.HABIT_VS_GOAL, AlertKind.OVERCOMMITTED) }
    }

    private fun List<app.masroufy.core.AlertCandidate>.kinds() = map { it.kind }.toSet()

    @Test fun oneBigShopOnDayOneIsNeverAPace() = runBlocking<Unit> {
        val lump = listOf(txn("2026-09-28", 80_000, fun_))
        assertEquals(emptySet(), candidates(lump, "2026-09-28").kinds(), "أول يوم")
        assertEquals(emptySet(), candidates(lump, "2026-10-07").kinds(), "بعدها بـ10 أيام — لسه خبطة واحدة")
        // وتنبيهات الميزانية العادية (عدّت العتبة 80%) زي ما هي — منفصلة
        val budget = LoadBudgetScreen(
            LoadBudgetScreenDeps(
                MemoryTransactionRepository(lump), MemoryCategoryRepository(categories), MemoryAllocationRepository(),
                MemoryBudgetRepository(listOf(Budget("2026-09", "2026-09", period.start, period.end, null, null, "c", "c")), listOf(CategoryBudget("cb-1", "2026-09", fun_, 100_000, true, 80))),
            ),
        ).load(LoadBudgetScreenRequest(period, "2026-09-28", 28))
        assertTrue(budgetNotificationEvents(budget).isNotEmpty(), "عدّى 80% من سقف الترفيه ⇒ تنبيه الميزانية القديم شغال")
    }

    @Test fun sameAmountInFourVisitsFiresTheCapPace() = runBlocking<Unit> {
        val split = listOf("2026-09-28", "2026-09-29", "2026-10-01", "2026-10-02").map { txn(it, 20_000, fun_) }
        val found = candidates(split, "2026-10-02")
        assertTrue(AlertKind.CAP_PACE in found.kinds(), "$found")
        assertEquals("cappace|2026-09-28|cat:$fun_", found.single { it.kind == AlertKind.CAP_PACE }.threadKey)
        assertTrue(AlertKind.HABIT_VS_GOAL in found.kinds(), "والترفيه اختياري وأعلى من معتاده بكتير ⇒ الخطة مش هتكمل")
    }

    @Test fun thePaceStartsOnTheThirdDay() = runBlocking<Unit> {
        // رد المالك (صفحة الضبط): من اليوم التالت مش الخامس — 3 مرات في أول يومين
        val early = listOf("2026-09-28", "2026-09-28", "2026-09-29").map { txn(it, 20_000, fun_) }
        assertEquals(emptySet(), candidates(early, "2026-09-29").kinds(), "اليوم التاني ⇒ بدري")
        assertTrue(AlertKind.CAP_PACE in candidates(early, "2026-09-30").kinds(), "اليوم التالت ⇒ بنحكم")
    }

    @Test fun habitMeasuresAgainstTheStarredGoal() = runBlocking<Unit> {
        // خطة قريبة فايضها كبير (ساكتة) وخطة أبعد متأخرة (بتنبّه) — بيانات مخترعة
        val repo = MemorySavingsGoalRepository(
            listOf(
                SavingsGoal("g-near", "خطة قريبة وهمية", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31", createdAt = "c", updatedAt = "c"),
                SavingsGoal("g-far", "خطة بعيدة وهمية", 2_000_000, Currency.SAR, "2026-01-01", "2027-06-30", createdAt = "c", updatedAt = "c"),
            ),
        )
        val deposits = MemoryGoalContributionRepository(listOf(GoalContribution("c-n", "g-near", "2026-02-01", 1_190_000, "x"), GoalContribution("c-f", "g-far", "2026-02-01", 500_000, "x")))
        val overview = LoadGoalsOverview(LoadGoalsOverviewDeps(repo, deposits))
        val manage = ManageSavingsGoals(ManageSavingsGoalsDeps(repo, deposits, SequentialIdGenerator(), FixedClock("2026-10-03T08:00:00.000Z")))
        val coffees = listOf("2026-09-29", "2026-10-01", "2026-10-02").map { txn(it, 10_000, coffee) }
        assertEquals(emptySet(), candidates(coffees, "2026-10-03", overview = overview).kinds(), "من غير نجمة ⇒ الأقرب (لسه هتكمل) ⇒ ساكت")
        manage.star("g-far")
        val c = candidates(coffees, "2026-10-03", overview = overview).single()
        assertEquals(AlertKind.HABIT_VS_GOAL, c.kind)
        assertTrue(c.body.contains("بدل 20,000.00 ر.س بحلول 2027-06-30"), "على الخطة اللي عليها نجمة رغم إن التانية أقرب: ${c.body}")
        manage.archive("g-far")
        assertEquals(emptySet(), candidates(coffees, "2026-10-03", overview = overview).kinds(), "النجمة على مؤرشفة ⇒ الأقرب تاني")
    }

    @Test fun twoCoffeesStayQuietThreeCoffeesWarn() = runBlocking<Unit> {
        val two = listOf("2026-09-29", "2026-10-02").map { txn(it, 15_000, coffee) }
        assertEquals(emptySet(), candidates(two, "2026-10-03").kinds(), "مرتين بس، حتى لو أعلى من المعتاد بكتير")
        val three = listOf("2026-09-29", "2026-10-01", "2026-10-02").map { txn(it, 15_000, coffee) }
        val c = candidates(three, "2026-10-03").single()
        assertEquals(AlertKind.HABIT_VS_GOAL, c.kind)
        assertTrue(c.body.startsWith("قهوة ومشروبات هذا الشهر 450.00 ر.س — بهذا المعدل ستحوش "), c.body)
        assertTrue(c.body.contains("بدل 12,000.00 ر.س آخر السنة") && c.body.contains("مرات في الأسبوع"), c.body)
    }

    @Test fun unknownInputsGiveNoAlertNotAFalseOne() = runBlocking<Unit> {
        val three = listOf("2026-09-29", "2026-10-01", "2026-10-02").map { txn(it, 15_000, coffee) }
        assertEquals(emptySet(), candidates(three, "2026-10-03", past = emptyList()).kinds(), "مفيش تاريخ ⇒ المعتاد مش معروف")
        val shaky = history() + txn("2026-07-30", 5_000, grocery, EconomicKind.UNCLASSIFIED)
        assertEquals(emptySet(), candidates(three, "2026-10-03", past = shaky).kinds(), "شهر فيه عملية من غير نوع ⇒ مش معروف")
        assertEquals(emptySet(), candidates(three, "2026-10-03", withGoal = false).kinds(), "مفيش خطة ⇒ مفيش habitVsGoal")
        val split = listOf("2026-09-28", "2026-09-29", "2026-10-01", "2026-10-02").map { txn(it, 20_000, fun_) }
        assertTrue(AlertKind.CAP_PACE !in candidates(split, "2026-10-02", withBudget = false).kinds(), "مفيش ميزانية ⇒ مفيش سقف")
        val groceries = listOf("2026-09-28", "2026-09-30", "2026-10-02").map { txn(it, 60_000, grocery) }
        assertEquals(emptySet(), candidates(groceries, "2026-10-03").kinds(), "البقالة مش اختيارية ومالهاش سقف")
    }
}
