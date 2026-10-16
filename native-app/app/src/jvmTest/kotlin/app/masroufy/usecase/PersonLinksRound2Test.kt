package app.masroufy.usecase

import app.masroufy.core.AutoMeaning
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatSubject
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProjectLinkRepository
import app.masroufy.memory.MemoryProjectRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryZakatFactRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionPatch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مراجعة r05 الجولة التانية (R2 · R3 · R4 · R5 · R6 · N2 · N3). **كل الأسماء والأرقام مخترعة.** */
class PersonLinksRound2Test {
    private fun t(id: String, dir: Direction, date: String, amount: Long, desc: String? = null, kind: EconomicKind = EconomicKind.UNCLASSIFIED) =
        Transaction(
            id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
            observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = if (kind != EconomicKind.UNCLASSIFIED) ReviewState.CONFIRMED else ReviewState.SUGGESTED, isCashTagged = false,
            createdAt = "2026-03-01T00:00:00.000Z", updatedAt = "x", rawDescription = desc, sourceOperationType = "عملية تحويل داخلية", walletId = "w-bank",
        )

    private val fromHim = "تجريبيW-/FRACCT/99990000005678FR:ملاحظة"
    private val toHim = "تجريبيW-/TOACCT/99990000005678TO:ملاحظة"
    private val out = t("t-out", Direction.OUT, "2026-02-01", 50_000, toHim)
    private val out2 = t("t-out2", Direction.OUT, "2026-03-10", 7_000, toHim)
    private val inc = t("t-in", Direction.IN, "2026-03-05", 20_000, fromHim)
    private val inc2 = t("t-in2", Direction.IN, "2026-03-06", 5_000, fromHim)
    private val cash = t("t-cash", Direction.OUT, "2026-03-02", 200_000, kind = EconomicKind.INTERNAL_TRANSFER).copy(transferToWalletId = "w-cash")
    private val own = t("t-own", Direction.OUT, "2026-03-03", 200_000, toHim)
    private val ownLeg = t("t-own$OWN_LEG_SUFFIX", Direction.IN, "2026-03-03", 200_000, kind = EconomicKind.INTERNAL_TRANSFER).copy(walletId = "w-two")
    private val bounced = t("t-bounce", Direction.OUT, "2026-03-11", 9_000, toHim, EconomicKind.INTERNAL_TRANSFER).copy(reversedById = "t-back")

    private val txns = MemoryTransactionRepository(listOf(out, out2, inc, inc2, cash, own, ownLeg, bounced))
    private val links = MemoryPersonLinkRepository()
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val allocations = MemoryAllocationRepository()
    private val parties = MemoryTransferPartyRepository()
    private val terms = MemoryDebtTermsRepository()
    private val zakat = MemoryZakatFactRepository()
    private val categories = MemoryCategoryRepository(emptyList())
    private val people = MemoryPersonRepository(listOf(Person("p-1", "شخص تجريبي")))
    private val clock = FixedClock("2026-03-01T10:00:00.000Z")
    private val ids = SequentialIdGenerator()
    private val uow = MemoryUnitOfWork(listOf(txns, links, parties))
    private val manage = ManagePersonLinks(
        PersonLinksDeps(links, people, obligations, settlements, allocations, txns, categories, parties, uow, ids, clock, terms = terms, zakatFacts = zakat),
    )
    private val deleteDebt = DeleteDebt(DeleteDebtDeps(obligations, settlements, allocations, uow, terms, links, txns, clock, zakatFacts = zakat))

    private suspend fun txn(id: String) = txns.findByIds(listOf(id)).single()
    private suspend fun personParty(dues: Boolean = false, auto: AutoMeaning? = null) = transferPartyOf(inc)!!.let { ref ->
        TransferParty(ref.key, ref.label, ref.last4, TransferVerdict.PERSON, "p-1", "2026-03-01", autoMeaning = auto, autoSince = auto?.let { "2026-03-01" }, dues = dues)
            .also { parties.save(it) }
    }

