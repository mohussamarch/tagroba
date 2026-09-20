package app.masroufy.core

/**
 * التحليل — نقل `src/domain/analytics.ts` (spec/01 و spec/02).
 * **«المتوسط والتوقع على البيانات المتاحة بس مع ذكر قصورها»**: null أو حالة صريحة، مش صفر ولا تخمين.
 */
data class CategorySlice(
    val categoryId: Id?,
    val amountMinor: Halalas,
    val count: Int,
    /** بالعُشر من المية (855 = 85.5٪). */
    val shareTenthPercent: Long,
)

data class CategoryDistribution(val slices: List<CategorySlice>, val totalMinor: Halalas)

/** توزيع المصروف الشخصي على التصنيفات — كل عملية مرة واحدة بتصنيفها النهائي، واللي من غير تصنيف تحت null. */
fun categoryDistribution(transactions: List<Transaction>, allocations: List<PersonAllocation> = emptyList()): CategoryDistribution {
    val byCategory = LinkedHashMap<Id?, Pair<Halalas, Int>>()
    var total = 0L
    for (t in transactions) {
        if (!countsAsPersonalExpense(t.economicKind) || t.excludedFromBudget) continue
        val share = personalShareOf(t, allocations)
        if (share == 0L) continue
        val (amount, count) = byCategory[t.categoryId] ?: (0L to 0)
        byCategory[t.categoryId] = addMoney(amount, share) to count + 1
        total = addMoney(total, share)
    }
    val slices = byCategory.map { (id, v) ->
        CategorySlice(id, v.first, v.second, if (total == 0L) 0 else rateOfMoney(v.first, 1000, total))
    }.sortedByDescending { it.amountMinor }
    return CategoryDistribution(slices, total)
}

data class DailyAllowance(
    /** null = غير متاح فعلًا. */
    val amountMinor: Halalas?,
    val remainingDays: Int,
    /** من المتبقي مش من سقف (OVERRIDES §19) — بيتعرض «تقريبي». */
    val approximate: Boolean,
    val reason: String,
)

/** المتاح اليومي = max(0، المتبقي) ÷ الأيام الباقية — مش 30 يوم ثابتة (spec/02). */
fun dailyAllowance(budgetLimitMinor: Halalas?, spentMinor: Halalas, today: IsoDate, period: Period, remainingMinor: Halalas? = null): DailyAllowance {
    val days = remainingDaysInPeriod(today, period)
    if (days == 0) return DailyAllowance(null, 0, false, uiText(TextKey.ALLOWANCE_PERIOD_ENDED))
    if (budgetLimitMinor == null) {
        if (remainingMinor == null) return DailyAllowance(null, days, false, uiText(TextKey.ALLOWANCE_NO_LIMIT_NO_REMAINING))
        val left = maxOf(0L, remainingMinor)
        return DailyAllowance(
            left / days, days, true,
            if (left == 0L) uiText(TextKey.ALLOWANCE_NO_LIMIT_SPENT)
            else uiText(TextKey.ALLOWANCE_NO_LIMIT, days.toString()),
        )
    }
    val left = maxOf(0L, budgetLimitMinor - spentMinor)
    return DailyAllowance(
        left / days, days, false,
        if (left == 0L) uiText(TextKey.ALLOWANCE_LIMIT_SPENT) else uiText(TextKey.ALLOWANCE_LIMIT, days.toString()),
    )
}

const val MIN_DAYS_FOR_FORECAST = 5

data class Forecast(
    /** null = البيانات ما تكفيش. */
    val projectedMinor: Halalas?,
    val elapsedDays: Int,
    val totalDays: Int,
    /** قصور التوقع مذكور دايمًا (spec/02). */
    val caveat: String,
)

/** توقع نهاية الفترة من المعدل اليومي لحد دلوقتي — بعد 5 أيام على الأقل. */
fun forecastPeriodSpend(spentMinor: Halalas, today: IsoDate, period: Period): Forecast {
    val totalDays = period.days
    val elapsed = maxOf(0, minOf(daysBetween(period.start, today) + 1, totalDays))
    if (elapsed < MIN_DAYS_FOR_FORECAST) {
        return Forecast(null, elapsed, totalDays, uiText(TextKey.FORECAST_TOO_EARLY, elapsed.toString(), MIN_DAYS_FOR_FORECAST.toString()))
    }
    if (elapsed >= totalDays) return Forecast(spentMinor, elapsed, totalDays, uiText(TextKey.FORECAST_PERIOD_ENDED))
    return Forecast(rateOfMoney(spentMinor, totalDays.toLong(), elapsed.toLong()), elapsed, totalDays, uiText(TextKey.FORECAST_RATE, elapsed.toString()))
}

data class DataCoverage(val total: Int, val unclassified: Int, val totalsReliable: Boolean, val note: String?)

/** كفاية البيانات قبل أي رقم — بيقيس المعروف فعلًا (كام عملية من غير نوع)، من غير افتراضات. */
fun assessCoverage(transactions: List<Transaction>): DataCoverage {
    val total = transactions.size
    val unclassified = transactions.count { it.economicKind == EconomicKind.UNCLASSIFIED }
    return when {
        total == 0 -> DataCoverage(0, 0, true, null)
        unclassified == total -> DataCoverage(total, unclassified, false, uiText(TextKey.COVERAGE_ALL_UNCLASSIFIED, total.toString()))
        unclassified > 0 -> DataCoverage(total, unclassified, false, uiText(TextKey.COVERAGE_SOME_UNCLASSIFIED, unclassified.toString(), total.toString()))
        else -> DataCoverage(total, 0, true, null)
    }
}
