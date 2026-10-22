package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.LinkRefusal
import app.masroufy.core.Transaction
import app.masroufy.core.canHaveLinkedInflows
import app.masroufy.core.checkLink
import app.masroufy.core.inflowRefusal
import app.masroufy.core.encodeLinkedSplit
import app.masroufy.core.isLinkedInflow
import app.masroufy.core.isLinkedTo
import app.masroufy.core.linkedShareTo
import app.masroufy.core.linkedSharesOf
import app.masroufy.core.linkRoomOf
import app.masroufy.core.linkWindowEnd
import app.masroufy.core.linkWindowStart
import app.masroufy.core.netAfterLinks
import app.masroufy.core.orderByCloseness
import app.masroufy.core.sumMoney
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

data class LinkInflowsDeps(
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val settlements: SettlementRepository,
    val obligations: ObligationRepository,
    val uow: UnitOfWork,
    val clock: Clock,
)

/** «عمليات تابعة» على عملية واحدة — اللي الشاشة بتعرضه (مفيش حساب في الشاشة). */
sealed interface LinkedInflowsView {
    /**
     * مصروف: [netMinor] الصافي (المبلغ − التابعة) · [linked] التابعة · [roomMinor] اللي فاضل يتخصم (المبلغ − نصيب الآخرين كدين − التابعة)
     * · [candidates] فلوس داخلة تنفع (نفس العملة، مش تابعة لحاجة، مش مربوطة بدين أو تسوية) — الأقرب لتاريخه الأول.
     */
    data class Expense(
        val expense: Transaction,
        val netMinor: Halalas,
        val linked: List<Transaction>,
        val roomMinor: Halalas,
        val candidates: List<Transaction>,
    ) : LinkedInflowsView

    /** فلوس داخلة تابعة لمصروف — [expense] = null لو المصروف اتمسح (الفك بيرجّعها زي ما كانت). */
    data class Inflow(val inflow: Transaction, val expense: Transaction?) : LinkedInflowsView

    /** العملية ما ينفعش يتربط بيها حاجة (دخل عادي · تحويل بين محافظك · ملغية …). */
    data object None : LinkedInflowsView
}

/** r041 A11 — اللي اتفك ([inflowId] كانت تابعة لـ[expenseId]) عشان «تراجع» يرجّعه. */
data class UnlinkUndo(val inflowId: Id, val expenseId: Id, val split: Map<Id, Halalas>? = null)

/**
 * §6.5-14 «عمليات تابعة» — الربط والفك. **الرصيد ما بيتغيرش**: النوع والمبلغ والمحفظة ما بيتلمسوش، حقلين اختياريين بس
 * (`linkedExpenseId` على الداخلة · `linkedInflowMinor` على المصروف = مجموع التابعة، بيتحسب من جديد من النافذة مع كل كتابة).
 * الفك بيشيل الحقلين ⇒ كل حاجة بترجع زي ما كانت بالظبط. القرايات كلها قبل الكتابات (وحدة العمل في فايربيز بتأجل الكتابة).
 * التحويل من شخص **ما بيسجلش** أي حاجة مع الشخص (لا دين ولا تسوية) — ربط بس (سؤال مفتوح للمالك).
 */
class LinkInflows(private val deps: LinkInflowsDeps) {
    suspend fun load(transactionId: Id): LinkedInflowsView {
        val t = deps.txns.findByIds(listOf(transactionId)).singleOrNull() ?: return LinkedInflowsView.None
        if (isLinkedInflow(t)) return LinkedInflowsView.Inflow(t, t.linkedExpenseId?.let { deps.txns.findByIds(listOf(it)).singleOrNull() })
        if (!canHaveLinkedInflows(t)) return LinkedInflowsView.None
        val window = windowOf(t)
        val linked = window.filter { isLinkedTo(it, t.id) }
        val linkedTotal = sharesTo(t.id, linked)
        // مجموع متخزن مش مطابق (داخلة اتمسحت أو تراجع دفعة) ⇒ بيتصلح هنا — آمن التكرار
        val expense = if ((t.linkedInflowMinor ?: 0L) == linkedTotal) t else resum(t, linkedTotal).let { fresh(t.id) ?: t }
        val open = window.filter { it.linkedExpenseId == null && inflowRefusal(expense, it) == null }
        val blocked = ledgerLinked(open.map { it.id })
        return LinkedInflowsView.Expense(
            expense, netAfterLinks(expense), orderByCloseness(expense, linked),
            linkRoomOf(expense, deps.allocations.listByTransactionIds(listOf(expense.id))),
            orderByCloseness(expense, open.filter { it.id !in blocked }),
        )
    }

