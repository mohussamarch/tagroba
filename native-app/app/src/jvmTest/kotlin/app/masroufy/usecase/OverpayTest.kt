package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.OverpayChoice
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.categoryDistribution
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.computePersonBalance
import app.masroufy.core.overpayOf
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §82-19 «دفعت أكتر من اللي عليك» — من لوحة السداد (من غير عملية) ومن سؤال «ده سداد دين عليك؟» على حوالة. الأرصدة لكل شخص وعملة لازم
 * تفضل صح بعد كل اختيار. كل الأسامي والأرقام مخترعة.
 */
class OverpayTest {
    private val op = "عملية تحويل داخلية"
    private fun desc(dir: Direction) = if (dir == Direction.IN) "W-/FRACCT/11112222333344445FRTESTPERSON:ملاحظة" else "W-/TOACCT/11112222333344445TOTESTPERSON:ملاحظة"

    private fun transfer(id: String, dir: Direction, amount: Long, date: String = "2026-10-05") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawDescription = desc(dir), sourceOperationType = op,
    )

    private val partyRef = transferPartyOf(transfer("probe", Direction.OUT, 1))!!
    private val asPerson = TransferParty(partyRef.key, partyRef.label, partyRef.last4, TransferVerdict.PERSON, "p-1", "2026-10-01T00:00:00.000Z")

    private inner class World(seed: List<Transaction> = emptyList()) {
        val txns = MemoryTransactionRepository(seed)
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val parties = MemoryTransferPartyRepository(listOf(asPerson))
        val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
        val clock = FixedClock("2026-10-08T10:00:00.000Z")
        val uow = MemoryUnitOfWork(listOf(txns, parties))
        val people = ManagePeople(
            ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, txns, uow, SequentialIdGenerator(), clock),
        )
        val answers = AnswerTransferAsks(AnswerTransferAsksDeps(txns, parties, obligations, settlements, allocations, persons, people, uow, clock))

        fun balance() = computePersonBalance("p-1", obligations.all(), settlements.all())
        suspend fun shares() = allocations.listByTransactionIds(listOf("t-out", "t-back"))
        fun money() = LoadMoneySummary(LoadMoneySummaryDeps(txns, MemoryCategoryRepository(), allocations))
        suspend fun kind(id: String) = txns.findByIds(listOf(id)).single().let { it.economicKind to it.economicKindConfirmed }
    }

    @Test fun overpayIsOnlyWhenPaidIsMoreThanAnOpenDebt() {
        assertEquals(20_000L, overpayOf(70_000, 50_000)?.surplusMinor)
        assertNull(overpayOf(50_000, 50_000), "قد الباقي بالظبط = تسوية عادية")
        assertNull(overpayOf(10_000, 0), "مفيش دين مفتوح ⇒ مفيش «أكتر من اللي عليك»")
    }

    @Test fun checkAsksOnlyForDebtsYouOwe() = runBlocking<Unit> {
        val w = World()
        val onYou = w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        val forYou = w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 50_000, app.masroufy.core.Currency.SAR)
        assertEquals(20_000L, w.people.overpay.check(onYou.id, "p-1", 70_000)?.surplusMinor)
        assertNull(w.people.overpay.check(onYou.id, "p-1", 50_000))
        assertNull(w.people.overpay.check(forYou.id, "p-1", 70_000), "هو اللي دفعلك أكتر ⇒ الرفض زي ما هو")
        assertFailsWith<IllegalStateException> { w.people.overpay.resolve(forYou.id, "p-1", 70_000, OverpayChoice.SUPPORT) }
        assertEquals(0, w.settlements.all().size, "ولا كتابة")
    }

    /** «أمانة ليك عنده»: الدين اتقفل بالباقي بالظبط، والزيادة دين ليك معلّم أمانة — «لك» = 200 ومنها 200 أمانة. */
    @Test fun heldForYouClosesTheDebtAndOpensCustodyHeldForYou() = runBlocking<Unit> {
        val w = World()
        val debt = w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        val out = w.people.overpay.resolve(debt.id, "p-1", 70_000, OverpayChoice.HELD_FOR_YOU)
        assertEquals(listOf(50_000L), out.settled.map { it.amountMinor })
        val created = out.created!!
        assertTrue(created.custodyHeld)
        assertEquals(ObligationKind.RECEIVABLE to 20_000L, created.kind to created.originalMinor)
        assertNull(created.originTransactionId, "من غير عملية — زي الدين القديم")
        val b = w.balance()
        assertEquals(listOf(0L, 20_000L, 20_000L), listOf(b.payableLoanMinor, b.receivableMinor, b.receivableCustodyMinor))
    }

    @Test fun newLoanIsAPlainReceivableAndSupportOnlyClosesTheDebt() = runBlocking<Unit> {
        val w = World()
        val a = w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        val loan = w.people.overpay.resolve(a.id, "p-1", 80_000, OverpayChoice.NEW_LOAN).created!!
        assertFalse(loan.custodyHeld)
        assertEquals(30_000L, w.balance().receivableMinor)
        assertEquals(0L, w.balance().receivableCustodyMinor)
        val b = w.people.addOpeningDebt("p-1", ObligationKind.CUSTODY_PAYABLE, 10_000, app.masroufy.core.Currency.SAR)
        val support = w.people.overpay.resolve(b.id, "p-1", 15_000, OverpayChoice.SUPPORT)
        assertNull(support.created, "الدعم من غير عملية ⇒ مفيش دين")
        val bal = w.balance()
        assertEquals(listOf(0L, 0L, 30_000L), listOf(bal.payableCustodyMinor, bal.payableLoanMinor, bal.receivableMinor))
    }

    @Test fun wrongAmountWritesNothing() = runBlocking<Unit> {
        val w = World()
        val debt = w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        val out = w.people.overpay.resolve(debt.id, "p-1", 70_000, OverpayChoice.WRONG_AMOUNT)
        assertTrue(out.editAmount)
        assertEquals(0 to 1, w.settlements.all().size to w.obligations.all().size)
        assertEquals(50_000L, w.balance().payableLoanMinor)
    }

    /** الدين بعملة البلد (جنيه) ⇒ الأمانة بنفس العملة — مش ريال، ورصيد كل عملة لوحده. */
    @Test fun theNewDebtKeepsTheDebtCurrency() = runBlocking<Unit> {
        val w = World()
        val egp = Obligation("obl-egp", "p-1", null, ObligationKind.LOAN_PAYABLE, 30_000, Currency.EGP)
        w.obligations.saveMany(listOf(egp))
        val created = w.people.overpay.resolve(egp.id, "p-1", 45_000, OverpayChoice.HELD_FOR_YOU).created!!
        assertEquals(Currency.EGP to 15_000L, created.currency to created.originalMinor)
    }

    /** الحوالة: «أيوه، سداد» على 700 والمفتوح 500 ⇒ سؤال «دفعت أكتر» بالزيادة، بنفس رسالة الرفض القديمة ومن غير كتابة. */
    @Test fun aTransferOverTheOpenDebtAsksInsteadOfJustRefusing() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-out", Direction.OUT, 70_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-out"))
        val e = assertFailsWith<TransferAskOverpaid> { w.answers.answerDebtRepayment("t-out", yes = true) }
        assertEquals(20_000L, e.overpay.surplusMinor)
        assertEquals(0, w.settlements.all().size)
        val bulk = w.answers.confirmRepaymentAll(listOf("t-out"))
        assertEquals(e.message, bulk.refused["t-out"], "«أيوه للكل» بيعرض نفس الرسالة")
    }

    @Test fun aTransferHeldForYouSettlesAndKeepsTheExtraAsCustody() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-out", Direction.OUT, 70_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        val answer = w.answers.answerOverpay("t-out", OverpayChoice.HELD_FOR_YOU)
        assertIs<DebtAnswer.Recorded>(answer)
        assertEquals(EconomicKind.DEBT_REPAID to true, w.kind("t-out"))
        val b = w.balance()
        assertEquals(listOf(0L, 20_000L, 20_000L), listOf(b.payableLoanMinor, b.receivableMinor, b.receivableCustodyMinor))
        assertEquals(listOf(AllocationKind.RECEIVABLE to 20_000L), w.shares().map { it.allocationKind to it.amountMinor })
        assertNull(w.answers.askOf("t-out"), "اتجاوب")
        assertFailsWith<TransferAskNotPending> { w.answers.answerOverpay("t-out", OverpayChoice.HELD_FOR_YOU) }
        // رجّعلك الأمانة بعدين ⇒ «ده سداد السلفة؟» بيقفلها
        w.txns.saveMany(listOf(transfer("t-back", Direction.IN, 20_000, "2026-10-07")))
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-back"))
        w.answers.answerDebtRepayment("t-back", yes = true)
        assertEquals(0L, w.balance().receivableMinor)
    }

    @Test fun aTransferSupportIsAGiftShareWithoutADebt() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-out", Direction.OUT, 70_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        w.answers.answerOverpay("t-out", OverpayChoice.SUPPORT)
        assertEquals(listOf(AllocationKind.GIFT to 20_000L), w.shares().map { it.allocationKind to it.amountMinor })
        val b = w.balance()
        assertEquals(listOf(0L, 0L), listOf(b.payableLoanMinor, b.receivableMinor))
        // §88: الزيادة «دعم» = مصروف الشهر زي أي هدية · السداد نفسه (500) مش مصروف ومكانه «خرج ومش مصروف»
        val month = w.money().load("2026-10-01", "2026-10-31")
        assertEquals(20_000L, month.expenseMinor)
        assertEquals(mapOf(EconomicKind.DEBT_REPAID to 50_000L), month.outflowNotExpense)
        val txn = w.txns.findByIds(listOf("t-out")).single()
        assertEquals(20_000L, categoryDistribution(listOf(txn), w.shares()).totalMinor, "توزيع التصنيفات نفس الرقم")
        assertTrue(w.shares().single().countedAsSpend)
        // «هدية» قديمة على سداد دين (من غير العلامة) ⇒ زي التطبيق القديم بالظبط: مش مصروف
        val old = w.shares().map { it.copy(countedAsSpend = false) }
        assertEquals(0L, computePeriodTotals(listOf(txn), old).personalExpenseMinor)
    }

    /** §88: «أمانة» و«سلفة جديدة» فلوس راجعة ⇒ مش مصروف؛ والعملية كلها «خرج ومش مصروف». */
    @Test fun heldOrNewLoanIsNotSpending() = runBlocking<Unit> {
        for (choice in listOf(OverpayChoice.HELD_FOR_YOU, OverpayChoice.NEW_LOAN)) {
            val w = World(listOf(transfer("t-out", Direction.OUT, 70_000)))
            w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
            w.answers.answerOverpay("t-out", choice)
            val month = w.money().load("2026-10-01", "2026-10-31")
            assertEquals(0L, month.expenseMinor, "$choice")
            assertEquals(mapOf(EconomicKind.DEBT_REPAID to 70_000L), month.outflowNotExpense, "$choice")
        }
    }

    @Test fun aTransferWrongAmountWritesNothingAndStaysAsked() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-out", Direction.OUT, 70_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        assertIs<DebtAnswer.FixAmount>(w.answers.answerOverpay("t-out", OverpayChoice.WRONG_AMOUNT))
        assertEquals(0 to 0, w.settlements.all().size to w.shares().size)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-out"))
        assertEquals(EconomicKind.UNCLASSIFIED to false, w.kind("t-out"))
    }

    @Test fun aTransferNotOverTheDebtIsRefused() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-out", Direction.OUT, 30_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 50_000, app.masroufy.core.Currency.SAR)
        assertFailsWith<TransferAskError> { w.answers.answerOverpay("t-out", OverpayChoice.SUPPORT) }
        assertEquals(0, w.settlements.all().size)
    }
}
