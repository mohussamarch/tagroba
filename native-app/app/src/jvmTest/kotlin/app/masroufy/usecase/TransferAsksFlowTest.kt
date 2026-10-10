package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.SchemaId
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.checkSettlement
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.computePersonBalance
import app.masroufy.core.transferPartyOf
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * «سلفة ولا دعم؟» و«ده سداد؟» من أول التسجيل لحد الإجابة (قرارات المالك §75-5 و§75-9) — كل الأسامي والأرقام مخترعة.
 * الطرف «TESTPERSON» آخر 4 أرقامه 4445، والشخص «شخص وهمي».
 */
class TransferAsksFlowTest {
    private val op = "عملية تحويل داخلية"
    private fun desc(dir: Direction) = if (dir == Direction.IN) "W-/FRACCT/11112222333344445FRTESTPERSON:ملاحظة" else "W-/TOACCT/11112222333344445TOTESTPERSON:ملاحظة"

    private fun transfer(id: String, dir: Direction, amount: Long, date: String = "2026-10-05", kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false) =
        Transaction(
            id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = confirmed,
            observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = desc(dir), sourceOperationType = op,
        )

    private val partyRef = transferPartyOf(transfer("probe", Direction.OUT, 1))!!
    private val asPerson = TransferParty(partyRef.key, partyRef.label, partyRef.last4, TransferVerdict.PERSON, "p-1", "2026-10-01T00:00:00.000Z")

    private inner class World(
        seed: List<Transaction> = emptyList(),
        parties: List<TransferParty> = listOf(asPerson),
        wrap: (MemoryTransactionRepository) -> TransactionRepository = { it },
    ) {
        val txns = MemoryTransactionRepository(seed)
        val repo = wrap(txns)
        val parties = MemoryTransferPartyRepository(parties)
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val allocations = MemoryAllocationRepository()
        val persons = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
        val clock = FixedClock("2026-10-08T10:00:00.000Z")
        val ids = SequentialIdGenerator()
        val uow = MemoryUnitOfWork(listOf(txns, this.parties))
        val people = ManagePeople(ManagePeopleDeps(persons, obligations, settlements, MemorySettlementWriter(obligations, settlements), allocations, repo, uow, ids, clock))
        val answers = AnswerTransferAsks(AnswerTransferAsksDeps(repo, this.parties, obligations, settlements, allocations, persons, people, uow, clock))
        val source = TransferAskSource(TransferAskSourceDeps("sa", txns, this.parties, obligations, settlements, allocations))
        val zone = ManageTransfers(ManageTransfersDeps(txns, this.parties, persons, uow, clock))

        suspend fun asks(from: String = "2026-01-01", to: String = "2026-12-31") = source.pending(from, to).map { it.kind to it.transactionId }
        suspend fun one(id: String) = txns.findByIds(listOf(id)).single()
        suspend fun balance() = computePersonBalance("p-1", obligations.all(), settlements.all())

        fun importer() = ImportStatement(
            ImportStatementDeps(
                txns, MemorySourceRecordRepository(), MemoryImportBatchRepository(), MemoryMerchantRepository(), MemoryCategoryRepository(),
                MemoryRuleRepository(), uow, ids, clock, transferParties = this.parties,
            ),
        )
    }

    private suspend fun World.importCsv(vararg rows: String): List<Transaction> {
        val csv = "التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n" + rows.joinToString("\n") + "\n"
        val request = ImportRequest("t.csv", csv, "acc-test", ImportSourceType.CSV_LEGACY, "w-1", SchemaId.LEGACY)
        val importer = importer()
        val before = txns.all().map { it.id }.toSet()
        importer.commit(request, importer.preview(request))
        return txns.all().filter { it.id !in before }
    }