    /** يربط [inflowIds] بالمصروف [expenseId] — null = اتربطت · غير كده سبب الرفض (ولا حاجة اتكتبت). */
    suspend fun link(expenseId: Id, inflowIds: List<Id>): LinkRefusal? {
        val ids = inflowIds.distinct()
        if (ids.isEmpty()) return null
        return deps.uow.run {
            val expense = fresh(expenseId) ?: return@run LinkRefusal.NOT_EXPENSE
            val inflows = deps.txns.findByIds(ids)
            if (inflows.size != ids.size) return@run LinkRefusal.NOT_INFLOW
            if (ledgerLinked(ids).isNotEmpty()) return@run LinkRefusal.LEDGER_LINKED
            checkLink(expense, inflows, deps.allocations.listByTransactionIds(listOf(expense.id)))?.let { return@run it }
            val already = windowOf(expense).filter { isLinkedTo(it, expense.id) && it.id !in ids }
            val adding = sumMoney(inflows.map { if (isLinkedTo(it, expense.id)) linkedShareTo(it, expense.id) else it.amountMinor })
            val now = deps.clock.nowIso()
            for (i in inflows) if (!isLinkedTo(i, expense.id)) deps.txns.update(i.id, TransactionPatch(linkedExpenseId = expense.id, updatedAt = now))
            resum(expense, sumMoney(listOf(sharesTo(expense.id, already), adding)), now)
            // PLAN-0.6 §7: الاسترداد وصل ⇒ «مستني استرداد» بتتشال والمشترى بيتحسب بالصافي
            if (expense.awaitingRefund == true) deps.txns.update(expense.id, TransactionPatch(awaitingRefund = false, updatedAt = now))
            null
        }
    }

    /**
     * r06-1b3 — «استرداد مصاريف» لأكتر من مشترى: الداخلة [inflowId] بتتوزع على المصروفات [shares] (المعرّف ⇒ النصيب بالهللة، بالترتيب).
     * كل نصيب لازم ما يعدّيش اللي فاضل في مصروفه ([linkRoomOf])، والمجموع ما يعدّيش الداخلة (الباقي منها ما بيتربطش بحاجة). الداخلة كلها
     * بتبقى تابعة (ما بتتحسبش دخل) — زي الربط العادي. «مستني استرداد» بتتشال من كل مصروف اتربط. null = اتربطت · غير كده سبب الرفض.
     */
    suspend fun linkSplit(inflowId: Id, shares: Map<Id, Halalas>): LinkRefusal? {
        val parts = shares.filterValues { it > 0 }
        if (parts.isEmpty()) return null
        return deps.uow.run {
            val inflow = fresh(inflowId) ?: return@run LinkRefusal.NOT_INFLOW
            if (inflow.linkedExpenseId != null) return@run LinkRefusal.ALREADY_LINKED
            if (sumMoney(parts.values.toList()) > inflow.amountMinor) return@run LinkRefusal.OVER_EXPENSE
            if (ledgerLinked(listOf(inflowId)).isNotEmpty()) return@run LinkRefusal.LEDGER_LINKED
            val found = deps.txns.findByIds(parts.keys.toList()).associateBy { it.id }
            val allocations = deps.allocations.listByTransactionIds(parts.keys.toList())
            val totals = LinkedHashMap<Id, Halalas>()
            for ((id, share) in parts) {
                val e = found[id] ?: return@run LinkRefusal.NOT_EXPENSE
                if (!canHaveLinkedInflows(e)) return@run LinkRefusal.NOT_EXPENSE
                inflowRefusal(e, inflow)?.let { return@run it }
                if (share > linkRoomOf(e, allocations)) return@run LinkRefusal.OVER_EXPENSE
                totals[id] = sumMoney(listOf(sharesTo(id, windowOf(e).filter { isLinkedTo(it, id) }), share))
            }
            val now = deps.clock.nowIso()
            val whole = parts.size == 1 && parts.values.first() == inflow.amountMinor
            deps.txns.update(
                inflowId,
                if (whole) TransactionPatch(linkedExpenseId = parts.keys.first(), updatedAt = now)
                else TransactionPatch(linkedExpenseId = parts.keys.first(), linkedSplit = encodeLinkedSplit(parts), updatedAt = now),
            )
            for ((id, total) in totals) {
                val e = found.getValue(id)
                resum(e, total, now)
                if (e.awaitingRefund == true) deps.txns.update(id, TransactionPatch(awaitingRefund = false, updatedAt = now))
            }
            null
        }
    }

