package app.masroufy.usecase

import app.masroufy.core.AnomalyResult
import app.masroufy.core.AverageResult
import app.masroufy.core.Budget
import app.masroufy.core.Category
import app.masroufy.core.CategoryBudget
import app.masroufy.core.CategoryBudgetLine
import app.masroufy.core.CategoryLimit
import app.masroufy.core.CompletedPeriodSpend
import app.masroufy.core.DailyAllowance
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.assessCoverage
import app.masroufy.core.averageCompletedSpend
import app.masroufy.core.budgetStatus
import app.masroufy.core.buildCategoryLines
import app.masroufy.core.categoryDistribution
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.dailyAllowance
import app.masroufy.core.detectAnomaly
import app.masroufy.core.uiText
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.BudgetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository

/**
 * شاشة الميزانية — نقل `src/application/useCases/loadBudgetScreen.ts` (spec/01).
 * «سقف إجمالي وسقوف اختيارية للتصنيفات، ومتوسط **منفصل** من فترات مكتملة، وعلامة شذوذ.
 *  لا تُخلق ميزانيات من متوسطات دون اختيار المستخدم.»
 * «منفصل» حرفيًا: `average` و`totalStatus` حقلين مختلفين، والأول عمره ما بيبقى قيمة افتراضية للتاني.
 */

/** كام فترة سابقة بتتقرا للمتوسط والشذوذ. */
private const val HISTORY_PERIODS = 6

data class BudgetScreenData(
    val period: Period,
    val budget: Budget?,
    /** null = المستخدم ما حطش سقف إجمالي. */
    val totalStatus: app.masroufy.core.BudgetStatus?,
    val spentMinor: Halalas,
    /** موثوقية مصروف الفترة الجارية. */
    val spentReliable: Boolean,
    val spentNote: String?,
    /**
     * المصروف **معروف أصلًا**؟ `false` لما ولا عملية محددة النوع: ساعتها الصفر الحسابي
     * **مجهول** مش صفر، وشريط «0 من 3000» راحة كاذبة (CLAUDE.md #10).
     */
    val spentKnown: Boolean,
    /** **معلومة بتتعرض، مش سقف بيتطبق.** */
    val average: AverageResult,
    val anomaly: AnomalyResult,
    val allowance: DailyAllowance,
    val categories: List<Category>,
    val lines: List<CategoryBudgetLine>,
    val categoryBudgets: List<CategoryBudget>,
)

data class LoadBudgetScreenDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val allocations: AllocationRepository,
    val budgets: BudgetRepository,
)

data class LoadBudgetScreenRequest(
    val period: Period,
    val today: String,
    val payday: Int,
)

class LoadBudgetScreen(private val deps: LoadBudgetScreenDeps) {
    suspend fun load(request: LoadBudgetScreenRequest): BudgetScreenData {
        val period = request.period
        val transactions = deps.txns.listByDateRange(period.start, period.end)
        val categories = deps.categories.listAll()
        val budget = deps.budgets.findByPeriod(period.key)
        val allocations = deps.allocations.listByTransactionIds(transactions.map { it.id })

        // الواضح بيتحسب بنوعه المقترح، بنفس قاعدة الرئيسية (OVERRIDES §18)
        val names = categories.associate { it.id to it.name }
        val estimated = withEstimatedKinds(transactions, names)
        val counted = estimated.transactions

        val totals = computePeriodTotals(counted, allocations)
        val coverage = assessCoverage(counted)
        val spentMinor = totals.personalExpenseMinor
        // مجهول مش صفر: فيه عمليات وولا واحدة محددة النوع
        val spentKnown = !(coverage.total > 0 && coverage.unclassified == coverage.total)

        val categoryBudgets = budget?.let { deps.budgets.listCategoryBudgets(it.id) } ?: emptyList()

        // ─── تاريخ الفترات السابقة: للمتوسط والشذوذ بس ───
        val history = mutableListOf<CompletedPeriodSpend>()
        val historyByCategory = LinkedHashMap<Id, MutableList<Halalas>>()

        for (i in 1..HISTORY_PERIODS) {
            val p = shiftPeriod(period, -i, request.payday)
            val rawRows = deps.txns.listByDateRange(p.start, p.end)
            val rowAllocations = deps.allocations.listByTransactionIds(rawRows.map { it.id })
            val rows = withEstimatedKinds(rawRows, names).transactions
            val t = computePeriodTotals(rows, rowAllocations)
            val c = assessCoverage(rows)

            history += CompletedPeriodSpend(
                periodKey = p.key,
                spentMinor = t.personalExpenseMinor,
                // فترة فيها عملية واحدة غير محددة **مش موثوقة**
                reliable = c.unclassified == 0,
                transactionCount = rows.size,
            )

            // تاريخ كل تصنيف — من الفترات الموثوقة بس
            if (c.unclassified == 0 && rows.isNotEmpty()) {
                for (slice in categoryDistribution(rows, rowAllocations).slices) {
                    val categoryId = slice.categoryId ?: continue
                    historyByCategory.getOrPut(categoryId) { mutableListOf() }.add(slice.amountMinor)
                }
            }
        }

        val average = averageCompletedSpend(history)

        // متوسط كل تصنيف — من تاريخه هو
        val averageByCategory = LinkedHashMap<Id, Halalas?>()
        for ((categoryId, values) in historyByCategory) {
            averageByCategory[categoryId] = averageCompletedSpend(
                values.mapIndexed { i, v -> CompletedPeriodSpend("$categoryId-$i", v, reliable = true, transactionCount = 1) },
            ).averageMinor
        }

        val spendByCategory = LinkedHashMap<Id, Halalas>()
        for (slice in categoryDistribution(counted, allocations).slices) {
            val categoryId = slice.categoryId ?: continue
            spendByCategory[categoryId] = slice.amountMinor
        }

        val limitByCategory = LinkedHashMap<Id, CategoryLimit>()
        for (cb in categoryBudgets) {
            limitByCategory[cb.categoryId] = CategoryLimit(cb.limitMinor, if (cb.notifyEnabled) cb.thresholdPercent else null)
        }

        val totalLimit = budget?.totalLimitMinor

        return BudgetScreenData(
            period = period,
            budget = budget,
            // من غير مصروف معروف ما تتبنيش حالة سقف: «0 من 3000» راحة كاذبة
            totalStatus = if (totalLimit == null || !spentKnown) null else budgetStatus(totalLimit, spentMinor, budget!!.thresholdPercent),
            spentMinor = spentMinor,
            spentReliable = coverage.totalsReliable && estimated.estimatedCount == 0,
            spentNote = when {
                estimated.needsReviewCount > 0 -> uiText(TextKey.BUDGET_SPENT_NEEDS_REVIEW, estimated.needsReviewCount.toString())
                estimated.estimatedCount > 0 -> uiText(TextKey.BUDGET_SPENT_ESTIMATED, estimated.estimatedCount.toString())
                else -> coverage.note
            },
            spentKnown = spentKnown,
            average = average,
            anomaly = detectAnomaly(
                spentMinor,
                history.filter { it.reliable && it.transactionCount > 0 }.map { it.spentMinor },
            ),
            // ⚠️ المتاح اليومي بيستعمل **السقف** مش المتوسط. لو السقف غايب يبقى غير متاح
            allowance = dailyAllowance(if (spentKnown) totalLimit else null, spentMinor, request.today, period),
            categories = categories,
            lines = buildCategoryLines(spendByCategory, limitByCategory, averageByCategory, historyByCategory),
            categoryBudgets = categoryBudgets,
        )
    }
}
