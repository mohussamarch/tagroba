package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LEGACY_AUTO_SUPPORT_UNTIL
import app.masroufy.core.Person
import app.masroufy.core.PersonAllocation
import app.masroufy.core.AllocationKind
import app.masroufy.core.ReviewState
import app.masroufy.core.formatMoney
import app.masroufy.core.periodForDate
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.UiKey
import app.masroufy.core.computePersonBalance
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
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
import kotlin.test.assertTrue

/**
 * **§82-13 (رد المالك 2026-10-10 ✱ «يسألك عليها»)**: «دعم» اتسجل لوحده قبل قرار «سلفة ولا دعم؟» (§75-5) بياخد نفس السؤال — **سؤال، مش تغيير
 * في صمت**: النوع بيفضل «دعم» لحد ما المالك يجاوب. الحد = آخر تعديل على العملية قبل [LEGACY_AUTO_SUPPORT_UNTIL]. كل الأسامي والأرقام مخترعة.
 */
class LegacySupportAskTest {
    private val op = "عملية تحويل داخلية"
    private val out = "W-/TOACCT/11112222333344445TOTESTPERSON:ملاحظة"

    private fun support(id: String, amount: Long, date: String, updatedAt: String) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.SUPPORT_GIFT, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = updatedAt, updatedAt = updatedAt, rawDescription = out, sourceOperationType = op,
    )

    /** «دعم» لوحده وقت قرار «شخص» في التطبيق القديم (2025) · «دعم» المالك أكّده بعد القرار (بعد الحد) · «دعم» لشخص مربوط بالدفتر من برّه. */
    private val auto = support("t-auto", 30_000, "2025-03-04", "2025-03-04T09:00:00.000Z")
    private val autoToo = support("t-auto2", 12_000, "2026-09-20", "2026-09-20T18:00:00.000Z")
    private val afterDecision = support("t-new", 5_000, "2026-10-09", "2026-10-10T08:00:00.000Z")
    private val linkedByHand = support("t-linked", 7_000, "2025-06-01", "2025-06-01T09:00:00.000Z")

    private val ref = transferPartyOf(auto)!!
    private val person = TransferParty(ref.key, ref.label, ref.last4, TransferVerdict.PERSON, "p-1", "2025-03-01T00:00:00.000Z")

    private inner class World(clock: String) {
        val txns = MemoryTransactionRepository(listOf(auto, autoToo, afterDecision, linkedByHand))
        val parties = MemoryTransferPartyRepository(listOf(person))
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository(listOf(PersonAllocation("alloc-hand", "t-linked", "p-1", AllocationKind.GIFT, 7_000, Currency.SAR)))
        val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
        val time = FixedClock(clock)
        val uow = MemoryUnitOfWork(listOf(txns, parties))
        val people = ManagePeople(ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow, SequentialIdGenerator(), time))
        val answers = AnswerTransferAsks(AnswerTransferAsksDeps(txns, parties, obligations, settlements, allocations, persons, people, uow, time))
        val source = TransferAskSource(TransferAskSourceDeps("sa", txns, parties, obligations, settlements, allocations))

        suspend fun asks() = source.pending("2026-10-01", "2026-10-11").map { it.kind to it.transactionId }.sortedBy { it.second }
    }

    @Test fun anOldAutomaticSupportGetsTheQuestionWithoutChangingAnything() = runBlocking<Unit> {
        val w = World("2026-10-11T09:00:00.000Z")
        // مهما كان تاريخها (برّه نافذة الأيام) — والمربوطة بالدفتر من برّه والمؤكدة بعد القرار ما عليهاش سؤال
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to "t-auto", AskKind.LOAN_OR_SUPPORT to "t-auto2"), w.asks())
        assertTrue(w.source.pending("2026-10-01", "2026-10-11").filter { it.transactionId == "t-auto" }.all { it.date == null }, "counted outside the window")
        val q = w.answers.question("t-auto")!!
        assertEquals(AskKind.LOAN_OR_SUPPORT to uiText(TextKey.ASK_LOAN_OR_SUPPORT), q.kind to q.title)
        assertEquals(uiText(UiKey.ASK_LEGACY_SUPPORT_BODY, formatMoney(30_000, Currency.SAR), "شخص وهمي"), q.body)
        // ولا حاجة اتغيّرت لوحدها
        assertEquals(listOf(auto, autoToo), w.txns.findByIds(listOf("t-auto", "t-auto2")))

        // «دعم» ⇒ بيفضل دعم ومؤكد، والسؤال ما بيرجعش
        val kept = w.answers.answerLoanOrSupport("t-auto2", LoanOrSupport.SUPPORT)
        assertEquals(EconomicKind.SUPPORT_GIFT to true, kept.economicKind to kept.economicKindConfirmed)
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to "t-auto"), w.asks())
        // «سلفة» ⇒ قرض ممنوح + دين ليك على الشخص بالمبلغ
        val loan = w.answers.answerLoanOrSupport("t-auto", LoanOrSupport.LOAN)
        assertEquals(EconomicKind.LOAN_GRANTED to true, loan.economicKind to loan.economicKindConfirmed)
        assertEquals(30_000L, computePersonBalance("p-1", w.obligations.all(), w.settlements.all()).receivableMinor)
        assertEquals(emptyList(), w.asks())
        assertFailsWith<TransferAskNotPending> { w.answers.answerLoanOrSupport("t-new", LoanOrSupport.LOAN) }
    }

    /** ساعة الجوال متأخرة (قبل الحد): «دعم» اللي المالك جاوبه ما يرجعش يتسأل. */
    @Test fun anAnswerWithABehindClockStillClosesTheQuestion() = runBlocking<Unit> {
        val w = World("2026-10-01T09:00:00.000Z")
        w.answers.answerLoanOrSupport("t-auto", LoanOrSupport.SUPPORT)
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to "t-auto2"), w.asks())
        assertTrue(w.txns.findByIds(listOf("t-auto")).single().updatedAt >= LEGACY_AUTO_SUPPORT_UNTIL)
    }

    /** في «محتاجة تأكيد» (§75-15): بيتعد مهما كان تاريخه — مرة لكل عملية. */
    @Test fun countedInNeedsConfirmationWhateverItsDate() = runBlocking<Unit> {
        val w = World("2026-10-11T09:00:00.000Z")
        val count = CountNeedsConfirmation(listOf(w.source)).load("2026-10-11", periodForDate("2026-10-11", 28))
        assertEquals(2, count.byKind[AskKind.LOAN_OR_SUPPORT])
        assertEquals(setOf("t-auto", "t-auto2"), count.asks.mapNotNull { it.transactionId }.toSet())
    }
}
