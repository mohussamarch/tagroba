package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
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
import app.masroufy.ui.screens.people.EventAddSheetRoute
import app.masroufy.ui.shell.CalendarGrid
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** الجاي بيتقري ٩٠ يوم قدام و٣١ ورا (اللي فات ولسه عليك يبان أحمر). */
private const val AHEAD_DAYS = 90
private const val BEHIND_DAYS = 31

/**
 * لوحة «حدث جديد» (`EventAddSheet`) بتاعة منطقة «الأشخاص» ومش متسجّلة على الفرع ده ⇒ الزرار **مقفول** وتحته «إضافة الأحداث غير متاحة بعد»
 * (من غير القفل كان بيفتح «قيد البناء» وهو شكله شغال — CLAUDE.md #15). ⚠️ وقت الدمج: `true` + `nav.open(people.EventAddSheetRoute(date))`
 * بدل المسار المؤقت هنا (والنص «لا مواعيد…» بيرجع يقول «تقدر تضيف حدث من الزرار اللي فوق» لوحده).
 */
private const val EVENT_ADD_READY = true

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
    // الفشل حالة لوحدها — مش «لا أحداث قادمة» ولا «لا مواعيد في هذا اليوم» (CLAUDE.md #10): شريط «تعذّر التحميل» + «أعد المحاولة»
    var upcoming by remember(deps) { mutableStateOf<ReadState<UpcomingRead>>(ReadState.Loading) }
    var monthRead by remember(deps) { mutableStateOf<ReadState<List<CalendarItem>>>(ReadState.Loading) }
    var tick by remember { mutableStateOf(0) }
    var monthTick by remember { mutableStateOf(0) }
    LaunchedEffect(deps, tick) {
        val day = toDayNumber(now)
        upcoming = readState {
            val items = deps.home.calendar.items(dayNumberToIso(day - BEHIND_DAYS), dayNumberToIso(day + AHEAD_DAYS), today)
            UpcomingRead(upcomingOf(items), deps.home.calendar.summary(today))
        }
    }
    LaunchedEffect(deps, year, month, monthTick) {
        monthRead = readState { deps.home.calendar.month(year, month, today).items }
    }
    InnerScaffold(t(UiKey.CALENDAR_TITLE)) {
        when (val u = upcoming) {
            ReadState.Loading -> item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(220.dp)) }
            ReadState.Failed -> item(key = "failed") { HomeErrorBanner { upcoming = ReadState.Loading; tick++ } }
            is ReadState.Ready -> if (u.value.items.isEmpty()) {
                item(key = "empty") { EmptyState(t(UiKey.CALENDAR_EMPTY_TITLE), t(UiKey.CALENDAR_EMPTY_BODY)) }
            } else {
                item(key = "summary") { SummaryCard(u.value.items, u.value.summary, all, onToggle = { all = !all }, onOpen = { openSource(nav, it) }) }
            }
        }
        item(key = "add") {
            val date = selected?.let { formatIsoDate(DateParts(year, month, it)) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryButton(
                    t(UiKey.CALENDAR_ADD_EVENT), onClick = { nav.open(EventAddSheetRoute(date)) }, modifier = Modifier.fillMaxWidth(),
                    enabled = EVENT_ADD_READY, height = 52.dp, leading = Lucide.PLUS,
                )
                if (!EVENT_ADD_READY) BasicText(t(UiKey.CALENDAR_ADD_EVENT_LATER), style = Type.caption().copy(color = Ink.muted))
            }
        }
        item(key = "grid") {
            CalendarGrid(
                year = year, month = month, today = today, marks = marksOf(monthRead.valueOrNull().orEmpty(), year, month), selectedDay = selected,
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
        item(key = "day") {
            when (val m = monthRead) {
                ReadState.Failed -> HomeErrorBanner { monthRead = ReadState.Loading; monthTick++ }
                else -> DayBlock(year, month, selected, m.valueOrNull(), onClear = { selected = null }, onOpen = { openSource(nav, it) })
            }
        }
    }
}

/** «الأحداث القادمة» والجملة الذكية بيتقروا مع بعض — لو واحدة فشلت الكارت كله «تعذّر التحميل» (مش جملة ناقصة من غير ما يقول). */
private class UpcomingRead(val items: List<CalendarItem>, val summary: SmartSummary)

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
                BasicText(t(UiKey.CALENDAR_UPCOMING), style = Type.of(16, FontWeight.Bold))
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
                    if (all) t(UiKey.CALENDAR_LESS) else t(UiKey.CALENDAR_ALL, sentenceNumber(ups.size)),
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
private fun DayBlock(year: Int, month: Int, selected: Int?, monthItems: List<CalendarItem>?, onClear: () -> Unit, onOpen: (CalendarItemType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val title = if (selected == null) t(UiKey.CALENDAR_PICK_DAY) else t(UiKey.CALENDAR_DAY_TITLE, dayMonth(formatIsoDate(DateParts(year, month, selected))))
            BasicText(title, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
            if (selected != null) TonalButton(t(UiKey.CALENDAR_CLEAR), onClick = onClear, height = 44.dp)
        }
        if (selected == null) return@Column
        // لسه بيتقري (شهر جديد) ⇒ هيكل، مش «لا مواعيد»
        if (monthItems == null) return@Column Skeleton(Modifier.fillMaxWidth().height(56.dp))
        val date: IsoDate = formatIsoDate(DateParts(year, month, selected))
        val day = monthItems.filter { it.date == date }
        if (day.isEmpty()) BasicText(t(if (EVENT_ADD_READY) UiKey.CALENDAR_DAY_EMPTY else UiKey.CALENDAR_DAY_EMPTY_NO_ADD), style = Type.of(13).copy(color = Ink.muted))
        for (item in day) FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) { CalRowView(calRowOf(item), onOpen) }
    }
}
