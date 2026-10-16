package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
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
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsLegVerdict
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.smsLegVerdict
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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r041-B — «الكشف غطّاها» في «صلّح رسايل البنك القديمة». بيانات مخترعة بس: محفظة «بنك أ» آخرها 2222 · كشف من 1 لـ20 مارس 2026
 * (100.00 · 99.00 يوم 12 · 300.00) · دفعة رسايل يوم 25 مارس فيها سجلات «مكررة» من غير عملية (القاعدة القديمة).
 */
class RepairOldSmsUnrecordedTest {
    private val a = Wallet("wA", "بنك أ", Currency.SAR, "bank", 0, "2026-01-01", "2222", null)
    private val stBatch = ImportBatch("bSt", ImportSourceType.CSV_PREVIEW, "hs", "st.csv", "2026-03-21T10:00:00Z", ImportBatchState.COMMITTED, ImportCounts(3, 3, 0, 0, 0, 0))
    private val smsBatch = ImportBatch("bSms", ImportSourceType.SMS, "hm", "bank-sms.json", "2026-03-25T10:00:00Z", ImportBatchState.COMMITTED, ImportCounts(6, 1, 5, 0, 0, 0))
    private val statement = listOf(txn("s1", "2026-03-01", 10_000), txn("s2", "2026-03-12", 9_900), txn("s3", "2026-03-20", 30_000))
    private val statementRecords = statement.mapIndexed { i, t -> SourceRecord("rs$i", "bSt", "بنك أ", "ST:$i", "h", i + 1, "line $i", t.id, MatchingState.NEW, "") }

    private fun purchase(amount: String, day: String) = "شراء\nبطاقة:6604;مدى\nمبلغ:SAR $amount\nلدى:TEST STORE\nفي:$day 10:00"

    private fun skipped(id: String, ref: String, body: String, row: Int = 1) = SourceRecord(id, "bSms", "بنك أ", ref, "h", row, body, null, MatchingState.DUPLICATE, "covered")

    private val notInStatement = skipped("r1", "SMS:one", purchase("250.00", "26-03-10"))

    private inner class World(records: List<SourceRecord>, extraTxns: List<Transaction> = emptyList(), backup: MemoryRepairBackup = MemoryRepairBackup()) {
        val txns = MemoryTransactionRepository(statement + extraTxns)
        val sources = MemorySourceRecordRepository(statementRecords + records)
        val batches = MemoryImportBatchRepository(listOf(stBatch, smsBatch))
        private val wallets = MemoryWalletRepository(listOf(a))
        private val clock = FixedClock("2026-10-16T10:00:00.000Z")
        private val uow = MemoryUnitOfWork(listOf(txns, sources, batches))
        private val deps = ImportStatementDeps(
            txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(), uow, SequentialIdGenerator(), clock,
        )
        val repair = RepairOldSms(txns, sources, batches, wallets, listOf(SaudiBankSmsReader), backup, clock, uow, CoveredSmsRepair(deps, wallets, listOf(SaudiBankSmsReader)))
    }

    @Test
    fun theTestBodiesReallyParseWithTheirOwnDate() {
        val row = (SaudiBankSmsReader.parse(BankSmsMessage("", "2026-03-25T12:00:00Z", purchase("250.00", "26-03-10")), 1) as SmsParseResult.Ok).row
        assertEquals(Triple("2026-03-10", 25_000L, Direction.OUT), Triple(row.date, row.amountMinor, row.direction))
    }

    @Test
    fun previewListsTheCoveredDayMessageWhoseAmountIsNotInTheStatement() = runBlocking {
        val w = World(listOf(notInStatement))
        val line = w.repair.previewAll().unrecorded.single()
        assertEquals(SmsUnrecordedLine("r1", "wA", "2026-03-10", 25_000, Currency.SAR, Direction.OUT, line.title), line)
        assertTrue(line.title.isNotBlank() && "شراء" !in line.title)
    }

    @Test
    fun aMessageWhoseAmountIsInTheStatementIsNeverListed() = runBlocking {
        // 99.00 يوم 12 في الكشف — حتى لو بعد يوم (النافذة الواسعة)
        val w = World(listOf(skipped("r2", "SMS:two", purchase("99.00", "26-03-12")), skipped("r3", "SMS:three", purchase("99.00", "26-03-13"), 2)))
        assertTrue(w.repair.previewAll().unrecorded.isEmpty())
    }

