package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** r06-polish: ديون الكاش القديمة (قبل علامة `cashOrigin`) بتتقري كاش لو عمليتها كاش أو مش موجودة. بيانات مخترعة. */
class OldCashDebtsTest {
    private fun tx(id: String, cash: Boolean) = Transaction(
        id = id, occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = 5_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = cash, createdAt = "x", updatedAt = "x",
    )

    @Test fun cashOrMissingOperationReadsAsCashOthersUnchanged() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository(listOf(tx("t-cash", true), tx("t-bank", false)))
        val obligations = MemoryObligationRepository()
        obligations.saveMany(
            listOf(
                Obligation("o-cash", "p-1", "t-cash", ObligationKind.RECEIVABLE, 5_000, Currency.SAR),
                Obligation("o-gone", "p-1", "t-gone", ObligationKind.RECEIVABLE, 5_000, Currency.SAR),
                Obligation("o-bank", "p-1", "t-bank", ObligationKind.RECEIVABLE, 5_000, Currency.SAR),
                Obligation("o-old", "p-1", null, ObligationKind.RECEIVABLE, 5_000, Currency.SAR),
                Obligation("o-new", "p-1", "t-bank", ObligationKind.LOAN_PAYABLE, 5_000, Currency.SAR, cashOrigin = true),
            ),
        )
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val people = ManagePeople(
            ManagePeopleDeps(
                MemoryPersonRepository(listOf(Person("p-1", "شخص تجريبي"))), obligations, settlements, MemorySettlementWriter(obligations, settlements),
                allocations, txns, MemoryUnitOfWork(listOf(txns)), SequentialIdGenerator(), FixedClock("2026-10-20T10:00:00.000Z"),
            ),
        )
        val cash = people.listWithBalances().single().obligations.associate { it.obligation.id to it.obligation.cashOrigin }
        assertEquals(mapOf("o-cash" to true, "o-gone" to true, "o-bank" to false, "o-old" to false, "o-new" to true), cash)
        // القراية بس — مفيش كتابة
        assertEquals(false, obligations.all().single { it.id == "o-cash" }.cashOrigin)
    }
}
