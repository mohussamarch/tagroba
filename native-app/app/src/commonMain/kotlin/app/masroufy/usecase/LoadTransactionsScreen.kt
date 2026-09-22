package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.Transaction
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.formatPeriodRange
import app.masroufy.core.merchantIndex
import app.masroufy.core.normalizeText
import app.masroufy.core.periodForDate
import app.masroufy.core.savingsRatePercent
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.MerchantRepository
import app.masroufy.port.TagRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.TransactionTagRepository

/**
 * شاشة العمليات — نقل `src/application/useCases/loadTransactionsScreen.ts`.
 * الشاشة ما بتكلمش مستودع ولا بتحسب مبلغ (ARCHITECTURE §3)، والقراية محدودة بفترة (§5.6).
 *
 * المبالغ ممكن تكون `null` عن قصد — CLAUDE.md #10: «لا صفر مؤكد مكان المجهول».
 */

data class TransactionsScreenData(
    val period: Period,
    val periodRange: String,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    /** أسماء الوسوم لكل عملية — الاسم المعروض، من غير تكرار وبترتيب أول ظهور. */
    val tagNamesByTransaction: Map<Id, List<String>>,
    /** اسم التاجر الأساسي وبعده البديلة — للبحث والعرض. */
    val merchantNamesByTransaction: Map<Id, List<String>>,
    val incomeMinor: Halalas?,
    val expenseMinor: Halalas?,
    val remainingMinor: Halalas?,
    val savingsRatePercent: Double?,
    /** «محتاجة تحديد نوع» = اللي التطبيق مش متأكد منها؛ المجاميع نفسها متاحة دايمًا (OVERRIDES §18). */
    val unclassifiedCount: Int,
    /** كام عملية اتحسبت بنوع تقديري (OVERRIDES §18). */
    val estimatedCount: Int,
    /** منهم: كام عملية التطبيق مش متأكد منها — محتاجة تأكيد. */
    val needsReviewCount: Int,
    val totalCount: Int,
)

data class LoadTransactionsScreenDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val allocations: AllocationRepository,
    val merchants: MerchantRepository? = null,
    val tags: TagRepository? = null,
    val transactionTags: TransactionTagRepository? = null,
)

/**
 * التطبيق الحالي لو مفيش فترة ولا يوم بياخد يوم الجهاز. هنا الوقت بييجي من برّه دايمًا
 * (نفس قاعدة `Clock` port)، فلازم `period` أو `today` — والشاشة بتبعت واحد منهم دايمًا.
 */
data class LoadTransactionsScreenRequest(
    val period: Period? = null,
    val today: String? = null,
    val payday: Int? = null,
)

class LoadTransactionsScreen(private val deps: LoadTransactionsScreenDeps) {
    suspend fun load(request: LoadTransactionsScreenRequest): TransactionsScreenData {
        val period = request.period
            ?: request.today?.let { if (request.payday != null) periodForDate(it, request.payday) else periodForDate(it) }
            ?: throw IllegalArgumentException("لازم فترة أو تاريخ اليوم — مفيش ساعة جوه المنطق")

        val transactions = deps.txns.listByDateRange(period.start, period.end)
        val categories = deps.categories.listAll()

        val ids = transactions.map { it.id }
        val allocations = deps.allocations.listByTransactionIds(ids)
        val tags = deps.tags?.listAll() ?: emptyList()
        val links = deps.transactionTags?.listByTransactionIds(ids) ?: emptyList()

        // الرابط اللي وسمه مش موجود بيتساب، والاسم المكرر لنفس العملية بيتشال بترتيب أول ظهور
        val tagNames = tags.associate { it.id to it.displayName }
        val tagNamesByTransaction = LinkedHashMap<Id, List<String>>()
        for (link in links) {
            val name = tagNames[link.tagId] ?: continue
            val merged = LinkedHashSet(tagNamesByTransaction[link.transactionId].orEmpty())
            merged.add(name)
            tagNamesByTransaction[link.transactionId] = merged.toList()
        }

        // المجاميع بالأنواع التقديرية للواضح (OVERRIDES §18)؛ القايمة المعروضة بتفضل بالعمليات الأصلية
        val estimated = withEstimatedKinds(transactions, categories.associate { it.id to it.name })
        val totals = computePeriodTotals(estimated.transactions, allocations)

        val merchantMap = merchantIndex(deps.merchants?.listAll() ?: emptyList())
        val merchantNamesByTransaction = LinkedHashMap<Id, List<String>>()
        for (t in transactions) {
            val merchant = merchantMap[normalizeText(t.rawMerchantName ?: "")] ?: continue
            merchantNamesByTransaction[t.id] = listOf(merchant.displayName) + merchant.aliases.orEmpty()
        }

        val unclassifiedCount = estimated.needsReviewCount
        val totalCount = transactions.size

        // فيه عمليات، لكن ولا واحدة اتحدد نوعها ⇒ المجاميع مجهولة مش صفر
        val allUnknown = totalCount > 0 && unclassifiedCount == totalCount

        return TransactionsScreenData(
            period = period,
            periodRange = formatPeriodRange(period),
            transactions = transactions,
            categories = categories,
            tagNamesByTransaction = tagNamesByTransaction,
            merchantNamesByTransaction = merchantNamesByTransaction,
            incomeMinor = if (allUnknown) null else totals.incomeMinor,
            expenseMinor = if (allUnknown) null else totals.personalExpenseMinor,
            remainingMinor = if (allUnknown) null else totals.remainingMinor,
            savingsRatePercent = if (allUnknown) null else savingsRatePercent(totals.incomeMinor, totals.remainingMinor),
            unclassifiedCount = unclassifiedCount,
            estimatedCount = estimated.estimatedCount,
            needsReviewCount = estimated.needsReviewCount,
            totalCount = totalCount,
        )
    }
}
