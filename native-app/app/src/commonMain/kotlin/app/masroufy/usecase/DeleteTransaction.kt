package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.EventLink
import app.masroufy.core.Id
import app.masroufy.core.PersonAllocation
import app.masroufy.core.ProjectLink
import app.masroufy.core.RecurringItem
import app.masroufy.core.SourceRecord
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.ObligationRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * «امسح» لعملية اتسجلت غلط (HANDOVER §6.5 بند 12) — **نفس قواعد** «إرجاع الملف» (`RevertImportBatch`) لعملية واحدة، والفرق إن المسح هنا
 * بإيد المالك وعليه «تراجع» ([restore] بيرجّع كل حاجة بنفس معرّفاتها):
 * - **ممنوع** ([EraseBlock]) لو عليها فلوس اتسجلت في حتة تانية — المالك يشيلها من مكانها الأول (الرسالة بتقوله فين): دين اتعمل منها ·
 *   سداد دين بيها · قسط أو جمعية أو مبلغ تمويل أو زكاة · شراء أو بيع أصل · نقطة في حدث · زوج «العملية اللي رجعت» · رجل تحويل لنفسك بين بلدين.
 * - **بيتشال معاها** (والتأكيد بيعدّه): الوسوم · ربط المشروع · ربط الحدث (مش نقطة) · «دعم» لشخص من غير دين (نصيب جوه العملية دي بس).
 * - **جت من رسالة أو كشف:** سجلات المصدر بتفضل **علامة** (`ownerDeletedAt` · من غير عملية) ⇒ نفس الرسالة لو اتقرت تاني ما ترجعش
 *   (`ErasedSources.kt`). خصم اشتراك كان حرّك ميعاده ⇒ الميعاد بيرجع (`SubscriptionChargeUndo`) و«تراجع» بيرجّعه زي ما كان.
 * الأرصدة والمجاميع بعد المسح = لو العملية ما اتسجلتش أصلًا (كلها محسوبة من العمليات — مفيش رقم مخزّن يتظبط).
 */
enum class EraseBlock { DEBT_ORIGIN, SETTLEMENT, DUE, INVESTMENT, EVENT_GIFT, REVERSAL_PAIR, SPACE_LEG }

data class TransactionRemoval(
    val transaction: Transaction,
    /** فاضية = المسح مسموح. */
    val blocks: List<EraseBlock>,
    val tags: List<TransactionTag> = emptyList(),
    val projectLinks: List<ProjectLink> = emptyList(),
    val eventLinks: List<EventLink> = emptyList(),
    val gifts: List<PersonAllocation> = emptyList(),
    /** سجلات المصدر زي ما كانت قبل المسح (فاضية = عملية يدوية). */
    val sources: List<SourceRecord> = emptyList(),
    /** أطراف «عمليات تابعة» زي ما كانت قبل ما الربط يتفك (للتراجع بس). */
    val linkedBefore: List<Transaction> = emptyList(),
    /** الاشتراكات زي ما كانت قبل ما ميعادها يرجع (للتراجع بس). */
    val subscriptionsBefore: List<RecurringItem> = emptyList(),
    /** r05: ربطها بشخص (من غير دين — اللي فيه دين ممنوع بـ[EraseBlock.DEBT_ORIGIN] أو [EraseBlock.SETTLEMENT]). */
    val personLinks: List<app.masroufy.core.PersonLink> = emptyList(),
) {
    val allowed: Boolean get() = blocks.isEmpty()

    /** جت من رسالة بنك أو كشف. */
    val imported: Boolean get() = sources.isNotEmpty()

    /** الروابط اللي هتتشال معاها (وسوم · مشاريع · أحداث · دعم · شخص). */
    val unlinkCount: Int get() = tags.size + projectLinks.size + eventLinks.size + gifts.size + personLinks.size
}

data class DeleteTransactionDeps(
    val txns: TransactionRepository,
    val sources: SourceRecordRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    /** نفس روابط «إرجاع الملف» (المشاريع · الأحداث · الوسوم · المستحقات · الزكاة · الاستثمار · رجول البلدين). */
    val links: RevertLinkDeps,
    val uow: UnitOfWork,
    val clock: Clock,
    val recurring: RecurringRepository? = null,
    /** r05: ربط العمليات بالأشخاص — بيتشال مع العملية (النسخة الشاملة ما بتقبلش ربط بيشاور على عملية مش موجودة). */
    val personLinks: app.masroufy.port.PersonLinkRepository? = null,
)

