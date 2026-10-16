package app.masroufy.usecase

import app.masroufy.core.BankFeeCategory
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ReviewState
import app.masroufy.core.SaudiBankSmsReader
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRepairBackup
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * مراجعة r041-B (B-1 · B-2 · B-3) على «الكشف غطّاها». كله مخترع: محفظة «بنك أ» آخرها 2222 · كشف من 1 لـ20 مارس 2026 (100.00 · 99.00 · 300.00).
 */
class CoveredSmsRepairFixesTest {
    private val a = Wallet("wA", "بنك أ", Currency.SAR, "bank", 0, "2026-01-01", "2222", null)
    private val stBatch = ImportBatch("bSt", ImportSourceType.CSV_PREVIEW, "hs", "st.csv", "2026-03-21T10:00:00Z", ImportBatchState.COMMITTED, ImportCounts(3, 3, 0, 0, 0, 0))
    private val statement = listOf(txn("s1", "2026-03-01", 10_000), txn("s2", "2026-03-12", 9_900), txn("s3", "2026-03-20", 30_000))
    private val statementRecords = statement.mapIndexed { i, t -> SourceRecord("rs$i", "bSt", "بنك أ", "ST:$i", "h", i + 1, "line $i", t.id, MatchingState.NEW, "") }

    private fun purchase(amount: String, day: String) = "شراء\nبطاقة:6604;مدى\nمبلغ:SAR $amount\nلدى:TEST STORE\nفي:$day 10:00"

    private fun skipped(id: String, ref: String, body: String, row: Int = 1) = SourceRecord(id, "bSms", "بنك أ", ref, "h", row, body, null, MatchingState.DUPLICATE, "covered")

    /** الكتابة رقم [crashAt] (عمليات أو سجلات — أيًّا كانت) بتقع — زي البرنامج اللي اتقفل في النص. 0 = مفيش وقوع. */
    private class Crash(var crashAt: Int = 0) {
        private var writes = 0

        fun tick() {
            writes++
            if (writes == crashAt) throw IllegalStateException("killed between the two writes")
        }
    }

    private class CrashTxns(val real: MemoryTransactionRepository, val crash: Crash) : TransactionRepository by real {
        override suspend fun saveMany(transactions: List<Transaction>) = crash.tick().also { real.saveMany(transactions) }
    }

    private class CrashSources(val real: MemorySourceRecordRepository, val crash: Crash) : SourceRecordRepository by real {
        override suspend fun saveMany(records: List<SourceRecord>) = crash.tick().also { real.saveMany(records) }
    }

    /** [importedAt] = يوم دفعة الرسايل · [fees] = أثر الرسوم بالمستودعات زي التشغيل · [passthrough] = وحدة عمل من غير رجوع (زي فايرستور). */
    private inner class World(
        records: List<SourceRecord>,
        importedAt: String = "2026-03-25T10:00:00Z",
        extraTxns: List<Transaction> = emptyList(),
        fees: Boolean = false,
        passthrough: Boolean = false,
    ) {
        val crash = Crash()
        val txnStore = MemoryTransactionRepository(statement + extraTxns)
        val sourceStore = MemorySourceRecordRepository(statementRecords + records)
        private val txns = CrashTxns(txnStore, crash)
        private val sources = CrashSources(sourceStore, crash)
        val batches = MemoryImportBatchRepository(
            listOf(stBatch, ImportBatch("bSms", ImportSourceType.SMS, "hm", "bank-sms.json", importedAt, ImportBatchState.COMMITTED, ImportCounts(6, 1, 5, 0, 0, 0))),
        )
        private val wallets = MemoryWalletRepository(listOf(a))
        private val clock = FixedClock("2026-10-16T10:00:00.000Z")
        private val uow: UnitOfWork = if (passthrough) {
            object : UnitOfWork {
                override suspend fun <T> run(work: suspend () -> T): T = work()
            }
        } else {
            MemoryUnitOfWork(listOf(txnStore, sourceStore, batches))
        }
        private val deps = ImportStatementDeps(
            txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(), uow, SequentialIdGenerator(), clock,
            effects = if (fees) listOf(SmsFeeEffect(MemoryCategoryRepository(), txns, sources)) else emptyList(),
        )
        val repair = RepairOldSms(txns, sources, batches, wallets, listOf(SaudiBankSmsReader), MemoryRepairBackup(), clock, uow, CoveredSmsRepair(deps, wallets, listOf(SaudiBankSmsReader)))

        suspend fun all(): List<Transaction> = txnStore.listByDateRange("2026-01-01", "2026-12-31")
    }

