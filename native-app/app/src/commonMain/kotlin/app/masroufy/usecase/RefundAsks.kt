package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
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
import app.masroufy.port.UnitOfWork

/**
 * إجابات أسئلة الفلوس اللي رجعت (§77-D — `AskKind.REVERSAL_CHECK`) — اللي الشاشة هتناديه (الشاشة نفسها ما اتبنتش). العملية عليها
 * اقتراح ([Transaction.suggestedKind]) والشاشة بتعرض السؤال بحسبه:
 * - «ده استرداد؟» (`REFUND_RECEIVED` — ما لقيناش الأصلية): [confirmRefund] ⇒ «استرداد» مؤكد (بينقّص المصروف — §42) · [rejectRefund] ⇒
 *   الاقتراح بيتشال والعملية بتفضل «غير محددة» (مسار التصنيف العادي) · أو [confirmReversal] مع أصلية من [reversalCandidates].
 * - «نلغي الاتنين؟» (`INTERNAL_TRANSFER` — الأصلية مربوطة أو مؤكدة أو المرجع قصير): [confirmReversal] ⇒ الزوج بيتلغي · [declineReversal] ⇒
 *   يرجع «ده استرداد؟».
 */
data class RefundAsksDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val links: ReversalLinkDeps,
    val clock: Clock,
    /** الكتابتين بتوع «أيوه نلغيهم» مع بعض. null = من غير (والترتيب بيخلي التصليح يكمّل لو وقع في النص). */
    val uow: UnitOfWork? = null,
)

/** نتيجة «أيوه نلغيهم». */
sealed interface ReversalAnswer {
    /** الاتنين اتلغوا ومربوطين ببعض. */
    data class Cancelled(val ret: Transaction, val original: Transaction) : ReversalAnswer

    /**
     * الأصلية **مربوطة** بحاجة ([links] — دين · تخصيص · تسوية · جمعية · قسط · زكاة · أصل · حدث · مشروع · تحويل بين البلاد) ⇒ ما اتلغاش حاجة:
     * الشاشة تقول للمالك يفك الربط الأول (الشخص كان هيفضل «عليه» المبلغ كله والحدث كان هيعد عملية اتلغت). ولا كتابة حصلت.
     */
    data class Linked(val links: Set<ReversalLink>) : ReversalAnswer
}

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
        val all = storedOriginals(deps.txns, deps.sources, ret)
        return all.sortedBy { if (tail != null && tail in it.referenceTails) 0 else 1 }.map { it.transaction }
    }

    /**
     * «أيوه نلغيهم»: الرجوع والأصلية بيبقوا «تحويل داخلي» مؤكد مربوطين ببعض، والنوع اللي المالك كان أكده على الأصلية بيتحفظ (التراجع
     * بيرجّعه). الأصلية لازم تنفع ([canReverse]) ومش مربوطة بحاجة (وإلا [ReversalAnswer.Linked] من غير ولا كتابة).
     * الكتابة: **الأصلية الأول** وبعدها الرجوع (جوه وحدة عمل لو موجودة) — لو وقع في النص الأصلية بتشاور على رجوع لسه مستني سؤاله،
     * و`RepairReversals` بيكمّله (إجابة المالك ما بتضيعش).
     */
    suspend fun confirmReversal(returnId: Id, originalId: Id): ReversalAnswer {
        val ret = pendingReturn(returnId)
        val original = find(originalId)
        if (!canReverse(ret, original)) throw IllegalStateException(uiText(TextKey.RETURNS_REVERSAL_NOT_POSSIBLE))
        val links = ReversalLinkReader(deps.links).linksOf(originalId)
        if (links.isNotEmpty()) return ReversalAnswer.Linked(links)
        val now = deps.clock.nowIso()
        val write: suspend () -> Unit = {
            deps.txns.update(originalId, cancelledOriginalPatch(original, returnId, now))
            deps.txns.update(returnId, cancelledReturnPatch(originalId, now))
        }
        val uow = deps.uow
        if (uow != null) uow.run { write() } else write()
        return ReversalAnswer.Cancelled(find(returnId), find(originalId))
    }

    /** «لأ، مش هي»: السؤال بيرجع «ده استرداد؟». */
    suspend fun declineReversal(returnId: Id): Transaction {
        val ret = pendingReturn(returnId)
        if (!awaitsReversalAnswer(ret)) throw IllegalStateException(uiText(TextKey.RETURNS_REFUND_NOT_PENDING))
        deps.txns.update(returnId, TransactionPatch(suggestedKind = EconomicKind.REFUND_RECEIVED, updatedAt = deps.clock.nowIso()))
        return find(returnId)
    }
}