class DeleteTransaction(private val deps: DeleteTransactionDeps) {
    /** اللي هيحصل — **من غير أي كتابة** (بيتعرض في التأكيد جوه الصفحة). */
    suspend fun preview(transactionId: Id): TransactionRemoval {
        val txn = deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw IllegalStateException(uiText(TextKey.TXN_NOT_FOUND))
        val ids = listOf(txn.id)
        val l = deps.links
        val allocations = deps.allocations.listByTransactionIds(ids)
        val eventLinks = l.eventLinks.listByTransactionIds(ids)
        val blocks = buildList {
            if (deps.obligations.listByTransactionIds(ids).isNotEmpty() || allocations.any { it.allocationKind != AllocationKind.GIFT }) add(EraseBlock.DEBT_ORIGIN)
            if (deps.settlements.listByTransactionIds(ids).isNotEmpty()) add(EraseBlock.SETTLEMENT)
            val due = l.roscaEntries.listByTransactionIds(ids).isNotEmpty() || l.installmentPayments.listByTransactionIds(ids).isNotEmpty() ||
                l.installmentPlans.listAll().any { it.receivedTransactionId == txn.id } || l.zakatPayments.listByTransactionIds(ids).isNotEmpty()
            if (due) add(EraseBlock.DUE)
            if (l.assetLots.listAll().any { it.transactionId == txn.id } || l.assetSales.listAll().any { it.transactionId == txn.id }) add(EraseBlock.INVESTMENT)
            if (eventLinks.any { it.role.isGift }) add(EraseBlock.EVENT_GIFT)
            if (txn.reversalOfId != null || txn.reversedById != null) add(EraseBlock.REVERSAL_PAIR)
            if (l.spaceLegs?.isLeg(txn.id) == true) add(EraseBlock.SPACE_LEG)
        }
        return TransactionRemoval(
            transaction = txn,
            blocks = blocks,
            tags = l.transactionTags.listByTransactionIds(ids),
            projectLinks = l.projectLinks.listByTransaction(txn.id),
            eventLinks = eventLinks.filter { !it.role.isGift },
            gifts = allocations.filter { it.allocationKind == AllocationKind.GIFT },
            sources = deps.sources.listByTransactionIds(ids),
            personLinks = deps.personLinks?.listByTransactionIds(ids).orEmpty(),
        )
    }

    /** بيمسح ذرّيًا (نفس وحدة العمل) — ممنوع ⇒ بيرمي بأول سبب ومفيش ولا كتابة. */
    suspend fun delete(transactionId: Id): TransactionRemoval {
        val plan = preview(transactionId)
        if (!plan.allowed) throw IllegalStateException(uiText(blockText(plan.blocks.first())))
        val now = deps.clock.nowIso()
        return deps.uow.run {
            val before = deps.recurring?.listAll().orEmpty()
            if (deps.recurring != null) SubscriptionChargeUndo(deps.recurring, deps.txns).undo("", plan.sources, listOf(plan.transaction.id))
            val moved = deps.recurring?.listAll().orEmpty().toSet()
            val l = deps.links
            if (plan.tags.isNotEmpty()) l.transactionTags.deleteMany(plan.tags.map { it.id })
            if (plan.projectLinks.isNotEmpty()) l.projectLinks.deleteMany(plan.projectLinks.map { it.id })
            if (plan.eventLinks.isNotEmpty()) l.eventLinks.deleteMany(plan.eventLinks.map { it.id })
            if (plan.gifts.isNotEmpty()) deps.allocations.deleteMany(plan.gifts.map { it.id })
            if (plan.personLinks.isNotEmpty()) deps.personLinks?.deleteMany(plan.personLinks.map { it.transactionId })
            // العلامة: نفس السجل من غير عملية — النسخة الشاملة ما بتقبلش سجل بيشاور على عملية مش موجودة
            if (plan.sources.isNotEmpty()) deps.sources.saveMany(plan.sources.map { it.copy(transactionId = null, ownerDeletedAt = now) })
            // مراجعة r04: «عمليات تابعة» — الطرف التاني بيتفك (ولا مصروف منقوص بداخلة ممسوحة، ولا داخلة مستخبية ورا مصروف ممسوح)
            val partners = unlinkBeforeDelete(deps.txns, deps.clock, listOf(plan.transaction.id))
            deps.txns.deleteMany(listOf(plan.transaction.id))
            plan.copy(subscriptionsBefore = before.filter { it !in moved }, linkedBefore = partners)
        }
    }

    /** «تراجع»: العملية وروابطها وسجلات مصدرها والاشتراكات — زي ما كانوا بالظبط. */
    suspend fun restore(removal: TransactionRemoval) {
        deps.uow.run {
            deps.txns.saveMany(listOf(removal.transaction) + removal.linkedBefore)
            val l = deps.links
            if (removal.sources.isNotEmpty()) deps.sources.saveMany(removal.sources)
            if (removal.tags.isNotEmpty()) l.transactionTags.saveMany(removal.tags)
            if (removal.projectLinks.isNotEmpty()) l.projectLinks.saveMany(removal.projectLinks)
            if (removal.eventLinks.isNotEmpty()) l.eventLinks.saveMany(removal.eventLinks)
            if (removal.gifts.isNotEmpty()) deps.allocations.saveMany(removal.gifts)
            if (removal.personLinks.isNotEmpty()) deps.personLinks?.saveMany(removal.personLinks)
            for (item in removal.subscriptionsBefore) deps.recurring?.save(item)
        }
    }
}

/** سبب المنع بكلام المالك (بيقول يشيلها منين الأول). */
fun blockText(block: EraseBlock): app.masroufy.core.UiKey = when (block) {
    EraseBlock.DEBT_ORIGIN -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_DEBT
    EraseBlock.SETTLEMENT -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_SETTLEMENT
    EraseBlock.DUE -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_DUE
    EraseBlock.INVESTMENT -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_INVESTMENT
    EraseBlock.EVENT_GIFT -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_GIFT
    EraseBlock.REVERSAL_PAIR -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_REVERSAL
    EraseBlock.SPACE_LEG -> app.masroufy.core.UiKey.ERASE_TXN_BLOCK_SPACE_LEG
}
