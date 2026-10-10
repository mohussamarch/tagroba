package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.ui.graphics.Color
import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarItemType
import app.masroufy.core.DueFlow
import app.masroufy.core.IsoDate
import app.masroufy.core.ReservationState
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysBetween
import app.masroufy.core.parseIsoDate
import app.masroufy.core.reservationState
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.shell.CalendarMark
import app.masroufy.ui.shell.MarkKind
import app.masroufy.ui.shell.UpcomingIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink

/**
 * التقويم (`Calendar` — §65 + رد المالك آخر §76: «ملخص أحداث أكتر ما هو ملخص فلوس»): الأحداث القادمة بجملة ذكية + أقرب ٦ في ٣٠ يوم
 * وجنب كل واحد سطر صغير «محجوز ✓ / غير محجوز / بلا مبلغ / ستستلمه / فات موعده» ⇒ «حدث جديد» ⇒ شبكة الشهر ⇒ مواعيد اليوم المختار.
 * المواعيد والمبالغ والحجز من `LoadCalendar` (`reservationState` من `core`) — هنا ترتيب وعدّ وكلام بس.
 */
const val CALENDAR_WINDOW_DAYS = 30
const val CALENDAR_SHOWN = 6

/** النبرة الصغيرة: أحمر فات · أخضر محجوز/هتستلم · كهرماني غير محجوز · رمادي بلا مبلغ. */
enum class CalTone(val color: Color) { LATE(Ink.expense), GOOD(Ink.income), OPEN(Ink.focus), QUIET(Ink.muted) }

data class CalRow(
    val key: String,
    val title: String,
    /** «١٤ أكتوبر، مناسبة». */
    val sub: String,
    /** «بعد يومين» / «منذ ٣ أيام» (لو فات). */
    val whenText: String,
    val whenTone: CalTone,
    val status: String,
    val statusTone: CalTone,
    val icon: UpcomingIcon,
    val type: CalendarItemType,
)

/** فات ميعاده ولسه عليك تدفعه (مش اللي هتستلمه ولا من غير فلوس). */
fun isLate(item: CalendarItem): Boolean = item.daysLeft < 0 && item.flow == DueFlow.PAY

fun iconOfType(type: CalendarItemType): UpcomingIcon = when (type) {
    CalendarItemType.RECURRING -> UpcomingIcon.SUBSCRIPTION
    CalendarItemType.INSTALLMENT -> UpcomingIcon.INSTALLMENT
    CalendarItemType.ROSCA_CONTRIBUTION, CalendarItemType.ROSCA_PAYOUT, CalendarItemType.DEBT -> UpcomingIcon.DEBT
    CalendarItemType.EVENT, CalendarItemType.PROJECT -> UpcomingIcon.EVENT
    CalendarItemType.OCCASION, CalendarItemType.PUBLIC_OCCASION -> UpcomingIcon.OCCASION
    CalendarItemType.PAYDAY, CalendarItemType.INCOME_PAY -> UpcomingIcon.PAYDAY
    CalendarItemType.ZAKAT -> UpcomingIcon.OTHER
}

private fun typeLabel(type: CalendarItemType): String = t(
    when (type) {
        CalendarItemType.RECURRING -> UiKey.CALENDAR_TYPE_SUB
        CalendarItemType.INSTALLMENT -> UiKey.CALENDAR_TYPE_INST
        CalendarItemType.ROSCA_CONTRIBUTION, CalendarItemType.ROSCA_PAYOUT -> UiKey.CALENDAR_TYPE_ROSCA
        CalendarItemType.DEBT -> UiKey.CALENDAR_TYPE_DEBT
        CalendarItemType.EVENT, CalendarItemType.PROJECT -> UiKey.CALENDAR_TYPE_EVENT
        CalendarItemType.OCCASION, CalendarItemType.PUBLIC_OCCASION -> UiKey.CALENDAR_TYPE_OCCASION
        CalendarItemType.PAYDAY, CalendarItemType.INCOME_PAY -> UiKey.CALENDAR_TYPE_PAY
        CalendarItemType.ZAKAT -> UiKey.CALENDAR_TYPE_ZAKAT
    },
)

/** «اليوم» · «غدًا» · «بعد يومين» · «بعد ٥ أيام» · «بعد ١٢ يومًا» — و«منذ …» لو فات. */
fun afterText(days: Int): String = when {
    days == 0 -> t(UiKey.CALENDAR_WHEN_TODAY)
    days == 1 -> t(UiKey.CALENDAR_WHEN_TOMORROW)
    days == 2 -> t(UiKey.CALENDAR_WHEN_TWO)
    days in 3..10 -> t(UiKey.CALENDAR_WHEN_FEW, sentenceNumber(days))
    days > 10 -> t(UiKey.CALENDAR_WHEN_MANY, sentenceNumber(days))
    days == -1 -> t(UiKey.CALENDAR_AGO_ONE)
    days == -2 -> t(UiKey.CALENDAR_AGO_TWO)
    days >= -10 -> t(UiKey.CALENDAR_AGO_FEW, sentenceNumber(-days))
    else -> t(UiKey.CALENDAR_AGO_MANY, sentenceNumber(-days))
}

