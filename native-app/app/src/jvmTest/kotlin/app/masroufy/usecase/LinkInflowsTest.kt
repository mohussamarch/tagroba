package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LinkRefusal
import app.masroufy.core.PersonAllocation
import app.masroufy.core.ReviewState
import app.masroufy.core.Settlement
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.categoryDistribution
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.walletBalancesOn
import app.masroufy.core.withEstimatedKinds
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §6.5-14 «عمليات تابعة»: مثال المالك (أكلة 120 وتحويل تابع 60 ⇒ مصروف 60 · الدخل من التحويل صفر · الرصيد زي ما هو) · أكتر من داخلة ·
 * عملة تانية مرفوضة · المجموع أكبر من المصروف مرفوض · المربوطة بتسوية مرفوضة · الفك بيرجّع كل حاجة بالظبط · مجاميع الشهر والتصنيفات.
 * كل الأرقام والأسامي مخترعة.
 */
class LinkInflowsTest {
    private var seq = 0

    private fun txn(
        id: String, date: String, amount: Long, dir: Direction, kind: EconomicKind,
        currency: Currency = Currency.SAR, category: String? = null, confirmed: Boolean = true,
    ) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = seq++, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = category != null, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", categoryId = category, walletId = "w-1",
    )

    private val meal = txn("meal", "2026-09-10", 12_000, Direction.OUT, EconomicKind.PURCHASE, category = "food")
    private val friend = txn("friend", "2026-09-11", 6_000, Direction.IN, EconomicKind.UNCLASSIFIED, confirmed = false)
    private val refund = txn("refund", "2026-09-12", 2_000, Direction.IN, EconomicKind.REFUND_RECEIVED)
    private val salary = txn("salary", "2026-09-01", 500_000, Direction.IN, EconomicKind.SALARY)
    private val dollars = txn("usd", "2026-09-11", 1_000, Direction.IN, EconomicKind.GIFT_RECEIVED, currency = Currency.USD)

    private inner class World(seed: List<Transaction>, allocations: List<PersonAllocation> = emptyList(), settlements: List<Settlement> = emptyList()) {
        val txns = MemoryTransactionRepository(seed)
        val allocs = MemoryAllocationRepository(allocations)
        val links = LinkInflows(
            LinkInflowsDeps(
                txns, allocs, MemorySettlementRepository(settlements), MemoryObligationRepository(), MemoryUnitOfWork(listOf(txns)),
                FixedClock("2026-09-20T10:00:00Z"),
            ),
        )

        fun one(id: String) = txns.all().single { it.id == id }

        suspend fun totals() = computePeriodTotals(withEstimatedKinds(txns.all(), emptyMap()).transactions, allocs.listByTransactionIds(txns.all().map { it.id }))

        fun balance() = walletBalancesOn(listOf(Wallet("w-1", "محفظة", Currency.SAR, "bank", 100_000, "2026-01-01")), txns.all(), "2026-12-31")["w-1"]
    }

    @Test fun theOwnersExampleCountsTheMealAtSixty() = runBlocking<Unit> {
        val w = World(listOf(meal, friend, salary))
        val before = w.totals()
        val balance = w.balance()
        assertNull(w.links.link("meal", listOf("friend")))
        val after = w.totals()
        assertEquals(6_000, after.personalExpenseMinor, "120 − 60 = 60")
        assertEquals(500_000, after.incomeMinor, "التحويل التابع مش دخل — الراتب بس")
        assertEquals(12_000, before.personalExpenseMinor)
        assertEquals(balance, w.balance(), "الرصيد ما اتغيرش")
        assertEquals("meal", w.one("friend").linkedExpenseId)
        assertEquals(6_000L, w.one("meal").linkedInflowMinor)
        assertEquals(EconomicKind.UNCLASSIFIED to 6_000L, w.one("friend").economicKind to w.one("friend").amountMinor, "النوع والمبلغ ما اتلمسوش")
        val view = assertIs<LinkedInflowsView.Expense>(w.links.load("meal"))
        assertEquals(6_000, view.netMinor)
        assertEquals(listOf("friend"), view.linked.map { it.id })
        val back = assertIs<LinkedInflowsView.Inflow>(w.links.load("friend"))
        assertEquals("meal", back.expense?.id)
    }

    @Test fun categoryTotalsUseTheNetAmount() = runBlocking<Unit> {
        val w = World(listOf(meal, friend))
        w.links.link("meal", listOf("friend"))
        val dist = categoryDistribution(withEstimatedKinds(w.txns.all(), emptyMap()).transactions)
        assertEquals(6_000, dist.slices.single { it.categoryId == "food" }.amountMinor)
        assertEquals(6_000, dist.totalMinor)
    }

    @Test fun severalInflowsAddUpAndCandidatesAreClosestFirst() = runBlocking<Unit> {
        val w = World(listOf(meal, friend, refund, salary, dollars))
        val view = assertIs<LinkedInflowsView.Expense>(w.links.load("meal"))
        assertEquals(listOf("friend", "refund", "salary"), view.candidates.map { it.id }, "نفس العملة بس، والأقرب الأول")
        assertNull(w.links.link("meal", listOf("friend", "refund")))
        assertEquals(4_000, w.totals().personalExpenseMinor, "120 − 60 − 20")
        assertEquals(500_000, w.totals().incomeMinor, "الاسترداد التابع ما نقّصش المصروف مرتين")
        assertEquals(8_000L, w.one("meal").linkedInflowMinor)
        val after = assertIs<LinkedInflowsView.Expense>(w.links.load("meal"))
        assertEquals(4_000, after.roomMinor)
        assertEquals(listOf("salary"), after.candidates.map { it.id })
    }

    @Test fun otherCurrencyAndOverTheExpenseAreRefusedWithoutWriting() = runBlocking<Unit> {
        val w = World(listOf(meal, friend, salary, dollars))
        val before = w.txns.all()
        assertEquals(LinkRefusal.OTHER_CURRENCY, w.links.link("meal", listOf("usd")))
        assertEquals(LinkRefusal.OVER_EXPENSE, w.links.link("meal", listOf("salary")), "الصافي ما ينزلش تحت الصفر")
        assertEquals(LinkRefusal.NOT_EXPENSE, w.links.link("salary", listOf("friend")))
        assertEquals(before, w.txns.all())
    }

    @Test fun theShareOnOthersAndSettledMoneyAreProtected() = runBlocking<Unit> {
        val owed = PersonAllocation("a-1", "meal", "p-1", AllocationKind.RECEIVABLE, 7_000, Currency.SAR)
        val w = World(listOf(meal, friend, refund), listOf(owed), listOf(Settlement("s-1", "friend", "o-1", 6_000)))
        assertEquals(LinkRefusal.LEDGER_LINKED, w.links.link("meal", listOf("friend")), "سداد دين اتحسب خلاص")
        val view = assertIs<LinkedInflowsView.Expense>(w.links.load("meal"))
        assertEquals(5_000, view.roomMinor, "120 − 70 على الشخص")
        assertEquals(listOf("refund"), view.candidates.map { it.id })
    }

    @Test fun unlinkRestoresEverythingExactly() = runBlocking<Unit> {
        val w = World(listOf(meal, friend, refund, salary))
        val totals = w.totals()
        assertNull(w.links.link("meal", listOf("friend", "refund")))
        assertTrue(w.links.unlink("refund"))
        assertEquals(6_000L, w.one("meal").linkedInflowMinor)
        assertTrue(w.links.unlink("friend"))
        assertEquals(totals, w.totals())
        for (t in listOf(meal, friend, refund)) assertEquals(t, w.one(t.id).copy(updatedAt = t.updatedAt), "زي ما كانت بالظبط")
        assertEquals(false, w.links.unlink("friend"), "مش تابعة لحاجة")
    }

    @Test fun aDeletedInflowIsRepairedOnLoad() = runBlocking<Unit> {
        val w = World(listOf(meal, friend))
        w.links.link("meal", listOf("friend"))
        w.txns.deleteMany(listOf("friend"))
        val view = assertIs<LinkedInflowsView.Expense>(w.links.load("meal"))
        assertEquals(12_000, view.netMinor)
        assertNull(w.one("meal").linkedInflowMinor)
    }
}
