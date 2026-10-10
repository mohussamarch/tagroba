package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.awaitsRefundAnswer
import app.masroufy.core.awaitsReversalAnswer
import app.masroufy.core.cancelledOriginal
import app.masroufy.core.restoredKindOf
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * كتابات زوج «العملية اللي رجعت» (§77-D) في مكان واحد — الأثر وقت التسجيل · إجابات المالك · التصليح · التراجع بيعدّوا كلهم من هنا
 * عشان القاعدة تبقى واحدة (والقواعد النقية في `core/Reversals.kt`).
 */

/** الأصلية اتلغت مع [returnId] (`cancelledOriginal`): نوع المالك المؤكد بيتحفظ في `kindBeforeReversal`. */
internal fun cancelledOriginalPatch(original: Transaction, returnId: Id, now: String): TransactionPatch {
    val c = cancelledOriginal(original, returnId, now)
    return TransactionPatch(
        economicKind = c.economicKind, economicKindConfirmed = true, reviewState = c.reviewState, reversedById = returnId,
        kindBeforeReversal = c.kindBeforeReversal, clearKindBeforeReversal = c.kindBeforeReversal == null, clearSuggestedKind = true, updatedAt = now,
    )
}

/** اللي رجعت اتلغت مع [originalId]. */
internal fun cancelledReturnPatch(originalId: Id, now: String) = TransactionPatch(
    economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED,
    reversalOfId = originalId, clearSuggestedKind = true, updatedAt = now,
)

/** الرجوع اتفك ربطه ⇒ «استرداد» مقترح مستني تأكيد (النوع «غير محدد» — مش دخل). */
internal fun reopenedReturnPatch(now: String) = TransactionPatch(
    economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW,
    suggestedKind = EconomicKind.REFUND_RECEIVED, clearReversalOfId = true, updatedAt = now,
)

/** الرجوع يرجع سؤال «نلغي الاتنين؟» (اقتراح «تحويل داخلي» والنوع «غير محدد»). */
internal fun checkAgainPatch(now: String) = TransactionPatch(
    economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false, reviewState = ReviewState.NEEDS_REVIEW,
    suggestedKind = EconomicKind.INTERNAL_TRANSFER, clearReversalOfId = true, updatedAt = now,
)

/**
 * الأصلية اتفك ربطها ⇒ ترجع **زي ما كانت** (`restoredKindOf`): النوع اللي المالك كان أكده لو اتحفظ (مؤكد)، وإلا «غير محددة» (الإلغاء
 * لوحده بياخد النوع غير المؤكد بس) وحالة مراجعة من تصنيفها. قبل كده كانت بترجع «غير محددة» دايمًا — سلفة مؤكدة رجعت «شراء» تقديري.
 */
internal fun restoredOriginalPatch(original: Transaction, now: String): TransactionPatch {
    val r = restoredKindOf(original)
    return TransactionPatch(
        economicKind = r.kind, economicKindConfirmed = r.confirmed, reviewState = r.reviewState,
        clearReversedById = true, clearKindBeforeReversal = true, clearSuggestedKind = true, updatedAt = now,
    )
}

/**
 * بيكمّل الزوج من ناحية الأصلية: «تحويل داخلي» مؤكد ومعاها `reversedById`. مرة واحدة بس: لو الأصلية ملغية خلاص (مع نفس العملية أو
 * غيرها) ⇒ ولا حاجة. بيرجّع true لو كتب.
 */
internal suspend fun finishReversal(txns: TransactionRepository, returnId: Id, originalId: Id, now: String): Boolean {
    val original = txns.findByIds(listOf(originalId)).singleOrNull() ?: return false
    if (original.reversedById != null || original.reversalOfId != null) return false
    txns.update(originalId, cancelledOriginalPatch(original, returnId, now))
    return true
}

/**
 * بيكمّل الزوج من ناحية **اللي رجعت** (الأصلية اتسجلت بعدها واتلغت معاها — كشف بعد رسالة): اللي رجعت لسه مستنية سؤالها ومش مربوطة،
 * والأصلية بتشاور عليها ⇒ «تحويل داخلي» مؤكد ومعاها `reversalOfId`. غير كده ⇒ ولا حاجة (`RepairReversals` بيرجّع الأصلية).
 */
internal suspend fun finishReturn(txns: TransactionRepository, originalId: Id, returnId: Id, now: String): Boolean {
    val ret = txns.findByIds(listOf(returnId)).singleOrNull() ?: return false
    val original = txns.findByIds(listOf(originalId)).singleOrNull() ?: return false
    if (ret.reversalOfId != null || !(awaitsRefundAnswer(ret) || awaitsReversalAnswer(ret)) || original.reversedById != returnId) return false
    txns.update(returnId, cancelledReturnPatch(originalId, now))
    return true
}
