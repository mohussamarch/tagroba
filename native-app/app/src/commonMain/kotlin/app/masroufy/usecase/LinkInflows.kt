package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.LinkRefusal
import app.masroufy.core.Transaction
import app.masroufy.core.canHaveLinkedInflows
import app.masroufy.core.checkLink
import app.masroufy.core.inflowRefusal
import app.masroufy.core.isLinkedInflow
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
        val linked = window.filter { it.linkedExpenseId == t.id && it.observedDirection == Direction.IN }
        // مجموع متخزن مش مطابق (داخلة اتمسحت أو تراجع دفعة) ⇒ بيتصلح هنا — آمن التكرار
        val expense = if ((t.linkedInflowMinor ?: 0L) == sumMoney(linked.map { it.amountMinor })) t else resum(t, linked).let { fresh(t.id) ?: t }
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
            val already = windowOf(expense).filter { it.linkedExpenseId == expense.id && it.observedDirection == Direction.IN && it.id !in ids }
            val now = deps.clock.nowIso()
            for (i in inflows) if (i.linkedExpenseId != expense.id) deps.txns.update(i.id, TransactionPatch(linkedExpenseId = expense.id, updatedAt = now))
            resum(expense, already + inflows, now)
            null
        }
    }

    /** يفك الداخلة [inflowId] من مصروفها — false = مش تابعة لحاجة. المصروف (لو موجود) بيرجع بمجموع الباقي. */
    suspend fun unlink(inflowId: Id): Boolean = deps.uow.run {
        val inflow = fresh(inflowId)
        val expenseId = inflow?.linkedExpenseId ?: return@run false
        val expense = fresh(expenseId)
        val rest = expense?.let { e -> windowOf(e).filter { it.linkedExpenseId == e.id && it.observedDirection == Direction.IN && it.id != inflowId } }
        val now = deps.clock.nowIso()
        deps.txns.update(inflowId, TransactionPatch(clearLinkedExpenseId = true, updatedAt = now))
        if (expense != null && rest != null) resum(expense, rest, now)
        true
    }

    private suspend fun fresh(id: Id): Transaction? = deps.txns.findByIds(listOf(id)).singleOrNull()

    private suspend fun windowOf(expense: Transaction): List<Transaction> =
        deps.txns.listByDateRange(linkWindowStart(expense.occurredAt), linkWindowEnd(expense.occurredAt))

    /** مجموع التابعة على المصروف = [linked] (صفر ⇒ الحقل بيتشال، فالمصروف بيرجع زي ما كان بالظبط). */
    private suspend fun resum(expense: Transaction, linked: List<Transaction>, now: String = deps.clock.nowIso()) {
        val total = sumMoney(linked.map { it.amountMinor })
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
    t.linkedExpenseId?.let { addAll(txns.findByIds(listOf(it))) }
    if (t.linkedInflowMinor != null) addAll(txns.listByDateRange(linkWindowStart(t.occurredAt), linkWindowEnd(t.occurredAt)).filter { it.linkedExpenseId == t.id })
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
            val expenseId = t.linkedExpenseId
            if (expenseId != null && expenseId !in gone) {
                val expense = txns.findByIds(listOf(expenseId)).singleOrNull() ?: continue
                val left = (expense.linkedInflowMinor ?: 0L) - t.amountMinor
                txns.update(expenseId, if (left > 0) TransactionPatch(linkedInflowMinor = left, updatedAt = now) else TransactionPatch(clearLinkedInflowMinor = true, updatedAt = now))
            }
            if (t.linkedInflowMinor != null && t.observedDirection == Direction.OUT) {
                val orphans = txns.listByDateRange(linkWindowStart(t.occurredAt), linkWindowEnd(t.occurredAt)).filter { it.linkedExpenseId == t.id && it.id !in gone }
                for (o in orphans) txns.update(o.id, TransactionPatch(clearLinkedExpenseId = true, updatedAt = now))
            }
        }
    }
}
