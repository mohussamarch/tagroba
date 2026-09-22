package app.masroufy.usecase

import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.UnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ذرّية الالتزام — نقل فكرة `tests/acceptance/atomicity.test.ts`: مستودع بينفجر عمدًا
 * في أخطر لحظة (بعد كتابة الدفعة والسجلات، وقت كتابة العمليات).
 * spec/06: «انقطاع وسط حفظ دفعة ⇒ صفر أو كامل الدفعة، مش نصها».
 * مفيش ملف مرجع للحالة دي: التطبيق الحالي ما بيقدرش يولّدها، فهي مكتوبة بالإيد.
 */
class ImportAtomicityTest {
    /** بينفجر عند أول كتابة عمليات — بعد ما الدفعة والسجلات اتكتبوا فعلًا. */
    private class ExplodingTransactionRepository(
        val inner: MemoryTransactionRepository = MemoryTransactionRepository(),
    ) : app.masroufy.port.TransactionRepository by inner, app.masroufy.memory.Snapshotable by inner {
        override suspend fun saveMany(transactions: List<Transaction>) {
            throw IllegalStateException("انقطاع مصطنع وقت كتابة العمليات")
        }
    }

    private val csv = listOf(
        "date,name,amount,type,source,reference",
        "2026-09-05,Amazon,100.50,expense,البنك,REF-1",
        "2026-09-06,STC,55.25,expense,البنك,REF-2",
    ).joinToString("\n")

    private val request = ImportRequest(
        fileName = "boom.csv",
        content = csv,
        accountIdentity = "حساب-تجريبي",
        sourceType = ImportSourceType.CSV_PREVIEW,
    )

    private fun build(uow: (List<app.masroufy.memory.Snapshotable>) -> UnitOfWork): Triple<ImportStatement, Pair<MemorySourceRecordRepository, MemoryImportBatchRepository>, ExplodingTransactionRepository> {
        val txns = ExplodingTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val deps = ImportStatementDeps(
            txns = txns, sources = sources, batches = batches,
            merchants = MemoryMerchantRepository(), categories = MemoryCategoryRepository(), rules = MemoryRuleRepository(),
            uow = uow(listOf(txns.inner, sources, batches)),
            ids = SequentialIdGenerator(),
            clock = FixedClock("2026-09-22T10:00:00.000Z"),
        )
        return Triple(ImportStatement(deps), sources to batches, txns)
    }

    @Test
    fun failureInsideRealUnitOfWorkLeavesZero() = runBlocking {
        val (importer, stores, txns) = build(::MemoryUnitOfWork)
        val (sources, batches) = stores

        val preview = importer.preview(request)
        assertFailsWith<IllegalStateException> { importer.commit(request, preview) }

        // صفر مش نص: ولا دفعة ولا سجل ولا عملية اتبقوا
        assertEquals(emptyList(), batches.all(), "الدفعة كان لازم تترجع")
        assertEquals(emptyList(), sources.all(), "السجلات كان لازم تترجع")
        assertEquals(emptyList(), txns.inner.all())

        // وإعادة المعاينة بعد الفشل ما بتشوفش «استيراد سابق»
        assertNull(importer.preview(request).previousBatch)
    }

    @Test
    fun failureWithoutRollbackLeavesOnlyStagedLeftovers() = runBlocking {
        val (importer, stores, txns) = build { PassthroughUnitOfWork() }
        val (sources, batches) = stores

        val preview = importer.preview(request)
        assertFailsWith<IllegalStateException> { importer.commit(request, preview) }

        // من غير تراجع: الدفعة اتكتبت staged والسجلات موجودة — بس مفيش عملية واحدة
        assertEquals(1, batches.all().size)
        assertEquals(ImportBatchState.STAGED, batches.all().single().state)
        assertTrue(sources.all().isNotEmpty())
        assertEquals(emptyList(), txns.inner.all())

        // العلامة staged مش «استيراد سابق»: findByFileHash بيشوف committed بس (ARCHITECTURE §11.1)
        assertNull(importer.preview(request).previousBatch)
    }
}
