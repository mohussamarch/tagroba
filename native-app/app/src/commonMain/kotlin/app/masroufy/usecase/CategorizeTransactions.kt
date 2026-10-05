package app.masroufy.usecase

import app.masroufy.core.CategorizationInput
import app.masroufy.core.CategorizeDeps
import app.masroufy.core.Id
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.categorize
import app.masroufy.core.merchantIndex
import app.masroufy.core.normalizeText
import app.masroufy.core.prepareRules
import app.masroufy.port.CategoryRepository
import app.masroufy.port.Clock
import app.masroufy.port.MerchantRepository
import app.masroufy.port.RuleRepository
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * CategorizeTransactions — نقل `categorizeTransactions.ts`: تطبيق ترتيب أولوية التصنيف.
 * القيد الأهم (spec/05): **المؤكد ما بيتستبدلش عند إعادة الاستيراد** — بيتساب ويتسجل صراحة.
 */

data class CategorizationChange(
    val transactionId: Id,
    val fromCategoryId: Id?,
    val toCategoryId: Id?,
    val reason: String,
    val source: String,
)

data class CategorizationReport(
    val changed: List<CategorizationChange>,
    /** عمليات اتسابت لأن المستخدم أكّدها — ما يتكتبش فوقها. */
    val skippedConfirmed: List<Id>,
    /** عمليات ما طابقها حاجة ومحتاجة مراجعة. */
    val stillNeedsReview: List<Id>,
)

data class CategorizeTransactionsDeps(
    val txns: TransactionRepository,
    val merchants: MerchantRepository,
    val categories: CategoryRepository,
    val rules: RuleRepository,
    val uow: UnitOfWork,
    val clock: Clock,
)

class CategorizeTransactions(private val deps: CategorizeTransactionsDeps) {
    private suspend fun loadDeps(): CategorizeDeps = CategorizeDeps(
        merchantsByNormalizedName = merchantIndex(deps.merchants.listAll()),
        categoryIdByName = deps.categories.listAll().associate { normalizeText(it.name) to it.id },
        rules = prepareRules(deps.rules.listAll()),
    )

    /** بيحسب اللي هيتغير من غير كتابة — لعرض الأثر قبل التطبيق. */
    suspend fun plan(transactions: List<Transaction>): CategorizationReport {
        val catDeps = loadDeps()
        val changed = mutableListOf<CategorizationChange>()
        val skippedConfirmed = mutableListOf<Id>()
        val stillNeedsReview = mutableListOf<Id>()

        for (txn in transactions) {
            // ─── الأولوية ١: المؤكد ما بيتمسش ───
            if (txn.categoryConfirmed) {
                skippedConfirmed += txn.id
                continue
            }

            // الفاضي زي الغايب — نفس فحص جافاسكربت (truthy)
            val result = categorize(
                CategorizationInput(
                    currentConfirmed = false,
                    currentCategoryId = txn.categoryId?.takeIf { it.isNotEmpty() },
                    merchantName = txn.rawMerchantName?.takeIf { it.isNotEmpty() },
                    description = txn.rawDescription?.takeIf { it.isNotEmpty() },
                    sourceCategory = txn.sourceCategory?.takeIf { it.isNotEmpty() },
                ),
                catDeps,
            )

            if (result.categoryId == null) {
                stillNeedsReview += txn.id
                continue
            }
            if (result.categoryId != txn.categoryId) {
                changed += CategorizationChange(
                    transactionId = txn.id,
                    fromCategoryId = txn.categoryId,
                    toCategoryId = result.categoryId,
                    reason = result.reason,
                    source = result.source.wire,
                )
            }
        }

        return CategorizationReport(changed, skippedConfirmed, stillNeedsReview)
    }

    /** بيطبّق التصنيف ذريًا. ما بيلمسش `categoryConfirmed`. */
    suspend fun apply(transactions: List<Transaction>): CategorizationReport {
        val report = plan(transactions)
        if (report.changed.isEmpty()) return report

        val now = deps.clock.nowIso()
        return deps.uow.run {
            for (change in report.changed) {
                deps.txns.update(
                    change.transactionId,
                    // مقترح مش مؤكد — spec/04
                    TransactionPatch(categoryId = change.toCategoryId, reviewState = ReviewState.SUGGESTED, updatedAt = now),
                )
            }
            report
        }
    }

    /** تأكيد المستخدم لتصنيف عملية — بيرفع الحماية ضد الكتابة الآلية (spec/05). */
    suspend fun confirm(transactionId: Id, categoryId: Id) {
        deps.txns.update(
            transactionId,
            TransactionPatch(categoryId = categoryId, categoryConfirmed = true, reviewState = ReviewState.CONFIRMED, updatedAt = deps.clock.nowIso()),
        )
    }
}
