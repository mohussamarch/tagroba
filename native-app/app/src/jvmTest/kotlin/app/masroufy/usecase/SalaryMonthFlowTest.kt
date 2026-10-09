package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EstimatePolicy
import app.masroufy.core.Period
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * مراجعة الشريحة S6 على §75-3 و§58: الراتب اللي بينزل في نفس اليوم كل شهر بيطلع **مرة واحدة في كل فترة** على الرئيسية (ولا شهر فاضي
 * ولا شهر براتبين)، و«حركة الفلوس» بتاريخ العملية (اللي دخل فعلًا) والدخل بشهر الراتب. كل المبالغ مخترعة.
 */
class SalaryMonthFlowTest {
    @AfterTest
    fun reset() {
        EstimatePolicy.current = EstimatePolicy.OWNER_2026_10
    }

    private fun txn(id: String, date: String, minor: Long, kind: EconomicKind, dir: Direction) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private fun salary(date: String) = txn("sal-$date", date, 1_000_000, EconomicKind.SALARY, Direction.IN)

    private fun home(rows: List<Transaction>) =
        LoadHomeScreen(LoadHomeScreenDeps(MemoryTransactionRepository(rows), MemoryCategoryRepository(), MemoryAllocationRepository(), MemoryBudgetRepository()))

    /** دخل كل فترة من [periods] على الرئيسية. */
    private suspend fun incomes(rows: List<Transaction>, payday: Int, periods: List<Period>): List<Long?> {
        val case = home(rows)
        return periods.map { case.load(LoadHomeScreenRequest(it, "2027-12-31", payday, includeHistory = false)).incomeMinor }
    }

    /** المراجعة: يوم راتب 1 والراتب يوم 24 كل شهر — سبتمبر (30 يوم) كان بيطلع فاضي وأكتوبر براتبين. */
    @Test fun paydayOneSalaryOnTheTwentyFourthEveryMonth() = runBlocking<Unit> {
        val rows = (7..12).map { salary("2026-${it.toString().padStart(2, '0')}-24") }
        val periods = (8..12).map { buildPeriod(2026, it, 1) }
        assertEquals(List(5) { 1_000_000L }, incomes(rows, 1, periods), "راتب 24 الشهر اللي فات في كل فترة")
    }

    /** يوم راتب 31 (بيتقيد بآخر الشهر القصير) والراتب يوم 23 أو 24 أو 25. */
    @Test fun paydayThirtyOneSalaryAroundTheTwentyFourth() = runBlocking<Unit> {
        for (day in 23..25) {
            val rows = (1..7).map { salary("2027-${it.toString().padStart(2, '0')}-$day") }
            val periods = (1..6).map { buildPeriod(2027, it, 31) }
            assertEquals(List(6) { 1_000_000L }, incomes(rows, 31, periods), "الراتب يوم $day")
        }
    }

    /** يوم راتب 28 والراتب عادةً يوم 21 وساعات يوم 20 (إجازة) — الحد في نص الشهر، فالاتنين للشهر الجديد. */
    @Test fun paydayTwentyEightSalaryWanderingBetweenTheTwentiethAndTwentyFirst() = runBlocking<Unit> {
        val rows = listOf("2026-07-21", "2026-08-21", "2026-09-20", "2026-10-21", "2026-11-20", "2026-12-21").map(::salary)
        val periods = (7..11).map { buildPeriod(2026, it, 28) }
        assertEquals(List(5) { 1_000_000L }, incomes(rows, 28, periods))
    }

    private val aug = buildPeriod(2026, 8, 28)
    private val sep = buildPeriod(2026, 9, 28)

