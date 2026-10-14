package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.ProjectError
import app.masroufy.core.ProjectLink
import app.masroufy.core.ReviewState
import app.masroufy.core.SelectionTotal
import app.masroufy.core.Tag
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.TransactionTag
import app.masroufy.core.isConsistentWithObservedDirection
import app.masroufy.core.jsTrim
import app.masroufy.core.membershipChanges
import app.masroufy.core.normalizeText
import app.masroufy.core.selectionTotals
import app.masroufy.core.uiText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.ProjectLinkRepository
import app.masroufy.port.ProjectRepository
import app.masroufy.port.SourceRecordRepository
import app.masroufy.port.SpaceTransferLegs
import app.masroufy.port.TagRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransactionTagRepository
import app.masroufy.port.UnitOfWork

/**
 * تحديد كذا عملية مرة واحدة في «العمليات» (قايمة المالك §6.5 بند 15): المجموع · غيّر التصنيف · غيّر النوع · ضيف لمشروع · ضيف وسم · امسح.
 * كل فعل **كتابة واحدة** (`saveMany`/`deleteMany` جوه وحدة عمل) وبيرجّع [BulkUndo] واحد بيرجّع الدفعة كلها زي ما كانت بالظبط.
 * - التصنيف والنوع نفس قواعد [EditTransaction.setCategory] و[SetEconomicKind.setOne] (اختيار المستخدم = مؤكد). **ما بيعلّمش ذاكرة
 *   التاجر** (§75-16) — الجماعي ممكن يخلط محلات كتير، والتراجع لازم يرجّع كل اللي اتكتب (سؤال مفتوح للمالك).
 * - النوع من [BULK_KINDS] بس (الديون والتحويلات محتاجة شخص أو محفظة لكل عملية)؛ اللي اتجاهه ما يناسبش النوع بيتساب ويتعدّ.
 * - المسح: العملية اللي اتضافت بإيد المستخدم بس — مالهاش مصدر (كشف/رسالة)، ومش متربطة بأي حاجة ([BulkTransactionsDeps.linked])،
 *   ومش رجل «تحويل لنفسك»، ومش طرف «عملية رجعت». غير كده بتتساب وتتعدّ. الوسوم بتاعتها بتتمسح معاها وبترجع معاها.
 */
data class BulkTransactionsDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val projects: ProjectRepository,
    val projectLinks: ProjectLinkRepository,
    val tags: TagRepository,
    val transactionTags: TransactionTagRepository,
    val sources: SourceRecordRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
    /** كل اللي بيربط عملية بحاجة تانية (`TransactionLinkRepos.linked`). `null` ⇒ مفيش مسح خالص (مش هنمسح وإحنا مش عارفين). */
    val linked: (suspend (List<Id>) -> Set<Id>)? = null,
    val spaceLegs: SpaceTransferLegs? = null,
)

/** اللي بيظهر وإنت بتحدد: العدد · المجموع لكل عملة · كام واحدة منهم ينفع تتمسح. */
data class SelectionSummary(val count: Int, val totals: List<SelectionTotal>, val deletable: Int)

enum class BulkAction { CATEGORY, KIND, PROJECT, TAG, DELETE }

/** «تراجع» للدفعة كلها مرة واحدة — اللي كان قبل الفعل بالظبط. */
data class BulkUndo(
    val action: BulkAction,
    /** العمليات زي ما كانت قبل التعديل (أو قبل المسح) — بتتكتب تاني زي ما هي. */
    val before: List<Transaction> = emptyList(),
    val createdProjectLinkIds: List<Id> = emptyList(),
    val previousProjectLinks: List<ProjectLink> = emptyList(),
    val createdTagLinkIds: List<Id> = emptyList(),
    val createdTagId: Id? = null,
    val deletedTagLinks: List<TransactionTag> = emptyList(),
)

/** [changed] اتغيّرت فعلًا · [skipped] اتسابت (ما ينفعش، أو كانت كده أصلًا) · [undo] `null` = مفيش حاجة اتكتبت. */
data class BulkResult(val changed: Int, val skipped: Int, val undo: BulkUndo?)

