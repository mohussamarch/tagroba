package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.port.AllocationRepository
import app.masroufy.port.ImportBatchRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * RevertImportBatch — نقل `revertImportBatch.ts`. قيد spec/03: التراجع **مش حذف أعمى** —
 * العملية اللي تؤيدها مصادر تانية، أو دخلت عليها تسوية، أو متربطة بشخص، بتفضل ويتشرح ليه.
 *
 * ⚠️ مسار «نسخة قبل الحذف» (`RepairBackupPort`, HANDOVER §36) **مش هنا لسه** — محتاج منفذ
 * ملفات الجهاز، وبيتوصّل مع طبقة التخزين. السلوك الحالي = التطبيق الحالي لما `deps.backup` غايبة.
 */

enum class RevertDecision(val wire: String) {
    DELETED("deleted"), KEPT_OTHER_SOURCE("kept_other_source"), KEPT_HAS_SETTLEMENT("kept_has_settlement"), KEPT_HAS_ALLOCATION("kept_has_allocation"),
}

data class RevertLineOutcome(val transactionId: Id, val decision: RevertDecision, val reason: String)

data class RevertPlan(
    val batchId: Id,
    /** اللي هيتمسح فعلًا. */
    val toDelete: List<Id>,
    /** اللي هيفضل وليه. */
    val toKeep: List<RevertLineOutcome>,
    val outcomes: List<RevertLineOutcome>,
    /** التراجع نظيف تمامًا (مفيش محتفظ بيه)؟ */
    val isClean: Boolean,
    /** عدد العمليات اللي الدفعة سجّلتها وقت الاستيراد. */
    val expectedCount: Int,
    /** عدد سجلات المصدر اللي اتلقت فعلًا للدفعة دي. */
    val recordsFound: Int,
    /** عمليات متسجلة وولا سجل مصدر — معرّفات تالفة غالبًا؛ التراجع بيترفض لحد ما تتصلح. */
    val blocked: Boolean,
)

data class RevertDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val batches: ImportBatchRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    val uow: UnitOfWork,
)

const val REVERT_BLOCKED_MESSAGE =
    "مقدرناش نلاقي عمليات الاستيراد ده. غالبًا معرّفاته اتحفظت ناقصة في نسخة قديمة. " +
        "من الإعدادات دوس «افحص البيانات القديمة» وصلّح، وبعدين جرّب التراجع تاني."

class RevertImportBatch(private val deps: RevertDeps) {
    /** سجل الاستيرادات، الأحدث الأول — للعرض بس. */
    suspend fun history(limit: Int = 50): List<ImportBatch> =
        deps.batches.listRecent(limit).sortedWith(compareByDescending { it.importedAt })

    /** بيحسب الأثر من غير أي كتابة — بيتعرض للمستخدم قبل التأكيد (spec/03). */
    suspend fun plan(batchId: Id): RevertPlan {
        val batch = deps.batches.findById(batchId) ?: throw IllegalStateException("دفعة غير موجودة: $batchId")
        if (batch.state == ImportBatchState.REVERTED) throw IllegalStateException("الدفعة دي متراجَع عنها قبل كده")

        val batchRecords = deps.sources.listByBatch(batchId)
        val expectedCount = batch.counts.imported
        val recordsFound = batchRecords.size
        val blocked = expectedCount > 0 && recordsFound == 0
        val txnIds = batchRecords.mapNotNull { it.transactionId }

        if (txnIds.isEmpty()) {
            return RevertPlan(batchId, emptyList(), emptyList(), emptyList(), isClean = true, expectedCount = expectedCount, recordsFound = recordsFound, blocked = blocked)
        }

        val allSources = deps.sources.listByTransactionIds(txnIds)
        val settlements = deps.settlements.listByTransactionIds(txnIds)
        val allocations = deps.allocations.listByTransactionIds(txnIds)
        val obligations = deps.obligations.listByTransactionIds(txnIds)

        val settledIds = settlements.map { it.transactionId }.toSet()
        val allocatedIds = allocations.map { it.transactionId }.toSet()
        val obligationOriginIds = obligations.mapNotNull { it.originTransactionId }.toSet()

        val sourceCountByTxn = mutableMapOf<Id, Int>()
        for (record in allSources) {
            val id = record.transactionId ?: continue
            sourceCountByTxn[id] = (sourceCountByTxn[id] ?: 0) + 1
        }

        val toDelete = mutableListOf<Id>()
        val toKeep = mutableListOf<RevertLineOutcome>()
        val outcomes = mutableListOf<RevertLineOutcome>()

        for (id in txnIds) {
            val outcome = when {
                id in settledIds -> RevertLineOutcome(id, RevertDecision.KEPT_HAS_SETTLEMENT, "العملية دي دخلت عليها تسوية بعد الاستيراد، فمش هتتحذف")
                id in allocatedIds || id in obligationOriginIds ->
                    RevertLineOutcome(id, RevertDecision.KEPT_HAS_ALLOCATION, "العملية دي متربطة بشخص (تخصيص أو التزام)، فمش هتتحذف")
                (sourceCountByTxn[id] ?: 0) > 1 ->
                    RevertLineOutcome(id, RevertDecision.KEPT_OTHER_SOURCE, "العملية دي ليها مصدر تاني غير الدفعة دي (رسالة مثلًا)، فمش هتتحذف")
                else -> {
                    toDelete += id
                    RevertLineOutcome(id, RevertDecision.DELETED, "الدفعة دي هي المصدر الوحيد ومفيش عليها تسويات")
                }
            }
            outcomes += outcome
            if (outcome.decision != RevertDecision.DELETED) toKeep += outcome
        }

        return RevertPlan(batchId, toDelete, toKeep, outcomes, isClean = toKeep.isEmpty(), expectedCount = expectedCount, recordsFound = recordsFound, blocked = blocked)
    }

    /** بينفّذ الخطة ذريًا. سجلات المصدر بتاعة الدفعة بتتمسح دايمًا. */
    suspend fun execute(batchId: Id): RevertPlan {
        val revertPlan = plan(batchId)
        if (revertPlan.blocked) throw IllegalStateException(REVERT_BLOCKED_MESSAGE)

        return deps.uow.run {
            if (revertPlan.toDelete.isNotEmpty()) deps.txns.deleteMany(revertPlan.toDelete)
            val batchRecords = deps.sources.listByBatch(batchId)
            deps.sources.deleteMany(batchRecords.map { it.id })
            deps.batches.updateState(batchId, ImportBatchState.REVERTED)
            revertPlan
        }
    }
}