fun calRowOf(item: CalendarItem): CalRow {
    val money = item.amountMinor?.let { t(UiKey.CALENDAR_WITH_AMOUNT, amountLabel(it, item.currency, AmountTone.PLAIN)) }.orEmpty()
    val (status, tone) = when {
        isLate(item) -> t(UiKey.CALENDAR_LATE) to CalTone.LATE
        item.daysLeft < 0 -> t(UiKey.CALENDAR_PASSED) to CalTone.QUIET
        item.flow == DueFlow.RECEIVE || item.type == CalendarItemType.PAYDAY || item.type == CalendarItemType.INCOME_PAY ->
            (t(UiKey.CALENDAR_INCOMING) + money) to CalTone.GOOD
        item.amountMinor == null -> t(UiKey.CALENDAR_NO_AMOUNT) to CalTone.QUIET
        reservationState(item).let { it == ReservationState.COVERED || it == ReservationState.RESERVED } ->
            (t(UiKey.CALENDAR_HELD) + money) to CalTone.GOOD
        else -> (t(UiKey.CALENDAR_NOT_HELD) + money) to CalTone.OPEN
    }
    val near = item.daysLeft in 0..3
    return CalRow(
        key = "${item.type.wire}-${item.sourceId}-${item.date}",
        title = item.title,
        sub = t(UiKey.CALENDAR_SUB, dayMonth(item.date), typeLabel(item.type)),
        whenText = afterText(item.daysLeft),
        whenTone = if (isLate(item)) CalTone.LATE else if (near) CalTone.OPEN else CalTone.QUIET,
        status = status,
        statusTone = tone,
        icon = iconOfType(item.type),
        type = item.type,
    )
}

/** الجاي بالترتيب: من النهارده + اللي فات ولسه عليك تدفعه (مكانه المستحقات، بس بيبان هنا أحمر). */
fun upcomingOf(items: List<CalendarItem>): List<CalendarItem> = items.filter { it.daysLeft >= 0 || isLate(it) }.sortedBy { it.date }

/** المعروض في الملخص: أقرب [CALENDAR_SHOWN] في [CALENDAR_WINDOW_DAYS] يوم، أو الكل لو «كل القادم». */
fun shownOf(upcoming: List<CalendarItem>, all: Boolean): List<CalendarItem> =
    if (all) upcoming else upcoming.filter { it.daysLeft <= CALENDAR_WINDOW_DAYS }.take(CALENDAR_SHOWN)

/** «موعد واحد» · «موعدان» · «٥ مواعيد» · «١٢ موعدًا». */
fun datesCount(n: Int): String = when {
    n == 1 -> t(UiKey.CALENDAR_COUNT_ONE)
    n == 2 -> t(UiKey.CALENDAR_COUNT_TWO)
    n <= 10 -> t(UiKey.CALENDAR_COUNT_FEW, sentenceNumber(n))
    else -> t(UiKey.CALENDAR_COUNT_MANY, sentenceNumber(n))
}

/**
 * الجملة الذكية: الأقرب · كام ميعاد في الـ٣٠ يوم ومنهم كام دفع مش محجوز · ميعاد دفع قبل الراتب على طول (من `SmartSummary.beforePayday`).
 * [nextPayday] من `SmartSummary.untilPayday`. فاضي ⇒ null.
 */
fun smartSentence(upcoming: List<CalendarItem>, beforePayday: List<CalendarItem>, nextPayday: IsoDate?): String? {
    val ahead = upcoming.filter { it.daysLeft >= 0 }
    val parts = mutableListOf<String>()
    ahead.firstOrNull()?.let { parts += t(UiKey.CALENDAR_SMART_NEAREST, it.title, afterText(it.daysLeft)) }
    val window = ahead.filter { it.daysLeft <= CALENDAR_WINDOW_DAYS }
    if (window.isNotEmpty()) {
        val open = window.count { it.flow == DueFlow.PAY && it.amountMinor != null && reservationState(it).let { s -> s == ReservationState.NOT_RESERVED || s == ReservationState.PARTIAL } }
        parts += if (open > 0) t(UiKey.CALENDAR_SMART_WINDOW_OPEN, datesCount(window.size), sentenceNumber(open))
        else t(UiKey.CALENDAR_SMART_WINDOW, datesCount(window.size))
    }
    val before = beforePayday.firstOrNull { it.daysLeft >= 0 }
    if (before != null && nextPayday != null) {
        val gap = daysBetween(before.date, nextPayday)
        // «قبل الراتب بيوم» بالظبط زي النموذج، وغيره (يومين أو ٣ — `beforePayday`) «قبل الراتب بأيام»
        parts += if (gap <= 1) t(UiKey.CALENDAR_SMART_BEFORE_PAY_ONE, before.title) else t(UiKey.CALENDAR_SMART_BEFORE_PAY, before.title)
    }
    return if (parts.isEmpty()) null else parts.joinToString(t(UiKey.CALENDAR_SMART_JOIN)) + t(UiKey.CALENDAR_SMART_END)
}

/** علامات الشبكة لشهر: اسم قصير (أول كلمة) بلون النوع، واللي فات وعليك أحمر. */
fun marksOf(items: List<CalendarItem>, year: Int, month: Int): List<CalendarMark> = items.mapNotNull { item ->
    val p = parseIsoDate(item.date)
    if (p.year != year || p.month != month) return@mapNotNull null
    val kind = when (item.type) {
        CalendarItemType.RECURRING -> MarkKind.SUBSCRIPTION
        CalendarItemType.INSTALLMENT -> MarkKind.INSTALLMENT
        CalendarItemType.ROSCA_CONTRIBUTION, CalendarItemType.ROSCA_PAYOUT -> MarkKind.ROSCA
        CalendarItemType.DEBT -> MarkKind.DEBT
        CalendarItemType.PAYDAY, CalendarItemType.INCOME_PAY -> MarkKind.PAYDAY
        else -> MarkKind.EVENT
    }
    CalendarMark(p.day, if (isLate(item)) MarkKind.LATE else kind, item.title.trim().substringBefore(' '), isLate(item))
}