class BulkTransactions(private val deps: BulkTransactionsDeps) {
    private suspend fun load(ids: List<Id>): List<Transaction> = if (ids.isEmpty()) emptyList() else deps.txns.findByIds(ids.distinct())

    private fun result(selected: List<Id>, changed: Int, undo: BulkUndo?) =
        BulkResult(changed, selected.distinct().size - changed, undo.takeIf { changed > 0 || it?.createdTagId != null })

    suspend fun summary(ids: List<Id>): SelectionSummary {
        val found = load(ids)
        return SelectionSummary(found.size, selectionTotals(found), deletableAmong(found).size)
    }

    /** نفس [EditTransaction.setCategory] لكل المحدد مرة واحدة. `null` بيشيل التصنيف. */
    suspend fun setCategory(ids: List<Id>, categoryId: Id?): BulkResult {
        if (categoryId != null && deps.categories.listAll().none { it.id == categoryId }) {
            throw IllegalStateException(uiText(TextKey.CATEGORY_THIS_NOT_FOUND))
        }
        val found = load(ids)
        val now = deps.clock.nowIso()
        val changed = found.map {
            it.copy(
                categoryId = categoryId,
                categoryConfirmed = categoryId != null,
                reviewState = if (categoryId != null) ReviewState.CONFIRMED else ReviewState.NEEDS_REVIEW,
                updatedAt = now,
            )
        }
        if (changed.isNotEmpty()) deps.uow.run { deps.txns.saveMany(changed) }
        return result(ids, changed.size, BulkUndo(BulkAction.CATEGORY, before = found))
    }

    /** نفس [SetEconomicKind.setOne] لكل المحدد — اللي اتجاهه ما يناسبش، أو رجل «تحويل لنفسك»، بيتساب. */
    suspend fun setKind(ids: List<Id>, kind: EconomicKind): BulkResult {
        if (kind !in BULK_KINDS) throw IllegalArgumentException(uiText(TextKey.NOT_AVAILABLE))
        val found = load(ids)
        val legs = deps.spaceLegs?.legsAmong(found.map { it.id }).orEmpty()
        val fits = found.filter { isConsistentWithObservedDirection(kind, it.observedDirection) && (it.economicKind == kind || it.id !in legs) }
        val now = deps.clock.nowIso()
        val changed = fits.map { it.copy(economicKind = kind, economicKindConfirmed = true, suggestedKind = null, updatedAt = now) }
        if (changed.isNotEmpty()) deps.uow.run { deps.txns.saveMany(changed) }
        return result(ids, changed.size, BulkUndo(BulkAction.KIND, before = fits))
    }

    /** إضافة بإيد المستخدم لمشروع (زي `ManageProjects.setMember`) — المستبعدة قبل كده بترجع، واللي فيه أصلًا بيتعدّ «اتساب». */
    suspend fun addToProject(ids: List<Id>, projectId: Id): BulkResult {
        deps.projects.listAll().find { it.id == projectId } ?: throw ProjectError(uiText(TextKey.PROJECT_NOT_FOUND))
        val found = load(ids)
        val legs = deps.spaceLegs?.legsAmong(found.map { it.id }).orEmpty()
        val eligible = found.filter { it.id !in legs }.map { it.id }.toSet()
        val existing = deps.projectLinks.listAll().filter { it.projectId == projectId && it.transactionId in eligible }
        val now = deps.clock.nowIso()
        val changes = found.filter { it.id in eligible }.flatMap { t ->
            membershipChanges(existing.filter { it.transactionId == t.id }, projectId, t.id, true, now)
        }
        if (changes.isNotEmpty()) deps.uow.run { deps.projectLinks.saveMany(changes) }
        val changedIds = changes.map { it.id }.toSet()
        val before = existing.filter { it.id in changedIds }
        val beforeIds = before.map { it.id }.toSet()
        return result(
            ids, changes.size,
            BulkUndo(BulkAction.PROJECT, createdProjectLinkIds = changes.map { it.id }.filter { it !in beforeIds }, previousProjectLinks = before),
        )
    }

