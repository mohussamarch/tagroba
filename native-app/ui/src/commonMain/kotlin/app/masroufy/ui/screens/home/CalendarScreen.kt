package app.masroufy.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.CalendarItem
import app.masroufy.core.CalendarItemType
import app.masroufy.core.DateParts
import app.masroufy.core.IsoDate
import app.masroufy.core.SmartSummary
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.formatIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.CalendarGrid
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** الجاي بيتقري ٩٠ يوم قدام و٣١ ورا (اللي فات ولسه عليك يبان أحمر). */
private const val AHEAD_DAYS = 90
private const val BEHIND_DAYS = 31

/**
 * التقويم: «الأحداث القادمة» (الجملة الذكية + أقرب ٦ + «كل القادم») ⇒ «حدث جديد» (`EventAddSheet` بيوم الشبكة المختار) ⇒ الشبكة ⇒ مواعيد اليوم.
 * الضغط على ميعاد ⇒ تبويب مصدره (الاشتراكات والأقساط والجمعيات والديون في «العمليات»/المستحقات · المناسبات والأحداث في «الأشخاص» · الزكاة في
 * «الاستثمار») — ⚠️ صفحة المصدر نفسها بتتوصل وقت الدمج.
 */
@Composable
fun CalendarScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val today = deps.shell.today()
    val now = parseIsoDate(today)
    var year by rememberSaveable { mutableStateOf(now.year) }
    var month by rememberSaveable { mutableStateOf(now.month) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var all by rememberSaveable { mutableStateOf(false) }
    var upcoming by remember(deps) { mutableStateOf<List<CalendarItem>?>(null) }
    var summary by remember(deps) { mutableStateOf<SmartSummary?>(null) }
    var monthItems by remember(deps) { mutableStateOf<List<CalendarItem>>(emptyList()) }
    LaunchedEffect(deps) {
        val day = toDayNumber(now)
        val items = runCatching { deps.home.calendar.items(dayNumberToIso(day - BEHIND_DAYS), dayNumberToIso(day + AHEAD_DAYS), today) }.getOrDefault(emptyList())
        summary = runCatching { deps.home.calendar.summary(today) }.getOrNull()
        upcoming = upcomingOf(items)
    }
    LaunchedEffect(deps, year, month) {
        monthItems = runCatching { deps.home.calendar.month(year, month, today).items }.getOrDefault(emptyList())
    }
    InnerScaffold(t(TextKey.CALENDAR_TITLE)) {
        val ups = upcoming
        when {
            ups == null -> item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }
            ups.isEmpty() -> item(key = "empty") { EmptyState(t(TextKey.CALENDAR_EMPTY_TITLE), t(TextKey.CALENDAR_EMPTY_BODY)) }
            else -> item(key = "summary") {
                SummaryCard(ups, summary, all, onToggle = { all = !all }, onOpen = { openSource(nav, it) })
            }
        }
        item(key = "add") {
            val date = selected?.let { formatIsoDate(DateParts(year, month, it)) }
            PrimaryButton(t(TextKey.CALENDAR_ADD_EVENT), onClick = { nav.open(EventAddSheetRoute(date)) }, modifier = Modifier.fillMaxWidth(), height = 52.dp, leading = Lucide.PLUS)
        }
        item(key = "grid") {
            CalendarGrid(
                year = year, month = month, today = today, marks = marksOf(monthItems, year, month), selectedDay = selected,
                onPick = { d -> selected = if (selected == d) null else d },
                onMonth = { delta ->
                    val total = year * 12 + (month - 1) + delta
                    year = total.floorDiv(12)
                    month = total.mod(12) + 1
                    selected = null
                },
                onToday = { year = now.year; month = now.month; selected = null },
            )
        }
        item(key = "day") { DayBlock(year, month, selected, monthItems, onClear = { selected = null }, onOpen = { openSource(nav, it) }) }
    }
}

