package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
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

/**
 * مراجعة r06 people-1: الدين القديم في بلد عملتها الجنيه بيتحفظ بالجنيه (كان بالريال دايمًا)، والتصليح بيرجّع اللي اتحفظ غلط — مرة واحدة،
 * والدين اللي ليه عملية ما بيتلمسش. كل الأسامي والمبالغ مخترعة.
 */
class OpeningDebtCurrencyTest {
    private val friend = Person("p-1", "صديق تجربة")

    @Test fun anOpeningDebtInAnEgyptianSpaceIsSavedInPounds() = runBlocking<Unit> {
        val people = MemoryPersonRepository(listOf(friend))
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val manage = ManagePeople(
            ManagePeopleDeps(
                people, obligations, settlements, MemorySettlementWriter(obligations, settlements), MemoryAllocationRepository(), MemoryTransactionRepository(),
                PassthroughUnitOfWork(), SequentialIdGenerator(), FixedClock("2026-10-15T10:00:00Z"),
            ),
        )
        val debt = manage.addOpeningDebt(friend.id, ObligationKind.RECEIVABLE, 25_000, Currency.EGP)
        assertEquals(Currency.EGP, debt.currency)
        assertEquals(listOf(Currency.EGP), obligations.listByPerson(friend.id).map { it.currency })
    }

    @Test fun theRepairFixesOldPoundDebtsSavedAsRiyalsOnce() = runBlocking<Unit> {
        val wrong = Obligation("o-1", friend.id, null, ObligationKind.LOAN_PAYABLE, 30_000, Currency.SAR)
        val fromTxn = Obligation("o-2", friend.id, "t-1", ObligationKind.RECEIVABLE, 10_000, Currency.SAR)
        val obligations = MemoryObligationRepository(listOf(wrong, fromTxn))
        val repair = RepairOpeningDebtCurrency(MemoryPersonRepository(listOf(friend)), obligations, Currency.EGP)

        assertEquals(1, repair.run())
        val after = obligations.listByPerson(friend.id).associateBy { it.id }
        assertEquals(wrong.copy(currency = Currency.EGP), after["o-1"], "المبلغ زي ما هو والعملة بقت الجنيه")
        assertEquals(fromTxn, after["o-2"], "الدين اللي ليه عملية ما بيتلمسش")
        assertEquals(0, repair.run(), "آمن التكرار")
        assertEquals(0, RepairOpeningDebtCurrency(MemoryPersonRepository(listOf(friend)), MemoryObligationRepository(listOf(wrong)), Currency.SAR).run(), "السعودية: مفيش تصليح")
    }
}
