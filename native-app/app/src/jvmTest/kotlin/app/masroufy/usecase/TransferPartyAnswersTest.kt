package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LABEL_DEFAULT_KIND
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferVerdict
import app.masroufy.core.applyTransferVerdict
import app.masroufy.core.computePersonBalance
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ردود «مَن هذا؟» الجديدة (طلب المالك 2026-10-16): شركة · مستحقات دايمًا · حاجة تانية · تجاهل — وكل واحد بتراجعه.
 * كل الأسامي والأرقام مخترعة: الطرف «SAMPLEPAYER» آخر 4 أرقامه 7788، والشخص «شخص تجريبي».
 */
class TransferPartyAnswersTest {
    private val op = "عملية تحويل داخلية"

    private fun transfer(id: String, amount: Long, date: String = "2026-10-10", dir: Direction = Direction.IN) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = if (dir == Direction.IN) "W-/FRACCT/99990000111177885FRSAMPLEPAYER:ملاحظة" else "W-/TOACCT/99990000111177885TOSAMPLEPAYER:ملاحظة",
        sourceOperationType = op,
    )

    private val party = transferPartyOf(transfer("probe", 1))!!

    private inner class World(seed: List<Transaction>) {
        val txns = MemoryTransactionRepository(seed)
        val parties = MemoryTransferPartyRepository()
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص تجريبي")))
        val clock = FixedClock("2026-10-08T10:00:00.000Z")
        val uow = MemoryUnitOfWork(listOf(txns, parties))
        val people = ManagePeople(
            ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow, SequentialIdGenerator(), clock),
        )
        val transfers = ManageTransfers(ManageTransfersDeps(txns, parties, persons, uow, clock))
        val asks = AnswerTransferAsks(AnswerTransferAsksDeps(txns, parties, obligations, settlements, allocations, persons, people, uow, clock))
        val kinds = SetEconomicKind(SetEconomicKindDeps(txns, MemoryCategoryRepository(), uow, clock))
        val answers = TransferPartyAnswers(
            TransferPartyAnswersDeps(txns, parties, transfers, asks, kinds, PersonLedgerRepos(obligations, settlements, allocations), uow, clock),
        )

        suspend fun tx(id: String) = txns.findByIds(listOf(id)).single()
        suspend fun verdict() = parties.listAll().firstOrNull { it.key == party.key }
        fun owed() = computePersonBalance("p-1", obligations.all(), settlements.all()).receivableMinor
    }

    @Test fun companyRemembersTheBodyAndSetsOnlyThisAmountThenUndoes() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 12_345), transfer("t-2", 500, "2026-10-11")))
        val before = w.tx("t-1")
        val undo = w.answers.markCompany(party, "t-1", EconomicKind.REFUND_RECEIVED)
        assertEquals(TransferVerdict.COMPANY to EconomicKind.REFUND_RECEIVED, w.verdict()!!.verdict to w.verdict()!!.kind)
        assertEquals(EconomicKind.REFUND_RECEIVED to true, w.tx("t-1").let { it.economicKind to it.economicKindConfirmed })
        assertEquals(EconomicKind.UNCLASSIFIED, w.tx("t-2").economicKind, "الجاي من الجهة بيتسأل عن نوعه")
        assertFailsWith<TransferPartyAnswerError> { w.answers.markCompany(party, "t-1", EconomicKind.LOAN_RECEIVED) }
        w.answers.undo(undo)
        assertNull(w.verdict())
        assertEquals(before.copy(updatedAt = w.tx("t-1").updatedAt), w.tx("t-1"))
    }

    @Test fun labelIsRememberedAndCarriedByFutureTransfers() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 80_000), transfer("t-2", 80_000, "2026-09-05")))
        val undo = w.answers.markLabel(party, "t-1", "  إيجار تجريبي ", null)
        assertEquals("إيجار تجريبي", w.verdict()!!.note)
        for (id in listOf("t-1", "t-2")) {
            val t = w.tx(id)
            assertEquals(Triple(LABEL_DEFAULT_KIND, true, "إيجار تجريبي"), Triple(t.economicKind, t.economicKindConfirmed, t.note), id)
        }
        // تحويل جديد بعد كده (نفس اللي التسجيل بيعمله — `applyTransferVerdict`)
        val next = applyTransferVerdict(transfer("t-3", 80_000, "2026-11-05"), w.verdict(), "now")
        assertEquals(LABEL_DEFAULT_KIND to "إيجار تجريبي", next.economicKind to next.note)
        assertFailsWith<TransferPartyAnswerError> { w.answers.markLabel(party, "t-1", " ", null) }
        w.answers.undo(undo)
        assertNull(w.verdict())
        assertEquals(EconomicKind.UNCLASSIFIED to null, w.tx("t-2").let { it.economicKind to it.note })
    }

    @Test fun labelWithAPickedKind() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 1_000)))
        w.answers.markLabel(party, "t-1", "أرباح تجريبية", EconomicKind.INVESTMENT_INCOME)
        assertEquals(EconomicKind.INVESTMENT_INCOME, w.tx("t-1").economicKind)
    }

    @Test fun duesSettleWhatThePersonOwesAndUndoRemovesIt() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 30_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 100_000, app.masroufy.core.Currency.SAR)
        val start = w.answers.markDues(party, "t-1", "p-1")
        assertTrue(start.settled)
        assertNull(start.choices)
        assertEquals(70_000L, w.owed())
        assertEquals(EconomicKind.DEBT_COLLECTED, w.tx("t-1").economicKind)
        assertTrue(w.verdict()!!.dues)
        assertEquals(1, w.answers.duesOf("p-1").size)
        w.answers.undo(start.undo)
        assertEquals(100_000L, w.owed(), "التراجع بيشيل السداد")
        assertNull(w.verdict())
        assertEquals(EconomicKind.UNCLASSIFIED, w.tx("t-1").economicKind)
    }

    @Test fun duesWithNothingOwedAsksOnceAndInventsNoDebt() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 30_000)))
        val start = w.answers.markDues(party, "t-1", "p-1")
        assertEquals(false, start.settled)
        assertTrue(EconomicKind.DEBT_COLLECTED !in start.choices!!)
        assertEquals(0, w.obligations.all().size, "ما بنخترعش دين")
        w.answers.answerDuesKind("t-1", EconomicKind.GIFT_RECEIVED)
        assertEquals(EconomicKind.GIFT_RECEIVED, w.tx("t-1").economicKind)
    }

    @Test fun duesRuleSettlesLaterTransfersAndCanBeStopped() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 10_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 50_000, app.masroufy.core.Currency.SAR)
        w.answers.markDues(party, "t-1", "p-1")
        w.txns.saveMany(listOf(transfer("t-2", 15_000, "2026-10-11")))
        assertEquals(true, w.answers.applyDues("t-2"))
        assertEquals(25_000L, w.owed())
        w.txns.saveMany(listOf(transfer("t-3", 5_000, "2026-10-12")))
        w.answers.stopDues(party.key)
        assertEquals(0, w.answers.sweepDues())
        assertEquals(false, w.answers.applyDues("t-3"))
        assertEquals(25_000L, w.owed())
        assertEquals(TransferVerdict.PERSON, w.verdict()!!.verdict, "الطرف بيفضل الشخص")
    }

    @Test fun ignoreIsListedRestorableAndUndoable() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", 1_000)))
        val undo = w.answers.ignore(party)
        assertEquals(listOf(party.key), w.answers.ignored().map { it.key })
        w.answers.undo(undo)
        assertEquals(emptyList(), w.answers.ignored())
        w.answers.ignore(party)
        w.answers.restore(party.key)
        assertNull(w.verdict())
    }
}