    @Test
    fun recordedLaterOwnerDeletedOutsideTheDaysOrAlreadyThereAreNotListed() = runBlocking {
        val later = SourceRecord("rL", "bSms", "بنك أ", "SMS:one", "h", 9, notInStatement.rawLine, "tX", MatchingState.NEW, "")
        // عمليتها في محفظة تانية — المرجع لوحده هو اللي بيمنع (مش حارس نفس اليوم)
        assertTrue(World(listOf(notInStatement, later), listOf(txn("tX", "2026-03-10", 25_000).copy(walletId = "wB"))).repair.previewAll().unrecorded.isEmpty())
        val deleted = later.copy(transactionId = null, ownerDeletedAt = "2026-04-01T10:00:00Z")
        assertTrue(World(listOf(notInStatement, deleted)).repair.previewAll().unrecorded.isEmpty())
        // آخر يوم في الكشف (20) وبعده مش «مغطّى»
        assertTrue(World(listOf(skipped("r5", "SMS:five", purchase("250.00", "26-03-20")))).repair.previewAll().unrecorded.isEmpty())
        // عملية بنفس اليوم والمبلغ والاتجاه في المحفظة (رسالة اتقرت تاني) ⇒ حارس التسجيل مرتين
        assertTrue(World(listOf(notInStatement), listOf(txn("tY", "2026-03-10", 25_000).copy(walletId = "wA"))).repair.previewAll().unrecorded.isEmpty())
    }

    @Test
    fun anUnknownAccountLegWaitsForTheQuestionInsteadOfBeingListed() = runBlocking {
        val body = "حوالة داخلية واردة\nمبلغ:SAR 400.00\nمن:7777\nالى:3333\nفي:26-03-11 09:00"
        val row = (SaudiBankSmsReader.parse(BankSmsMessage("", "2026-03-25T12:00:00Z", body), 1) as SmsParseResult.Ok).row
        assertTrue(smsLegVerdict(row, setOf("2222")) is SmsLegVerdict.Unknown)
        assertTrue(World(listOf(skipped("r6", "SMS:six", body))).repair.previewAll().unrecorded.isEmpty())
    }

    @Test
    fun applyRecordsWithTheMarkLinksTheRecordAndUndoRestoresEverythingExactly() = runBlocking {
        val w = World(listOf(notInStatement))
        val result = assertNotNull(w.repair.applyAll(emptySet(), setOf("r1")))
        assertTrue(result.backup.fileName.contains("sms-repair"))
        assertEquals(1, result.undo.count)
        val record = w.sources.listByBatch("bSms").single()
        val created = w.txns.findByIds(listOf(assertNotNull(record.transactionId))).single()
        assertEquals(MatchingState.NEW, record.matchingState)
        assertEquals(true, created.notInStatement)
        assertEquals(listOf("wA", "2026-03-10", 25_000L, Direction.OUT), listOf(created.walletId, created.occurredAt, created.amountMinor, created.observedDirection))
        assertEquals(smsBatch.counts.copy(imported = 2, duplicates = 4), w.batches.findById("bSms")?.counts)
        // تشغيل تاني بعد التطبيق ⇒ مفيش حاجة
        assertTrue(w.repair.previewAll().unrecorded.isEmpty())
        w.repair.undo(result.undo)
        assertEquals(notInStatement, w.sources.listByBatch("bSms").single())
        assertTrue(w.txns.findByIds(listOf(created.id)).isEmpty())
        assertEquals(statement.map { it.id }.toSet(), w.txns.listByDateRange("2026-01-01", "2026-12-31").map { it.id }.toSet())
        assertEquals(smsBatch, w.batches.findById("bSms"))
        // اتمسحت من غير علامة مسح المالك ⇒ بتتعرض تاني
        assertEquals(listOf("r1"), w.repair.previewAll().unrecorded.map { it.recordId })
    }

    @Test
    fun aSkippedLineIsNotAppliedAndALineThatChangedSinceThePreviewDropsOut() = runBlocking {
        val second = skipped("r7", "SMS:seven", purchase("70.00", "26-03-15"), 2)
        val w = World(listOf(notInStatement, second))
        assertEquals(listOf("r1", "r7"), w.repair.previewAll().unrecorded.map { it.recordId })
        // المالك شال r7 بلمسة
        assertNotNull(w.repair.applyAll(emptySet(), setOf("r1")))
        assertNull(w.sources.listByBatch("bSms").single { it.id == "r7" }.transactionId)
        // r7 اتسجلت بطريقة تانية بعد المعاينة ⇒ ما بتتطبقش
        w.sources.saveMany(listOf(second.copy(id = "r8", matchingState = MatchingState.NEW, transactionId = "tZ")))
        assertNull(w.repair.applyAll(emptySet(), setOf("r7")))
        assertNull(w.sources.listByBatch("bSms").single { it.id == "r7" }.transactionId)
    }

