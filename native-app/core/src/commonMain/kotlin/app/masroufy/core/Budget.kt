package app.masroufy.core

import kotlin.math.abs

/**
 * الميزانية والمتوسطات والشذوذ — نقل `src/domain/budget.ts` (spec/01 و spec/06).
 * ١. **مفيش ميزانية بتتعمل من متوسط من غير اختيار المستخدم** — المتوسط معلومة مش سقف.
 * ٢. تصنيف من غير سقف أو تاريخ ⇒ **مفيش سقف ولا متوسط مخترع ولا تنبيه** — null وسبب.
 */
enum class BudgetLevel(val wire: String) { UNDER("under"), NEAR("near"), OVER("over") }

data class BudgetStatus(
    val limitMinor: Halalas,
    val spentMinor: Halalas,
    /** سالب لما السقف يتعدّى — وما بيتخباش. */
    val remainingMinor: Halalas,
    /** المستهلك بالعُشر من المية (855 = 85.5٪). */
    val usedTenthPercent: Long,
    val level: BudgetLevel,
    /** عدّى عتبة التنبيه اللي حددها المستخدم؟ */
    val thresholdCrossed: Boolean,
)

class BudgetError(message: String) : IllegalArgumentException(message)

fun budgetStatus(limitMinor: Halalas, spentMinor: Halalas, thresholdPercent: Int?): BudgetStatus {
    if (limitMinor <= 0) throw BudgetError(uiText(TextKey.BUDGET_LIMIT_POSITIVE))
    val used = rateOfMoney(spentMinor, 1000, limitMinor)
    val level = when {
        used >= 1000 -> BudgetLevel.OVER
        used >= 900 -> BudgetLevel.NEAR
        else -> BudgetLevel.UNDER
    }
    return BudgetStatus(limitMinor, spentMinor, subtractMoney(limitMinor, spentMinor), used, level, thresholdPercent != null && used >= thresholdPercent * 10L)
}

const val MIN_PERIODS_FOR_AVERAGE = 3

data class CompletedPeriodSpend(
    val periodKey: String,
    val spentMinor: Halalas,
    /** كل عمليات الفترة نوعها محدد؟ الفترة الناقصة ما بتدخلش المتوسط. */
    val reliable: Boolean,
    val transactionCount: Int,
)

data class ExcludedPeriod(val periodKey: String, val reason: String)

data class AverageResult(
    /** null = البيانات ما تكفيش. */
    val averageMinor: Halalas?,
    val usedPeriods: List<String>,
    val excluded: List<ExcludedPeriod>,
    val reason: String,
)

/** متوسط **الفترات المكتملة الموثوقة بس** — الفترة الجارية برا دايمًا. */
fun averageCompletedSpend(periods: List<CompletedPeriodSpend>): AverageResult {
    val excluded = mutableListOf<ExcludedPeriod>()
    val used = mutableListOf<CompletedPeriodSpend>()
    for (p in periods) {
        when {
            !p.reliable -> excluded += ExcludedPeriod(p.periodKey, uiText(TextKey.PERIOD_EXCLUDED_UNRELIABLE))
            p.transactionCount == 0 -> excluded += ExcludedPeriod(p.periodKey, uiText(TextKey.PERIOD_EXCLUDED_EMPTY))
            else -> used += p
        }
    }
    if (used.size < MIN_PERIODS_FOR_AVERAGE) {
        return AverageResult(
            null, used.map { it.periodKey }, excluded,
            uiText(TextKey.AVERAGE_NEEDS_PERIODS, MIN_PERIODS_FOR_AVERAGE.toString(), used.size.toString()),
        )
    }
    val total = sumMoney(used.map { it.spentMinor })
    return AverageResult(JsText.round(total.toDouble() / used.size).toLong(), used.map { it.periodKey }, excluded, uiText(TextKey.AVERAGE_OF_PERIODS, used.size.toString()))
}

const val MIN_HISTORY_FOR_ANOMALY = 3
private const val MIN_RELATIVE_DEVIATION_PER_THOUSAND = 200L
private const val ROBUST_MULTIPLIER = 3L

data class AnomalyResult(
    /** null = ما نقدرش نحكم — **مش** «مفيش شذوذ». */
    val isAnomaly: Boolean?,
    val medianMinor: Halalas?,
    /** موجب = أعلى من المعتاد. */
    val deviationMinor: Halalas?,
    val reason: String,
)

