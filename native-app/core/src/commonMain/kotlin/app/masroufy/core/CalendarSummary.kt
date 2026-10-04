package app.masroufy.core

/**
 * الملخص الذكي فوق التقويم (OVERRIDES §65 — اختيار Claude، المالك يقدر يغيّره): لحد المرتب الجاي عليك كام · الأسبوع الأتقل في
 * الشهر · ميعاد قبل المرتب بأيام قليلة · حدث أو مناسبة قرّبت ومالهاش مبلغ محجوز.
 * كله **أعداد صحيحة**، و**المبلغ المجهول عمره ما بيتحسب صفر**: بيتعد لوحده. والحجز ما بيدخلش في أي مجموع هنا.
 */

/** «قبل المرتب بأيام قليلة» = من يوم لـ3 أيام قبله. */
const val BEFORE_PAYDAY_DAYS = 3

/** «قرّب» للحدث والمناسبة اللي مالهاش حجز = خلال 30 يوم. */
const val UNRESERVED_SOON_DAYS = 30

/** «أسبوع» = 7 أيام من أول الشهر المالي (والأخير ممكن يبقى أقصر). */
const val CALENDAR_WEEK_DAYS = 7

data class PaydayOutlook(
    val nextPayday: IsoDate,
    /** مواعيد دفع بمبلغ معروف بعملة المساحة، من النهارده لحد قبل المرتب. */
    val knownCount: Int,
    val knownTotalMinor: Halalas,
    /** مواعيد دفع **من غير مبلغ معروف** — بتتعد لوحدها ومش بتتحسب صفر. */
    val unknownAmountCount: Int,
    /** مواعيد دفع بعملة تانية — ما بتتجمعش ولا بتتحوّل. */
    val otherCurrencyCount: Int,
)

data class HeavyWeek(val number: Int, val start: IsoDate, val end: IsoDate, val totalMinor: Halalas)

data class SmartSummary(
    /** null = مفيش يوم مرتب (مفيش ملف حساب). */
    val untilPayday: PaydayOutlook?,
    /** null = مفيش ولا مبلغ دفع معروف في الشهر. */
    val heaviestWeek: HeavyWeek?,
    /** مواعيد دفع قبل المرتب بيوم لـ3 أيام. */
    val beforePayday: List<CalendarItem>,
    /** أحداث ومناسبات (شخصية وعامة) خلال 30 يوم ومالهاش مبلغ محجوز. */
    val unreservedSoon: List<CalendarItem>,
)

private fun isPay(item: CalendarItem) = item.flow == DueFlow.PAY

/** من [today] لحد اليوم اللي قبل [nextPayday]. */
fun paydayOutlook(items: List<CalendarItem>, today: IsoDate, nextPayday: IsoDate, currency: Currency): PaydayOutlook {
    val window = items.filter { isPay(it) && it.date >= today && it.date < nextPayday }
    val local = window.filter { it.currency == currency }
    val known = local.mapNotNull { it.amountMinor }
    return PaydayOutlook(
        nextPayday = nextPayday,
        knownCount = known.size,
        knownTotalMinor = sumMoney(known),
        unknownAmountCount = local.count { it.amountMinor == null },
        otherCurrencyCount = window.size - local.size,
    )
}

/** الأسبوع اللي فيه أكبر مجموع مواعيد دفع **معروفة المبلغ** في [period] (التساوي ⇒ الأبدر). */
fun heaviestWeek(items: List<CalendarItem>, period: Period, currency: Currency): HeavyWeek? {
    val start = toDayNumber(parseIsoDate(period.start))
    val end = toDayNumber(parseIsoDate(period.end))
    var best: HeavyWeek? = null
    var weekStart = start
    var number = 1
    while (weekStart <= end) {
        val weekEnd = minOf(weekStart + CALENDAR_WEEK_DAYS - 1, end)
        val from = dayNumberToIso(weekStart)
        val to = dayNumberToIso(weekEnd)
        val known = items.filter { isPay(it) && it.currency == currency && it.date in from..to }.mapNotNull { it.amountMinor }
        val total = sumMoney(known)
        if (known.isNotEmpty() && total > 0 && (best == null || total > best.totalMinor)) best = HeavyWeek(number, from, to, total)
        weekStart += CALENDAR_WEEK_DAYS
        number++
    }
    return best
}

/** مواعيد دفع بين يوم و3 أيام قبل المرتب (ومن النهارده وطالع). */
fun dueShortlyBeforePayday(items: List<CalendarItem>, today: IsoDate, nextPayday: IsoDate): List<CalendarItem> =
    items.filter { isPay(it) && it.date >= today && daysBetween(it.date, nextPayday) in 1..BEFORE_PAYDAY_DAYS }

private val PLANNABLE = setOf(CalendarItemType.EVENT, CalendarItemType.OCCASION, CalendarItemType.PUBLIC_OCCASION)

/** حدث أو مناسبة خلال 30 يوم (النهارده محسوب) ومالهاش حجز. */
fun unreservedSoon(items: List<CalendarItem>): List<CalendarItem> =
    items.filter { it.type in PLANNABLE && it.daysLeft in 0..UNRESERVED_SOON_DAYS && it.reservedMinor == null }

/**
 * الملخص كله. [items] لازم تغطي من [today] لحد بعد المرتب الجاي وآخر [period] و30 يوم قدام (`LoadCalendar.summary` بيعمل كده).
 * [payday] null ⇒ مفيش «لحد المرتب» ولا «قبل المرتب».
 */
fun smartSummary(items: List<CalendarItem>, today: IsoDate, payday: Int?, period: Period, currency: Currency): SmartSummary {
    val next = payday?.let { nextPaydayAfter(today, it) }
    return SmartSummary(
        untilPayday = next?.let { paydayOutlook(items, today, it, currency) },
        heaviestWeek = heaviestWeek(items, period, currency),
        beforePayday = next?.let { dueShortlyBeforePayday(items, today, it) }.orEmpty(),
        unreservedSoon = unreservedSoon(items),
    )
}
