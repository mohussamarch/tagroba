package app.masroufy.usecase

import app.masroufy.core.AutoMeaning
import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonLinkRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مراجعة جولة r05 (F1 · F2 · F3 · F7 · F9 · F11 · F12 · F13) وردود المالك (A2). **كل الأسماء والأرقام مخترعة.** */
class PersonLinksFixesTest {
    private fun t(id: String, dir: Direction, date: String, amount: Long, desc: String? = null, kind: EconomicKind = EconomicKind.UNCLASSIFIED, transfer: Boolean = true) =
        Transaction(
            id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
            observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = if (kind != EconomicKind.UNCLASSIFIED) ReviewState.CONFIRMED else ReviewState.SUGGESTED, isCashTagged = false,
            createdAt = "2026-03-01T00:00:00.000Z", updatedAt = "x", rawDescription = desc, sourceOperationType = if (transfer) "عملية تحويل داخلية" else null,
            walletId = "w-bank",
        )

    private val fromHim = "تجريبيW-/FRACCT/99990000005678FR:ملاحظة"
    private val out = t("t-out", Direction.OUT, "2026-02-01", 50_000, "تجريبيW-/TOACCT/99990000005678TO:ملاحظة")
    private val inc = t("t-in", Direction.IN, "2026-03-05", 20_000, fromHim)
    private val inc2 = t("t-in2", Direction.IN, "2026-03-06", 5_000, fromHim)
    private val buy = t("t-buy", Direction.OUT, "2026-02-05", 12_000, kind = EconomicKind.PURCHASE, transfer = false)

    private val txns = MemoryTransactionRepository(listOf(out, inc, inc2, buy))
    private val links = MemoryPersonLinkRepository()
    private val obligations = MemoryObligationRepository()
    private val settlements = MemorySettlementRepository()
    private val allocations = MemoryAllocationRepository()
    private val parties = MemoryTransferPartyRepository()
    private val terms = MemoryDebtTermsRepository()
    private val people = MemoryPersonRepository(listOf(Person("p-1", "شخص تجريبي"), Person("p-2", "شخص تاني")))
    private val clock = FixedClock("2026-03-01T10:00:00.000Z")
    private val ids = SequentialIdGenerator()
    private val uow = MemoryUnitOfWork(listOf(txns, links, parties))
    private val deps = PersonLinksDeps(links, people, obligations, settlements, allocations, txns, MemoryCategoryRepository(emptyList()), parties, uow, ids, clock, terms = terms)
    private val manage = ManagePersonLinks(deps)
    private val deleteDebt = DeleteDebt(DeleteDebtDeps(obligations, settlements, allocations, uow, terms, links, txns, clock))

    private suspend fun kind(id: String) = txns.findByIds(listOf(id)).single().economicKind
    private suspend fun personParty(dues: Boolean = false, auto: AutoMeaning? = null) = transferPartyOf(inc)!!.let { ref ->
        TransferParty(ref.key, ref.label, ref.last4, TransferVerdict.PERSON, "p-1", "2026-03-01", autoMeaning = auto, autoSince = auto?.let { "2026-03-01" }, dues = dues)
            .also { parties.save(it) }
    }

