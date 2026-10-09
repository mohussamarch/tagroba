package app.masroufy.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.daysInMonth
import app.masroufy.core.formatIsoDate
import app.masroufy.core.DateParts
import app.masroufy.core.dayMonth
import app.masroufy.core.monthYear
import app.masroufy.core.saturdayColumnOf
import app.masroufy.core.sentenceNumber
import app.masroufy.core.weekdayShortNamesFromSaturday
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** نوع الميعاد في الشبكة ولونه (النموذج: اشتراكات كهرماني · أقساط · جمعيات · ديون أزرق · أحداث وردي · الراتب أخضر مليان · فات موعده أحمر). */
enum class MarkKind(val ink: Color, val bg: Color, val legend: TextKey) {
    SUBSCRIPTION(Color(0xFF956000), Color(0x1F956000), TextKey.CAL_LEGEND_SUB),
    INSTALLMENT(Color(0xFF4D747C), Color(0x244D747C), TextKey.CAL_LEGEND_INST),
    ROSCA(Color(0xFF27785B), Color(0x2127785B), TextKey.CAL_LEGEND_ROSCA),
    DEBT(Color(0xFF2469BA), Color(0x1F2469BA), TextKey.CAL_LEGEND_DEBT),
    EVENT(Color(0xFFA55060), Color(0x1FA55060), TextKey.CAL_LEGEND_EVENT),
    PAYDAY(Color(0xFFFFFFFF), Color(0xFF13764D), TextKey.CAL_LEGEND_PAY),
    LATE(Color(0xFFBE3D48), Color(0x1FBE3D48), TextKey.CAL_LEGEND_LATE),
}

/** ميعاد في يوم: [label] اسم قصير. [late] = فات ميعاده ⇒ أحمر. */
data class CalendarMark(val day: Int, val kind: MarkKind, val label: String, val late: Boolean = false)

/**
 * شبكة شهر ميلادي (`CalendarGrid` — OVERRIDES §65 · §76): **السبت أول الأسبوع في البلدين**، خانات واسعة فيها المواعيد بلون نوعها
 * (أكتر من اتنين ⇒ أول واحد + «+N»)، النهارده دايرة خضرا، اللي فات باهت، واليوم المختار أخضر فاتح بحد. السهم اليمين = الشهر اللي فات.
 * [onMonth] بياخد −1 / +1، و[onToday] يرجع للشهر الحالي. المواعيد بتيجي جاهزة من الشاشة (`LoadCalendar.month`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    today: IsoDate,
    marks: List<CalendarMark>,
    selectedDay: Int?,
    onPick: (Int) -> Unit,
    onMonth: (Int) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
    noPrev: Boolean = false,
    noNext: Boolean = false,
) {
    val first = formatIsoDate(DateParts(year, month, 1))
    val lead = saturdayColumnOf(first)
    val days = daysInMonth(year, month)
    val showToday = today.take(7) != first.take(7)
    FloatingCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Arrow(Lucide.CHEVRON_RIGHT, t(TextKey.CAL_PREV), noPrev) { onMonth(-1) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(monthYear(year, month), style = Type.of(17, FontWeight.Bold))
                    if (showToday) TodayChip(onToday)
                }
                Arrow(Lucide.CHEVRON_LEFT, t(TextKey.CAL_NEXT), noNext) { onMonth(1) }
            }
            Row(Modifier.fillMaxWidth()) {
                for (h in weekdayShortNamesFromSaturday()) BasicText(h, Modifier.weight(1f), style = Type.of(11).copy(color = Ink.muted, textAlign = TextAlign.Center))
            }
            val cells = lead + days
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - lead + 1
                        if (day < 1 || day > days) Box(Modifier.weight(1f)) else DayCell(Modifier.weight(1f), year, month, day, today, marks.filter { it.day == day }, selectedDay == day) { onPick(day) }
                    }
                }
            }
            val kinds = MarkKind.entries.filter { k -> marks.any { (if (k == MarkKind.LATE) it.late else it.kind == k && !it.late) } }
            if (kinds.isNotEmpty()) FlowRow(Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in kinds) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (k == MarkKind.PAYDAY) k.bg else k.ink))
                    BasicText(t(k.legend), style = Type.of(11).copy(color = Ink.muted))
                }
            }
        }
    }
}

@Composable
private fun DayCell(modifier: Modifier, year: Int, month: Int, day: Int, today: IsoDate, mine: List<CalendarMark>, on: Boolean, onClick: () -> Unit) {
    val iso = formatIsoDate(DateParts(year, month, day))
    val isToday = iso == today
    val past = iso < today
    val press = rememberPress()
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        on -> Modifier.background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary)
        isToday -> Modifier.background(Color(0x1208634F))
        else -> Modifier.background(Color(0x08193D33))
    }
    val shown = if (mine.size > 2) mine.take(1) else mine
    val aria = dayMonth(iso) + (if (isToday) "، " + t(TextKey.CAL_TODAY) else "") + "، " + (if (mine.isEmpty()) t(TextKey.CAL_NO_DATES) else mine.joinToString("، ") { it.label })
    Column(
        modifier.defaultMinSize(minHeight = 60.dp).pressScale(press).clip(shape).then(bg).tap(press, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = aria; selected = on }.padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.widthIn(min = 22.dp).height(22.dp).clip(CircleShape).background(if (isToday) Ink.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(sentenceNumber(day), style = Type.of(12, FontWeight.Bold).copy(color = if (isToday) Color.White else if (past) Ink.faded else Ink.text))
        }
        for (m in shown) {
            val kind = if (m.late) MarkKind.LATE else m.kind
            BasicText(
                m.label,
                Modifier.fillMaxWidth().alpha(if (past && !m.late) 0.55f else 1f).clip(RoundedCornerShape(6.dp)).background(kind.bg).padding(horizontal = 2.dp),
                style = Type.of(10, FontWeight.Bold, 1.5).copy(color = kind.ink, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (mine.size > 2) BasicText(
            t(TextKey.CAL_MORE, sentenceNumber(mine.size - 1)),
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0x0F193D33)),
            style = Type.of(10, FontWeight.Bold, 1.5).copy(color = Ink.muted, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun Arrow(icon: Lucide, label: String, off: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(48.dp).alpha(if (off) 0.35f else 1f).pressScale(press, !off).clip(RoundedCornerShape(16.dp)).background(Color(0x0D193D33))
            .tap(press, enabled = !off, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, modifier = Modifier.mirrorInLtr()) }
}

@Composable
private fun TodayChip(onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.height(32.dp).pressScale(press).clip(RoundedCornerShape(12.dp)).background(Ink.selected).tap(press, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(t(TextKey.CAL_TODAY), style = Type.captionBold().copy(color = Ink.primary)) }
}
