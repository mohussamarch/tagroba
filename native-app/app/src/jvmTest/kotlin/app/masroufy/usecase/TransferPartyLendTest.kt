package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.computePersonBalance
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-polish: «سلفة» من «مَن هذا؟» على صادر لشخص ⇒ دين «ليك» عليه مربوط بالعملية (نفس «ضيف دين»)، و«تراجع» بيشيله.
 * كل الأسامي والأرقام مخترعة (الطرف «SAMPLEPAYER» · آخر 4 أرقام 7788).
 */
class TransferPartyLendTest {
    private fun transfer(id: String, amount: Long, dir: Direction) = Transaction(
        id = id, occurredAt = "2026-10-10", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = if (dir == Direction.IN) "W-/FRACCT/99990000111177885FRSAMPLEPAYER:ملاحظة" else "W-/TOACCT/99990000111177885TOSAMPLEPAYER:ملاحظة",
        sourceOperationType = "عملية تحويل داخلية",
    )

    private val txns = MemoryTransactionRepository(listOf(transfer("t-out", 30_000, Direction.OUT), transfer("t-gift", 7_000, Direction.OUT)))
    private val party = transferPartyOf(transfer("probe", 1, Direction.OUT))!!
    private val parties = MemoryTransferPartyRepository()
    private val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص تجريبي")))
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val allocations = MemoryAllocationRepository()
    private val links = MemoryPersonLinkRepository()
    private val terms = MemoryDebtTermsRepository()
    private val clock = FixedClock("2026-10-20T10:00:00.000Z")
    private val ids = SequentialIdGenerator()
    private val uow = MemoryUnitOfWork(listOf(txns, parties, links))
    private val people = ManagePeople(ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow, ids, clock))
    private val personLinks = ManagePersonLinks(
        PersonLinksDeps(links, persons, obligations, settlements, allocations, txns, MemoryCategoryRepository(), parties, uow, ids, clock, terms = terms),
    )
    private val installments = ManageInstallments(
        ManageInstallmentsDeps(
            MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(), MemoryRoscaEntryRepository(), terms, obligations, txns, uow, ids, clock,
            MemoryCategoryRepository(),
        ),
    )
    private val transfers = ManageTransfers(ManageTransfersDeps(txns, parties, persons, uow, clock))
    private val asks = AnswerTransferAsks(AnswerTransferAsksDeps(txns, parties, obligations, settlements, allocations, persons, people, uow, clock))
    private val kinds = SetEconomicKind(SetEconomicKindDeps(txns, MemoryCategoryRepository(), uow, clock))
    private val answers = TransferPartyAnswers(
        TransferPartyAnswersDeps(
            txns, parties, transfers, asks, kinds, PersonLedgerRepos(obligations, settlements, allocations), uow, clock,
            personLinks = links, lend = AddDebt(people, personLinks, installments, obligations), links = personLinks,
        ),
    )

    private suspend fun tx(id: String) = txns.findByIds(listOf(id)).single()
    private fun owed() = computePersonBalance("p-1", obligations.all(), settlements.all()).receivableMinor

    @Test fun loanToPersonCreatesLinkedDebtForYouAndUndoRemovesIt() = runBlocking<Unit> {
        val added = assertNotNull(answers.markPerson(party, "t-out", "p-1", EconomicKind.LOAN_GRANTED))
        assertEquals(ObligationKind.RECEIVABLE, added.obligation.kind)
        assertEquals(30_000L, added.obligation.originalMinor)
        assertEquals("t-out", added.obligation.originTransactionId)
        assertEquals(30_000L, owed())
        assertEquals(EconomicKind.LOAN_GRANTED, tx("t-out").economicKind)
        assertTrue(tx("t-out").economicKindConfirmed)
        assertEquals("p-1", parties.listAll().single { it.key == party.key }.personId)

        assertTrue(answers.undoLend("t-out"))
        assertEquals(0L, owed())
        assertTrue(obligations.all().isEmpty())
    }

    @Test fun otherKindsOnlySetTheKindWithoutDebt() = runBlocking<Unit> {
        assertNull(answers.markPerson(party, "t-gift", "p-1", EconomicKind.SUPPORT_GIFT))
        assertEquals(EconomicKind.SUPPORT_GIFT, tx("t-gift").economicKind)
        assertTrue(obligations.all().isEmpty())
    }
}
