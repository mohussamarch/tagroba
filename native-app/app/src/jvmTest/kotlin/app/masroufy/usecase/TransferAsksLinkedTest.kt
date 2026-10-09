package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
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
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.ObligationRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * أسئلة التحويل مع شخص مربوط لما العملية **مربوطة بالدفتر** أو الإجابة بتتغير (مراجعة الشريحة S5) — كل الأسامي والأرقام مخترعة.
 * الطرف «TESTPERSON» آخر 4 أرقامه 4445، والشخص «شخص وهمي».
 */
class TransferAsksLinkedTest {
    private val op = "عملية تحويل داخلية"
    private fun desc(dir: Direction) = if (dir == Direction.IN) "W-/FRACCT/11112222333344445FRTESTPERSON:ملاحظة" else "W-/TOACCT/11112222333344445TOTESTPERSON:ملاحظة"

    private fun transfer(id: String, dir: Direction, amount: Long, date: String = "2026-10-05") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawDescription = desc(dir), sourceOperationType = op,
    )

    private val partyRef = transferPartyOf(transfer("probe", Direction.OUT, 1))!!
    private val asPerson = TransferParty(partyRef.key, partyRef.label, partyRef.last4, TransferVerdict.PERSON, "p-1", "2026-10-01T00:00:00.000Z")

    private inner class World(
        seed: List<Transaction>,
        wrapTxns: (MemoryTransactionRepository) -> TransactionRepository = { it },
        wrapObligations: (MemoryObligationRepository) -> ObligationRepository = { it },
        /** وحدة عمل فايربيز بتمرّر الشغل زي ما هو (مش ذرّية) — للقطع في النص. */
        passthroughZone: Boolean = false,
    ) {
        private val seedIds = seed.map { it.id }
        val txns = MemoryTransactionRepository(seed)
        val repo = wrapTxns(txns)
        val parties = MemoryTransferPartyRepository(listOf(asPerson))
        val obligations = MemoryObligationRepository()
        val obligationRepo = wrapObligations(obligations)
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
        val clock = FixedClock("2026-10-08T10:00:00.000Z")
        val uow = MemoryUnitOfWork(listOf(txns, parties))
        val people = ManagePeople(
            ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, repo, uow, SequentialIdGenerator(), clock),
        )
        val answers = AnswerTransferAsks(AnswerTransferAsksDeps(repo, parties, obligations, settlements, allocations, persons, people, uow, clock))
        val source = TransferAskSource(TransferAskSourceDeps("sa", txns, parties, obligations, settlements, allocations))
        val zone = ManageTransfers(
            ManageTransfersDeps(repo, parties, persons, if (passthroughZone) PassthroughUnitOfWork() else uow, clock, PersonLedgerRepos(obligationRepo, settlements, allocations)),
        )

        suspend fun one(id: String) = txns.findByIds(listOf(id)).single()
        suspend fun kind(id: String) = one(id).let { it.economicKind to it.economicKindConfirmed }
        suspend fun balance() = computePersonBalance("p-1", obligations.all(), settlements.all())
        suspend fun shares() = allocations.listByTransactionIds(seedIds)
        suspend fun asks() = source.pending("2026-01-01", "2026-12-31").map { it.kind to it.transactionId }
    }

    /** مراجعة S5: سداد اتربط بالدين من «اربطها بدين موجود» (§30) ⇒ ما يتسألش «ده سداد؟» تاني، و«أيوه» ما تسددش مرتين. */
    @Test fun alreadyLinkedRepaymentIsNotSettledTwice() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-in", Direction.IN, 30_000)))
        val debt = w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 100_000)
        w.people.settle(debt.id, "p-1", 30_000, "t-in", "old-sheet-1") // زي شيت التطبيق القديم
        assertEquals(70_000L, w.balance().receivableMinor)
        assertNull(w.answers.askOf("t-in"), "المالك جاوب بنفسه")
        assertNull(w.answers.question("t-in"))
        assertEquals(emptyList(), w.asks(), "ولا في «محتاجة تأكيد»")
        assertFailsWith<TransferAskNotPending> { w.answers.answerDebtRepayment("t-in", yes = true) }
        assertEquals(70_000L to 1, w.balance().receivableMinor to w.settlements.all().size, "مفيش تسوية تانية")
    }

    /** نفس الحكم للعملية اللي اتعمل منها دين أو ليها نصيب شخص من شاشة الأشخاص (`linkToPerson` في التطبيقين). */
    @Test fun aTransferLinkedFromThePeopleScreenIsNotAsked() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-borrow", Direction.IN, 40_000), transfer("t-gift", Direction.OUT, 10_000), transfer("t-free", Direction.OUT, 5_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 100_000) // كان هيسأل «ده سداد؟» على الوارد
        w.people.linkToPerson("t-borrow", "p-1", ObligationKind.LOAN_PAYABLE, 40_000) // «استلمت قرض»
        w.people.linkToPerson("t-gift", "p-1", ObligationKind.RECEIVABLE, 10_000, asGift = true)
        assertEquals(listOf(AskKind.DEBT_REPAYMENT to "t-free"), w.asks(), "التحويل اللي مش مربوط بس — وبيتسأل «سداد؟» عشان القرض اللي استلمه")
        assertNull(w.answers.askOf("t-borrow"))
        assertNull(w.answers.askOf("t-gift"))
    }

    /** مراجعة S5: «سلفة» على تحويل ليه نصيب جزئي من قبل ⇒ مرفوضة ومفيش كتابة (مش دين 100 من 300 ولا «التخصيصات أكبر من العملية»). */
    @Test fun aPartlyLinkedTransferIsNotAskedAndTheLoanAnswerWritesNothing() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", Direction.OUT, 30_000), transfer("t-2", Direction.OUT, 30_000)))
        w.people.linkToPerson("t-1", "p-1", ObligationKind.RECEIVABLE, 10_000) // «دفعت عنه» جزء
        w.people.linkToPerson("t-2", "p-1", ObligationKind.RECEIVABLE, 10_000, asGift = true)
        for (id in listOf("t-1", "t-2")) {
            assertNull(w.answers.askOf(id), id)
            assertFailsWith<TransferAskNotPending>(id) { w.answers.answerLoanOrSupport(id, LoanOrSupport.LOAN) }
            assertEquals(EconomicKind.UNCLASSIFIED to false, w.kind(id))
        }
        assertEquals(10_000L to 1, w.balance().receivableMinor to w.obligations.all().size)
    }

    /** مراجعة S5: فك قرار «شخص» ما بيمسحش إجابات المالك — «دعم» و«سلفة» بيفضلوا زي بعض. */
    @Test fun forgetKeepsTheOwnersExplicitAnswers() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-gift", Direction.OUT, 20_000), transfer("t-loan", Direction.OUT, 50_000), transfer("t-open", Direction.OUT, 1_000)))
        w.answers.answerLoanOrSupport("t-gift", LoanOrSupport.SUPPORT)
        w.answers.answerLoanOrSupport("t-loan", LoanOrSupport.LOAN)
        assertEquals(0, w.zone.forget(partyRef.key), "مفيش نوع القرار حطه لوحده")
        assertEquals((EconomicKind.SUPPORT_GIFT to true) to (EconomicKind.LOAN_GRANTED to true), w.kind("t-gift") to w.kind("t-loan"))
        assertEquals(EconomicKind.UNCLASSIFIED to false, w.kind("t-open"))
        assertEquals(50_000L, w.balance().receivableMinor)
        assertTrue(w.parties.listAll().isEmpty(), "الطرف رجع يتسأل")
    }

    /** مراجعة S5: «ده حسابي التاني» بعد ما المالك جاوب ⇒ الدين والتسويات اللي الأسئلة كتبتها بتتشال (الفلوس راحت لحسابه). */
    @Test fun ownAccountRemovesWhatTheAnswersWrote() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", Direction.OUT, 50_000), transfer("t-2", Direction.IN, 20_000, "2026-10-06"), transfer("t-3", Direction.OUT, 10_000, "2026-10-07")))
        w.answers.answerLoanOrSupport("t-1", LoanOrSupport.LOAN)
        assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("t-2", yes = true)) // سداد 200 من السلفة
        val owed = w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 100_000)
        assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("t-3", yes = true)) // سداد 100 من دين عليه
        assertEquals(30_000L to 90_000L, w.balance().let { it.receivableMinor to it.payableLoanMinor })

        assertEquals(3, w.zone.markOwnAccount(partyRef))
        for (id in listOf("t-1", "t-2", "t-3")) assertEquals(EconomicKind.INTERNAL_TRANSFER to true, w.kind(id), id)
        assertEquals(0L to 100_000L, w.balance().let { it.receivableMinor to it.payableLoanMinor }, "مفيش حد مديون بفلوس راحت لحسابك، والدين القديم رجع زي ما كان")
        assertEquals(listOf(owed), w.obligations.all())
        assertTrue(w.settlements.all().isEmpty() && w.shares().isEmpty())
    }

    /** السلفة اللي عليها سداد اتسجل بإيد المالك ⇒ «حسابي التاني» مرفوض بسببه، ومفيش ولا كتابة. ربط المالك نفسه ما بيتلمسش. */
    @Test fun ownAccountRefusesWhenTheLoanHasAManualRepayment() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", Direction.OUT, 50_000), transfer("t-2", Direction.OUT, 20_000)))
        w.answers.answerLoanOrSupport("t-1", LoanOrSupport.LOAN)
        val loan = w.obligations.all().single()
        w.people.settle(loan.id, "p-1", 10_000, null, "cash-1") // سداد كاش من شاشة الأشخاص
        val before = w.txns.all()
        val refused = assertFailsWith<TransferZoneError> { w.zone.markOwnAccount(partyRef) }
        assertEquals(uiText(TextKey.ASK_LOAN_HAS_REPAYMENT), refused.message)
        assertEquals(before, w.txns.all())
        assertEquals(TransferVerdict.PERSON, w.parties.listAll().single().verdict)
        assertEquals(40_000L, w.balance().receivableMinor)

        // ربط من شاشة الأشخاص (مش من السؤال) ⇒ «حسابي التاني» ما بيمسحوش (زي ما كان قبل S5)
        val v = World(listOf(transfer("t-9", Direction.OUT, 50_000)))
        v.people.linkToPerson("t-9", "p-1", ObligationKind.RECEIVABLE, 50_000)
        assertEquals(1, v.zone.markOwnAccount(partyRef))
        assertEquals(50_000L, v.balance().receivableMinor)
    }

    /** الشيل اتقطع في النص ⇒ نفس القرار تاني بيكمّله (مش بس العمليات اللي اتغيرت). */
    @Test fun ownAccountCutHalfwayFinishesOnRepeat() = runBlocking<Unit> {
        var fail = 1
        val flaky = { real: MemoryObligationRepository ->
            object : ObligationRepository by real {
                override suspend fun deleteMany(ids: List<String>) {
                    if (fail-- > 0) throw IllegalStateException("انقطاع وهمي")
                    real.deleteMany(ids)
                }
            }
        }
        val w = World(listOf(transfer("t-1", Direction.OUT, 50_000)), wrapObligations = flaky, passthroughZone = true)
        w.answers.answerLoanOrSupport("t-1", LoanOrSupport.LOAN)
        assertFailsWith<IllegalStateException> { w.zone.markOwnAccount(partyRef) }
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, w.kind("t-1"), "النوع اتكتب قبل الشيل")
        assertEquals(50_000L, w.balance().receivableMinor, "الدين لسه موجود")
        assertEquals(0, w.zone.markOwnAccount(partyRef), "مفيش عملية تتغير — بس الشيل بيكمل")
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, w.kind("t-1"))
        assertTrue(w.obligations.all().isEmpty() && w.shares().isEmpty())
    }

    /** مراجعة S5: «لأ، مش سداد» **ما بتتخزنش** — الشاشة بتمررها في نفس الخطوة، والسؤال بيرجع لو قفل قبل ما يختار. */
    @Test fun aNoIsPassedByTheScreenNotStored() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", Direction.OUT, 10_000), transfer("t-in", Direction.IN, 5_000)))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 100_000)
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 100_000)
        assertEquals(DebtAnswer.ThenAskLoanOrSupport, w.answers.answerDebtRepayment("t-1", yes = false))
        assertEquals(AskKind.LOAN_OR_SUPPORT, w.answers.askOf("t-1", debtRuledOut = true))
        assertEquals(uiText(TextKey.ASK_LOAN_OR_SUPPORT), w.answers.question("t-1", debtRuledOut = true)!!.title)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-1"), "مش متخزنة: من غير ما الشاشة تمررها السؤال الأول بيرجع")
        assertNull(w.answers.askOf("t-in", debtRuledOut = true), "الوارد بعد «لأ» ⇒ اختيارات §39.1 مش سؤال هنا")
        assertEquals(2, w.asks().size, "العدد نفسه: سؤال واحد لكل عملية")
        assertEquals(EconomicKind.SUPPORT_GIFT, w.answers.answerLoanOrSupport("t-1", LoanOrSupport.SUPPORT).economicKind)
    }

    /** إجابة اتقطعت وبعدين المالك جاوب **غيرها** ⇒ اللي الأولى كتبته بيتشال (مفيش دين يتيم ولا تسوية على «دعم»). */
    @Test fun aDifferentAnswerAfterACutRemovesTheFirstAnswersWrites() = runBlocking<Unit> {
        var fail = 0
        val flaky = { real: MemoryTransactionRepository ->
            object : TransactionRepository by real {
                override suspend fun update(id: String, patch: TransactionPatch) {
                    if (fail-- > 0) throw IllegalStateException("انقطاع وهمي")
                    real.update(id, patch)
                }
            }
        }
        val w = World(listOf(transfer("t-1", Direction.OUT, 50_000), transfer("t-2", Direction.OUT, 30_000)), wrapTxns = flaky)
        fail = 1
        assertFailsWith<IllegalStateException> { w.answers.answerLoanOrSupport("t-1", LoanOrSupport.LOAN) }
        assertEquals(50_000L, w.balance().receivableMinor, "الدين اتكتب قبل النوع")
        assertEquals(AskKind.LOAN_OR_SUPPORT, w.answers.askOf("t-1"), "اللي السؤال كتبه ما بيقفلش السؤال")
        assertEquals(EconomicKind.SUPPORT_GIFT, w.answers.answerLoanOrSupport("t-1", LoanOrSupport.SUPPORT).economicKind)
        assertEquals(0L, w.balance().receivableMinor)
        assertTrue(w.obligations.all().isEmpty() && w.shares().isEmpty())

        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 100_000)
        fail = 1
        assertFailsWith<IllegalStateException> { w.answers.answerDebtRepayment("t-2", yes = true) }
        assertEquals(70_000L, w.balance().payableLoanMinor)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-2"))
        assertEquals(EconomicKind.LOAN_GRANTED, w.answers.answerLoanOrSupport("t-2", LoanOrSupport.LOAN).economicKind)
        assertEquals(100_000L to 30_000L, w.balance().let { it.payableLoanMinor to it.receivableMinor }, "التسوية المقطوعة اتشالت")
    }

    /** السلفة اللي اتكتبت من إجابة مقطوعة وعليها سداد من مكان تاني ⇒ تغيير الإجابة مرفوض (ما بنسيبش تسوية على دين ممسوح). */
    @Test fun aDifferentAnswerIsRefusedWhenTheCutLoanWasRepaid() = runBlocking<Unit> {
        var fail = 1
        val flaky = { real: MemoryTransactionRepository ->
            object : TransactionRepository by real {
                override suspend fun update(id: String, patch: TransactionPatch) {
                    if (fail-- > 0) throw IllegalStateException("انقطاع وهمي")
                    real.update(id, patch)
                }
            }
        }
        val w = World(listOf(transfer("t-1", Direction.OUT, 50_000)), wrapTxns = flaky)
        assertFailsWith<IllegalStateException> { w.answers.answerLoanOrSupport("t-1", LoanOrSupport.LOAN) }
        val loan: Obligation = w.obligations.all().single()
        w.people.settle(loan.id, "p-1", 10_000, null, "cash-1")
        val refused = assertFailsWith<TransferAskError> { w.answers.answerLoanOrSupport("t-1", LoanOrSupport.SUPPORT) }
        assertEquals(uiText(TextKey.ASK_LOAN_HAS_REPAYMENT), refused.message)
        assertEquals(EconomicKind.LOAN_GRANTED, w.answers.answerLoanOrSupport("t-1", LoanOrSupport.LOAN).economicKind, "نفس الإجابة بتكمل عادي")
        assertEquals(40_000L, w.balance().receivableMinor)
    }
}