/** فين بيودّي الميعاد (أقرب تبويب لصفحة مصدره). */
private fun openSource(nav: app.masroufy.ui.nav.Navigator, type: CalendarItemType) {
    when (type) {
        CalendarItemType.OCCASION, CalendarItemType.EVENT, CalendarItemType.PROJECT -> nav.switchTab(Tab.PEOPLE)
        CalendarItemType.ZAKAT, CalendarItemType.PUBLIC_OCCASION -> nav.switchTab(Tab.INVESTMENT)
        CalendarItemType.PAYDAY, CalendarItemType.INCOME_PAY -> nav.push(app.masroufy.ui.screens.more.MoreRoute)
        else -> nav.switchTab(Tab.OPERATIONS)
    }
}

@Composable
private fun SummaryCard(ups: List<CalendarItem>, summary: SmartSummary?, all: Boolean, onToggle: () -> Unit, onOpen: (CalendarItemType) -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(Lucide.SPARKLES, size = 18.dp, tint = Ink.primary)
                BasicText(t(TextKey.CALENDAR_UPCOMING), style = Type.of(16, FontWeight.Bold))
            }
            smartSentence(ups, summary?.beforePayday.orEmpty(), summary?.untilPayday?.nextPayday)?.let {
                BasicText(it, style = Type.of(13).copy(color = Ink.soft))
            }
            val shown = shownOf(ups, all)
            shown.forEachIndexed { i, item ->
                CalRowView(calRowOf(item), onOpen)
                if (i < shown.lastIndex) Divider()
            }
            if (ups.size > shownOf(ups, false).size) {
                TonalButton(
                    if (all) t(TextKey.CALENDAR_LESS) else t(TextKey.CALENDAR_ALL, sentenceNumber(ups.size)),
                    onClick = onToggle, modifier = Modifier.fillMaxWidth(), height = 44.dp,
                )
            }
        }
    }
}

@Composable
internal fun CalRowView(row: CalRow, onOpen: (CalendarItemType) -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().pressScale(press).tap(press, onClick = { onOpen(row.type) }).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(row.icon.color, size = 40.dp, radius = 13.dp) { LucideIcon(row.icon.icon, size = 20.dp, tint = row.icon.color) }
        Column(Modifier.weight(1f)) {
            BasicText(row.title, style = Type.bodyBold(), maxLines = 1)
            BasicText(row.sub, style = Type.caption().copy(color = Ink.muted), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                row.whenText,
                Modifier.clip(RoundedCornerShape(10.dp)).background(row.whenTone.color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 2.dp),
                style = Type.of(11, FontWeight.Bold).copy(color = row.whenTone.color),
            )
            BasicText(row.status, style = Type.of(11, FontWeight.Bold).copy(color = row.statusTone.color), maxLines = 1)
        }
    }
}

/** «اضغط يومًا لترى مواعيده» ⇒ «يوم ١٤ أكتوبر» + مواعيده (أو «لا مواعيد في هذا اليوم»). */
@Composable
private fun DayBlock(year: Int, month: Int, selected: Int?, monthItems: List<CalendarItem>, onClear: () -> Unit, onOpen: (CalendarItemType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val title = if (selected == null) t(TextKey.CALENDAR_PICK_DAY) else t(TextKey.CALENDAR_DAY_TITLE, dayMonth(formatIsoDate(DateParts(year, month, selected))))
            BasicText(title, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
            if (selected != null) TonalButton(t(TextKey.CALENDAR_CLEAR), onClick = onClear, height = 44.dp)
        }
        if (selected == null) return@Column
        val date: IsoDate = formatIsoDate(DateParts(year, month, selected))
        val day = monthItems.filter { it.date == date }
        if (day.isEmpty()) BasicText(t(TextKey.CALENDAR_DAY_EMPTY), style = Type.of(13).copy(color = Ink.muted))
        for (item in day) FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { CalRowView(calRowOf(item), onOpen) }
    }
}
