package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * §75-10 (الشريحة S4) — سجل الدمج في التخزين: `matchingState` = «مكرر» (قيمة التطبيق القديم نفسها) و`transactionId` = العملية الموجودة،
 * + خريطة متداخلة `mergeUndo` **بس لو سطر الكشف غيّر حاجة**. السجل العادي هو هو بالحرف. بيكتب `build/kotlin-merge-backup.json` عشان
 * التطبيق القديم يتجرب عليه (`checkFullBackup.ts` بيفحص المستوى الأول بس). بيانات وهمية.
 */
class MergeRecordStorageTest {
    private val merge = SourceRecord(
        "sr-2", "b-2", "بنك وهمي", null, "H2", 2, "2026-10-02,100.00,0.00,4900.00,TEST CAFE RIYADH,,,شراء", "t-1", MatchingState.DUPLICATE, "دمج وهمي",
        mergeUndo = MergeRestore("2026-10-01", 1, null),
    )

    /** بالقيم اللي سطر الكشف كتبها (مراجعة S4). */
    private val wroteMerge = merge.copy(mergeUndo = MergeRestore("2026-10-01", 1, null, "2026-10-02", 490_000))
    private val oldShape = setOf("id", "batchId", "accountIdentity", "sourceReference", "sourceHash", "originalRowIndex", "rawLine", "transactionId", "matchingState", "reason")

    @Test fun mergeUndoRoundTripsAndPlainRecordsKeepTheOldShape() {
        assertEquals(merge, LedgerCodecs.sourceRecords.decode(LedgerCodecs.sourceRecords.toStore(merge)))
        val withBalance = merge.copy(mergeUndo = MergeRestore("2026-10-01", 1, -2_500))
        assertEquals(withBalance, LedgerCodecs.sourceRecords.decode(LedgerCodecs.sourceRecords.toStore(withBalance)))
        assertEquals(mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L), LedgerCodecs.sourceRecords.toStore(merge)["mergeUndo"])
        assertEquals(oldShape, LedgerCodecs.sourceRecords.toStore(merge.copy(mergeUndo = null)).keys)
        assertEquals(oldShape + "mergeUndo", LedgerCodecs.sourceRecords.toStore(merge).keys)
        // اللي سطر الكشف كتبه (مراجعة S4) جوه نفس الخريطة المتداخلة — المستوى الأول زي ما هو
        assertEquals(wroteMerge, LedgerCodecs.sourceRecords.decode(LedgerCodecs.sourceRecords.toStore(wroteMerge)))
        assertEquals(
            mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L, "mergedOccurredAt" to "2026-10-02", "mergedStatedBalanceMinor" to 490_000L),
            LedgerCodecs.sourceRecords.toStore(wroteMerge)["mergeUndo"],
        )
        assertEquals(oldShape + "mergeUndo", LedgerCodecs.sourceRecords.toStore(wroteMerge).keys)
    }

    private fun txn() = Transaction(
        id = "t-1", occurredAt = "2026-10-02", datePrecision = "day", sourceOrder = 2, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = 10_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-10-01T09:00:00.000Z", updatedAt = "2026-10-05T10:00:00.000Z",
        statedBalanceMinor = 490_000, rawMerchantName = "TEST CAFE",
    )

    private fun account(record: SourceRecord) = emptyBackupData().also {
        it.getValue("transactions") += LedgerCodecs.transactions.toStore(txn())
        it.getValue("importBatches") += LedgerCodecs.importBatches.toStore(ImportBatch("b-1", ImportSourceType.SMS, "F1", "bank-sms.json", "2026-10-01T09:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(1, 1, 0, 0, 0, 0)))
        it.getValue("importBatches") += LedgerCodecs.importBatches.toStore(ImportBatch("b-2", ImportSourceType.CSV_LEGACY, "F2", "statement.csv", "2026-10-05T10:00:00.000Z", ImportBatchState.COMMITTED, ImportCounts(1, 0, 0, 0, 0, 0)))
        it.getValue("sourceRecords") += LedgerCodecs.sourceRecords.toStore(SourceRecord("sr-1", "b-1", "بنك وهمي", "SMS:TEST", "H1", 1, "رسالة وهمية", "t-1", MatchingState.NEW, "جديد"))
        it.getValue("sourceRecords") += LedgerCodecs.sourceRecords.toStore(record)
    }

    @Test fun theMergeRecordTravelsInTheFullBackup() = runBlocking<Unit> {
        val file = FullBackup(MemoryFullBackup(account(wroteMerge))).create("2026-10-06T00:00:00.000Z")
        File("build/kotlin-merge-backup.json").writeText(file.toJsonText())
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        restore.apply(restore.plan(file.toJsonText()).file)
        assertEquals(wroteMerge, target.read().getValue("sourceRecords").map { LedgerCodecs.sourceRecords.decode(it) }.single { it.id == "sr-2" })
        assertEquals(file.checksum, FullBackup(target).create("2026-10-06T00:00:00.000Z").checksum, "اللي اترجع = الأصل")
    }

    @Test fun aBrokenMergeRecordIsRefused() = runBlocking<Unit> {
        val bad = account(merge.copy(mergeUndo = null)).also { data ->
            val rows = data.getValue("sourceRecords")
            rows[1] = rows[1] + ("mergeUndo" to mapOf("occurredAt" to "2026-02-30", "sourceOrder" to 1L))
        }
        val e = assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(bad)).create("2026-10-06T00:00:00.000Z") }
        assertTrue("mergeUndo" in (e.message ?: ""), e.message)
        val badWrote = account(merge.copy(mergeUndo = null)).also { data ->
            val rows = data.getValue("sourceRecords")
            rows[1] = rows[1] + ("mergeUndo" to mapOf("occurredAt" to "2026-10-01", "sourceOrder" to 1L, "mergedOccurredAt" to "2026-02-30"))
        }
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(badWrote)).create("2026-10-06T00:00:00.000Z") }
        val onNew = account(merge.copy(matchingState = MatchingState.NEW))
        assertFailsWith<IllegalArgumentException> { FullBackup(MemoryFullBackup(onNew)).create("2026-10-06T00:00:00.000Z") }
    }
}
