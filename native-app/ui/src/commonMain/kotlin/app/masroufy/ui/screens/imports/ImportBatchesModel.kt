package app.masroufy.ui.screens.imports

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportSourceType
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.jsTrim
import app.masroufy.usecase.RevertDecision
import app.masroufy.usecase.RevertPlan

/** حالة «دفعات الاستيراد»: المعلّق (اتقطع في النص) لوحده فوق، والباقي (مسجّلة · أُرجعت) الأحدث الأول. */
data class ImportBatchesUi(val batches: List<ImportBatch>, val staged: List<ImportBatch>) {
    val isEmpty: Boolean get() = batches.isEmpty() && staged.isEmpty()
}

fun importBatchesUi(history: List<ImportBatch>, staged: List<ImportBatch>): ImportBatchesUi =
    ImportBatchesUi(history.filter { it.state != ImportBatchState.STAGED }.sortedByDescending { it.importedAt }, staged)

/** مصدر الدفعة بكلمتين (رسائل البنك · كشف الراجحي · كشف QNB مصر · ملف CSV). */
fun batchSourceLabel(type: ImportSourceType): TextKey = when (type) {
    ImportSourceType.SMS -> TextKey.IMPORT_BATCHES_SRC_SMS
    ImportSourceType.PDF_ALRAJHI -> TextKey.IMPORT_BATCHES_SRC_ALRAJHI
    ImportSourceType.PDF_QNB -> TextKey.IMPORT_BATCHES_SRC_QNB
    ImportSourceType.CSV_PREVIEW, ImportSourceType.CSV_LEGACY -> TextKey.IMPORT_BATCHES_SRC_CSV
}

/** شارات العدّ (أُضيف · مكرر · شبيه · تعارض · غير صالح) — اللي مش صفر بس. */
fun batchCounts(b: ImportBatch): List<Pair<TextKey, Int>> = listOf(
    TextKey.IMPORT_BATCHES_ADDED to b.counts.imported,
    TextKey.IMPORT_REVIEW_DUP to b.counts.duplicates,
    TextKey.IMPORT_REVIEW_SIMILAR to b.counts.similar,
    TextKey.IMPORT_REVIEW_CONFLICT to b.counts.conflicts,
    TextKey.IMPORT_REVIEW_INVALID to b.counts.invalid,
).filter { it.second > 0 }

/** عملية في معاينة الإرجاع (الاسم والمبلغ من تفاصيلها — null لو ما اتقرتش). */
data class RevertTxnUi(val id: Id, val name: String?, val amountMinor: Halalas?, val currency: Currency?, val direction: Direction?)

data class KeptUi(val txn: RevertTxnUi, val decision: RevertDecision, val reason: String)

/** معاينة «إرجاع الدفعة» (`RevertImportBatch.plan`): هيتمسح كام · اللي هيفضل وليه · أول [SHOW] من اللي هيتمسح · الروابط اللي هتتشال. */
data class RevertUi(
    val deleteCount: Int,
    val kept: List<KeptUi>,
    val deleted: List<RevertTxnUi>,
    val more: Int,
    val unlink: Int,
    val blocked: Boolean,
) {
    val clean: Boolean get() = kept.isEmpty()

    companion object {
        const val SHOW = 4
    }
}

fun revertUi(plan: RevertPlan, details: Map<Id, Transaction>): RevertUi {
    fun txn(id: Id) = details[id].let { t -> RevertTxnUi(id, t?.let { jsTrim(it.rawMerchantName ?: it.rawDescription ?: "") }, t?.amountMinor, t?.currency, t?.observedDirection) }
    val shown = plan.toDelete.take(RevertUi.SHOW)
    return RevertUi(
        deleteCount = plan.toDelete.size,
        kept = plan.toKeep.map { KeptUi(txn(it.transactionId), it.decision, it.reason) },
        deleted = shown.map(::txn),
        more = plan.toDelete.size - shown.size,
        unlink = plan.unlinkCount,
        blocked = plan.blocked,
    )
}

/** اللي محتاج تفاصيله يتقري: اللي هيفضل كله + أول [RevertUi.SHOW] من اللي هيتمسح. */
fun revertDetailIds(plan: RevertPlan): List<Id> = (plan.toKeep.map { it.transactionId } + plan.toDelete.take(RevertUi.SHOW)).distinct()

/** سبب البقاء بكلمة (الشارة) — الجملة الكاملة من المنطق نفسه (`REVERT_KEPT_*`). */
fun keptLabel(decision: RevertDecision): TextKey = when (decision) {
    RevertDecision.KEPT_HAS_SETTLEMENT -> TextKey.REVERT_SHEET_KEEP_SETTLE
    RevertDecision.KEPT_HAS_ALLOCATION -> TextKey.REVERT_SHEET_KEEP_ALLOC
    RevertDecision.KEPT_HAS_DUE -> TextKey.REVERT_SHEET_KEEP_DUE
    RevertDecision.KEPT_HAS_INVESTMENT -> TextKey.REVERT_SHEET_KEEP_INVEST
    RevertDecision.KEPT_HAS_GIFT -> TextKey.REVERT_SHEET_KEEP_GIFT
    RevertDecision.KEPT_OTHER_SOURCE, RevertDecision.DELETED -> TextKey.REVERT_SHEET_KEEP_SOURCE
}