    @Test fun anOutgoingStatementTransferToAPersonWaitsAndAsksEveryTime() = runBlocking<Unit> {
        val w = World()
        val first = w.importCsv("2026/10/02,500.00,0.00,900.00,,,$op,${desc(Direction.OUT)}").single()
        assertEquals(
            Triple(EconomicKind.UNCLASSIFIED, false, ReviewState.NEEDS_REVIEW), Triple(first.economicKind, first.economicKindConfirmed, first.reviewState),
            "مش «دعم» لوحده (§75-5)",
        )
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to first.id), w.asks())
        val second = w.importCsv("2026/10/03,200.00,0.00,700.00,,,$op,${desc(Direction.OUT)}").single()
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to first.id, AskKind.LOAN_OR_SUPPORT to second.id), w.asks(), "سؤال تاني للتحويل التاني")
        assertEquals(listOf(AskKind.LOAN_OR_SUPPORT to second.id), w.asks("2026-10-03", "2026-10-31"), "القراية بالأيام بس")
    }

    @Test fun anOutgoingSmsTransferToAPersonWaitsAndAsks() = runBlocking<Unit> {
        val space = SmsSpace()
        val world = SmsWorld(listOf(space)).enable()
        val screen = ReviewSmsInbox(ReviewSmsInboxDeps(ManageSmsInbox(world.inbox, space.parse), ImportStatement(space.importDeps()), MemoryMerchantRepository(), space.categories, space.ids))
        world.receive(sms("m1", TO_PERSON))
        screen.load(SmsReviewTarget(BANK.id, BANK.name))
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()))
        val party = transferPartyOf(space.all().single())!!
        space.parties.save(TransferParty(party.key, party.label, party.last4, TransferVerdict.PERSON, "p-1", "2026-10-07T12:00:00.000Z"))
        world.receive(sms("m2", TO_PERSON.replace("SR 500", "SR 300").replace("09:35", "10:35")))
        screen.load(SmsReviewTarget(BANK.id, BANK.name))
        assertEquals(1, screen.recordAll(emptyMap(), emptyList()))
        val sms = space.all().single { it.amountMinor == 30_000L }
        assertEquals(Triple(EconomicKind.UNCLASSIFIED, false, ReviewState.NEEDS_REVIEW), Triple(sms.economicKind, sms.economicKindConfirmed, sms.reviewState))
        val asks = TransferAskSource(TransferAskSourceDeps("sa", space.txnStore, space.parties, MemoryObligationRepository(), MemorySettlementRepository(), MemoryAllocationRepository()))
        assertEquals(2, asks.pending("2026-10-01", "2026-10-31").count { it.kind == AskKind.LOAN_OR_SUPPORT }, "الاتنين بيتسألوا — القديم كمان")
    }

    @Test fun loanBecomesAReceivableAndSupportAnExpense() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-loan", Direction.OUT, 50_000), transfer("t-gift", Direction.OUT, 20_000)))
        val q = w.answers.question("t-loan")!!
        assertEquals(AskKind.LOAN_OR_SUPPORT to uiText(TextKey.ASK_LOAN_OR_SUPPORT), q.kind to q.title)
        assertTrue("شخص وهمي" in q.body, q.body)

        val loan = w.answers.answerLoanOrSupport("t-loan", LoanOrSupport.LOAN)
        assertEquals(Triple(EconomicKind.LOAN_GRANTED, true, ReviewState.CONFIRMED), Triple(loan.economicKind, loan.economicKindConfirmed, loan.reviewState))
        assertEquals(loan, w.one("t-loan"))
        assertEquals(50_000L, w.balance().receivableMinor, "لك عنده زاد بالمبلغ")
        assertEquals("t-loan", w.obligations.all().single().originTransactionId)

        val gift = w.answers.answerLoanOrSupport("t-gift", LoanOrSupport.SUPPORT)
        assertEquals(EconomicKind.SUPPORT_GIFT to true, gift.economicKind to gift.economicKindConfirmed)
        assertEquals(50_000L, w.balance().receivableMinor, "الدعم ما بيعملش دين")
        assertEquals(20_000L, computePeriodTotals(w.txns.all(), emptyList()).personalExpenseMinor, "الدعم مصروف والسلفة لأ")
        assertEquals(emptyList(), w.asks())

        assertFailsWith<TransferAskNotPending>("اتجاوب خلاص") { w.answers.answerLoanOrSupport("t-loan", LoanOrSupport.SUPPORT) }
        assertEquals(1, w.obligations.all().size)
    }

    @Test fun existingDataStaysAndThePersonDecisionAsksAboutThePast() = runBlocking<Unit> {
        val old = transfer("t-old", Direction.OUT, 10_000, "2026-09-01", EconomicKind.SUPPORT_GIFT, confirmed = true)
        val past = (1..3).map { transfer("t-p$it", Direction.OUT, 10_000L * it, "2026-09-0${it + 1}").copy(reviewState = ReviewState.SUGGESTED) }
        val w = World(listOf(old) + past, parties = emptyList())
        assertEquals(3, w.zone.markPerson(partyRef, "p-1"))
        assertEquals(old, w.one("t-old"), "«دعم» اتأكد قبل §75-5 ما بيتلمسش")
        assertEquals(past.map { AskKind.LOAN_OR_SUPPORT to it.id }, w.asks(), "3 تحويلات قديمة = 3 أسئلة")

        val result = w.answers.answerLoanOrSupportAll(past.map { it.id } + "t-old", LoanOrSupport.SUPPORT)
        assertEquals(past.map { it.id } to listOf("t-old"), result.answered to result.skipped)
        assertTrue(result.refused.isEmpty())
        assertEquals(emptyList(), w.asks())
        assertTrue(past.all { w.one(it.id).let { t -> t.economicKind == EconomicKind.SUPPORT_GIFT && t.economicKindConfirmed } })
    }

    @Test fun ownAccountStillMakesAnInternalTransfer() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-1", Direction.OUT, 10_000), transfer("t-2", Direction.IN, 5_000)), parties = emptyList())
        assertEquals(2, w.zone.markOwnAccount(partyRef))
        for (id in listOf("t-1", "t-2")) assertEquals(EconomicKind.INTERNAL_TRANSFER to true, w.one(id).let { it.economicKind to it.economicKindConfirmed })
        assertEquals(emptyList(), w.asks())
    }

    @Test fun incomingFromAPersonWhoOwesAsksRepayment() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-in", Direction.IN, 30_000), transfer("t-big", Direction.IN, 60_000, "2026-10-06")))
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 50_000)
        assertEquals(listOf(AskKind.DEBT_REPAYMENT to "t-in", AskKind.DEBT_REPAYMENT to "t-big"), w.asks(), "مش «تحصيل دين» لوحده (§75-9)")
        val q = w.answers.question("t-in")!!
        assertEquals(uiText(TextKey.ASK_DEBT_COLLECTED) to 50_000L, q.title to q.openDebtMinor)

        // أكبر من الدين ⇒ نفس رسالة الزيادة، ومفيش ولا كتابة
        val before = w.txns.all()
        val refused = assertFailsWith<TransferAskError> { w.answers.answerDebtRepayment("t-big", yes = true) }
        assertEquals(checkSettlement(w.obligations.all().single(), emptyList(), 60_000).reason, refused.message)
        assertEquals(before, w.txns.all())
        assertTrue(w.settlements.all().isEmpty())

        val done = assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("t-in", yes = true))
        assertEquals(EconomicKind.DEBT_COLLECTED to true, done.transaction.economicKind to done.transaction.economicKindConfirmed)
        assertEquals(listOf(30_000L), done.settlements.map { it.amountMinor })
        assertEquals("t-in", done.settlements.single().transactionId)
        assertEquals(20_000L, w.balance().receivableMinor, "فاضل 200")

        // «لأ» على الوارد ⇒ اختيارات §39.1 من غير «تحصيل دين»، ومفيش كتابة
        val no = assertIs<DebtAnswer.ThenChooseIncomingKind>(w.answers.answerDebtRepayment("t-big", yes = false))
        assertTrue(EconomicKind.DEBT_COLLECTED !in no.choices && EconomicKind.GIFT_RECEIVED in no.choices)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-big"), "«لأ» ما بتتخزنش ولسه ما اختارش نوعه ⇒ لسه بيتسأل (الشاشة بتمرر «لأ» في نفس الخطوة)")
    }

    @Test fun outgoingToAPersonHeOwesAsksRepayment() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-pay", Direction.OUT, 40_000), transfer("t-more", Direction.OUT, 70_000, "2026-10-06")))
        w.people.addOpeningDebt("p-1", ObligationKind.LOAN_PAYABLE, 100_000)
        assertEquals(AskKind.DEBT_REPAYMENT, w.answers.askOf("t-pay"))
        assertEquals(uiText(TextKey.ASK_DEBT_REPAID), w.answers.question("t-pay")!!.title)
        val done = assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("t-pay", yes = true))
        assertEquals(EconomicKind.DEBT_REPAID, done.transaction.economicKind)
        assertEquals(60_000L, w.balance().payableLoanMinor)
        // «لأ» على الصادر ⇒ «سلفة ولا دعم؟» على طول
        assertEquals(DebtAnswer.ThenAskLoanOrSupport, w.answers.answerDebtRepayment("t-more", yes = false))
        assertEquals(EconomicKind.SUPPORT_GIFT, w.answers.answerLoanOrSupport("t-more", LoanOrSupport.SUPPORT).economicKind)
        assertFailsWith<TransferAskNotPending>("الوارد مالوش «سلفة ولا دعم؟»") {
            World(listOf(transfer("t-in", Direction.IN, 1_000))).answers.answerLoanOrSupport("t-in", LoanOrSupport.LOAN)
        }
    }

    @Test fun bulkRepaymentSettlesTheOldestFirstAndRefusesTheSurplusAlone() = runBlocking<Unit> {
        val w = World(listOf(transfer("t-2", Direction.IN, 30_000, "2026-10-06"), transfer("t-1", Direction.IN, 30_000, "2026-10-04")))
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 50_000)
        val result = w.answers.confirmRepaymentAll(listOf("t-2", "t-1"))
        assertEquals(listOf("t-1"), result.answered, "الأقدم الأول")
        assertEquals(setOf("t-2"), result.refused.keys, "الباقي 200 والمبلغ 300 ⇒ مرفوض لوحده")
        assertEquals(20_000L, w.balance().receivableMinor)
    }

    /** الدين والتسوية بيتكتبوا قبل نوع العملية — لو النوع وقع، الإعادة ما بتعملش دين تاني ولا تسوية تانية. */
    @Test fun anAnswerCutHalfwayCanBeRepeatedWithoutDoubling() = runBlocking<Unit> {
        var fail = 1
        val flaky = { real: MemoryTransactionRepository ->
            object : TransactionRepository by real {
                override suspend fun update(id: String, patch: TransactionPatch) {
                    if (fail-- > 0) throw IllegalStateException("انقطاع وهمي")
                    real.update(id, patch)
                }
            }
        }
        val w = World(listOf(transfer("t-loan", Direction.OUT, 50_000), transfer("t-in", Direction.IN, 10_000, "2026-10-09")), wrap = flaky)
        assertFailsWith<IllegalStateException> { w.answers.answerLoanOrSupport("t-loan", LoanOrSupport.LOAN) }
        assertEquals(EconomicKind.LOAN_GRANTED, w.answers.answerLoanOrSupport("t-loan", LoanOrSupport.LOAN).economicKind)
        assertEquals(1, w.obligations.all().size, "دين واحد بس")
        fail = 1
        assertFailsWith<IllegalStateException> { w.answers.answerDebtRepayment("t-in", yes = true) }
        assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("t-in", yes = true))
        assertEquals(listOf(10_000L), w.settlements.all().map { it.amountMinor }, "تسوية واحدة بنفس المعرّف")
        assertEquals(40_000L, w.balance().receivableMinor)
    }

    /** معرّف عملية فيه حروف مش مسموحة في معرّف الطلب ⇒ بصمة ثابتة منه: عمليتين مختلفتين عمرهم ما ياخدوا نفس التسوية. */
    @Test fun unusualTransactionIdsNeverShareASettlement() = runBlocking<Unit> {
        val w = World(listOf(transfer("ت-١", Direction.IN, 10_000), transfer("ت-٢", Direction.IN, 15_000, "2026-10-06")))
        w.people.addOpeningDebt("p-1", ObligationKind.RECEIVABLE, 50_000)
        assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("ت-١", yes = true))
        assertIs<DebtAnswer.Recorded>(w.answers.answerDebtRepayment("ت-٢", yes = true))
        assertEquals(setOf(10_000L, 15_000L), w.settlements.all().map { it.amountMinor }.toSet())
        assertEquals(25_000L, w.balance().receivableMinor)
    }
}
