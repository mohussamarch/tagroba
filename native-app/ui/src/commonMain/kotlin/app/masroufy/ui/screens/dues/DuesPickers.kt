package app.masroufy.ui.screens.dues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.DateParts
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.dayMonth
import app.masroufy.core.daysInMonth
import app.masroufy.core.formatIsoDate
import app.masroufy.core.monthYear
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * اختيار يوم من شهر (سؤال «أول دفعة» في الجمعية · «الموعد القادم» في الاشتراك · «أول موعد» في الخطة): رأس الشهر بسهمين + أيام الشهر
 * في شبكة ٧ (زي النموذج — من غير محاذاة أيام الأسبوع). المختار أخضر مليان، والنهارده أخضر خفيف. تاريخ بس — من غير فلوس.
 */
@Composable
internal fun DayPicker(selected: IsoDate?, today: IsoDate, onPick: (IsoDate) -> Unit, cell: Int = 40) {
    val start = parseIsoDate(selected ?: today)
    var ym by remember { mutableStateOf(start.year * 12 + start.month - 1) }
    val year = ym.floorDiv(12)
    val month = ym.mod(12) + 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(Lucide.CHEVRON_RIGHT, t(TextKey.CAL_PREV)) { ym -= 1 }
            BasicText(monthYear(year, month), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold).copy(textAlign = TextAlign.Center))
            MonthArrow(Lucide.CHEVRON_LEFT, t(TextKey.CAL_NEXT)) { ym += 1 }
        }
        val days = daysInMonth(year, month)
        for (r in 0 until (days + 6) / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val day = r * 7 + c + 1
                    if (day > days) Box(Modifier.weight(1f)) else {
                        val iso = formatIsoDate(DateParts(year, month, day))
                        DayButton(Modifier.weight(1f), day, iso == selected, iso == today, cell) { onPick(iso) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayButton(modifier: Modifier, day: Int, on: Boolean, isToday: Boolean, cell: Int, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        on -> Ink.primary
        isToday -> Color(0x1408634F)
        else -> Color.Transparent
    }
    Box(
        modifier.height(cell.dp).pressScale(press).clip(shape).background(bg).tap(press, onClick = onClick)
            .semantics { this.selected = on },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            sentenceNumber(day),
            style = Type.of(14, if (on || isToday) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Color.White else if (isToday) Ink.primary else Ink.text),
        )
    }
}

@Composable
private fun MonthArrow(icon: Lucide, label: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(Color(0x0D193D33))
            .tap(press, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, modifier = Modifier.mirrorInLtr()) }
}

/** اسم التاريخ المختار للقارئ («٧ أكتوبر») — للأزرار اللي بتفتح الاختيار. */
internal fun pickedLabel(date: IsoDate?): String = date?.let(::dayMonth).orEmpty()