    /** يفك الداخلة [inflowId] من مصروفها — false = مش تابعة لحاجة. المصروف (لو موجود) بيرجع بمجموع الباقي. */
    suspend fun unlink(inflowId: Id): Boolean = unlinkWithUndo(inflowId) != null

    /**
     * r041 A11 — نفس [unlink] بس بيرجّع اللي يلزم لـ«تراجع» (8 ثواني في الشاشة): null = مش تابعة لحاجة (ولا حاجة اتكتبت).
     * التراجع = [relink] (نفس فحوص [link] — لو المصروف اتمسح أو الحد اتغير بيرجع سبب الرفض).
     */
    suspend fun unlinkWithUndo(inflowId: Id): UnlinkUndo? = deps.uow.run {
        val inflow = fresh(inflowId)
        val expenseId = inflow?.linkedExpenseId ?: return@run null
        val shares = linkedSharesOf(inflow)
        // r06-1b3: الموزّعة على أكتر من مصروف بتتفك من كلهم — القرايات الأول
        val rests = shares.keys.mapNotNull { id -> fresh(id)?.let { e -> e to sharesTo(id, windowOf(e).filter { isLinkedTo(it, id) && it.id != inflowId }) } }
        val now = deps.clock.nowIso()
        deps.txns.update(inflowId, TransactionPatch(clearLinkedExpenseId = true, clearLinkedSplit = inflow.linkedSplit != null, updatedAt = now))
        for ((e, rest) in rests) resum(e, rest, now)
        UnlinkUndo(inflowId, expenseId, shares.takeIf { inflow.linkedSplit != null })
    }

    /** «تراجع» بعد «فك الربط»: يربطها تاني بنفس المصروف — آمن التكرار (مربوطة فعلًا ⇒ ولا كتابة). null = رجعت · غير كده سبب الرفض. */
    suspend fun relink(undo: UnlinkUndo): LinkRefusal? {
        val split = undo.split ?: return link(undo.expenseId, listOf(undo.inflowId))
        if (fresh(undo.inflowId)?.let { linkedSharesOf(it) == split } == true) return null
        return linkSplit(undo.inflowId, split)
    }

    private suspend fun fresh(id: Id): Transaction? = deps.txns.findByIds(listOf(id)).singleOrNull()

    private suspend fun windowOf(expense: Transaction): List<Transaction> =
        deps.txns.listByDateRange(linkWindowStart(expense.occurredAt), linkWindowEnd(expense.occurredAt))

    /** مجموع أنصبة [expenseId] من الداخلات [linked]. */
    private fun sharesTo(expenseId: Id, linked: List<Transaction>): Halalas = sumMoney(linked.map { linkedShareTo(it, expenseId) })

    /** مجموع التابعة على المصروف = [total] (صفر ⇒ الحقل بيتشال، فالمصروف بيرجع زي ما كان بالظبط). */
    private suspend fun resum(expense: Transaction, total: Halalas, now: String = deps.clock.nowIso()) {
        if ((expense.linkedInflowMinor ?: 0L) == total) return
        deps.txns.update(
            expense.id,
            if (total == 0L) TransactionPatch(clearLinkedInflowMinor = true, updatedAt = now) else TransactionPatch(linkedInflowMinor = total, updatedAt = now),
        )
    }

    /** المربوطة بدين أو تسوية أو تقسيم على شخص — ربطها هيحسبها مرتين (السداد بينقّص الدين، والتقسيم بينقّص المصروف). */
    private suspend fun ledgerLinked(ids: List<Id>): Set<Id> {
        if (ids.isEmpty()) return emptySet()
        val wanted = ids.toSet()
        return buildSet {
            deps.settlements.listByTransactionIds(ids).forEach { add(it.transactionId) }
            deps.allocations.listByTransactionIds(ids).forEach { add(it.transactionId) }
            deps.obligations.listByTransactionIds(ids).forEach { o -> o.originTransactionId?.let { if (it in wanted) add(it) } }
        }.intersect(wanted)
    }
}

