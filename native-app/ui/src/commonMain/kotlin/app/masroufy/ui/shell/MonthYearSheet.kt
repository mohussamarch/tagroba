package app.masroufy.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.monthName
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** شهر في الشبكة: [enabled] = false ⇒ لسه ما جاش (باهت ومش بيتختار — OVERRIDES §121-1). */
data class MonthCell(val month: Int, val selected: Boolean, val enabled: Boolean)

/** آخر شهر ينفع يتختار (سنة · شهر). null ⇒ مفيش حد (تاريخ في المستقبل مسموح). */
data class MonthLimit(val year: Int, val month: Int) {
    companion object {
        fun of(today: IsoDate): MonthLimit = parseIsoDate(today).let { MonthLimit(it.year, it.month) }
    }
}

/** الـ١٢ شهر لسنة [year]: المختار [selected] والشهور اللي بعد [limit] مقفولة. */
fun monthCells(year: Int, selected: Int?, limit: MonthLimit?): List<MonthCell> = (1..12).map { m ->
    MonthCell(m, selected == m, limit == null || year < limit.year || (year == limit.year && m <= limit.month))
}

/** سهم السنة الجاية مقفول لو السنة دي هي سنة الحد (أو بعدها). */
fun canGoNextYear(year: Int, limit: MonthLimit?): Boolean = limit == null || year < limit.year

/** بعد تغيير السنة: الشهر المختار لو بقى في المستقبل بيرجع لآخر شهر مسموح. */
fun clampMonth(year: Int, month: Int, limit: MonthLimit?): Int =
    if (limit != null && year == limit.year && month > limit.month) limit.month else month

/**
 * OVERRIDES §121-1 — اختيار الشهر والسنة (الشكل الأول في `status-board/mockups.html`): لوحة من تحت فيها السنة بسهمين (اليمين = اللي فاتت)
 * + شبكة ١٢ شهر ٣×٤ + زرار «تمام». الشهور اللي لسه ما جتش ([limit]) باهتة ومش بتتختار. الاختيار بيوصل بـ[onPick] بعد «تمام» بس.
 */
@Composable
fun MonthYearSheet(visible: Boolean, year: Int, month: Int, limit: MonthLimit?, onDismiss: () -> Unit, onPick: (year: Int, month: Int) -> Unit) {
    var y by remember(visible, year) { mutableIntStateOf(year) }
    var m by remember(visible, month) { mutableIntStateOf(month) }
    val title = t(R06Key.C121_MY_TITLE)
    Sheet(visible, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            YearArrow(Lucide.CHEVRON_RIGHT, t(R06Key.C121_MY_PREV_YEAR), off = false) { y -= 1 }
            BasicText(sentenceNumber(y), style = Type.of(17, FontWeight.Bold))
            YearArrow(Lucide.CHEVRON_LEFT, t(R06Key.C121_MY_NEXT_YEAR), off = !canGoNextYear(y, limit)) { y += 1; m = clampMonth(y, m, limit) }
        }
        val cells = monthCells(y, m, limit)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in cells.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in row) {
                        SelectChip(
                            monthName(c.month), c.selected, { m = c.month },
                            Modifier.weight(1f).alpha(if (c.enabled) 1f else 0.4f), height = 44.dp, enabled = c.enabled,
                        )
                    }
                }
            }
        }
        PrimaryButton(t(R06Key.C121_MY_OK), { onPick(y, clampMonth(y, m, limit)) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun YearArrow(icon: Lucide, label: String, off: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        Modifier.size(44.dp).alpha(if (off) 0.35f else 1f).pressScale(press, !off).clip(RoundedCornerShape(14.dp)).background(Ink.tint(0x0D))
            .tap(press, enabled = !off, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, modifier = Modifier.mirrorInLtr()) }
}