    @Test fun r5AnUnlinkOnAnAccountWithoutARuleStillStopsALaterDuesRule() = runBlocking {
        obligations.saveMany(listOf(Obligation("o-old", "p-1", null, ObligationKind.RECEIVABLE, 30_000, Currency.SAR)))
        manage.link("t-in", "p-1", PersonMeaning.COLLECTED)
        manage.unlink("t-in")
        assertTrue(parties.listAll().isEmpty(), "الحساب لسه ما اتقررش — مفيش مكان autoSkip")
        assertEquals(true, txn("t-in").personRulesOff, "العلامة على العملية نفسها")
        personParty(dues = true) // بعد أسبوع: «بيحوّلي مستحقات دايمًا» (قرار جديد من غير autoSkip)
        assertEquals(false, manage.rules.dues(txn("t-in")), "القاعدة ما رجعتش على عملية المالك فك ربطها")
        assertEquals(0, manage.rules.onRecorded(listOf("t-in")))
        assertTrue(links.all().isEmpty())
        assertEquals(true, manage.rules.dues(txn("t-in2")), "الشاهد: نفس القاعدة شغالة على غيرها")
    }

    @Test fun n2ADebtTheOwnerDeletedIsNeverRecreatedByTheAutoRule() = runBlocking {
        personParty(auto = AutoMeaning.LOAN)
        assertEquals(1, manage.rules.onRecorded(listOf("t-out2")), "اتربطت لحظة التسجيل")
        val debt = obligations.listByPerson("p-1").single()
        val removal = deleteDebt.delete("p-1", debt.id)
        assertEquals(0, manage.accounts.applyAuto(listOf("t-out2")), "فتح التفاصيل ما بيرجّعش الدين")
        manage.accounts.applyAuto() // ولا تشغيل «سجّل تحويلاته لوحده» من صفحة الشخص
        assertTrue(links.listByTransactionIds(listOf("t-out2")).isEmpty() && obligations.listByTransactionIds(listOf("t-out2")).isEmpty())
        deleteDebt.restore(removal)
        assertEquals(debt.id, links.listByTransactionIds(listOf("t-out2")).single().obligationId, "«تراجع» بيرجّع الدين وربطه")
        assertNull(txn("t-out2").personRulesOff, "والعلامة بترجع زي ما كانت")
    }