/**
 * تراجع دفعة فيها طرف من «عمليات تابعة» (§6.5-14) — بيتوصّل في `ReturnsWiring.undoers` جنب `ReversalUndo` وبيتنادى جوه وحدة عمل
 * التراجع قبل المسح: **الداخلة** هتتمسح والمصروف لأ ⇒ المصروف بيرجع بمجموع الباقي · **المصروف** هيتمسح والداخلة لأ ⇒ الداخلة بترجع
 * زي ما كانت (تتحسب تاني بنوعها). الاتنين في نفس الدفعة ⇒ ولا حاجة. ولا ربط بيفضل بيشاور على عملية مش موجودة.
 */
/** أطراف «عمليات تابعة» اللي ليها علاقة بـ[t]: مصروف الداخلة دي، أو الداخلات المربوطة بالمصروف ده — زي ما هي (للتراجع). */
internal suspend fun linkedPartners(txns: TransactionRepository, t: Transaction): List<Transaction> = buildList {
    linkedSharesOf(t).keys.takeIf { it.isNotEmpty() }?.let { addAll(txns.findByIds(it.toList())) }
    if (t.linkedInflowMinor != null) addAll(txns.listByDateRange(linkWindowStart(t.occurredAt), linkWindowEnd(t.occurredAt)).filter { isLinkedTo(it, t.id) })
}

/**
 * مراجعة r04: قبل مسح [ids] بأي طريق (امسح · المسح الجماعي · «دول عملية واحدة» · شيل جزء الرصيد) — نفس [LinkedUndo]: المصروف بيرجع بمجموع الباقي
 * والداخلة بترجع تتحسب بنوعها، فمفيش ربط بيشاور على عملية مش موجودة. بيرجّع الأطراف زي ما كانت **قبل** الفك (عشان «تراجع» يرجّعها).
 */
internal suspend fun unlinkBeforeDelete(txns: TransactionRepository, clock: Clock, ids: List<Id>): List<Transaction> {
    val gone = ids.toSet()
    val partners = txns.findByIds(ids).flatMap { linkedPartners(txns, it) }.filter { it.id !in gone }.distinctBy { it.id }
    if (partners.isNotEmpty()) LinkedUndo(txns, clock).undo("", emptyList(), ids)
    return partners
}

class LinkedUndo(private val txns: TransactionRepository, private val clock: Clock) : BatchUndo {
    override suspend fun undo(batchId: Id, records: List<app.masroufy.core.SourceRecord>, deleting: List<Id>) {
        if (deleting.isEmpty()) return
        val gone = deleting.toSet()
        val now = clock.nowIso()
        for (t in txns.findByIds(deleting)) {
            for ((expenseId, share) in linkedSharesOf(t)) {
                if (expenseId in gone) continue
                val expense = txns.findByIds(listOf(expenseId)).singleOrNull() ?: continue
                val left = (expense.linkedInflowMinor ?: 0L) - share
                txns.update(expenseId, if (left > 0) TransactionPatch(linkedInflowMinor = left, updatedAt = now) else TransactionPatch(clearLinkedInflowMinor = true, updatedAt = now))
            }
            if (t.linkedInflowMinor != null && t.observedDirection == Direction.OUT) {
                val orphans = txns.listByDateRange(linkWindowStart(t.occurredAt), linkWindowEnd(t.occurredAt)).filter { isLinkedTo(it, t.id) && it.id !in gone }
                for (o in orphans) {
                    // r06-1b3: الموزّعة بتفضل تابعة للباقيين بس
                    val kept = linkedSharesOf(o).filterKeys { it !in gone }
                    txns.update(
                        o.id,
                        if (o.linkedSplit == null || kept.isEmpty()) TransactionPatch(clearLinkedExpenseId = true, clearLinkedSplit = o.linkedSplit != null, updatedAt = now)
                        else TransactionPatch(linkedExpenseId = kept.keys.first(), linkedSplit = encodeLinkedSplit(kept), updatedAt = now),
                    )
                }
            }
        }
    }
}
