package app.masroufy.core

/**
 * الشهر المالي — نقل `src/domain/period.ts` (spec/02).
 * البداية يوم الراتب (29–31 بيتقيد بآخر يوم في الشهر)، والنهاية اليوم اللي قبل بداية الفترة الجاية.
 * كله أعداد صحيحة على التقويم الميلادي — من غير منطقة زمنية ولا تاريخ الجهاز.
 */
typealias IsoDate = String

const val DEFAULT_PAYDAY = 28

data class Period(
    /** "2026-09" = الفترة اللي بتبدأ في سبتمبر 2026. */
    val key: String,
    /** أول يوم (شامل). */
    val start: IsoDate,
    /** آخر يوم (شامل). */
    val end: IsoDate,
    /** عدد الأيام — محسوب مش 30 ثابتة. */
    val days: Int,
)

data class DateParts(val year: Int, val month: Int, val day: Int)

class PeriodError(message: String) : IllegalArgumentException(message)

fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

private val MONTH_LENGTHS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

fun daysInMonth(year: Int, month: Int): Int {
    if (month < 1 || month > 12) throw PeriodError("شهر غير صالح: $month")
    if (month == 2 && isLeapYear(year)) return 29
    return MONTH_LENGTHS[month - 1]
}

/** YYYY-MM-DD بالظبط، وبيرفض التاريخ المستحيل صراحة (spec/05). */
fun parseIsoDate(value: String): DateParts {
    val ok = value.length == 10 && value[4] == '-' && value[7] == '-' &&
        (0 until 10).all { it == 4 || it == 7 || JsText.isAsciiDigit(value[it]) }
    if (!ok) throw PeriodError("صيغة التاريخ غير صالحة: $value")
    val year = value.substring(0, 4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    if (month < 1 || month > 12) throw PeriodError("شهر غير صالح في: $value")
    val max = daysInMonth(year, month)
    if (day < 1 || day > max) throw PeriodError("يوم غير موجود: $value ($month فيه $max يوم)")
    return DateParts(year, month, day)
}

fun isValidIsoDate(value: String): Boolean = runCatching { parseIsoDate(value) }.isSuccess

fun formatIsoDate(parts: DateParts): IsoDate =
    "${parts.year}-${parts.month.toString().padStart(2, '0')}-${parts.day.toString().padStart(2, '0')}"

/** رقم اليوم من 1970-01-01 (خوارزمية Howard Hinnant). */
fun toDayNumber(parts: DateParts): Int {
    val (year, month, day) = parts
    val y = if (month <= 2) year - 1 else year
    val era = y.floorDiv(400)
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

fun dayNumberToIso(dayNumber: Int): IsoDate {
    val z = dayNumber + 719468
    val era = z.floorDiv(146097)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = if (month <= 2) y + 1 else y
    return formatIsoDate(DateParts(year, month, day))
}

fun daysBetween(from: IsoDate, to: IsoDate): Int = toDayNumber(parseIsoDate(to)) - toDayNumber(parseIsoDate(from))

/** فبراير 2028 ويوم راتب 31 ⇒ 29 فبراير، مش «31 فبراير». */
fun clampPaydayToMonth(year: Int, month: Int, payday: Int): Int {
    if (payday < 1 || payday > 31) throw PeriodError("يوم الراتب لازم يكون بين 1 و31، مش $payday")
    return minOf(payday, daysInMonth(year, month))
}

private fun addMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
    val total = year * 12 + (month - 1) + delta
    return total.floorDiv(12) to total.mod(12) + 1
}

/** الفترة اللي مفتاحها YYYY-MM (شهر البداية). */
fun buildPeriod(year: Int, month: Int, payday: Int = DEFAULT_PAYDAY): Period {
    val startDay = clampPaydayToMonth(year, month, payday)
    val (nextYear, nextMonth) = addMonth(year, month, 1)
    val nextStartDay = clampPaydayToMonth(nextYear, nextMonth, payday)
    val start = formatIsoDate(DateParts(year, month, startDay))
    val nextStart = formatIsoDate(DateParts(nextYear, nextMonth, nextStartDay))
    val endDayNumber = toDayNumber(parseIsoDate(nextStart)) - 1
    return Period(
        key = "$year-${month.toString().padStart(2, '0')}",
        start = start,
        end = dayNumberToIso(endDayNumber),
        days = endDayNumber - toDayNumber(parseIsoDate(start)) + 1,
    )
}

/** الفترة اللي فيها التاريخ — قبل يوم الراتب يبقى لسه في فترة الشهر اللي فات. */
fun periodForDate(date: IsoDate, payday: Int = DEFAULT_PAYDAY): Period {
    val (year, month, day) = parseIsoDate(date)
    if (day < clampPaydayToMonth(year, month, payday)) {
        val (prevYear, prevMonth) = addMonth(year, month, -1)
        return buildPeriod(prevYear, prevMonth, payday)
    }
    return buildPeriod(year, month, payday)
}

fun isDateInPeriod(date: IsoDate, period: Period): Boolean {
    val d = toDayNumber(parseIsoDate(date))
    return d >= toDayNumber(parseIsoDate(period.start)) && d <= toDayNumber(parseIsoDate(period.end))
}

/** الأيام الباقية من `today` شاملة اليوم نفسه («المتاح اليومي») — 0 بعد نهاية الفترة. */
fun remainingDaysInPeriod(today: IsoDate, period: Period): Int {
    val t = toDayNumber(parseIsoDate(today))
    val end = toDayNumber(parseIsoDate(period.end))
    val start = toDayNumber(parseIsoDate(period.start))
    if (t > end) return 0
    if (t < start) return period.days
    return end - t + 1
}

fun formatPeriodRange(period: Period): String = "${period.start} ← ${period.end}"

/** سنة × 12 + الشهر — للمقارنة بين الفترات بس. */
fun periodIndex(period: Period): Int {
    val (year, month) = period.key.split('-').map { it.toInt() }
    return year * 12 + (month - 1)
}

/** إزاحة شهور **من غير ما تعدّي آخر فترة مسموحة** (OVERRIDES §31: مفيش مستقبل). */
fun shiftPeriodWithin(period: Period, delta: Int, latest: Period, payday: Int = DEFAULT_PAYDAY): Period {
    val target = minOf(periodIndex(period) + delta, periodIndex(latest))
    return buildPeriod(target.floorDiv(12), target.mod(12) + 1, payday)
}