    /** §58: «اللي دخل» = اللي وصل فعلًا بتاريخه. الراتب اللي نزل 26 سبتمبر في «اللي دخل» بتاع أغسطس، وفي دخل سبتمبر. */
    @Test fun cashMovementFollowsTheRealDateIncomeTheSalaryMonth() = runBlocking<Unit> {
        val rows = listOf(salary("2026-09-26"), txn("food", "2026-10-01", 50_000, EconomicKind.PURCHASE, Direction.OUT))
        val case = home(rows)
        val homeAug = case.load(LoadHomeScreenRequest(aug, "2026-09-27", 28, includeHistory = false))
        assertEquals(0L to 1_000_000L, homeAug.incomeMinor to homeAug.cash.inMinor, "أغسطس: مش دخله، بس دخل فعلًا فيه")
        val homeSep = case.load(LoadHomeScreenRequest(sep, "2026-10-05", 28, includeHistory = false))
        assertEquals(1_000_000L to 0L, homeSep.incomeMinor to homeSep.cash.inMinor, "سبتمبر: دخله، بس ما دخلش فيه")
        assertEquals(50_000, homeSep.cash.outMinor)

        val money = LoadMoneySummary(LoadMoneySummaryDeps(MemoryTransactionRepository(rows), MemoryCategoryRepository(), MemoryAllocationRepository()))
        val sepSummary = money.load(sep.start, sep.end, 28)
        assertEquals(1_000_000L to 0L, sepSummary.incomeMinor to sepSummary.cash.inMinor)
        assertEquals(listOf("sal-2026-09-26") to 1_000_000L, sepSummary.countedFromEarlier.map { it.id } to sepSummary.countedFromEarlierMinor, "الفرق متفسّر")
        val twoMonths = money.load(aug.start, sep.end, 28)
        assertEquals(1_000_000L to 1_000_000L, twoMonths.incomeMinor to twoMonths.cash.inMinor, "مدى الشهرين: مرة واحدة في الاتنين")
        assertEquals(emptyList(), twoMonths.countedFromEarlier + twoMonths.countedInNextPeriod)
    }

    /** المراجعة: ملخص «لحد النهارده» براتب الشهر الجاي اللي نزل بدري — كان بيختفي من الدخل ومن «اللي دخل»، ومن غير خانة تعرضه. */
    @Test fun moneySummaryToTodayKeepsTheEarlySalaryVisible() = runBlocking<Unit> {
        val rows = listOf(salary("2026-10-23"))
        val money = LoadMoneySummary(LoadMoneySummaryDeps(MemoryTransactionRepository(rows), MemoryCategoryRepository(), MemoryAllocationRepository()))
        val toToday = money.load(sep.start, "2026-10-25", 28)
        assertEquals(0L to 1_000_000L, toToday.incomeMinor to toToday.cash.inMinor, "دخل الشهر الجاي، بس دخل فعلًا")
        assertEquals(listOf("sal-2026-10-23") to 1_000_000L, toToday.countedInNextPeriod.map { it.id } to toToday.countedInNextPeriodMinor, "بعلامة «بيتحسب للشهر الجديد»")
        assertEquals(1_000_000, money.load(sep.start, "2026-10-25").cash.inMinor, "من غير يوم راتب: نفس «اللي دخل»")
        EstimatePolicy.current = EstimatePolicy.LEGACY
        val old = money.load(sep.start, "2026-10-25", 28)
        assertEquals(1_000_000L to 1_000_000L, old.incomeMinor to old.cash.inMinor, "القديم: بتاريخه")
        assertEquals(emptyList(), old.countedInNextPeriod)
    }

    /** الميزانية: «تقريبي» بيعدّ اللي اتحسب بنوع تقديري بس — الداخل المستني مش جوه الرقم. */
    @Test fun budgetNoteCountsOnlyGuessesInsideTheSpend() = runBlocking<Unit> {
        val rows = listOf(
            txn("in-1", "2026-10-02", 150_000, EconomicKind.UNCLASSIFIED, Direction.IN),
            txn("out-1", "2026-10-03", 20_000, EconomicKind.UNCLASSIFIED, Direction.OUT),
        )
        val budget = LoadBudgetScreen(
            LoadBudgetScreenDeps(
                MemoryTransactionRepository(rows), MemoryCategoryRepository(), MemoryAllocationRepository(),
                MemoryBudgetRepository(listOf(Budget(sep.key, sep.key, sep.start, sep.end, 300_000, 80, "x", "x"))),
            ),
        ).load(LoadBudgetScreenRequest(sep, "2026-10-05", 28))
        assertFalse(budget.spentReliable, "الشراء المتخمن جوه الرقم")
        assertEquals(uiText(TextKey.BUDGET_SPENT_NEEDS_REVIEW, "1"), budget.spentNote, "عملية واحدة (الصادر) مش اتنين")
    }
}
