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
import app.masroufy.core.DuesCategories
import app.masroufy.core.subtractMoney
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.assessCoverage
import app.masroufy.core.averageCompletedSpend
import app.masroufy.core.budgetStatus
import app.masroufy.core.buildCategoryLines
import app.masroufy.core.categoryDistribution
import app.masroufy.core.ExclusionScope
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.dailyAllowance
import app.masroufy.core.detectAnomaly
import app.masroufy.core.LIMIT_AVERAGE_PERIODS
import app.masroufy.core.lastPeriodsAverage
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
    /**
     * «استخدم المتوسط» في لوحة السقف (رد المالك OVERRIDES §82-17) = متوسط **آخر 3 شهور** مكتملة (`lastPeriodsAverage`): للشهر كله
     * ([limitAverageMinor]) ولكل تصنيف ([limitAverageByCategory] — اللي مالوش مفتاح أو null = غير متاح). مش [average] (اللي بيتعرض كمعلومة).
     */
    val limitAverageMinor: Halalas? = null,
    val limitAverageByCategory: Map<Id, Halalas?> = emptyMap(),
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
    /** من ملف الحساب (`duesInBudget`؛ `null` ⇒ `true`) — `false` = أقساط «المستحقات» برا الحد (§56). */
    val duesInBudget: Boolean = true,
)

class LoadBudgetScreen(private val deps: LoadBudgetScreenDeps) {
    suspend fun load(request: LoadBudgetScreenRequest): BudgetScreenData {
        val period = request.period
        val categories = deps.categories.listAll()
        val names = categories.associate { it.id to it.name }
        // نفس عمليات الرئيسية للفترة (§75-3 — الراتب ما بيغيّرش الصرف، بس الشاشتين بيقروا نفس الصفوف)
        val transactions = loadPeriodRows(deps.txns, period, request.payday, names).rows
        val budget = deps.budgets.findByPeriod(period.key)
        val allocations = deps.allocations.listByTransactionIds(transactions.map { it.id })

        // الواضح بيتحسب بنوعه المقترح، بنفس قاعدة الرئيسية (OVERRIDES §18)
        val estimated = withEstimatedKinds(transactions, names)
        val counted = estimated.transactions
        val estimatedSpend = estimated.estimatedCount - estimated.pendingIncomingCount
        val needsReviewSpend = estimated.needsReviewCount - estimated.pendingIncomingCount

        val totals = computePeriodTotals(counted, allocations, ExclusionScope.BUDGET)
        // الداخل المستني (§75-1) عمره ما بيبقى صرف ⇒ ما بيخلّيش الصرف «مش معروف»
        val coverage = assessCoverage(estimated.withoutPendingIncoming)
        // «المستحقات» برا الحد لو صاحب الحساب اختار كده — المصروف الشهري نفسه (الرئيسية) فيها دايمًا
        val duesIds = if (request.duesInBudget) emptySet() else DuesCategories.idsIn(categories)
        val spentMinor = subtractMoney(totals.personalExpenseMinor, DuesCategories.spendMinor(counted, allocations, duesIds))
        // مجهول مش صفر: فيه عمليات وولا واحدة محددة النوع
        val spentKnown = !(coverage.total > 0 && coverage.unclassified == coverage.total)

        val categoryBudgets = budget?.let { deps.budgets.listCategoryBudgets(it.id) } ?: emptyList()

        // ─── تاريخ الفترات السابقة: للمتوسط والشذوذ بس ───
        val history = mutableListOf<CompletedPeriodSpend>()
        val historyByCategory = LinkedHashMap<Id, MutableList<Halalas>>()
        // آخر 3 شهور لكل تصنيف (لـ«استخدم المتوسط») — الشهر اللي التصنيف ما صرفش فيه = صفر لو الشهر نفسه موثوق
        val recentSlices = mutableListOf<Map<Id, Halalas>>()

        for (i in 1..HISTORY_PERIODS) {
            val p = shiftPeriod(period, -i, request.payday)
            val rawRows = loadPeriodRows(deps.txns, p, request.payday, names).rows
            val rowAllocations = deps.allocations.listByTransactionIds(rawRows.map { it.id })
            val view = withEstimatedKinds(rawRows, names)
            val rows = view.transactions
            val t = computePeriodTotals(rows, rowAllocations, ExclusionScope.BUDGET)
            val c = assessCoverage(view.withoutPendingIncoming)

            history += CompletedPeriodSpend(
                periodKey = p.key,
                spentMinor = subtractMoney(t.personalExpenseMinor, DuesCategories.spendMinor(rows, rowAllocations, duesIds)),
                // فترة فيها عملية واحدة غير محددة **مش موثوقة**
                reliable = c.unclassified == 0,
                transactionCount = rows.size,
            )

            // تاريخ كل تصنيف — من الفترات الموثوقة بس
            val slices = LinkedHashMap<Id, Halalas>()
            if (c.unclassified == 0 && rows.isNotEmpty()) {
                for (slice in categoryDistribution(rows, rowAllocations, ExclusionScope.BUDGET).slices) {
                    val categoryId = slice.categoryId ?: continue
                    historyByCategory.getOrPut(categoryId) { mutableListOf() }.add(slice.amountMinor)
                    slices[categoryId] = slice.amountMinor
                }
            }
            if (i <= LIMIT_AVERAGE_PERIODS) recentSlices += slices
        }
        val recent = history.take(LIMIT_AVERAGE_PERIODS)
        val limitAverageByCategory = (categories.map { it.id } + spendByCategoryIds(recentSlices) + categoryBudgets.map { it.categoryId }).associateWith { id ->
            lastPeriodsAverage(recent.mapIndexed { k, p -> p.copy(spentMinor = recentSlices.getOrNull(k)?.get(id) ?: 0L) })
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
        for (slice in categoryDistribution(counted, allocations, ExclusionScope.BUDGET).slices) {
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
            // الداخل المستني (§75-1) مش داخل الرقم ولا بتخمين ⇒ ما بيخلّيهوش «تقريبي» (اللي بيتعد هنا = المحسوب بنوع تقديري)
            spentReliable = coverage.totalsReliable && estimatedSpend == 0,
            spentNote = when {
                needsReviewSpend > 0 -> uiText(TextKey.BUDGET_SPENT_NEEDS_REVIEW, needsReviewSpend.toString())
                estimatedSpend > 0 -> uiText(TextKey.BUDGET_SPENT_ESTIMATED, estimatedSpend.toString())
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
            limitAverageMinor = lastPeriodsAverage(recent),
            limitAverageByCategory = limitAverageByCategory,
        )
    }
}

/** كل التصنيفات اللي ليها صرف في الشهور الأخيرة (بترتيب ظهورها). */
private fun spendByCategoryIds(slices: List<Map<Id, Halalas>>): Set<Id> = slices.flatMapTo(LinkedHashSet()) { it.keys }
