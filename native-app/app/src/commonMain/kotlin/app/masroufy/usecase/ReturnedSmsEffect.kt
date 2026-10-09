package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.OriginalCandidate
import app.masroufy.core.ReversalMatch
import app.masroufy.core.SmsKind
import app.masroufy.core.SourceRecord
import app.masroufy.core.Transaction
import app.masroufy.core.awaitsRefundAnswer
import app.masroufy.core.canReverse
import app.masroufy.core.cancelledOriginal
import app.masroufy.core.cancelledReturn
import app.masroufy.core.isStrongReference
import app.masroufy.core.matchReversal
import app.masroufy.core.pendingRefund
import app.masroufy.core.pendingReversalCheck
import app.masroufy.core.referenceTail
import app.masroufy.core.reversalWindowEnd
import app.masroufy.core.reversalWindowStart
import app.masroufy.core.smsReferenceOf
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * §77-D (قرار المالك 2026-10-09): رسالة «تم رد المبلغ» / «التحويل رجع» ([SmsKind.RETURNED]) وهي بتتسجل ⇒ بتدوّر على العملية الأصلية
 * برقمها المرجعي وتلغيها، ولو ما لقيتهاش ⇒ «استرداد» مقترح ويسأل. القواعد النقية في `core/Reversals.kt`.
 *
 * - `prepare` (جوه وحدة العمل): أصلية واحدة بنفس المحفظة والعملة والمبلغ والاتجاه العكسي وذيل المرجع، في الـ60 يوم اللي قبل الرجوع:
 *   مش مربوطة ونوعها مش مؤكد والمرجع قوي ⇒ اللي رجعت «تحويل داخلي» مؤكد ومعاها `reversalOfId` · مربوطة أو مؤكدة أو مرجع قصير أو
 *   الروابط مش معروفة ([ReturnedSmsDeps.links] = null) ⇒ «نلغي الاتنين؟» · غير كده ⇒ «استرداد» مقترح (النوع «غير محدد» — مش دخل).
 * - **الأصلية جت بعد رجوعها** (رسالة الرجوع اتسجلت، والكشف بالشراء اتسجل بعدها): سطر **صادر** بيدوّر على رجوع متخزن مستني «ده
 *   استرداد؟» بنفس الذيل والمبلغ والمحفظة في الـ60 يوم اللي بعده ⇒ الاتنين بيتلغوا (أو «نلغي الاتنين؟» لو مؤكد أو المرجع قصير).
 * - `afterCommit`: الطرف المتخزن بيتكمّل (الأصلية ومعاها `reversedById`، أو اللي رجعت ومعاها `reversalOfId`). لو وقع هنا ⇒
 *   `RepairReversals` بيكمّل (ومرتين = نفس النتيجة).
 * - الأصلية في **نفس الدفعة** (الشراء ورجوعه وصلوا مع بعض) ⇒ الاتنين بيتلغوا هنا على طول.
 */
data class ReturnedSmsDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    /** null = الروابط مش معروفة (الخط اتبنى من غير `ReversalLinkDeps`) ⇒ عمرها ما بتلغي لوحدها، بتسأل بس. */
    val links: ReversalLinkDeps?,
)

/** ذيول المراجع: من وصف الرسالة المتخزن (محجوب «••••7781») ومن مرجع الكشف (مش `SMS:`). */
internal fun referenceTailsOf(t: Transaction, statementReferences: List<String?>): Set<String> {
    val tails = LinkedHashSet<String>()
    referenceTail(smsReferenceOf(t.rawDescription ?: ""))?.let(tails::add)
    for (ref in statementReferences) if (ref != null && !ref.startsWith("SMS:")) referenceTail(ref)?.let(tails::add)
    return tails
}

/** المرشحين المتخزنين لـ[ret] (نفس المحفظة والعملة والمبلغ والاتجاه العكسي في النافذة) بمراجعهم — من غير روابطهم (بتتقري للي اتلاقت بس). */
internal suspend fun storedOriginals(txns: TransactionRepository, sources: SourceRecordRepository, ret: Transaction): List<OriginalCandidate> {
    val pool = txns.listByDateRange(reversalWindowStart(ret.occurredAt), ret.occurredAt).filter { canReverse(ret, it) }
    if (pool.isEmpty()) return emptyList()
    val records: Map<Id?, List<SourceRecord>> = sources.listByTransactionIds(pool.map { it.id }).groupBy { it.transactionId }
    return pool.map { t -> OriginalCandidate(t, referenceTailsOf(t, records[t.id].orEmpty().map { it.sourceReference })) }
}