    @Test
    fun aFailedBackupWritesNothing() = runBlocking {
        val w = World(listOf(notInStatement), backup = MemoryRepairBackup(truncate = true))
        assertTrue(runCatching { w.repair.applyAll(emptySet(), setOf("r1")) }.isFailure)
        assertEquals(notInStatement, w.sources.listByBatch("bSms").single())
        assertEquals(statement.size, w.txns.listByDateRange("2026-01-01", "2026-12-31").size)
        assertEquals(smsBatch, w.batches.findById("bSms"))
    }

    @Test
    fun oneApplyForAMoveAndAnUnrecordedLineHasOneUndoThatRestoresBoth() = runBlocking {
        val moveTxn = txn("t1", "2026-03-03", 50_000).copy(walletId = "wA", reviewState = ReviewState.CONFIRMED)
        val moveRecord = SourceRecord("rM", "bSms", "بنك أ", "SMS:move", "h", 3, "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00", "t1", MatchingState.NEW, "")
        val w = World(listOf(notInStatement, moveRecord), listOf(moveTxn))
        val preview = w.repair.previewAll()
        assertEquals(listOf("t1"), preview.moves.map { it.transactionId })
        val result = assertNotNull(w.repair.applyAll(setOf("t1"), setOf("r1")))
        assertEquals(2, result.undo.count)
        assertEquals(Direction.IN, w.txns.findByIds(listOf("t1")).single().observedDirection)
        val created = assertNotNull(w.sources.listByBatch("bSms").single { it.id == "r1" }.transactionId)
        // مربوطة بعد التطبيق ⇒ «تراجع» بيترفض ومفيش حاجة بتتغير
        val linked = w.txns.findByIds(listOf(created)).single()
        w.txns.saveMany(listOf(linked.copy(linkedExpenseId = "tOther")))
        assertFailsWith<IllegalStateException> { w.repair.undo(result.undo) }
        w.txns.saveMany(listOf(linked))
        w.repair.undo(result.undo)
        assertEquals(moveTxn, w.txns.findByIds(listOf("t1")).single())
        assertEquals(notInStatement, w.sources.listByBatch("bSms").single { it.id == "r1" })
        assertTrue(w.txns.findByIds(listOf(created)).isEmpty())
    }

    @Test
    fun aRefundWaitsForItsOwnQuestionInsteadOfBeingListed() = runBlocking {
        // سطر ليه سبب انتظار (استرداد) ⇒ مش في النسخة دي — حتى لو مبلغه مش في الكشف ويومه جوه أيامه
        val body = "Notification: Refund\nTransaction: TEST OPTICS\nCard: ***2222\nAmount: 89.00 SAR\nDate: 2026-03-14 16:02"
        val row = (SaudiBankSmsReader.parse(BankSmsMessage("", "2026-03-25T12:00:00Z", body), 1) as SmsParseResult.Ok).row
        assertEquals(Triple(SmsKind.REFUND, "2026-03-14", Direction.IN), Triple(row.kind, row.date, row.direction))
        assertTrue(World(listOf(skipped("r9", "SMS:nine", body))).repair.previewAll().unrecorded.isEmpty())
    }

    @Test
    fun undoIsRefusedWithoutAnyWriteWhenTheRecordedLineWasEditedSince() = runBlocking {
        val w = World(listOf(notInStatement))
        val result = assertNotNull(w.repair.applyAll(emptySet(), setOf("r1")))
        val record = w.sources.listByBatch("bSms").single()
        val created = w.txns.findByIds(listOf(assertNotNull(record.transactionId))).single()
        val edited = created.copy(categoryId = "cOther", updatedAt = "2026-10-17T09:00:00.000Z")
        w.txns.saveMany(listOf(edited))
        assertFailsWith<IllegalStateException> { w.repair.undo(result.undo) }
        assertEquals(edited, w.txns.findByIds(listOf(created.id)).single())
        assertEquals(record, w.sources.listByBatch("bSms").single())
    }

    @Test
    fun coveredDaysAreTheSameRuleAsRecording() {
        val days = "2026-03-01" to "2026-03-20"
        assertEquals(listOf(true, true, false, false), listOf("2026-03-01", "2026-03-19", "2026-03-20", "2026-02-28").map { inCoveredDays(it, days) })
        assertTrue(amountInStatement(statement, "2026-03-13", 9_900, Direction.OUT))
        assertTrue(!amountInStatement(statement, "2026-03-13", 9_900, Direction.IN))
    }

    private fun txn(id: String, day: String, amount: Long) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-03-21T10:00:00Z",
        updatedAt = "2026-03-21T10:00:00Z", walletId = "wA",
    )
}
