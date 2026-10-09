package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.OriginalCandidate
import app.masroufy.core.ReversalMatch
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.canReverse
import app.masroufy.core.cancelledOriginal
import app.masroufy.core.cancelledReturn
import app.masroufy.core.matchReversal
import app.masroufy.core.pendingRefund
import app.masroufy.core.pendingReversalCheck
import app.masroufy.core.referenceTail
import app.masroufy.core.reversalWindowStart
import app.masroufy.core.smsReferenceOf
import app.masroufy.port.AllocationRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * §77-D (قرار المالك 2026-10-09): رسالة «تم رد المبلغ» / «التحويل رجع» ([SmsKind.RETURNED]) وهي بتتسجل ⇒ بتدوّر على العملية الأصلية
 * برقمها المرجعي وتلغيها، ولو ما لقيتهاش ⇒ «استرداد» مقترح ويسأل. القواعد النقية في `core/Reversals.kt`.
 *
 * - `prepare` (جوه وحدة العمل): أصلية واحدة بنفس المحفظة والعملة والمبلغ والاتجاه العكسي وذيل المرجع، في الـ60 يوم اللي قبل الرجوع:
 *   مش مربوطة ونوعها مش مؤكد ⇒ اللي رجعت «تحويل داخلي» مؤكد ومعاها `reversalOfId` · مربوطة أو مؤكدة ⇒ «نلغي الاتنين؟» (اقتراح «تحويل
 *   داخلي») · غير كده (مفيش مرجع · ما لقيناش · مبلغ جزئي · أكتر من واحدة) ⇒ «استرداد» مقترح (النوع «غير محدد» — مش دخل).
 * - `afterCommit`: الأصلية المتخزنة بتتلغي ومعاها `reversedById`. لو وقع هنا ⇒ `RepairReversals` بيكمّل (ومرتين = نفس النتيجة).
 * - الأصلية في **نفس الدفعة** (الشراء ورجوعه وصلوا مع بعض) ⇒ الاتنين بيتلغوا هنا على طول.
 */

/** كل اللي بيشاور على عملية بمعرّفها (§77-D أمان): العملية المربوطة ما بتتلغيش لوحدها. نفس روابط التراجع + الديون. */
data class ReversalLinkDeps(
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val links: RevertLinkDeps,
)

/** اللي مربوط من [ids]: تخصيص · أصل دين · تسوية · جمعية · قسط · مبلغ تمويل · زكاة · أصل · حدث · مشروع · رجل تحويل بين البلاد. */
internal suspend fun ReversalLinkDeps.linkedAmong(ids: List<Id>): Set<Id> {
    if (ids.isEmpty()) return emptySet()
    val wanted = ids.toSet()
    val l = links
    val found = HashSet<Id>()
    allocations.listByTransactionIds(ids).mapTo(found) { it.transactionId }
    obligations.listByTransactionIds(ids).mapNotNullTo(found) { it.originTransactionId }
    settlements.listByTransactionIds(ids).mapTo(found) { it.transactionId }
    l.roscaEntries.listByTransactionIds(ids).mapTo(found) { it.transactionId }
    l.installmentPayments.listByTransactionIds(ids).mapTo(found) { it.transactionId }
    l.installmentPlans.listAll().mapNotNullTo(found) { it.receivedTransactionId }
    l.zakatPayments.listByTransactionIds(ids).mapNotNullTo(found) { it.transactionId }
    l.assetLots.listAll().mapNotNullTo(found) { it.transactionId }
    l.assetSales.listAll().mapNotNullTo(found) { it.transactionId }
    l.eventLinks.listByTransactionIds(ids).mapTo(found) { it.transactionId }
    l.projectLinks.listAll().mapTo(found) { it.transactionId }
    l.spaceLegs?.legsAmong(ids)?.let { found.addAll(it) }
    return found.filterTo(HashSet()) { it in wanted }
}

/** ذيول المراجع: من وصف الرسالة المتخزن (محجوب «••••7781») ومن مرجع الكشف (مش `SMS:`). */
internal fun referenceTailsOf(t: Transaction, statementReferences: List<String?>): Set<String> {
    val tails = LinkedHashSet<String>()
    referenceTail(smsReferenceOf(t.rawDescription ?: ""))?.let(tails::add)
    for (ref in statementReferences) if (ref != null && !ref.startsWith("SMS:")) referenceTail(ref)?.let(tails::add)
    return tails
}

