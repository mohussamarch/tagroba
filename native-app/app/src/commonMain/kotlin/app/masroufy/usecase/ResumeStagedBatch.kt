package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository

/**
 * ResumeStagedBatch — نقل `resumeStagedBatch.ts`: تنظيف دفعة انقطعت قبل اكتمالها.
 * أي انقطاع بيسيب دفعة `staged`، واللي اتكتب تحتها **غير معتمد** — الحالة دي بتنظّفه
 * عند فتح التطبيق، فـ«صفر أو كامل الدفعة» بتتحقق **بالأثر** (ARCHITECTURE §11.1).
 */

data class StagedCleanupOutcome(
    val batchId: Id,
    val fileName: String,
    val deletedTransactions: Int,
    val deletedRecords: Int,
    /** سبب تعذر التنظيف، لو اتعذر. الدفعة بتفضل معلّقة ومفيش حاجة بتتحذف منها. */
    val error: String? = null,
)

data class ResumeStagedBatchDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val batches: ImportBatchRepository,
    /** كام دفعة أخيرة بتتفحص. القراية محدودة — ARCHITECTURE §5.6. */
    val scanLimit: Int? = null,
)

class ResumeStagedBatch(private val deps: ResumeStagedBatchDeps) {
    /** بيعرض الدفعات المعلّقة من غير حذف — للعرض على المستخدم قبل التنظيف. */
    suspend fun findStaged(): List<ImportBatch> =
        deps.batches.listRecent(deps.scanLimit ?: 20).filter { it.state == ImportBatchState.STAGED }

    /**
     * بينظّف دفعة معلّقة: بيحذف اللي اتكتب تحتها وبيعلّمها `reverted`.
     * آمن التكرار: لو التنظيف نفسه اتقطع، الدفعة بتفضل `staged` وبيتعاد التنظيف بلا ضرر.
     */
    suspend fun cleanup(batchId: Id): StagedCleanupOutcome {
        val batch = deps.batches.findById(batchId) ?: throw IllegalStateException("دفعة غير موجودة: $batchId")
        if (batch.state != ImportBatchState.STAGED) {
            throw IllegalStateException("الدفعة دي حالتها «${batch.state.wire}» مش «staged». التنظيف بيشتغل على المعلّق بس.")
        }

        val records = deps.sources.listByBatch(batchId)
        val txnIds = records.mapNotNull { it.transactionId }

        // الترتيب مقصود: العمليات الأول وبعدها السجلات وبعدها علامة الدفعة —
        // لو اتقطع بينهم الدفعة بتفضل staged ويتعاد التنظيف بلا ضرر
        if (txnIds.isNotEmpty()) deps.txns.deleteMany(txnIds)
        if (records.isNotEmpty()) deps.sources.deleteMany(records.map { it.id })
        deps.batches.updateState(batchId, ImportBatchState.REVERTED)

        return StagedCleanupOutcome(batchId, batch.fileName, deletedTransactions = txnIds.size, deletedRecords = records.size)
    }

    /**
     * بينظّف كل المعلّق — بيتندى عند فتح التطبيق.
     * ⚠️ دفعة واحدة اتعذر تنظيفها **ما بتوقفش الباقي ولا بتفتح التطبيق على خطأ**
     * (بلاغ المالك 2026-09-11 — `importVisibility.test.ts` في التطبيق الحالي).
     */
    suspend fun cleanupAll(): List<StagedCleanupOutcome> {
        val staged = findStaged()
        return staged.map { batch ->
            try {
                cleanup(batch.id)
            } catch (error: Throwable) {
                StagedCleanupOutcome(batch.id, batch.fileName, deletedTransactions = 0, deletedRecords = 0, error = error.message ?: error.toString())
            }
        }
    }
}