    @Test fun f1DeletingTheDebtRemovesItsLinkAndUndoBringsBothBack() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        val debt = obligations.listByPerson("p-1").single()
        val removal = deleteDebt.delete("p-1", debt.id)
        assertTrue(links.all().isEmpty(), "مفيش ربط بيشاور على دين ممسوح (النسخة الشاملة كانت بترفض)")
        assertEquals(EconomicKind.UNCLASSIFIED, kind("t-out"), "النوع رجع زي ما كان قبل الربط")
        deleteDebt.restore(removal)
        assertEquals(debt.id, links.all().single().obligationId)
        assertEquals(EconomicKind.LOAN_GRANTED, kind("t-out"))
    }

    @Test fun f1DeletingADebtAnotherLinkPaidKeepsThatLinkWithoutThePayment() = runBlocking {
        obligations.saveMany(listOf(Obligation("o-manual", "p-1", null, ObligationKind.RECEIVABLE, 30_000, Currency.SAR)))
        manage.link("t-in", "p-1", PersonMeaning.COLLECTED)
        assertEquals(1, links.all().single().settlementIds.size)
        deleteDebt.delete("p-1", "o-manual")
        val kept = links.all().single()
        assertEquals(emptyList(), kept.settlementIds, "السداد اتشال مع الدين والربط فضل من غير معرّف مقطوع")
    }

    @Test fun f2UnlinkAlsoRemovesTheDebtTerms() = runBlocking {
        manage.link("t-in", "p-1", PersonMeaning.BORROWED)
        val debt = obligations.listByPerson("p-1").single()
        terms.save(DebtTerms(debt.id, "p-1", "2026-04-01"))
        manage.unlink("t-in")
        assertTrue(terms.listAll().isEmpty(), "ميعاد الدين اتشال مع الدين")
        assertTrue(obligations.listByPerson("p-1").isEmpty())
    }

    @Test fun f3AndA2AutoLinkAtRecordTimeAndNeverAgainAfterUnlink() = runBlocking {
        personParty(auto = AutoMeaning.GIFT)
        assertEquals(2, manage.rules.onRecorded(listOf("t-in", "t-in2")))
        assertTrue(links.all().all { it.auto && it.meaning == PersonMeaning.RECEIVED })
        manage.unlink("t-in")
        assertEquals(listOf("t-in"), parties.listAll().single().autoSkip)
        assertEquals(0, manage.rules.onRecorded(listOf("t-in")), "القاعدة ما رجعتش على عملية المالك فك ربطها")
        assertEquals(0, manage.accounts.applyAuto(listOf("t-in")), "ولا من تفاصيل العملية")
        manage.link("t-in", "p-1", PersonMeaning.SUPPORTED)
        assertEquals(PersonMeaning.SUPPORTED, links.listByTransactionIds(listOf("t-in")).single().meaning, "المالك اختار معنى تاني")
    }

    @Test fun a2DuesRuleSettlesAtRecordTimeAsAnUndoableLink() = runBlocking {
        manage.link("t-out", "p-1", PersonMeaning.LENT)
        personParty(dues = true)
        assertEquals(2, manage.rules.onRecorded(listOf("t-in", "t-in2")))
        assertEquals(EconomicKind.DEBT_COLLECTED, kind("t-in"))
        assertEquals(PersonMeaning.COLLECTED, links.listByTransactionIds(listOf("t-in")).single().meaning)
        manage.unlink("t-in")
        assertEquals(0, manage.rules.onRecorded(listOf("t-in")))
        assertTrue(!manage.rules.dues(txns.findByIds(listOf("t-in")).single()), "تفاصيل العملية وزون التحويلات كمان")
    }

    @Test fun f7APurchaseLinksThroughTheSamePathWithAShare() = runBlocking {
        val out = manage.link("t-buy", "p-1", PersonMeaning.LENT, amountMinor = 4_000)
        assertEquals(4_000L, obligations.listByPerson("p-1").single().originalMinor)
        assertEquals(4_000L, allocations.listByTransactionIds(listOf("t-buy")).single().amountMinor)
        assertEquals(EconomicKind.PURCHASE, kind("t-buy"), "الشراء بيفضل شراء")
        assertEquals("t-buy", manage.page.load("p-1")!!.ledger.entries.single().transactionId, "بيبان في «السجل بينا»")
        manage.unlink("t-buy")
        assertFailsWith<PersonLinkError> { manage.link("t-buy", "p-1", PersonMeaning.REPAID) }
        assertFailsWith<PersonLinkError> { manage.link("t-buy", "p-1", PersonMeaning.GAVE, amountMinor = 13_000) }
        assertNull(out.link.kindBefore)
    }

    @Test fun f9NoMeaningOnAnOperationThatAlreadyHasADebt() = runBlocking {
        obligations.saveMany(listOf(Obligation("o-legacy", "p-1", "t-out", ObligationKind.RECEIVABLE, 50_000, Currency.SAR)))
        for (m in listOf(PersonMeaning.WITH, PersonMeaning.GAVE)) assertFailsWith<PersonLinkError>(m.name) { manage.link("t-out", "p-1", m) }
        assertTrue(links.all().isEmpty())
    }

    @Test fun f11TwoLinksAtTheSameMomentWriteOneDebt() = runBlocking {
        val tries = (1..6).map { async(Dispatchers.Default) { runCatching { manage.link("t-in", "p-1", PersonMeaning.BORROWED) }.isSuccess } }.awaitAll()
        assertEquals(1, tries.count { it })
        assertEquals(1, obligations.listByPerson("p-1").size, "دين واحد بس — مش «عليك له» بالضعف")
    }

    @Test fun f12RepaidSettlesTheLoanNotAnOlderCustody() = runBlocking {
        obligations.saveMany(
            listOf(
                Obligation("o-custody", "p-1", null, ObligationKind.CUSTODY_PAYABLE, 100_000, Currency.SAR),
                Obligation("o-loan", "p-1", null, ObligationKind.LOAN_PAYABLE, 50_000, Currency.SAR),
            ),
        )
        manage.link("t-out", "p-1", PersonMeaning.REPAID)
        assertEquals(listOf("o-loan"), settlements.listByTransactionIds(listOf("t-out")).map { it.obligationId })
    }

    @Test fun f13AManualAccountNeverOverwritesAnotherDecision() = runBlocking {
        parties.save(TransferParty("#1234", "حسابي التاني", "1234", TransferVerdict.OWN_ACCOUNT))
        assertFailsWith<PersonLinkError> { manage.accounts.addManual("p-1", "1234", null) }
        assertEquals(TransferVerdict.OWN_ACCOUNT, parties.listAll().single().verdict)
        parties.save(TransferParty("#5678", "••••5678", "5678", TransferVerdict.PERSON, "p-1", autoMeaning = AutoMeaning.LOAN, autoSince = "2026-03-01"))
        manage.accounts.addManual("p-1", "٥٦٧٨", "بنك تجريبي")
        assertEquals(AutoMeaning.LOAN, parties.listAll().first { it.key == "#5678" }.autoMeaning, "قاعدة نفس الشخص ما اتمسحتش")
    }
}
