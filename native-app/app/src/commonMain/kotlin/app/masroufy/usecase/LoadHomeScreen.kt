package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.CategorySlice
import app.masroufy.core.DailyAllowance
import app.masroufy.core.DataCoverage
import app.masroufy.core.Forecast
import app.masroufy.core.Halalas
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.assessCoverage
import app.masroufy.core.categoryDistribution
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.dailyAllowance
import app.masroufy.core.forecastPeriodSpend
import app.masroufy.core.formatPeriodRange
import app.masroufy.core.parseIsoDate
import app.masroufy.core.savingsRatePercent
import app.masroufy.core.uiText
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.BudgetRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.TransactionRepository

/**
 * الشاشة الرئيسية (spec/01) — نقل `src/application/useCases/loadHomeScreen.ts`.
 * «الفترة المختارة؛ المصروف الشخصي؛ الدخل والمتبقي والادخار؛ المتاح يوميًا؛ توقع نهاية الفترة؛
 * توزيع التصنيفات؛ أحدث العمليات؛ آخر ست فترات.»
 *
 * كل رقم هنا إما محسوب أو `null` بتتعرض «غير متاح» — CLAUDE.md #10.
 */

/** ملخص فترة سابقة في شريط «آخر ست فترات». */
data class PeriodSummary(
    val period: Period,
    val expenseMinor: Halalas?,
    val incomeMinor: Halalas?,
    val transactionCount: Int,
)

data class HomeScreenData(
    val period: Period,
    val periodRange: String,
    /** المصروف الشخصي — الرقم الأبرز في الشاشة. */
    val expenseMinor: Halalas?,
    val incomeMinor: Halalas?,
    val remainingMinor: Halalas?,
    val savingsRatePercent: Double?,
    /** المستبعد من الميزانية — بيتعرض لوحده وما بيتخباش (spec/02). */
    val excludedExpenseMinor: Halalas?,
    /**
     * الدخل والمصروف محسوبين من العمليات المحددة بس، وفيه عمليات لسه ما اتحددتش.
     * الرقم صح «لحد دلوقتي» مش نهائي، ولازم ده يتقال.
     */
    val partial: Boolean,
    /** كام عملية اتحسبت بنوع تقديري (OVERRIDES §18). */
    val estimatedCount: Int,
    /** منهم: كام عملية التطبيق مش متأكد منها — محتاجة تأكيد. */
    val needsReviewCount: Int,
    val distribution: List<CategorySlice>,
    val categories: List<Category>,
    /** أحدث العمليات — عدد محدود للعرض. */
    val latest: List<Transaction>,
    val transactionCount: Int,
    val allowance: DailyAllowance,
    val forecast: Forecast,
    val coverage: DataCoverage,
    /** آخر ست فترات، الأحدث الأول. */
    val recentPeriods: List<PeriodSummary>,
)

data class LoadHomeScreenDeps(
    val txns: TransactionRepository,
    val categories: CategoryRepository,
    val allocations: AllocationRepository,
    val budgets: BudgetRepository? = null,
)

data class LoadHomeScreenRequest(
    val period: Period,
    val today: String,
    val payday: Int,
    /** سقف الفترة لما مفيش مستودع ميزانيات (بيتستعمل في الاختبار والمعاينة). */
    val budgetLimitMinor: Halalas? = null,
    val includeHistory: Boolean = true,
)

/** عدد الفترات في الشريط السفلي — spec/01. */
private const val RECENT_PERIOD_COUNT = 6
private const val LATEST_COUNT = 5

