package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.Tag
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag
import app.masroufy.core.arabicCompare
import app.masroufy.core.jsTrim
import app.masroufy.core.normalizeText
import app.masroufy.core.planAmountEdit
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TagRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransactionTagRepository
import app.masroufy.port.UnitOfWork

/**
 * EditTransaction — نقل `editTransaction.ts`: تعديل التصنيف والمبلغ (OVERRIDES §32)
 * والملاحظة ووسم الكاش والاستبعاد والوسوم. التاريخ واتجاه الحركة **ما بيتعدلوش أبدًا** —
 * حقايق من الكشف مش آراء. وأي تعديل من المستخدم بيتحسب تأكيدًا (spec/05).
 */

const val MAX_NOTE = 1000
const val MAX_TAG_NAME = 40

data class TransactionDetail(val transaction: Transaction, val tags: List<Tag>)

data class EditTransactionDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val tags: TagRepository,
    val transactionTags: TransactionTagRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /**
     * بعد ما المستخدم يأكد تصنيف — مساهمة قاعدة التجار المشتركة (OVERRIDES §25.1).
     * فشلها **ما بيفشّلش** تعديل المستخدم — بيتبلع.
     */
    val onCategoryConfirmed: (suspend (Transaction, Id) -> Unit)? = null,
    /** لتعديل المبلغ: ما ينزلش تحت المتوزع أو المتسوّى بيه (OVERRIDES §32). */
    val allocations: AllocationRepository? = null,
    val settlements: SettlementRepository? = null,
)

class EditTransaction(private val deps: EditTransactionDeps) {
    private suspend fun find(transactionId: Id): Transaction =
        deps.txns.findByIds(listOf(transactionId)).firstOrNull() ?: throw IllegalStateException("العملية دي مش موجودة")

    suspend fun load(transactionId: Id): TransactionDetail {
        val transaction = find(transactionId)
        val links = deps.transactionTags.listByTransactionIds(listOf(transactionId))
        val byId = deps.tags.listAll().associateBy { it.id }
        return TransactionDetail(transaction, links.mapNotNull { byId[it.tagId] })
    }

    /** بيغيّر التصنيف. `null` بيشيله. التغيير اليدوي بيتأكد فورًا فالقواعد ما تكتبش فوقه. */
    suspend fun setCategory(transactionId: Id, categoryId: Id?) {
        val transaction = find(transactionId)

        if (categoryId != null && deps.categories.listAll().none { it.id == categoryId }) {
            throw IllegalStateException("التصنيف ده مش موجود")
        }

        deps.txns.update(
            transactionId,
            TransactionPatch(
                categoryId = categoryId,
                clearCategoryId = categoryId == null,
                // اختيار المستخدم قرار مؤكد، وشيل التصنيف قرار كمان
                categoryConfirmed = categoryId != null,
                reviewState = if (categoryId != null) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW,
                updatedAt = deps.clock.nowIso(),
            ),
        )
        if (categoryId != null) deps.onCategoryConfirmed?.let { share -> runCatching { share(transaction, categoryId) } }
    }

    /** بيعدّل المبلغ — OVERRIDES §32. الأصلي من المصدر بيتحفظ مرة واحدة. */
    suspend fun setAmount(transactionId: Id, amountMinor: Long) {
        val transaction = find(transactionId)
        val allocations = deps.allocations?.listByTransactionIds(listOf(transactionId)) ?: emptyList()
        val settlements = deps.settlements?.listByTransactionIds(listOf(transactionId)) ?: emptyList()
        val fields = planAmountEdit(
            transaction.amountMinor, transaction.originalAmountMinor, amountMinor,
            allocations.map { it.amountMinor }, settlements.map { it.amountMinor },
        )
        deps.txns.update(
            transactionId,
            TransactionPatch(amountMinor = fields.amountMinor, originalAmountMinor = fields.originalAmountMinor, updatedAt = deps.clock.nowIso()),
        )
    }