private fun median(values: List<Long>): Long {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else JsText.round((sorted[mid - 1] + sorted[mid]).toDouble() / 2).toLong()
}

/** الشذوذ بالوسيط والانحراف المطلق الوسيط — شهر استثنائي واحد ما يخبيش شذوذ اللي بعده. */
fun detectAnomaly(valueMinor: Halalas, historyMinor: List<Halalas>): AnomalyResult {
    if (historyMinor.size < MIN_HISTORY_FOR_ANOMALY) {
        return AnomalyResult(
            null, null, null,
            uiText(TextKey.ANOMALY_NEEDS_HISTORY, MIN_HISTORY_FOR_ANOMALY.toString(), historyMinor.size.toString()),
        )
    }
    val med = median(historyMinor)
    val deviation = valueMinor - med
    val absDeviation = abs(deviation)
    if (med == 0L) return AnomalyResult(null, 0, deviation, uiText(TextKey.ANOMALY_MEDIAN_ZERO))
    val relative = rateOfMoney(absDeviation, 1000, abs(med))
    if (relative < MIN_RELATIVE_DEVIATION_PER_THOUSAND) {
        return AnomalyResult(false, med, deviation, uiText(TextKey.ANOMALY_WITHIN_TWENTY))
    }
    val mad = median(historyMinor.map { abs(it - med) })
    val isAnomaly = mad == 0L || absDeviation >= mad * ROBUST_MULTIPLIER
    val direction = if (deviation > 0) uiText(TextKey.ANOMALY_HIGHER) else uiText(TextKey.ANOMALY_LOWER)
    // نفس `(n / 10).toFixed(0)`: قيمة بخانة عشرية واحدة، والنص بيروح لفوق
    val percent = (relative + 5) / 10
    return AnomalyResult(
        isAnomaly, med, deviation,
        if (isAnomaly) uiText(TextKey.ANOMALY_OUTSIDE, direction, percent.toString())
        else uiText(TextKey.ANOMALY_INSIDE, direction, percent.toString()),
    )
}

data class CategoryLimit(val limitMinor: Halalas, val thresholdPercent: Int?)

data class CategoryBudgetLine(
    val categoryId: Id,
    /** null = المستخدم ما حطش سقف. */
    val status: BudgetStatus?,
    val spentMinor: Halalas,
    /** نصيبه من مجموع مصروف التصنيفات بالعُشر في المية (OVERRIDES §31). */
    val shareTenthPercent: Long,
    val averageMinor: Halalas?,
    val anomaly: AnomalyResult,
    /** سبب غياب السقف — بيتعرض مش بيتساب فاضي. */
    val noLimitReason: String?,
)

/** سطور شاشة الميزانية — اللي من غير سقف بيتعرض بمصروفه ومتوسطه، من غير حالة سقف ولا تنبيه. */
fun buildCategoryLines(
    spendByCategory: Map<Id, Halalas>,
    limitByCategory: Map<Id, CategoryLimit>,
    averageByCategory: Map<Id, Halalas?>,
    historyByCategory: Map<Id, List<Halalas>>,
): List<CategoryBudgetLine> {
    val ids = LinkedHashSet(spendByCategory.keys).apply { addAll(limitByCategory.keys) }
    var total = 0L
    for (spent in spendByCategory.values) if (spent > 0) total = addMoney(total, spent)
    return ids.map { id ->
        val spent = spendByCategory[id] ?: 0L
        val limit = limitByCategory[id]
        CategoryBudgetLine(
            categoryId = id,
            status = limit?.let { budgetStatus(it.limitMinor, spent, it.thresholdPercent) },
            spentMinor = spent,
            shareTenthPercent = if (total > 0 && spent > 0) rateOfMoney(spent, 1000, total) else 0,
            averageMinor = averageByCategory[id],
            anomaly = detectAnomaly(spent, historyByCategory[id].orEmpty()),
            noLimitReason = if (limit != null) null else uiText(TextKey.CATEGORY_NO_LIMIT),
        )
    }.sortedByDescending { it.spentMinor }
}

/** ارتفاع أعمدة «آخر ست فترات» بالعُشر في المية من أكبر فترة (OVERRIDES §31) — null بيفضل null. */
fun relativeTenths(values: List<Halalas?>): List<Long?> {
    val max = values.fold(0L) { highest, v -> if (v != null && v > highest) v else highest }
    return values.map { v ->
        when {
            v == null -> null
            max <= 0 || v <= 0 -> 0
            else -> rateOfMoney(v, 1000, max)
        }
    }
}
