package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SchemaId
import app.masroufy.core.SourceRecord
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.ImportRequest
import app.masroufy.usecase.ImportStatement
import app.masroufy.usecase.ImportStatementDeps
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * الاستيراد بحالة الاستخدام نفسها (`ImportStatement`) على **فايربيز** (محاكي) بدل الذاكرة: العمليات ومصادرها ودفعاتها
 * بتتكتب وتتقرا من المستودعات الحقيقية، ومنع التكرار بيقرا من فايربيز. التصنيفات والتجار لسه في الذاكرة. بيانات وهمية.
 */
@RunWith(AndroidJUnit4::class)
class ImportOnFirestoreTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(120_000) { block() } }

    /** كشف وهمي بشكل CSV الراجحي — الرصيد متسلسل صح. */
    private fun statement(rows: Int, startDay: Int = 1): String {
        val sb = StringBuilder("التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n")
        var balance = 1_000_000L
        for (i in 0 until rows) {
            val amount = 1_000L + i * 7
            balance -= amount
            val day = (startDay + i).toString().padStart(2, '0')
            sb.append("2026/09/$day,${amount / 100}.${(amount % 100).toString().padStart(2, '0')},0.00,${balance / 100}.${(balance % 100).toString().padStart(2, '0')},متجر وهمي $i,مطاعم,شراء,\n")
        }
        return sb.toString()
    }

    @Test fun batchesAndSourcesRoundTrip() = run {
        val s = space()
        val batches = FirestoreImportBatchRepository(s)
        val sources = FirestoreSourceRecordRepository(s)
        val batch = ImportBatch("b-1", ImportSourceType.CSV_LEGACY, "hash-1", "statement-9988776655.csv", "2026-09-30T00:00:00.000Z", ImportBatchState.STAGED, ImportCounts(2, 1, 0, 0, 0, 1))
        batches.save(batch)
        assertNull(batches.findByFileHash("hash-1"), "الدفعة المرحلية مش «اتستوردت قبل كده»")
        batches.updateState("b-1", ImportBatchState.COMMITTED)
        assertEquals(ImportBatchState.COMMITTED, batches.findByFileHash("hash-1")?.state)
        assertEquals("statement-****6655.csv", batches.findById("b-1")!!.fileName, "اسم الملف بيتقص زي التطبيق الحالي")
        sources.saveMany(listOf(
            SourceRecord("sr-1", "b-1", "acct", "ref", "h1", 1, "raw", "t-1", MatchingState.NEW, "جديد"),
            SourceRecord("sr-2", "b-1", "acct", null, "h2", 2, "raw", null, MatchingState.INVALID, "غلط"),
        ))
        assertEquals(setOf("sr-1", "sr-2"), sources.listByBatch("b-1").map { it.id }.toSet())
        assertEquals(listOf("sr-1"), sources.listByTransactionIds(listOf("t-1")).map { it.id })
        assertEquals(2, sources.listByAccountIdentity("acct").size)
        sources.deleteMany(listOf("sr-2"))
        assertEquals(listOf("sr-1"), sources.listByBatch("b-1").map { it.id })
        assertEquals(listOf("b-1"), batches.listRecent(5).map { it.id })
    }

    @Test fun importTwiceOnFirestoreAddsNothingTheSecondTime() = run {
        val s = space()
        val txns = FirestoreTransactionRepository(s)
        val sources = FirestoreSourceRecordRepository(s)
        val batches = FirestoreImportBatchRepository(s)
        val importer = ImportStatement(
            ImportStatementDeps(
                txns, sources, batches, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(),
                PassthroughUnitOfWork(), SequentialIdGenerator(), FixedClock("2026-09-30T00:00:00.000Z"),
            ),
        )
        val first = ImportRequest("sep.csv", statement(25), "fake-account", ImportSourceType.CSV_LEGACY, "w-bank", SchemaId.LEGACY)
        val preview = importer.preview(first)
        assertEquals(25, preview.counts.newCount)
        importer.commit(first, preview)
        assertEquals(25, txns.listByDateRange("2026-09-01", "2026-09-30").size)
        assertEquals(25, sources.listByAccountIdentity("fake-account").size)

        // نفس الكشف تاني بعد ما اتحفظ في فايربيز ⇒ كله مكرر
        val again = importer.preview(first.copy(fileName = "sep-again.csv"))
        assertEquals(0, again.counts.newCount)
        assertEquals(ImportBatchState.COMMITTED, again.previousBatch?.state)
        // كشف متداخل: آخر 5 صفوف موجودين + 3 جديد (بالرصيد المتسلسل من آخر صف)
        val lines = statement(28).trimEnd('\n').split('\n')
        val overlap = (listOf(lines.first()) + lines.takeLast(8)).joinToString("\n") + "\n"
        val mixed = importer.preview(ImportRequest("overlap.csv", overlap, "fake-account", ImportSourceType.CSV_LEGACY, "w-bank", SchemaId.LEGACY))
        assertEquals(3, mixed.counts.newCount)
        assertEquals(5, mixed.counts.duplicates)
    }
}
