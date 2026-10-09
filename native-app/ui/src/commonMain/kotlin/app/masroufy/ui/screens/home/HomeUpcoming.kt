package app.masroufy.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarItemType
import app.masroufy.core.DueFlow
import app.masroufy.core.ReservationState
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.reservationState
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.shell.UpcomingCard
import app.masroufy.ui.shell.UpcomingIcon
import app.masroufy.ui.shell.UpcomingStatus

/** «القادم» = أقرب [UPCOMING_COUNT] مواعيد في [UPCOMING_DAYS] يوم من `LoadCalendar.items` (الحالة من `reservationState` في `core`). */
private const val UPCOMING_DAYS = 45
private const val UPCOMING_COUNT = 6

/** null = لسه بيحمّل. فشل القراية ⇒ قايمة فاضية («لا مواعيد قادمة.») — مش بنخترع مواعيد. */
@Composable
internal fun rememberUpcomingCards(): List<UpcomingCard>? {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    var cards by remember(deps) { mutableStateOf<List<UpcomingCard>?>(null) }
    LaunchedEffect(deps) {
        val today = deps.shell.today()
        val until = dayNumberToIso(toDayNumber(parseIsoDate(today)) + UPCOMING_DAYS)
        val items = runCatching { deps.home.calendar.items(today, until, today) }.getOrDefault(emptyList())
        cards = items.filter { it.date >= today }.take(UPCOMING_COUNT).map { item -> item.toCard { nav.push(CalendarRoute) } }
    }
    return cards
}

private fun CalendarItem.toCard(onClick: () -> Unit): UpcomingCard {
    val icon = when (type) {
        CalendarItemType.RECURRING -> UpcomingIcon.SUBSCRIPTION
        CalendarItemType.INSTALLMENT -> UpcomingIcon.INSTALLMENT
        CalendarItemType.DEBT -> UpcomingIcon.DEBT
        CalendarItemType.EVENT -> UpcomingIcon.EVENT
        CalendarItemType.OCCASION, CalendarItemType.PUBLIC_OCCASION -> UpcomingIcon.OCCASION
        CalendarItemType.PAYDAY, CalendarItemType.INCOME_PAY -> UpcomingIcon.PAYDAY
        else -> UpcomingIcon.OTHER
    }
    val incoming = flow == DueFlow.RECEIVE || type == CalendarItemType.PAYDAY || type == CalendarItemType.INCOME_PAY
    val status = when {
        daysLeft < 0 -> UpcomingStatus.LATE
        incoming -> UpcomingStatus.INCOMING
        amountMinor == null -> UpcomingStatus.NO_AMOUNT
        else -> when (reservationState(this)) {
            ReservationState.COVERED, ReservationState.RESERVED -> UpcomingStatus.HELD
            ReservationState.PARTIAL, ReservationState.NOT_RESERVED -> UpcomingStatus.OPEN
        }
    }
    return UpcomingCard(
        key = "${type.wire}-$sourceId-$date",
        title = title,
        date = date,
        daysLeft = daysLeft,
        icon = icon,
        status = status,
        amountMinor = amountMinor,
        currency = currency,
        onClick = onClick,
    )
}
