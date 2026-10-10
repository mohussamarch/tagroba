package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.computePersonBalance
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
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

/**
 * «ده سداد؟» (§75-9) لما التحويل **متسجل سداد من قبل** أو جه **قبل** الدين (مراجعة الشريحة S5) — كل الأسامي والأرقام مخترعة.
 * الطرف «TESTPERSON» آخر 4 أرقامه 4445، والشخص «شخص وهمي».
 */
class TransferAsksRepaymentTest {
    private val op = "عملية تحويل داخلية"
    private fun desc(dir: Direction) = if (dir == Direction.IN) "W-/FRACCT/11112222333344445FRTESTPERSON:ملاحظة" else "W-/TOACCT/11112222333344445TOTESTPERSON:ملاحظة"

    private fun transfer(id: String, dir: Direction, amount: Long, date: String = "2026-10-05") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawDescription = desc(dir), sourceOperationType = op,
    )

    private val partyRef = transferPartyOf(transfer("probe", Direction.OUT, 1))!!
    private val asPerson = TransferParty(partyRef.key, partyRef.label, partyRef.last4, TransferVerdict.PERSON, "p-1", "2026-10-01T00:00:00.000Z")

    private inner class World(seed: List<Transaction>) {
        val txns = MemoryTransactionRepository(seed)
        val parties = MemoryTransferPartyRepository(listOf(asPerson))
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
        val clock = FixedClock("2026-10-08T10:00:00.000Z")
        val uow = MemoryUnitOfWork(listOf(txns, parties))
        val people = ManagePeople(
            ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow, SequentialIdGenerator(), clock),
        )
        val answers = AnswerTransferAsks(AnswerTransferAsksDeps(txns, parties, obligations, settlements, allocations, persons, people, uow, clock))
        val source = TransferAskSource(TransferAskSourceDeps("sa", txns, parties, obligations, settlements, allocations))

        fun balance() = computePersonBalance("p-1", obligations.all(), settlements.all())
        suspend fun asks() = source.pending("2025-01-01", "2026-12-31").map { it.kind to it.transactionId }
        suspend fun kind(id: String) = txns.findByIds(listOf(id)).single().let { it.economicKind to it.economicKindConfirmed }
    }

    /** المالك ربط التحويل بالدين بنفسه (شاشة الأشخاص أو التطبيق القديم — بمعرّف تسوية عادي) ⇒ مفيش سؤال، ولا سداد تاني ولو بالجملة. */
    @Test fun aTransferTheOwnerAlreadySettledIsNotAskedOrSettledAgain() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-in", Direction.IN, 30_000)))
        val debt = w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 100_000)
        w.people.settle(debt.id, "p-1", 30_000, transactionId = "t-in")
        assertEquals(70_000L, w.balance().receivableMinor)
        assertNull(w.answers.askOf("t-in"))
        assertEquals(emptyList(), w.asks())
        assertFailsWith<TransferAskNotPending> { w.answers.answerDebtRepayment("t-in", yes = true) }
        val bulk = w.answers.confirmRepaymentAll(listOf("t-in"))
        assertEquals(emptyList<String>() to listOf("t-in"), bulk.answered to bulk.skipped)
        assertEquals(70_000L to 1, w.balance().receivableMinor to w.settlements.all().size, "مفيش تسوية تانية للـ300")
        assertEquals(EconomicKind.UNCLASSIFIED to false, w.kind("t-in"), "ولا كتابة على العملية")
    }

    /** دين قفلته تسوية التحويل نفسه بإيد المالك ⇒ السؤال ما بيفضلش معلّق للأبد ومرفوض «زيادة». */
    @Test fun aTransferThatClosedTheDebtByHandIsNotAskedForever() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-in", Direction.IN, 30_000)))
        val debt = w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 30_000)
        w.people.settle(debt.id, "p-1", 30_000, transactionId = "t-in")
        assertEquals(0L, w.balance().receivableMinor)
        assertNull(w.answers.askOf("t-in"))
        assertNull(w.answers.question("t-in"))
        assertEquals(emptyList(), w.asks())
    }

    /** الفلوس اللي جت **قبل** ما السلفة تتعمل ما بتتسألش «ده سداد السلفة؟» وما بتسددهاش — ولو «أيوه» بالجملة الأقدم الأول. */
    @Test fun moneyThatCameBeforeTheLoanIsNotItsRepayment() = runBlocking<Unit> {
        val w = World(
            listOf(
                transfer("t-jan", Direction.IN, 30_000, "2026-01-10"),
                transfer("t-loan", Direction.OUT, 50_000, "2026-10-05"),
                transfer("t-same", Direction.IN, 5_000, "2026-10-05"),
                transfer("t-nov", Direction.IN, 20_000, "2026-11-02"),
            ),
        )
        w.answers.answerLoanOrSupport("t-loan", LoanOrSupport.LOAN)
        assertNull(w.answers.askOf("t-jan"), "يناير قبل سلفة أكتوبر ⇒ مالوش سؤال سداد")
        assertEquals(listOf(AskKind.DEBT_REPAYMENT to "t-same", AskKind.DEBT_REPAYMENT to "t-nov"), w.asks(), "يوم السلفة وبعده بس")
        val bulk = w.answers.confirmRepaymentAll(listOf("t-jan", "t-nov"))
        assertEquals(listOf("t-nov") to listOf("t-jan"), bulk.answered to bulk.skipped)
        assertEquals(30_000L, w.balance().receivableMinor, "500 − 200 بس")
        assertEquals(EconomicKind.UNCLASSIFIED to false, w.kind("t-jan"))

        // الدين القديم من غير عملية (§27) بيتحسب دايمًا — والمفتوح في السؤال من غير السلفة اللي بعده
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 10_000)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-jan"))
        assertEquals(10_000L, w.answers.question("t-jan")!!.openDebtMinor)
        assertFailsWith<TransferAskError>("300 أكبر من الـ100 اللي كانت مفتوحة يومها") { w.answers.answerDebtRepayment("t-jan", yes = true) }
        assertEquals(40_000L, w.balance().receivableMinor, "ولا كتابة")
    }
}