class LoadHomeScreen(private val deps: LoadHomeScreenDeps) {
    suspend fun load(request: LoadHomeScreenRequest): HomeScreenData {
        val period = request.period
        val transactions = deps.txns.listByDateRange(period.start, period.end)
        val categories = deps.categories.listAll()
        val savedBudget = deps.budgets?.findByPeriod(period.key)
        val budgetLimitMinor = if (deps.budgets != null) savedBudget?.totalLimitMinor else request.budgetLimitMinor
        val allocations = deps.allocations.listByTransactionIds(transactions.map { it.id })

        // الواضح بيتحسب بنوعه المقترح «تقديري» (OVERRIDES §18)؛ «أحدث العمليات» بتفضل بالعمليات الأصلية
        val names = categories.associate { it.id to it.name }
        val estimated = withEstimatedKinds(transactions, names)
        val counted = estimated.transactions
        val totals = computePeriodTotals(counted, allocations)
        val coverage = assessCoverage(counted)
        val slices = categoryDistribution(counted, allocations).slices

        /*
         * تلات حالات مش اتنين — CLAUDE.md #10:
         * ١. ولا عملية محددة ⇒ كل حاجة «غير متاح».
         * ٢. بعضها محدد ⇒ الدخل والمصروف **جزئيين صحيحين** بيتعرضوا بعلامة `partial`،
         *    إنما **المتبقي ومعدل الادخار `null`**: كل واحد فيهم بيخلط مدخلين ناقصين
         *    فيطلع رقم مضلل مش ناقص.
         * ٣. كلها محددة ⇒ كل حاجة متاحة.
         */
        val allUnknown = coverage.total > 0 && coverage.unclassified == coverage.total
        val partial = !allUnknown && coverage.unclassified > 0

        val latest = transactions.sortedWith(
            compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder },
        ).take(LATEST_COUNT)

        // آخر ست فترات — كل واحدة استعلام محدود بمداها
        val recentPeriods = if (!request.includeHistory) {
            emptyList()
        } else {
            (0 until RECENT_PERIOD_COUNT).map { i ->
                val p = if (i == 0) period else shiftPeriod(period, -i, request.payday)
                val rows = if (i == 0) counted else withEstimatedKinds(deps.txns.listByDateRange(p.start, p.end), names).transactions
                val rowAllocations = if (i == 0) allocations else deps.allocations.listByTransactionIds(rows.map { it.id })
                val t = computePeriodTotals(rows, rowAllocations)
                val c = assessCoverage(rows)
                val unknown = c.total > 0 && c.unclassified == c.total
                PeriodSummary(
                    period = p,
                    expenseMinor = if (unknown) null else t.personalExpenseMinor,
                    incomeMinor = if (unknown) null else t.incomeMinor,
                    transactionCount = rows.size,
                )
            }
        }

        // التأكد إن التاريخ صالح قبل ما يتستعمل في التوقع والمتاح اليومي
        parseIsoDate(request.today)

        val forecast = forecastPeriodSpend(totals.personalExpenseMinor, request.today, period)
        return HomeScreenData(
            period = period,
            periodRange = formatPeriodRange(period),
            expenseMinor = if (allUnknown) null else totals.personalExpenseMinor,
            incomeMinor = if (allUnknown) null else totals.incomeMinor,
            remainingMinor = if (allUnknown || partial) null else totals.remainingMinor,
            savingsRatePercent = if (allUnknown || partial) null else savingsRatePercent(totals.incomeMinor, totals.remainingMinor),
            excludedExpenseMinor = if (allUnknown) null else totals.excludedExpenseMinor,
            partial = partial,
            estimatedCount = estimated.estimatedCount,
            needsReviewCount = estimated.needsReviewCount,
            distribution = slices,
            categories = categories,
            latest = latest,
            transactionCount = transactions.size,
            /* OVERRIDES §18 و§19: الرقم بيظهر دايمًا — من السقف لو فيه سقف، وإلا تقريبي من المتبقي. */
            allowance = dailyAllowance(budgetLimitMinor, totals.personalExpenseMinor, request.today, period, totals.remainingMinor),
            forecast = if (allUnknown || partial) {
                forecast.copy(projectedMinor = null, caveat = uiText(TextKey.FORECAST_NEEDS_KINDS))
            } else {
                forecast
            },
            coverage = coverage,
            recentPeriods = recentPeriods,
        )
    }
}
