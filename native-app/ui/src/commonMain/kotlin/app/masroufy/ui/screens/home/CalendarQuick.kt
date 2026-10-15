package app.masroufy.ui.screens.home

import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarItemType
import app.masroufy.core.DateParts
import app.masroufy.core.UiKey
import app.masroufy.core.dayMonth
import app.masroufy.core.formatIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.overlay.QuickMenu
import app.masroufy.ui.overlay.QuickMenuRequest
import app.masroufy.ui.overlay.QuickTarget
import app.masroufy.ui.screens.common.QuickAct
import app.masroufy.ui.screens.common.calendarDayQuickActs
import app.masroufy.ui.screens.common.calendarItemQuickActs
import app.masroufy.ui.screens.common.quickItems
import app.masroufy.ui.screens.dues.DebtDetailRoute
import app.masroufy.ui.screens.dues.InstallmentDetailRoute
import app.masroufy.ui.screens.dues.RoscaDetailRoute
import app.masroufy.ui.screens.dues.SettleSheetRoute
import app.masroufy.ui.screens.dues.SubscriptionDetailRoute
import app.masroufy.ui.screens.people.EventAddSheetRoute
import app.masroufy.ui.screens.people.EventDetailRoute
import app.masroufy.ui.screens.people.EventPrepRoute
import app.masroufy.ui.screens.people.ProjectDetailRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Type

/**
 * القايمة السريعة في التقويم (قايمة المالك §6.5-13 «وفي الكالندر بردو»): الضغطة الطويلة على **يوم** ⇒ «مواعيد اليوم» (زي الضغطة)
 * و«حدث جديد في اليوم ده» (لوحة «حدث جديد» بتاريخه) · على **ميعاد** ⇒ صفحته (بالرقم اللي في السطر) · «سجّل سداد» للدين ·
 * «التجهيزات» للحدث · القسم (= الضغطة العادية). كل فعل بيفتح شاشة أو لوحة موجودة.
 */
internal fun calendarDayRequest(
    anchor: Anchor,
    year: Int,
    month: Int,
    day: Int,
    canAddEvent: Boolean,
    nav: Navigator,
    onShowDay: (Int) -> Unit,
): QuickMenuRequest {
    val date = formatIsoDate(DateParts(year, month, day))
    val items = quickItems(calendarDayQuickActs(canAddEvent)) { act ->
        when (act) {
            QuickAct.SHOW_DAY -> onShowDay(day)
            QuickAct.ADD_EVENT_ON_DAY -> nav.open(EventAddSheetRoute(date))
            else -> Unit
        }
    }
    return QuickMenuRequest(anchor, t(UiKey.CALENDAR_DAY_TITLE, dayMonth(date)), items, liftedPadding = 2.dp) {
        BasicText(sentenceNumber(day), style = Type.of(15, FontWeight.Bold))
    }
}

/** صفحة الميعاد نفسه (لو ليه) — وإلا القسم زي الضغطة العادية. */
internal fun openCalendarPage(nav: Navigator, item: CalendarItem, openSection: (CalendarItemType) -> Unit) {
    when (item.type) {
        CalendarItemType.RECURRING -> nav.push(SubscriptionDetailRoute(item.sourceId))
        CalendarItemType.INSTALLMENT -> nav.push(InstallmentDetailRoute(item.sourceId))
        CalendarItemType.ROSCA_CONTRIBUTION, CalendarItemType.ROSCA_PAYOUT -> nav.push(RoscaDetailRoute(item.sourceId))
        CalendarItemType.DEBT -> nav.push(DebtDetailRoute(item.sourceId))
        CalendarItemType.EVENT -> nav.push(EventDetailRoute(item.sourceId))
        CalendarItemType.PROJECT -> nav.push(ProjectDetailRoute(item.sourceId))
        else -> openSection(item.type)
    }
}

/** ميعاد في التقويم بقايمته السريعة (الكارت الملخص ومواعيد اليوم). الضغطة العادية زي ما هي ([onOpen] = القسم). */
@Composable
internal fun CalendarItemRow(item: CalendarItem, quick: QuickMenu, nav: Navigator, onOpen: (CalendarItemType) -> Unit) {
    val row = calRowOf(item)
    val items = {
        quickItems(calendarItemQuickActs(item.type)) { act ->
            when (act) {
                QuickAct.OPEN -> openCalendarPage(nav, item, onOpen)
                QuickAct.SETTLE -> nav.open(SettleSheetRoute(item.sourceId))
                QuickAct.PREP -> nav.push(EventPrepRoute(item.sourceId))
                QuickAct.OPEN_SECTION -> onOpen(item.type)
                else -> Unit
            }
        }
    }
    QuickTarget(quick, row.title, items, subtitle = row.sub) { long ->
        CalRowView(row, { type -> quick.guardTap { onOpen(type) }() }, onLong = long)
    }
}