    /** B-1: «اقرأ اللي فات» يوم 10 أكتوبر — الرسالة بتاريخ بسنتين أرقام (10 مارس) أقدم من 60 يوم من يوم الدفعة. كانت بتختفي من غير ما تتعد. */
    @Test
    fun aCatchUpBatchStillListsTheOldTwoDigitYearMessage() = runBlocking {
        val w = World(listOf(skipped("r1", "SMS:one", purchase("250.00", "26-03-10"))), importedAt = "2026-10-10T12:00:00Z")
        val line = w.repair.previewAll().unrecorded.single()
        assertEquals(Triple("2026-03-10", 25_000L, Direction.OUT), Triple(line.date, line.amountMinor, line.direction))
    }

    /** B-1: رسالة من غير تاريخ في نصها — يومها كان من وقت وصولها (مش محفوظ) ⇒ ولا وقت وصول بيدّي نفس اليوم ⇒ ما بتتعرضش (القاعدة 10). */
    @Test
    fun aDatelessMessageInACatchUpBatchIsStillNotListed() = runBlocking {
        val dateless = "شراء\nبطاقة:6604;مدى\nمبلغ:SAR 250.00\nلدى:TEST STORE"
        assertTrue(World(listOf(skipped("r1", "SMS:one", dateless))).repair.previewAll().unrecorded.isEmpty())
        assertTrue(World(listOf(skipped("r1", "SMS:one", dateless)), importedAt = "2026-03-15T12:00:00Z").repair.previewAll().unrecorded.isEmpty())
    }

    /**
     * B-2: رسالة رسوم لوحدها اتسجلت قبل كده (عملية F) والتحويل بنفس الرسوم «الكشف غطّاه» ⇒ التسجيل بيكتب «#fee» على F (رسوم واحدة).
     * «تراجع» لازم يشيله — كان بيفضل، والتطبيق التاني كان بيشوف F «محسوبة» ويعمل رسوم تانية (الرسوم مرتين).
     */
    @Test
    fun undoRemovesTheFeeClaimSoReapplyingKeepsOneFee() = runBlocking {
        val fee = txn("F", "2026-03-10", 575).copy(economicKind = EconomicKind.FEE, sourceOperationType = BankFeeCategory.NAME)
        val transfer = "حوالة محلية صادرة\nمصرف:ANB\nمن:2222\nمبلغ:SAR 1000\nالى:TEST PERSON\nالرسوم:SAR 5.75\n26/03/10 09:10"
        val w = World(listOf(skipped("r1", "SMS:t1", transfer)), extraTxns = listOf(fee), fees = true)
        assertEquals(listOf("r1"), w.repair.previewAll().unrecorded.map { it.recordId })
        val result = assertNotNull(w.repair.applyAll(emptySet(), setOf("r1")))
        suspend fun claims() = w.sourceStore.listByTransactionIds(listOf("F")).filter { it.sourceReference?.endsWith("#fee") == true }
        suspend fun fees() = w.all().filter { it.economicKind == EconomicKind.FEE }
        assertEquals(1, claims().size, "رسوم التحويل اتحسبت على F")
        assertEquals(listOf("F"), fees().map { it.id })
        w.repair.undo(result.undo)
        assertEquals(emptyList(), claims(), "«تراجع» رجّع كل حاجة بالحرف")
        assertEquals((statement + fee).map { it.id }.toSet(), w.all().map { it.id }.toSet())
        assertNotNull(w.repair.applyAll(emptySet(), setOf("r1")))
        assertEquals(listOf("F"), fees().map { it.id }, "رسوم واحدة بعد التطبيق التاني")
    }

    /**
     * B-3: فايرستور مالوش رجوع ⇒ لو البرنامج اتقفل بين الكتابتين: العملية الأول وبعدها السجل. السجل بيفضل «مكرر» من غير عملية وحارس
     * التسجيل مرتين بيخفيه ⇒ الـ250 ما بتضيعش ولا بتتكرر. (كان السجل الأول ⇒ سجل بيشاور على عملية مش موجودة والمبلغ ضاع من الاتنين.)
     */
    @Test
    fun aCrashBetweenTheTwoWritesNeverLosesTheAmount() = runBlocking {
        val w = World(listOf(skipped("r1", "SMS:one", purchase("250.00", "26-03-10"))), passthrough = true)
        w.crash.crashAt = 2
        assertTrue(runCatching { w.repair.applyAll(emptySet(), setOf("r1")) }.isFailure)
        assertEquals(1, w.all().count { it.amountMinor == 25_000L && it.occurredAt == "2026-03-10" }, "الـ250 اتسجلت مرة واحدة")
        w.crash.crashAt = 0
        assertTrue(w.repair.previewAll().unrecorded.isEmpty(), "ومش بتتعرض تاني")
    }

    private fun txn(id: String, day: String, amount: Long) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-03-21T10:00:00Z",
        updatedAt = "2026-03-21T10:00:00Z", walletId = "wA",
    )
}
