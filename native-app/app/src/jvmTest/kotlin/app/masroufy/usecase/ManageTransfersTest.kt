package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Person
import app.masroufy.core.ReviewState
import app.masroufy.core.SchemaId
import app.masroufy.core.Transaction
import app.masroufy.core.TransferVerdict
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «زون التحويلات» (OVERRIDES §60) على مستودعات الذاكرة — كل الأسماء والأرقام مخترعة. */
class ManageTransfersTest {
    private var seq = 0
    private val internalOp = "عملية تحويل داخلية"
    private val ali = "ﻲﻠﻋ" // «علي» بحروف العرض المقلوبة زي كشف الراجحي

    private fun t(dir: Direction, date: String, amount: Long = 10_000, kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false, desc: String? = null, currency: Currency = Currency.SAR) =
        Transaction(
            id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
            observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = if (confirmed) ReviewState.CONFIRMED else ReviewState.SUGGESTED, isCashTagged = false, createdAt = "x", updatedAt = "x",
            rawDescription = desc ?: "${ali}W-/${if (dir == Direction.IN) "FR" else "TO"}ACCT/12345678901234567${if (dir == Direction.IN) "FR" else "TO"}:ملاحظة",
            sourceOperationType = internalOp,
        )

    private val aliTxns = listOf(
        t(Direction.OUT, "2026-02-01"), t(Direction.IN, "2026-02-02", 5_000), t(Direction.OUT, "2026-02-03"),
        t(Direction.OUT, "2026-02-04", kind = EconomicKind.LOAN_GRANTED, confirmed = true), t(Direction.IN, "2026-02-05", 2_500),
    )
    private val others = listOf(
        t(Direction.OUT, "2026-02-06", desc = "W-/TOACCT/99998888777766665TOSAMI:ملاحظة"),
        t(Direction.OUT, "2026-02-07", currency = Currency.EGP, desc = "IPN TRANSFER-VC••••1234-OWNER- IPN???? _????_??1a2b3c4dACC"),
        t(Direction.OUT, "2026-02-08", currency = Currency.EGP, desc = "IPN TRANSFER-VC••••1234-OWNER- IPNTEST_PERSONa1b2c3d4e5f6ACC"),
    ).map { if (it.rawDescription!!.startsWith("IPN")) it.copy(sourceOperationType = "IPN TRANSFER") else it }

    private val txns = MemoryTransactionRepository(aliTxns + others)
    private val parties = MemoryTransferPartyRepository()
    private val people = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي")))
    private val clock = FixedClock("2026-03-01T10:00:00.000Z")

    private fun manage(tx: TransactionRepository = txns) = ManageTransfers(ManageTransfersDeps(tx, parties, people, MemoryUnitOfWork(listOf(txns, parties)), clock))

    private suspend fun byId(id: String) = txns.findByIds(listOf(id)).single()

    @Test fun zoneGroupsByPartyAndCurrencyAndAsksAboutTheBusyOne() = runBlocking<Unit> {
        val zone = manage().zone()
        val first = zone.rows.first()
        assertEquals("علي" to 5, first.party.label to first.count)
        assertEquals(7_500L to 30_000L, first.incomingMinor to first.outgoingMinor)
        assertEquals(listOf("علي", "SAMI", "TEST PERSON"), zone.rows.map { it.party.label })
        assertEquals(Currency.EGP, zone.rows.single { it.party.label == "TEST PERSON" }.currency)
        assertEquals(1, zone.unidentified, "التحويل اللي اسمه ضاع من ملف البنك بيتعد بس")
        assertEquals(listOf("علي"), zone.questions.map { it.party.label }, "5 تحويلات في فبراير")
        assertEquals(6, zone.incomingChoices.size)
    }

    @Test fun ownAccountFixesEveryTransferWithThatPartyEvenConfirmedOnes() = runBlocking<Unit> {
        val ali = manage().zone().questions.single().party
        assertEquals(5, manage().markOwnAccount(ali), "كله — حتى السلفة اللي كانت متأكدة (قرار المالك §39 (ج))")
        for (t in aliTxns) {
            val now = byId(t.id)
            assertEquals(EconomicKind.INTERNAL_TRANSFER to true, now.economicKind to now.economicKindConfirmed)
        }
        assertEquals(EconomicKind.UNCLASSIFIED, byId(others[0].id).economicKind, "طرف تاني ما اتلمسش")
        assertTrue(manage().zone().questions.isEmpty(), "اتقرر فيه ⇒ ما يتسألش تاني")
        assertEquals(TransferVerdict.OWN_ACCOUNT, manage().zone().rows.first().decision?.verdict)
    }