    /** وسم واحد على كل المحدد — بينشئه لو مش موجود (نفس تطبيع [EditTransaction.addTag])، والمكرر ما بيتضافش تاني. */
    suspend fun addTag(ids: List<Id>, displayName: String): BulkResult {
        val name = jsTrim(displayName)
        if (name.isEmpty()) throw IllegalStateException(uiText(TextKey.TAG_NAME_REQUIRED))
        if (name.length > MAX_TAG_NAME) throw IllegalStateException(uiText(TextKey.TAG_NAME_TOO_LONG, "$MAX_TAG_NAME"))
        val found = load(ids)
        if (found.isEmpty()) return result(ids, 0, null)
        val normalized = normalizeText(name)
        return deps.uow.run {
            var tag = deps.tags.listAll().find { it.normalizedName == normalized }
            var created: Id? = null
            if (tag == null) {
                tag = Tag(id = deps.ids.next("tag"), normalizedName = normalized, displayName = name)
                deps.tags.save(tag)
                created = tag.id
            }
            val tagId = tag.id
            val have = deps.transactionTags.listByTransactionIds(found.map { it.id }).filter { it.tagId == tagId }.map { it.transactionId }.toSet()
            val links = found.filter { it.id !in have }.map { TransactionTag(id = deps.ids.next("ttag"), transactionId = it.id, tagId = tagId) }
            if (links.isNotEmpty()) deps.transactionTags.saveMany(links)
            result(ids, links.size, BulkUndo(BulkAction.TAG, createdTagLinkIds = links.map { it.id }, createdTagId = created))
        }
    }

    /** بيمسح اللي ينفع يتمسح بس (شوف أول الملف)، ومعاه وسومه — والباقي بيتساب ويتعدّ. */
    suspend fun delete(ids: List<Id>): BulkResult {
        val gone = deletableAmong(load(ids))
        if (gone.isEmpty()) return result(ids, 0, null)
        val goneIds = gone.map { it.id }
        val tagLinks = deps.transactionTags.listByTransactionIds(goneIds)
        deps.uow.run {
            if (tagLinks.isNotEmpty()) deps.transactionTags.deleteMany(tagLinks.map { it.id })
            deps.txns.deleteMany(goneIds)
        }
        return result(ids, gone.size, BulkUndo(BulkAction.DELETE, before = gone, deletedTagLinks = tagLinks))
    }

    /** بيرجّع الدفعة كلها زي ما كانت. الوسم اللي اتعمل جديد بيتشال بس لو مفيش عملية تانية عليه. */
    suspend fun undo(u: BulkUndo) {
        deps.uow.run {
            if (u.before.isNotEmpty()) deps.txns.saveMany(u.before)
            if (u.deletedTagLinks.isNotEmpty()) deps.transactionTags.saveMany(u.deletedTagLinks)
            if (u.createdProjectLinkIds.isNotEmpty()) deps.projectLinks.deleteMany(u.createdProjectLinkIds)
            if (u.previousProjectLinks.isNotEmpty()) deps.projectLinks.saveMany(u.previousProjectLinks)
            if (u.createdTagLinkIds.isNotEmpty()) deps.transactionTags.deleteMany(u.createdTagLinkIds)
            u.createdTagId?.let { id -> if (deps.transactionTags.listByTag(id).isEmpty()) deps.tags.remove(id) }
        }
    }

    private suspend fun deletableAmong(found: List<Transaction>): List<Transaction> {
        val linked = deps.linked ?: return emptyList()
        if (found.isEmpty()) return emptyList()
        val ids = found.map { it.id }
        val blocked = deps.sources.listByTransactionIds(ids).mapNotNull { it.transactionId }.toSet() +
            linked(ids) + deps.spaceLegs?.legsAmong(ids).orEmpty()
        return found.filter { it.id !in blocked && it.reversalOfId == null && it.reversedById == null }
    }
}
