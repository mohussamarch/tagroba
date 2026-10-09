package app.masroufy.usecase

import app.masroufy.core.CashMovement
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.periodForDate
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * قرار المالك §58: السلفة بتبان في «حركة الفلوس» مش في «الدخل الحقيقي» — بمثال وهمي:
 * مرتب 6,000 · سلفة من تاح 2,000 · تحويل بين محافظي 1,000 · شراء 1,500 · سداد السلفة 2,000.
 */
class MoneySummaryTest {
    private fun txn(id: String, dir: Direction, minor: Long, kind: EconomicKind) = Transaction(
        id = id, occurredAt = "2026-01-05", datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private val txns = MemoryTransactionRepository(
        listOf(
            txn("salary", Direction.IN, 600_000, EconomicKind.SALARY),
            txn("loan", Direction.IN, 200_000, EconomicKind.LOAN_RECEIVED),
            txn("move-out", Direction.OUT, 100_000, EconomicKind.INTERNAL_TRANSFER),
            txn("move-in", Direction.IN, 100_000, EconomicKind.INTERNAL_TRANSFER),
            txn("food", Direction.OUT, 150_000, EconomicKind.PURCHASE),
            txn("repay", Direction.OUT, 200_000, EconomicKind.DEBT_REPAID),
        ),
    )

    @Test fun cashMovesMoreThanRealIncome() = runBlocking<Unit> {
        val s = LoadMoneySummary(LoadMoneySummaryDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository())).load("2026-01-01", "2026-12-31")
        assertEquals(CashMovement(800_000, 350_000), s.cash, "التحويل بين محافظي مش حركة")
        assertEquals(600_000, s.incomeMinor, "الدخل الحقيقي من غير السلفة")
        assertEquals(150_000, s.expenseMinor)
        assertEquals(mapOf(EconomicKind.LOAN_RECEIVED to 200_000L), s.inflowNotIncome)
        assertEquals(mapOf(EconomicKind.DEBT_REPAID to 200_000L), s.outflowNotExpense)
    }

    /** §75-1: فلوس داخلة نوعها مش معروف ⇒ «حركة» بس، ومستنية برّه الدخل الحقيقي لحد ما تتأكد. */
    @Test fun unknownIncomingIsMovementButNotIncomeYet() = runBlocking<Unit> {
        val unknown = txn("who", Direction.IN, 150_000, EconomicKind.UNCLASSIFIED).copy(economicKindConfirmed = false)
        val withUnknown = MemoryTransactionRepository(txns.listByDateRange("2026-01-01", "2026-12-31") + unknown)
        val s = LoadMoneySummary(LoadMoneySummaryDeps(withUnknown, MemoryCategoryRepository(), MemoryAllocationRepository())).load("2026-01-01", "2026-12-31")
        assertEquals(CashMovement(950_000, 350_000), s.cash, "دخلت فعلًا")
        assertEquals(600_000, s.incomeMinor, "بس مش دخل لسه")
        assertEquals(150_000, s.expenseMinor, "ولا بتخلّي الصرف مش معروف")
        assertEquals(1, s.pendingIncomingCount)
        assertEquals(mapOf(Currency.SAR to 150_000L), s.pendingIncomingMinor)
        assertEquals(150_000, s.inflowNotIncome[EconomicKind.UNCLASSIFIED], "ظاهرة في «اللي دخل ومش دخل» بنوعها «لسه»")
    }

    @Test fun homeShowsBoth() = runBlocking<Unit> {
        val home = LoadHomeScreen(LoadHomeScreenDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository(), MemoryBudgetRepository()))
            .load(LoadHomeScreenRequest(periodForDate("2026-01-10", 28), "2026-01-10", 28, includeHistory = false))
        assertEquals(600_000, home.incomeMinor)
        assertEquals(CashMovement(800_000, 350_000), home.cash)
    }
}