    /** بيكتب ملاحظة أو بيشيلها — الفاضية بتتشال بدل ما نص فاضي يتحفظ. */
    suspend fun setNote(transactionId: Id, note: String) {
        val trimmed = jsTrim(note)
        if (trimmed.length > MAX_NOTE) throw IllegalStateException("الملاحظة أطول من $MAX_NOTE حرف")
        deps.txns.update(
            transactionId,
            TransactionPatch(note = trimmed.ifEmpty { null }, clearNote = trimmed.isEmpty(), updatedAt = deps.clock.nowIso()),
        )
    }

    /** وسم الكاش — بيعلّم إن العملية اتدفعت كاش، **مش** بيغيّر المحفظة ولا المبلغ. */
    suspend fun setCashTag(transactionId: Id, isCashTagged: Boolean) {
        deps.txns.update(transactionId, TransactionPatch(isCashTagged = isCashTagged, updatedAt = deps.clock.nowIso()))
    }

    /** استبعاد من الميزانية أو رجوع — الاستبعاد **ما بيلغيش** خصم المحفظة. */
    suspend fun setExcludedFromBudget(transactionId: Id, excluded: Boolean) {
        deps.txns.update(transactionId, TransactionPatch(excludedFromBudget = excluded, updatedAt = deps.clock.nowIso()))
    }

    /**
     * بيضيف وسم للعملية، وبينشئه لو مش موجود. التطبيع بيمنع «مطاعم» و«  مطاعم » يبقوا وسمين،
     * والوسم المكرر على نفس العملية ما بيتضافش تاني (كان هيضاعف الجمع — spec/02).
     */
    suspend fun addTag(transactionId: Id, displayName: String): Tag {
        val name = jsTrim(displayName)
        if (name.isEmpty()) throw IllegalStateException("اكتب اسم الوسم")
        if (name.length > MAX_TAG_NAME) throw IllegalStateException("اسم الوسم أطول من $MAX_TAG_NAME حرف")

        find(transactionId)
        val normalized = normalizeText(name)

        return deps.uow.run {
            var tag = deps.tags.listAll().find { it.normalizedName == normalized }
            if (tag == null) {
                tag = Tag(id = deps.ids.next("tag"), normalizedName = normalized, displayName = name)
                deps.tags.save(tag)
            }

            val links = deps.transactionTags.listByTransactionIds(listOf(transactionId))
            if (links.none { it.tagId == tag.id }) {
                deps.transactionTags.saveMany(listOf(TransactionTag(id = deps.ids.next("ttag"), transactionId = transactionId, tagId = tag.id)))
            }
            tag
        }
    }

    /** بيشيل وسم من عملية — الوسم نفسه بيفضل موجود لباقي العمليات. */
    suspend fun removeTag(transactionId: Id, tagId: Id) {
        val target = deps.transactionTags.listByTransactionIds(listOf(transactionId)).filter { it.tagId == tagId }.map { it.id }
        if (target.isNotEmpty()) deps.transactionTags.deleteMany(target)
    }

    /** كل الوسوم المتاحة — للاقتراح في الواجهة، بترتيب الحروف العربي. */
    suspend fun listTags(): List<Tag> = deps.tags.listAll().sortedWith { a, b -> arabicCompare(a.displayName, b.displayName) }

    /** الوسوم لمجموعة عمليات — استعلام واحد مش واحد لكل عملية. */
    suspend fun tagsFor(transactionIds: List<Id>): Map<Id, List<Tag>> {
        if (transactionIds.isEmpty()) return emptyMap()
        val links = deps.transactionTags.listByTransactionIds(transactionIds)
        val byId = deps.tags.listAll().associateBy { it.id }
        val out = LinkedHashMap<Id, MutableList<Tag>>()
        for (link in links) {
            val tag = byId[link.tagId] ?: continue
            out.getOrPut(link.transactionId) { mutableListOf() }.add(tag)
        }
        return out
    }
}
