package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesCategories
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.Transaction
import app.masroufy.core.periodForDate
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * قرار المالك §56 بمثاله هو: أكل 1,500 · قسط جمعية 1,000 · قسط تمويل 1,000 ⇒ **مصروف الشهر 3,500** تحت «المستحقات»،
 * و«تاكل من حد الميزانية؟» إعداد في الحساب بيأثر على شاشة الميزانية بس.
 */
class DuesSpendingTest {
    private fun txn(id: String, dir: Direction, minor: Long, categoryId: String? = null, kind: EconomicKind = EconomicKind.UNCLASSIFIED) = Transaction(
        id = id, occurredAt = "2026-01-05", datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryId = categoryId, categoryConfirmed = categoryId != null,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
    )

    private val txns = MemoryTransactionRepository(
        listOf(
            txn("food", Direction.OUT, 150_000, "c-food", EconomicKind.PURCHASE),
            txn("rc", Direction.OUT, 100_000), txn("ip", Direction.OUT, 100_000), txn("po", Direction.IN, 1_000_000),
        ),
    )
    // المستخدم كان غيّر اسم «المستحقات» بنفسه — لازم يفضل زي ما هو
    private val categories = MemoryCategoryRepository(listOf(Category("c-food", null, "أكل", "utensils", "#AA3344", "#FF8899", true, 1), DuesCategories.defaults().first().copy(name = "التزاماتي")))
    private val entries = MemoryRoscaEntryRepository()
    private val payments = MemoryInstallmentPaymentRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-01-10T00:00:00.000Z")
    private val period = periodForDate("2026-01-10", 28)

    private suspend fun linkAll() {
        val roscas = ManageRoscas(ManageRoscasDeps(MemoryRoscaRepository(), entries, payments, txns, MemoryUnitOfWork(listOf(entries, txns)), ids, clock, categories))
        val rosca = roscas.save(RoscaInput(name = "جمعية وهمية", currency = Currency.SAR, contributionMinor = 100_000, firstDueAt = "2025-10-01", cycleCount = 10, myTurns = listOf(4)))
        roscas.link(rosca.id, "rc", RoscaEntryKind.CONTRIBUTION)
        roscas.link(rosca.id, "po", RoscaEntryKind.PAYOUT)
        val plans = ManageInstallments(
            ManageInstallmentsDeps(
                MemoryInstallmentPlanRepository(), payments, entries, MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns)), ids, clock, categories,
            ),
        )
        val plan = plans.save(
            InstallmentInput(
                name = "تمويل وهمي", provider = "بنك وهمي", kind = InstallmentKind.FINANCING, currency = Currency.SAR,
                principalMinor = 1_000_000, totalMinor = 1_200_000, installmentMinor = 100_000, firstDueAt = "2026-01-05",
            ),
        )
        plans.link(plan.id, "ip")
    }

    @Test fun duesAreSpendingUnderTheirOwnCategory() = runBlocking<Unit> {
        linkAll()
        val byId = txns.findByIds(listOf("rc", "ip", "po")).associateBy { it.id }
        assertEquals(DuesCategories.ROSCAS, byId.getValue("rc").categoryId)
        assertEquals(DuesCategories.ROSCAS, byId.getValue("po").categoryId)
        assertEquals(DuesCategories.FINANCING, byId.getValue("ip").categoryId)
        // التصنيفات اتعملت مرة واحدة، واللي المستخدم سمّاه بنفسه ما اتكتبش فوقه
        val all = categories.listAll()
        assertEquals(1, all.count { it.id == DuesCategories.ROOT })
        assertEquals("التزاماتي", all.single { it.id == DuesCategories.ROOT }.name)
        assertEquals(setOf(DuesCategories.ROSCAS, DuesCategories.FINANCING, DuesCategories.PURCHASE_PLANS), all.filter { it.parentId == DuesCategories.ROOT }.map { it.id }.toSet())

        val home = LoadHomeScreen(LoadHomeScreenDeps(txns, categories, MemoryAllocationRepository(), MemoryBudgetRepository()))
            .load(LoadHomeScreenRequest(period, "2026-01-10", 28, includeHistory = false))
        assertEquals(350_000, home.expenseMinor, "مثال المالك: كله مصروف")
        assertEquals(1_000_000, home.incomeMinor, "قبض الجمعية دخل")
    }

    @Test fun ownerDecidesWhetherDuesCountAgainstTheBudget() = runBlocking<Unit> {
        linkAll()
        val budgets = MemoryBudgetRepository(listOf(Budget(period.key, period.key, period.start, period.end, 400_000, 80, "x", "x")))
        val screen = LoadBudgetScreen(LoadBudgetScreenDeps(txns, categories, MemoryAllocationRepository(), budgets))
        assertEquals(350_000, screen.load(LoadBudgetScreenRequest(period, "2026-01-10", 28)).spentMinor, "من غير إعداد: بتاكل من الحد")
        val outside = screen.load(LoadBudgetScreenRequest(period, "2026-01-10", 28, duesInBudget = false))
        assertEquals(150_000, outside.spentMinor, "برا الحد: الأكل بس")
    }

    @Test fun unlinkingClearsTheDuesCategory() = runBlocking<Unit> {
        linkAll()
        ManageRoscas(ManageRoscasDeps(MemoryRoscaRepository(), entries, payments, txns, MemoryUnitOfWork(listOf(entries, txns)), ids, clock, categories)).unlink("rc")
        val t = txns.findByIds(listOf("rc")).single()
        assertNull(t.categoryId)
        assertEquals(EconomicKind.UNCLASSIFIED, t.economicKind)
    }
}
