package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.awaitsRefundAnswer
import app.masroufy.core.awaitsReversalAnswer
import app.masroufy.core.canReverse
import app.masroufy.core.referenceTail
import app.masroufy.core.smsReferenceOf
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * إجابات أسئلة الفلوس اللي رجعت (§75-6 · §77-D) — اللي الشاشة هتناديه (الشاشة نفسها ما اتبنتش):
 * - «ده استرداد؟» (`REFUND_CONFIRM`): [confirmRefund] ⇒ «استرداد» مؤكد (بينقّص المصروف — §42) · [rejectRefund] ⇒ الاقتراح بيتشال
 *   والعملية بتفضل «غير محددة» (مسار التصنيف العادي).
 * - «نلغي الاتنين؟» (`REVERSAL_CHECK` — الأصلية مربوطة أو نوعها مؤكد، فما اتلغتش لوحدها): [confirmReversal] ⇒ الزوج بيتلغي (تحويل داخلي
 *   مؤكد مربوط) · [declineReversal] ⇒ يرجع «ده استرداد؟».
 * [reversalCandidates] = الأصليات اللي ممكن تتلغي مع الرجوع (نفس المرجع الأول) — للشاشة.
 */
data class RefundAsksDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val links: ReversalLinkDeps,
    val clock: Clock,
)

class RefundAsks(private val deps: RefundAsksDeps) {
    private suspend fun find(id: Id): Transaction =
        deps.txns.findByIds(listOf(id)).singleOrNull() ?: throw IllegalArgumentException(uiText(TextKey.TXN_NOT_FOUND))

    /** الرجوع مستني سؤال من الاتنين (وارد). */
    private suspend fun pendingReturn(id: Id): Transaction {
        val t = find(id)
        if (t.observedDirection != Direction.IN || !(awaitsRefundAnswer(t) || awaitsReversalAnswer(t))) {
            throw IllegalStateException(uiText(TextKey.RETURNS_REFUND_NOT_PENDING))
        }
        return t
    }

    /** «أيوه استرداد»: «استرداد» مؤكد والاقتراح بيتشال. */
    suspend fun confirmRefund(txnId: Id): Transaction {
        pendingReturn(txnId)
        val now = deps.clock.nowIso()
        deps.txns.update(txnId, TransactionPatch(economicKind = EconomicKind.REFUND_RECEIVED, economicKindConfirmed = true, clearSuggestedKind = true, updatedAt = now))
        return find(txnId)
    }

    /** «لأ»: الاقتراح بيتشال والعملية بتفضل «غير محددة». */
    suspend fun rejectRefund(txnId: Id): Transaction {
        pendingReturn(txnId)
        deps.txns.update(txnId, TransactionPatch(clearSuggestedKind = true, updatedAt = deps.clock.nowIso()))
        return find(txnId)
    }

    /** الأصليات اللي ممكن تتلغي مع [returnId]: اللي ليها نفس ذيل المرجع الأول، وبعدها الباقي (نفس المبلغ والمحفظة والاتجاه العكسي). */
    suspend fun reversalCandidates(returnId: Id): List<Transaction> {
        val ret = find(returnId)
        val tail = referenceTail(smsReferenceOf(ret.rawDescription ?: ""))
        val all = storedOriginals(deps.txns, deps.sources, deps.links, ret)
        return all.sortedBy { if (tail != null && tail in it.referenceTails) 0 else 1 }.map { it.transaction }
    }

    /**
     * «أيوه نلغيهم»: الرجوع والأصلية بيبقوا «تحويل داخلي» مؤكد مربوطين ببعض. الأصلية لازم تنفع ([canReverse] — نفس المحفظة والعملة
     * والمبلغ والاتجاه العكسي وقبل الرجوع بـ60 يوم بالكتير ومش ملغية). الروابط اللي على الأصلية (دين · حدث …) **بتفضل** — الشاشة تعرضها.
     * الكتابة: الرجوع الأول وبعده الأصلية — لو وقع في النص `RepairReversals` بيكمّل.
     */
    suspend fun confirmReversal(returnId: Id, originalId: Id): Pair<Transaction, Transaction> {
        val ret = pendingReturn(returnId)
        val original = find(originalId)
        if (!canReverse(ret, original)) throw IllegalStateException(uiText(TextKey.RETURNS_REVERSAL_NOT_POSSIBLE))
        val now = deps.clock.nowIso()
        deps.txns.update(
            returnId,
            TransactionPatch(
                economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED,
                reversalOfId = originalId, clearSuggestedKind = true, updatedAt = now,
            ),
        )
        finishReversal(deps.txns, returnId, originalId, now)
        return find(returnId) to find(originalId)
    }

    /** «لأ، مش هي»: السؤال بيرجع «ده استرداد؟». */
    suspend fun declineReversal(returnId: Id): Transaction {
        val ret = pendingReturn(returnId)
        if (!awaitsReversalAnswer(ret)) throw IllegalStateException(uiText(TextKey.RETURNS_REFUND_NOT_PENDING))
        deps.txns.update(returnId, TransactionPatch(suggestedKind = EconomicKind.REFUND_RECEIVED, updatedAt = deps.clock.nowIso()))
        return find(returnId)
    }
}
