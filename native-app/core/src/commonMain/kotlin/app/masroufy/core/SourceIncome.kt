package app.masroufy.core

/**
 * §120 (التصميم — صفحة مصدر الدخل، رد المالك «الاتنين»): قايمة اللي وصل من المصدر **شهر بشهر** (الشهر المالي من يوم الراتب) + سطر
 * «وصل كام من المتوقع الشهر ده». مراجعة todo-types-2. دالة نقية بأعداد صحيحة بالهللة.
 *
 * اللي بيتحسب: إيداع منسوب للمصدر ([attributedSourceId]) و**نوعه مؤكد**: للوظيفة والبارت تايم «مرتب» بس، ولباقي الأنواع أي نوع دخل.
 * الإيداع اللي لسه بيتسأل عن نوعه ما بيتحسبش (ما بنخمّنش — قاعدة 10)، وعدده ومجموعه في [SourceIncome.waitingCount] و[SourceIncome.waitingMinor]
 * — §121-9: سطر لوحده في الصفحة، عمره ما يتجمع مع اللي وصل.
 */
data class SourceMonth(val period: Period, val receivedMinor: Halalas, val count: Int)

/**
 * [months] الأحدث الأول (من شهر البداية لحد الشهر ده أو شهر النهاية — آخر [SOURCE_MONTHS_MAX] بس) · [thisMonth] null = المصدر مش شغال
 * الشهر ده · [expectedMinor] null = المتوقع مش مكتوب ⇒ «غير متاح» (مش صفر).
 */
data class SourceIncome(
    val currency: Currency,
    val months: List<SourceMonth>,
    val thisMonth: SourceMonth?,
    val expectedMinor: Halalas?,
    val waitingCount: Int,
    /** §121-9: مجموع الإيداعات اللي نوعها لسه ما اتحددش (سطر لوحده — مش جوه [months] ولا [thisMonth]). */
    val waitingMinor: Halalas = 0,
)

const val SOURCE_MONTHS_MAX = 12

/** الإيداع ده محسوب «وصل» للمصدر ده. */
fun countsForSource(t: Transaction, source: IncomeSource, sources: List<IncomeSource>): Boolean {
    if (!t.economicKindConfirmed || attributedSourceId(t, sources) != source.id) return false
    return if (source.kind in SALARIED_KINDS) t.economicKind == EconomicKind.SALARY else countsAsIncome(t.economicKind)
}

fun sourceIncome(source: IncomeSource, sources: List<IncomeSource>, txns: List<Transaction>, payday: Int, today: IsoDate): SourceIncome {
    val mine = txns.filter { countsForSource(it, source, sources) }
    val waiting = txns.filter { !it.economicKindConfirmed && it.observedDirection == Direction.IN && attributedSourceId(it, sources) == source.id }
    val lastDay = listOfNotNull(source.endedAt, today).min()
    val first = periodForDate(source.startedAt, payday)
    val periods = ArrayList<Period>()
    var p = periodForDate(lastDay, payday)
    while (periods.size < SOURCE_MONTHS_MAX && p.start >= first.start) {
        periods += p
        p = periodForDate(dayNumberToIso(toDayNumber(parseIsoDate(p.start)) - 1), payday)
    }
    val months = periods.map { period ->
        // §75-3: الراتب اللي نزل قبل يوم الراتب بشوية بيتحسب للشهر الجديد ([countingDate]) — زي باقي المجاميع
        val inMonth = mine.filter { isDateInPeriod(countingDate(it, payday).take(10), period) }
        SourceMonth(period, inMonth.fold(0L) { sum, t -> addMoney(sum, t.amountMinor) }, inMonth.size)
    }
    val current = periodForDate(today, payday)
    val thisMonth = months.firstOrNull { it.period.key == current.key }?.takeIf { source.endedAt == null || source.endedAt >= current.start }
    // الأسبوعي: المتوقع مكتوب للقبضة الواحدة مش للشهر ⇒ «غير متاح» في سطر الشهر (ما بنضربش في عدد أسابيع مش متأكدين منه)
    val expected = source.expectedMinor.takeIf { source.payFrequency == PayFrequency.MONTHLY }
    return SourceIncome(source.currency, months, thisMonth, expected, waiting.size, waiting.fold(0L) { sum, t -> addMoney(sum, t.amountMinor) })
}
