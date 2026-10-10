package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.DateParts
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.formatIsoDate
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.CalendarGrid
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * خانة تاريخ (النموذج: `<input type="date">` — تاريخ الميلاد · بداية العمل · «بحلول أي تاريخ؟»): بتعرض التاريخ بالكلام، واللمس بيفتح لوحة
 * فيها السنة (± سنة) وشبكة الشهر (`CalendarGrid`). القيمة نص ISO (`2028-10-07`) زي باقي الخانات.
 */
@Composable
fun CalcDateField(
    label: String,
    value: String,
    onPick: (IsoDate) -> Unit,
    today: IsoDate,
    modifier: Modifier = Modifier,
    note: String? = null,
    error: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val press = rememberPress()
    val shown = value.takeIf { isValidIsoDate(it) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().height(48.dp).pressScale(press).clip(shape).background(Color.White)
                .insetRing(shape, if (error != null) 1.5.dp else 1.dp, if (error != null) Ink.expense else Ink.fieldEdge)
                .tap(press, label = label) { open = true }
                .semantics { contentDescription = shown?.let(::fullDate) ?: t(UiKey.CALCUI_PICK_DATE) }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                shown?.let(::fullDate) ?: t(UiKey.CALCUI_PICK_DATE),
                Modifier.weight(1f),
                style = Type.of(17, FontWeight.Bold).copy(color = if (shown != null) Ink.text else Color(0xFF8A9A95)),
            )
            LucideIcon(Lucide.CALENDAR, size = 20.dp, tint = Ink.muted)
        }
        if (error != null) FieldError(error) else if (!note.isNullOrEmpty()) BasicText(note, style = Type.caption().copy(color = Ink.muted))
    }
    DateSheet(open, label, shown ?: today, today, onDismiss = { open = false }) { onPick(it); open = false }
}

@Composable
private fun DateSheet(visible: Boolean, title: String, start: IsoDate, today: IsoDate, onDismiss: () -> Unit, onPick: (IsoDate) -> Unit) {
    val first = parseIsoDate(start)
    var year by remember(visible) { mutableIntStateOf(first.year) }
    var month by remember(visible) { mutableIntStateOf(first.month) }
    val selected = if (year == first.year && month == first.month) first.day else null
    Sheet(visible, onDismiss, title) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton44(Lucide.CHEVRON_RIGHT, t(UiKey.CALCUI_PREV_YEAR), { year -= 1 }, enabled = year > 1900)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicText(sentenceNumber(year), style = Type.of(18, FontWeight.Bold))
            }
            IconButton44(Lucide.CHEVRON_LEFT, t(UiKey.CALCUI_NEXT_YEAR), { year += 1 }, enabled = year < 2200)
        }
        CalendarGrid(
            year = year,
            month = month,
            today = today,
            marks = emptyList(),
            selectedDay = selected,
            onPick = { day -> onPick(formatIsoDate(DateParts(year, month, day))) },
            onMonth = { delta ->
                val index = year * 12 + (month - 1) + delta
                year = index / 12
                month = index % 12 + 1
            },
            onToday = {
                val p = parseIsoDate(today)
                year = p.year
                month = p.month
            },
        )
        Box(Modifier.padding(bottom = 4.dp))
    }
}