    @Test fun r4AKindChangeOnALinkedOperationIsRefusedAndR5UnlinkKeepsANewerKind() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        val kinds = SetEconomicKind(SetEconomicKindDeps(txns, categories, uow, clock, personLinks = links))
        assertFailsWith<IllegalStateException> { kinds.setOne("t-out", EconomicKind.SUPPORT_GIFT) }
        val bulk = BulkTransactions(
            BulkTransactionsDeps(
                txns, categories, MemoryProjectRepository(), MemoryProjectLinkRepository(), MemoryTagRepository(), MemoryTransactionTagRepository(),
                MemorySourceRecordRepository(), uow, ids, clock, personLinks = links,
            ),
        )
        assertEquals(0, bulk.setKind(listOf("t-out"), EconomicKind.SUPPORT_GIFT).changed, "«غيّر النوع» بيسيب المربوطة")
        assertEquals(EconomicKind.LOAN_GRANTED, txn("t-out").economicKind)
        // نوع اتغير بعد الربط من طريق قديم ⇒ «فك الربط» ما بيكتبش فوقه
        txns.update("t-out", TransactionPatch(economicKind = EconomicKind.SUPPORT_GIFT, economicKindConfirmed = true))
        manage.unlink("t-out")
        assertEquals(EconomicKind.SUPPORT_GIFT, txn("t-out").economicKind)
        assertTrue(obligations.listByPerson("p-1").isEmpty())
    }

    @Test fun r6AKindTheOwnerConfirmedIsNotOverwrittenWhenTheDetailsOpen() = runBlocking {
        personParty(auto = AutoMeaning.GIFT)
        txns.update("t-in2", TransactionPatch(economicKind = EconomicKind.SUPPORT_RECEIVED, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED))
        assertEquals(0, manage.accounts.applyAuto(listOf("t-in2")), "اتأكد من «راجع العمليات» ⇒ القاعدة ما تكتبش فوقه")
        assertEquals(EconomicKind.SUPPORT_RECEIVED, txn("t-in2").economicKind)
        assertEquals(1, manage.accounts.applyAuto(listOf("t-in")), "الشاهد: اللي ما اتأكدش بيتربط")
    }

    @Test fun n3AndR3OwnMoneyMovesAndCancelledPairsAreNeverLinked() = runBlocking {
        for (id in listOf("t-cash", "t-own", "t-bounce")) {
            assertFailsWith<PersonLinkError>(id) { manage.link(id, "p-1", PersonMeaning.LENT) }
        }
        personParty(auto = AutoMeaning.LOAN)
        assertEquals(0, manage.rules.onRecorded(listOf("t-bounce")), "نص زوج «رجعت» ما بيبقاش سلفة لحظة التسجيل")
        assertTrue(obligations.listByPerson("p-1").isEmpty())
        val found = manage.page.search("2000").map { it.id } + manage.page.suggestions("p-1").map { it.transaction.id }
        assertTrue(found.none { it == "t-cash" || it == "t-own" || it.endsWith(OWN_LEG_SUFFIX) }, "ما بتتقترحش ولا بتظهر في البحث: $found")
    }

    @Test fun r3UnlinkAndDeleteDebtTakeTheZakatFactWithThem() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        zakat.save(ZakatFact(obligations.listByPerson("p-1").single().id, ZakatSubject.OBLIGATION, updatedAt = "x"))
        manage.unlink("t-out")
        assertTrue(zakat.listAll().isEmpty(), "فك الربط")
        manage.link("t-in", "p-1", PersonMeaning.BORROWED)
        val debt = obligations.listByPerson("p-1").single()
        val fact = ZakatFact(debt.id, ZakatSubject.OBLIGATION, updatedAt = "x").also { zakat.save(it) }
        val removal = deleteDebt.delete("p-1", debt.id)
        assertTrue(zakat.listAll().isEmpty(), "مسح الدين")
        deleteDebt.restore(removal)
        assertEquals(listOf(fact), zakat.listAll(), "«تراجع» بيرجّعها")
    }

    @Test fun r2MergeNeverDeletesAPersonLinkedRow() = runBlocking {
        val w = LedgerWorld()
        w.add(w.txn("sms", "2026-08-01", 4_250), ImportSourceType.SMS)
        w.add(w.txn("stmt", "2026-08-01", 4_250), ImportSourceType.CSV_LEGACY)
        val personLinks = MemoryPersonLinkRepository()
        personLinks.saveMany(listOf(PersonLink("sms", "p-1", PersonMeaning.RECEIVED, 4_250, "x")))
        mergerOf(w.txns, w.sources, w.batches, w.backup, personLinks = personLinks).merge("sms", "stmt")
        assertTrue(w.txns.findByIds(listOf("sms")).isNotEmpty(), "الرابط ما بيفضلش بيشاور على عملية اتشالت")
    }

    @Test fun r2UndoingAnImportKeepsAPersonLinkedRow() = runBlocking {
        val w = MatchingWorld()
        w.import(w.statement("2026-10-01,12.00,0.00,4988.00,TEST CAFE RIYADH,,,شراء نقاط بيع"))
        val row = w.all().single()
        val personLinks = MemoryPersonLinkRepository()
        personLinks.saveMany(listOf(PersonLink(row.id, "p-1", PersonMeaning.WITH, row.amountMinor, "x")))
        val plan = w.revert(personLinks = personLinks).execute(w.sources.listByTransactionIds(listOf(row.id)).single().batchId)
        assertEquals(RevertDecision.KEPT_HAS_ALLOCATION, plan.outcomes.single().decision)
        assertTrue(w.txns.findByIds(listOf(row.id)).isNotEmpty())
    }
}