/** المرشحين المتخزنين لـ[ret] (نفس المحفظة والعملة والمبلغ والاتجاه العكسي في النافذة) بمراجعهم وربطهم. */
internal suspend fun storedOriginals(
    txns: TransactionRepository, sources: SourceRecordRepository, links: ReversalLinkDeps, ret: Transaction,
): List<OriginalCandidate> {
    val pool = txns.listByDateRange(reversalWindowStart(ret.occurredAt), ret.occurredAt).filter { canReverse(ret, it) }
    if (pool.isEmpty()) return emptyList()
    val records: Map<Id?, List<SourceRecord>> = sources.listByTransactionIds(pool.map { it.id }).groupBy { it.transactionId }
    val linked = links.linkedAmong(pool.map { it.id })
    return pool.map { t -> OriginalCandidate(t, referenceTailsOf(t, records[t.id].orEmpty().map { it.sourceReference }), t.id in linked) }
}

/**
 * بيكمّل الزوج من ناحية الأصلية: «تحويل داخلي» مؤكد ومعاها `reversedById`. مرة واحدة بس: لو الأصلية ملغية مع نفس العملية خلاص أو
 * مربوطة بعملية تانية ⇒ ولا حاجة. بيرجّع true لو كتب.
 */
internal suspend fun finishReversal(txns: TransactionRepository, returnId: Id, originalId: Id, now: String): Boolean {
    val original = txns.findByIds(listOf(originalId)).singleOrNull() ?: return false
    if (original.reversedById != null || original.reversalOfId != null) return false
    txns.update(
        originalId,
        TransactionPatch(
            economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true, reviewState = ReviewState.CONFIRMED,
            reversedById = returnId, clearSuggestedKind = true, updatedAt = now,
        ),
    )
    return true
}

data class ReturnedSmsDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val links: ReversalLinkDeps,
)

class ReturnedSmsEffect(private val deps: ReturnedSmsDeps) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        // أصلية اتاخدت في نفس الدفعة (رسالتين رجوع لنفس العملية) ما تتاخدش تاني
        val claimed = HashSet<Id>()
        for (line in ctx.lines) {
            val ret = line.transaction
            // قرار متخزن قبل كده (زون التحويلات «حسابي التاني» مثلًا) بيأكد النوع ⇒ ما بيتلمسش
            if (line.sms?.kind != SmsKind.RETURNED || ret.observedDirection != Direction.IN || ret.economicKindConfirmed) continue
            val inBatch = ctx.lines.filter { it !== line && canReverse(ret, it.transaction) }
                .map { OriginalCandidate(it.transaction, referenceTailsOf(it.transaction, listOf(it.line.row.reference))) }
            val candidates = (storedOriginals(deps.txns, deps.sources, deps.links, ret) + inBatch).filter { it.transaction.id !in claimed }
            when (val match = matchReversal(ret, referenceTail(line.sms.bankReference), candidates)) {
                is ReversalMatch.Cancel -> {
                    claimed += match.original.id
                    line.transaction = cancelledReturn(ret, match.original.id, ctx.nowIso)
                    // الأصلية في نفس الدفعة ⇒ تتلغي هنا؛ المتخزنة ⇒ بعد الحفظ
                    ctx.lines.firstOrNull { it.transaction.id == match.original.id }?.let {
                        it.transaction = cancelledOriginal(it.transaction, ret.id, ctx.nowIso)
                    }
                }
                is ReversalMatch.Check -> line.transaction = pendingReversalCheck(ret, ctx.nowIso)
                is ReversalMatch.NotFound -> line.transaction = pendingRefund(ret, ctx.nowIso)
            }
        }
    }

    override suspend fun afterCommit(ctx: RecordContext) {
        val inBatch = ctx.lines.map { it.transaction.id }.toSet()
        for (line in ctx.lines) {
            val originalId = line.transaction.reversalOfId ?: continue
            if (originalId !in inBatch) finishReversal(deps.txns, line.transaction.id, originalId, ctx.nowIso)
        }
    }
}
