package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.KindSuggestion
import app.masroufy.core.SuggestionInput
import app.masroufy.core.Transaction
import app.masroufy.core.isBulkConfirmable
import app.masroufy.core.isConsistentWithObservedDirection
import app.masroufy.core.suggestEconomicKind
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * SetEconomicKind — نقل `setEconomicKind.ts`. spec/02: «التصنيف الآلي يقترح،
 * و**تأكيد المستخدم** يحمي اختياره». مفيش هنا «طبّق الاقتراحات تلقائيًا» —
 * الاقتراح بيتعرض والمستخدم بيوافق، فرديًا أو جماعيًا على اللي شايفه.
 */

data class SuggestionLine(val transaction: Transaction, val suggestion: KindSuggestion)

data class SuggestionSummary(
    /** اقتراحات قاطعة قابلة للتأكيد الجماعي. */
    val confirmable: List<SuggestionLine>,
    /** اقتراحات مرجّحة محتاجة نظرة قبل التأكيد. */
    val needsLook: List<SuggestionLine>,
    /** غامضة: من غير اقتراح، قرار فردي مطلوب. */
    val ambiguous: List<SuggestionLine>,
    /** محدَّدة بالفعل وما بتتمسش. */
    val alreadySet: Int,
)

data class BulkConfirmResult(val applied: Int, val skipped: Int)

data class SetEconomicKindDeps(
    val txns: TransactionRepository,
    /** لترجمة categoryId لاسم يفهمه محرك الاقتراح. */
    val categories: CategoryRepository,
    val uow: UnitOfWork,
    val clock: Clock,
)

class SetEconomicKind(private val deps: SetEconomicKindDeps) {
    /** بيبني الاقتراحات متجمّعة حسب قوتها. **ما بيكتبش حاجة.** */
    suspend fun summarize(transactions: List<Transaction>): SuggestionSummary {
        val categoryNameById = deps.categories.listAll().associate { it.id to it.name }

        val confirmable = mutableListOf<SuggestionLine>()
        val needsLook = mutableListOf<SuggestionLine>()
        val ambiguous = mutableListOf<SuggestionLine>()
        var alreadySet = 0

        for (transaction in transactions) {
            if (transaction.economicKindConfirmed) {
                alreadySet++
                continue
            }

            val suggestion = suggestEconomicKind(
                SuggestionInput(
                    direction = transaction.observedDirection,
                    sourceCategory = transaction.sourceCategory,
                    // اللي التطبيق عارفه عن التصنيف دليل كمان، ما يتهدرش
                    categoryName = transaction.categoryId?.let { categoryNameById[it] },
                    sourceOperationType = transaction.sourceOperationType,
                    merchantName = transaction.rawMerchantName,
                    description = transaction.rawDescription,
                ),
            )

            val line = SuggestionLine(transaction, suggestion)
            when {
                isBulkConfirmable(suggestion) -> confirmable += line
                suggestion.kind != null -> needsLook += line
                else -> ambiguous += line
            }
        }

        return SuggestionSummary(confirmable, needsLook, ambiguous, alreadySet)
    }

    /**
     * بيحدد نوع عملية واحدة بقرار صريح من المستخدم، وبيرفض التناقض الصريح
     * مع اتجاه السيولة الملاحظ: مينفعش حركة صادرة تتعلم «مرتب».
     */
    suspend fun setOne(transactionId: Id, kind: EconomicKind) {
        val transaction = deps.txns.findByIds(listOf(transactionId)).firstOrNull()
            ?: throw IllegalStateException("عملية غير موجودة: $transactionId")

        if (!isConsistentWithObservedDirection(kind, transaction.observedDirection)) {
            val observed = if (transaction.observedDirection == Direction.IN) "وارد" else "صادر"
            throw IllegalStateException("النوع ده ما يتوافقش مع اتجاه الحركة. الكشف بيقول العملية دي «$observed».")
        }

        deps.txns.update(
            transactionId,
            TransactionPatch(economicKind = kind, economicKindConfirmed = true, updatedAt = deps.clock.nowIso()),
        )
    }

    /**
     * تأكيد جماعي لاقتراحات قاطعة **اختارها المستخدم صراحةً** —
     * ما بيتندهش تلقائيًا في أي مكان، والغامض عمره ما بيتأكد جماعيًا.
     */
    suspend fun confirmBulk(transactions: List<Transaction>, selectedIds: List<Id>): BulkConfirmResult {
        val wanted = selectedIds.toSet()
        val summary = summarize(transactions)

        val eligible = summary.confirmable.filter { it.transaction.id in wanted }
        val skipped = wanted.size - eligible.size

        if (eligible.isEmpty()) return BulkConfirmResult(applied = 0, skipped = skipped)

        val now = deps.clock.nowIso()
        return deps.uow.run {
            for (line in eligible) {
                deps.txns.update(
                    line.transaction.id,
                    TransactionPatch(economicKind = line.suggestion.kind!!, economicKindConfirmed = true, updatedAt = now),
                )
            }
            BulkConfirmResult(applied = eligible.size, skipped = skipped)
        }
    }
}
