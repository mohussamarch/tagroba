package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.computePersonBalance
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** «امسح» لدين اتكتب غلط (HANDOVER §6.5 بند 12) — كل الأسامي والمبالغ مخترعة. */
class DeleteDebtTest {
    private val friend = Person("p-1", "صديق تجربة")
    private val coffee = Transaction(
        id = "t-1", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "2026-10-01T10:00:00Z",
        updatedAt = "2026-10-01T10:00:00Z", walletId = "w-1",
    )

    private inner class World {
        val people = MemoryPersonRepository(listOf(friend))
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val txns = MemoryTransactionRepository(listOf(coffee))
        val terms = MemoryDebtTermsRepository()
        val uow = PassthroughUnitOfWork()
        val manage = ManagePeople(
            ManagePeopleDeps(
                people, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow,
                SequentialIdGenerator(), FixedClock("2026-10-15T10:00:00Z"),
            ),
        )
        val erase = DeleteDebt(DeleteDebtDeps(obligations, settlements, allocations, uow, terms))

        /** كل اللي المسح ممكن يلمسه — للمقارنة «زي ما كان» بالظبط. */
        suspend fun state(): List<Any> {
            val obl = obligations.listByPerson(friend.id)
            return listOf(
                obl.toSet(), settlements.listByObligations(obl.map { it.id }).toSet(), allocations.listByTransactionIds(listOf(coffee.id)).toSet(),
                terms.listAll().toSet(), computePersonBalance(friend.id, obl, settlements.listByObligations(obl.map { it.id })),
                computePeriodTotals(txns.listByDateRange("2026-01-01", "2026-12-31"), allocations.listByTransactionIds(listOf(coffee.id))),
            )
        }
    }

    @Test fun oldDebtWithoutAnOperationIsDeletedAndTheBalancesAreAsIfItWasNeverWritten() = runBlocking<Unit> {
        val w = World()
        val never = w.state()
        val debt = w.manage.addOpeningDebt(friend.id, ObligationKind.RECEIVABLE, 50_000)
        w.terms.save(DebtTerms(debt.id, friend.id, firstDueAt = "2026-11-01"))
        val removal = w.erase.delete(friend.id, debt.id)
        assertEquals(debt, removal.obligation)
        assertTrue(removal.terms != null, "ميعاده كمان اتشال")
        assertEquals(never, w.state(), "زي ما يكون ما اتكتبش خالص")
    }

    @Test fun undoBringsBackTheSameDebtWithTheSameIds() = runBlocking<Unit> {
        val w = World()
        val debt = w.manage.addOpeningDebt(friend.id, ObligationKind.LOAN_PAYABLE, 30_000)
        w.manage.settle(debt.id, friend.id, 10_000)
        w.terms.save(DebtTerms(debt.id, friend.id, firstDueAt = "2026-11-01"))
        val before = w.state()
        val removal = w.erase.delete(friend.id, debt.id)
        w.erase.restore(removal)
        assertEquals(before, w.state())
    }

    @Test fun repaymentsAreCountedInThePreviewAndRemovedWithTheDebt() = runBlocking<Unit> {
        val w = World()
        val never = w.state()
        val debt = w.manage.addOpeningDebt(friend.id, ObligationKind.RECEIVABLE, 40_000)
        w.manage.settle(debt.id, friend.id, 15_000)
        w.manage.settle(debt.id, friend.id, 5_000)
        val preview = w.erase.preview(friend.id, debt.id)
        assertEquals(2, preview.settlements.size, "التأكيد بيعدّ السداد قبل ما يتمسح")
        assertEquals(20_000, preview.settledMinor)
        assertEquals(1, w.obligations.listByPerson(friend.id).size, "المعاينة ما بتكتبش")
        w.erase.delete(friend.id, debt.id)
        assertEquals(never, w.state(), "ولا تسوية فضلت بتشاور على دين مش موجود")
    }

    @Test fun debtFromAnOperationTakesThePersonShareWithItAndLeavesTheOperation() = runBlocking<Unit> {
        val w = World()
        val never = w.state()
        val link = w.manage.linkToPerson(coffee.id, friend.id, ObligationKind.RECEIVABLE, 4_000)
        val preview = w.erase.preview(friend.id, link.obligation!!.id)
        assertTrue(preview.fromTransaction)
        assertEquals(listOf(link.allocation), preview.allocations, "نصيب الشخص اللي اتكتب مع الدين")
        w.erase.delete(friend.id, link.obligation!!.id)
        assertEquals(never, w.state(), "المصروف رجع كامل والعملية زي ما هي")
        assertEquals(listOf(coffee), w.txns.findByIds(listOf(coffee.id)))
    }

    @Test fun twoDebtsOnTheSameOperationEachTakesOnlyItsOwnShare() = runBlocking<Unit> {
        val w = World()
        val first = w.manage.linkToPerson(coffee.id, friend.id, ObligationKind.RECEIVABLE, 2_000, requestId = "a1")
        val second = w.manage.linkToPerson(coffee.id, friend.id, ObligationKind.RECEIVABLE, 2_000, requestId = "b2")
        w.erase.delete(friend.id, first.obligation!!.id)
        assertEquals(listOf(second.allocation), w.allocations.listByTransactionIds(listOf(coffee.id)), "نصيب الدين التاني فضل")
        assertEquals(listOf(second.obligation), w.obligations.listByPerson(friend.id))
    }

    @Test fun deletingADebtThatIsGoneSaysSoAndWritesNothing() = runBlocking<Unit> {
        val w = World()
        assertFailsWith<IllegalStateException> { w.erase.delete(friend.id, "obl-missing") }
    }
}