    @Test fun personAsksAboutOutgoingAndIncoming() = runBlocking<Unit> {
        val ali = manage().zone().questions.single().party
        assertFailsWith<TransferZoneError> { manage().markPerson(ali, "p-مش-موجود") }
        assertEquals(4, manage().markPerson(ali, "p-1"), "2 صادر + 2 وارد بقوا يتسألوا")
        val out = byId(aliTxns[0].id)
        assertEquals(
            Triple(EconomicKind.UNCLASSIFIED, false, ReviewState.NEEDS_REVIEW), Triple(out.economicKind, out.economicKindConfirmed, out.reviewState),
            "الصادر بيتسأل «سلفة ولا دعم؟» — مش دعم لوحده (§75-5)",
        )
        assertEquals(EconomicKind.LOAN_GRANTED, byId(aliTxns[3].id).economicKind, "اللي إنت أكدته قبل كده ما بيتكتبش فوقه")
        val incoming = byId(aliTxns[1].id)
        assertEquals(EconomicKind.UNCLASSIFIED to ReviewState.NEEDS_REVIEW, incoming.economicKind to incoming.reviewState, "الوارد يتسأل — ما يتصنفش لوحده (§39.1)")
        assertEquals("p-1", parties.listAll().single().personId)
    }

    @Test fun dismissStopsTheQuestionWithoutTouchingTransactions() = runBlocking<Unit> {
        val before = txns.all()
        manage().dismiss(manage().zone().questions.single().party)
        assertTrue(manage().zone().questions.isEmpty())
        assertEquals(before, txns.all())
    }

    @Test fun forgetPutsTheQuestionBackAndAsksAboutTheTransactionsAgain() = runBlocking<Unit> {
        val ali = manage().zone().questions.single().party
        manage().markOwnAccount(ali)
        assertEquals(5, manage().forget(ali.key))
        assertEquals(EconomicKind.UNCLASSIFIED to ReviewState.NEEDS_REVIEW, byId(aliTxns[0].id).let { it.economicKind to it.reviewState })
        assertEquals(listOf("علي"), manage().zone().questions.map { it.party.label })
        assertEquals(0, manage().forget("مش-موجود"))
    }

    @Test fun ifSavingTransactionsFailsTheDecisionIsNotKept() = runBlocking<Unit> {
        val failing = object : TransactionRepository by txns {
            override suspend fun saveMany(transactions: List<Transaction>) = throw IllegalStateException("انقطاع وهمي")
        }
        val ali = manage().zone().questions.single().party
        assertFailsWith<IllegalStateException> { manage(failing).markOwnAccount(ali) }
        assertTrue(parties.listAll().isEmpty())
    }

    @Test fun aNewImportedTransferTakesTheDecisionByItself() = runBlocking<Unit> {
        manage().markOwnAccount(manage().zone().questions.single().party)
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(),
                MemoryUnitOfWork(listOf(txns, sources, batches)), SequentialIdGenerator(), clock, transferParties = parties,
            ),
        )
        val csv = "التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n" +
            "2026/03/02,100.00,0.00,900.00,,,$internalOp,${ali}W-/TOACCT/12345678901234567TO:ملاحظة\n" +
            "2026/03/03,50.00,0.00,850.00,,,$internalOp,W-/TOACCT/99998888777766665TOSAMI:ملاحظة\n"
        val request = ImportRequest("t.csv", csv, "a", ImportSourceType.CSV_LEGACY, "w-1", SchemaId.LEGACY)
        importer.commit(request, importer.preview(request))
        val added = txns.all().filter { it.occurredAt.startsWith("2026-03") }
        assertEquals(2, added.size)
        val toAli = added.single { transferPartyOf(it)?.label == "علي" }
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, toAli.economicKind to toAli.economicKindConfirmed)
        assertNull(added.single { transferPartyOf(it)?.label == "SAMI" }.takeIf { it.economicKindConfirmed }, "الطرف اللي مالوش قرار ما بيتغيرش")
    }
}