class ReturnedSmsEffect(private val deps: ReturnedSmsDeps) : RecordEffect {
    override suspend fun prepare(ctx: RecordContext) {
        val reader = deps.links?.let(::ReversalLinkReader)
        // أصلية اتاخدت في نفس الدفعة (رسالتين رجوع لنفس العملية) ما تتاخدش تاني
        val claimed = HashSet<Id>()
        for (line in ctx.lines) {
            val ret = line.transaction
            // قرار متخزن قبل كده (زون التحويلات «حسابي التاني» مثلًا) بيأكد النوع ⇒ ما بيتلمسش
            if (line.sms?.kind != SmsKind.RETURNED || ret.observedDirection != Direction.IN || ret.economicKindConfirmed) continue
            val inBatch = ctx.lines.filter { it !== line && canReverse(ret, it.transaction) }
                .map { OriginalCandidate(it.transaction, referenceTailsOf(it.transaction, listOf(it.line.row.reference))) }
            val candidates = (storedOriginals(deps.txns, deps.sources, ret) + inBatch).filter { it.transaction.id !in claimed }
            val reference = line.sms.bankReference
            val match = matchReversal(ret, referenceTail(reference), candidates, isStrongReference(reference))
            val inBatchOriginal = (match as? ReversalMatch.Cancel)?.let { m -> ctx.lines.firstOrNull { it.transaction.id == m.original.id } }
            // الأصلية المتخزنة: الربط بيتقري ليها هي بس — مربوطة أو روابطها مش معروفة ⇒ تتسأل
            val decided = if (match is ReversalMatch.Cancel && inBatchOriginal == null && (reader == null || reader.linksOf(match.original.id).isNotEmpty())) {
                ReversalMatch.Check(match.original)
            } else {
                match
            }
            when (decided) {
                is ReversalMatch.Cancel -> {
                    claimed += decided.original.id
                    line.transaction = cancelledReturn(ret, decided.original.id, ctx.nowIso)
                    // الأصلية في نفس الدفعة ⇒ تتلغي هنا؛ المتخزنة ⇒ بعد الحفظ
                    inBatchOriginal?.let { it.transaction = cancelledOriginal(it.transaction, ret.id, ctx.nowIso) }
                }
                is ReversalMatch.Check -> line.transaction = pendingReversalCheck(ret, ctx.nowIso)
                is ReversalMatch.NotFound -> line.transaction = pendingRefund(ret, ctx.nowIso)
            }
        }
        originalsAfterTheirReturn(ctx)
    }

    /**
     * السطور الصادرة اللي اتسجلت **بعد** رجوعها (§77-D — كانت بتفضل «استرداد» مقترح للأبد): رجوع متخزن مستني «ده استرداد؟» بنفس الذيل
     * والمبلغ والمحفظة، والصادر قبله بـ60 يوم بالكتير ⇒ واحد لواحد بس (أكتر من واحد ⇒ السؤال بيفضل زي ما هو).
     * قراية واحدة للدفعة كلها (من أقدم سطر صادر ليه ذيل لحد 60 يوم بعد أحدثه).
     */
    private suspend fun originalsAfterTheirReturn(ctx: RecordContext) {
        val outs = ctx.lines.filter { it.transaction.observedDirection == Direction.OUT && it.transaction.reversedById == null && it.transaction.reversalOfId == null }
            .map { it to referenceTailsOf(it.transaction, listOf(it.line.row.reference)) }
            .filter { it.second.isNotEmpty() }
        if (outs.isEmpty()) return
        val batchIds = ctx.lines.map { it.transaction.id }.toSet()
        val from = outs.minOf { it.first.transaction.occurredAt }
        val to = reversalWindowEnd(outs.maxOf { it.first.transaction.occurredAt })
        val waiting = deps.txns.listByDateRange(from, to)
            .filter { it.id !in batchIds && it.observedDirection == Direction.IN && it.reversalOfId == null && awaitsRefundAnswer(it) }
            .mapNotNull { r -> smsReferenceOf(r.rawDescription ?: "")?.let { ref -> referenceTail(ref)?.let { tail -> Triple(r, tail, isStrongReference(ref)) } } }
        if (waiting.isEmpty()) return
        fun fits(ret: Transaction, tail: String, out: Pair<RecordedLine, Set<String>>) = tail in out.second && canReverse(ret, out.first.transaction)
        for ((ret, tail, strong) in waiting) {
            val lines = outs.filter { fits(ret, tail, it) }
            if (lines.size != 1) continue
            val line = lines.single().first
            // نفس السطر الصادر ممكن يناسب رجوعين ⇒ ولا واحد (السؤال بيفضل)
            if (waiting.count { (r, t, _) -> fits(r, t, lines.single()) } != 1) continue
            val original = line.transaction
            if (original.economicKindConfirmed || !strong) {
                // مؤكد بقرار متخزن (زون التحويلات) أو المرجع قصير ⇒ «نلغي الاتنين؟» على اللي رجعت (جوه وحدة العمل)
                deps.txns.update(ret.id, TransactionPatch(suggestedKind = EconomicKind.INTERNAL_TRANSFER, updatedAt = ctx.nowIso))
            } else {
                line.transaction = cancelledOriginal(original, ret.id, ctx.nowIso)
            }
        }
    }

    override suspend fun afterCommit(ctx: RecordContext) {
        val inBatch = ctx.lines.map { it.transaction.id }.toSet()
        for (line in ctx.lines) {
            val t = line.transaction
            t.reversalOfId?.let { originalId -> if (originalId !in inBatch) finishReversal(deps.txns, t.id, originalId, ctx.nowIso) }
            t.reversedById?.let { returnId -> if (returnId !in inBatch) finishReturn(deps.txns, t.id, returnId, ctx.nowIso) }
        }
    }
}
