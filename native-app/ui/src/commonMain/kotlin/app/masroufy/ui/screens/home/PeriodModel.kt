package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import app.masroufy.core.DateParts
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.daysBetween
import app.masroufy.core.formatIsoDate
import app.masroufy.core.monthName
import app.masroufy.core.monthYear
import app.masroufy.core.parseIsoDate
import app.masroufy.core.periodForDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.ui.text.t

/**
 * «اختر الشهر» (`PeriodPicker` — spec/02 · §30 · §31): الشهر المالي من يوم الراتب لليوم اللي قبله في الشهر اللي بعده (`core` `periodForDate`).
 * **اسم الشهر = الشهر اللي بيخلص فيه** (رد المالك آخر §76: 28 سبتمبر–27 أكتوبر = «أكتوبر»). ⚠️ مفتاح الفترة في كوتلن (`Period.key`) بشهر
 * البداية (`2026-09`) — ده عرض بس، والمفتاح زي ما هو. ما بيروحش للمستقبل، ولا قبل أول بياناتك (أقدم رصيد بداية لمحفظة).
 * كله تواريخ (أيام) — مفيش فلوس.
 */

private fun dayAfter(date: IsoDate): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(date)) + 1)

private fun dayBefore(date: IsoDate): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(date)) - 1)

/** الفترة اللي بتخلص في الشهر ده (= فترة أول يوم في الشهر، لأن البداية قبله أو فيه). */
fun periodEndingIn(year: Int, month: Int, payday: Int): Period = periodForDate(formatIsoDate(DateParts(year, month, 1)), payday)

fun previousPeriod(p: Period, payday: Int): Period = periodForDate(dayBefore(p.start), payday)

fun nextPeriod(p: Period, payday: Int): Period = periodForDate(dayAfter(p.end), payday)

/** «أكتوبر 2026» — بشهر النهاية. */
fun fiscalName(p: Period): String {
    val end = parseIsoDate(p.end)
    return monthYear(end.year, end.month)
}

/** «28 سبتمبر – 27 أكتوبر 2026» ([withYear]) — السنة بتتكتب جنب البداية كمان لو مختلفة. */
fun fiscalRange(p: Period, withYear: Boolean): String {
    val s = parseIsoDate(p.start)
    val e = parseIsoDate(p.end)
    val start = if (s.year != e.year) t(UiKey.PERIOD_PICKER_DAY_MONTH_YEAR, dayMonth(p.start), sentenceNumber(s.year)) else dayMonth(p.start)
    val end = if (withYear) t(UiKey.PERIOD_PICKER_DAY_MONTH_YEAR, dayMonth(p.end), sentenceNumber(e.year)) else dayMonth(p.end)
    return t(UiKey.PERIOD_PICKER_RANGE, start, end)
}

/** حالة خانة شهر في لوحة السنة. */
enum class TileState { OPEN, FUTURE, BEFORE_DATA }

data class PeriodTile(val month: Int, val name: String, val period: Period, val state: TileState, val isNow: Boolean, val selected: Boolean) {
    val range: String
        get() = when (state) {
            TileState.FUTURE -> t(UiKey.PERIOD_PICKER_NOT_STARTED)
            TileState.BEFORE_DATA -> t(UiKey.PERIOD_PICKER_BEFORE_DATA)
            TileState.OPEN -> fiscalRange(period, withYear = false)
        }
}

/** [dataStart] = أقدم يوم في بياناتك (null = مش معروف ⇒ مفيش حد من ورا). */
fun tileState(p: Period, current: Period, dataStart: IsoDate?): TileState = when {
    p.start > current.start -> TileState.FUTURE
    dataStart != null && p.end < dataStart -> TileState.BEFORE_DATA
    else -> TileState.OPEN
}

fun yearTiles(year: Int, payday: Int, current: Period, selected: Period, dataStart: IsoDate?): List<PeriodTile> = (1..12).map { m ->
    val p = periodEndingIn(year, m, payday)
    PeriodTile(m, monthName(m), p, tileState(p, current, dataStart), p.key == current.key, p.key == selected.key)
}

/** اليوم كام من كام في الشهر الحالي (للشريط) — و«بقي N» لحد الراتب. */
data class PeriodProgress(val day: Int, val days: Int) {
    val left: Int get() = days - day + 1
}

fun progressOf(current: Period, today: IsoDate): PeriodProgress = PeriodProgress(daysBetween(current.start, today) + 1, current.days)

/** «اليوم 10 من 30، بقي 21 يومًا على الراتب» / «كان 30 يومًا». */
fun periodNote(selected: Period, current: Period, today: IsoDate): String {
    if (selected.key != current.key) return t(UiKey.PERIOD_PICKER_WAS, daysWord(selected.days))
    val pr = progressOf(current, today)
    return t(UiKey.PERIOD_PICKER_NOW_NOTE, sentenceNumber(pr.day), sentenceNumber(pr.days), daysWord(pr.left))
}

/** «يوم واحد» · «يومان» · «5 أيام» · «21 يومًا». */
fun daysWord(n: Int): String = when {
    n == 1 -> t(UiKey.PERIOD_PICKER_DAYS_ONE)
    n == 2 -> t(UiKey.PERIOD_PICKER_DAYS_TWO)
    n <= 10 -> t(UiKey.PERIOD_PICKER_DAYS_FEW, sentenceNumber(n))
    else -> t(UiKey.PERIOD_PICKER_DAYS_MANY, sentenceNumber(n))
}
