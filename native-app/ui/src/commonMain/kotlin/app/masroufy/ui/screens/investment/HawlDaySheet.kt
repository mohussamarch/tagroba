package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.hijriOf
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.ui.app.LocalSpace
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** شكل «يوم زكاتك الهجري»: المقترح (أو المؤكد) والنهارده بالهجري — عرض بس (`hijriOf` من `core`). */
data class HawlDayUi(val intro: String, val todayLine: String, val month: Int, val day: Int)

fun hawlDayUi(today: IsoDate, due: IsoDate?): HawlDayUi {
    val start = due?.let(::hijriOf)
    val now = hijriOf(today)
    return HawlDayUi(
        intro = if (start != null) uiText(UiKey.HAWL_INTRO, hijriDayMonth(start.month, start.day)) else uiText(UiKey.HAWL_INTRO_PLAIN),
        todayLine = uiText(UiKey.HAWL_TODAY, hijriText(now)),
        month = (start ?: now).month,
        day = (start ?: now).day,
    )
}

/**
 * «غيّره ليوم هجري» (`HawlDaySheet` جوه `Zakat`): الشهر الهجري (٤ أعمدة) واليوم (٦ أعمدة). ⚠️ **الحفظ مقفول بسببه**: مفيش حالة استخدام
 * بتحوّل يوم هجري (شهر/يوم) لأول ميعاد ميلادي جاي وبداية الحول (أم القرى) — آخر §76 «ناقص في كوتلن» ⇒ النموذج نفسه عنده حالة
 * «التاريخ الميلادي غير متاح» وبنعرضها بصراحة (HANDOVER).
 */
@Composable
internal fun HawlDaySheet(visible: Boolean, due: IsoDate?, onDismiss: () -> Unit) {
    val deps = LocalSpace.current.investment
    val ui = remember(visible, due) { hawlDayUi(deps.today(), due) }
    var month by remember(visible, due) { mutableIntStateOf(ui.month) }
    var day by remember(visible, due) { mutableIntStateOf(ui.day) }
    val title = t(UiKey.HAWL_TITLE)
    Sheet(visible, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 8.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = Type.of(17, FontWeight.Bold))
            BasicText(ui.intro, style = Type.caption().copy(color = Ink.muted))
        }
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(t(UiKey.HAWL_MONTH), style = Type.of(13, FontWeight.Bold))
                BasicText(ui.todayLine, style = Type.caption().copy(color = Ink.muted))
            }
            ChipGrid(12, 4) { i -> GridChip(hijriMonthName(i + 1), month == i + 1, 13) { month = i + 1 } }
            BasicText(t(UiKey.HAWL_DAY), style = Type.of(13, FontWeight.Bold))
            ChipGrid(30, 6) { i -> GridChip(sentenceNumber(i + 1), day == i + 1, 15, uiText(UiKey.HAWL_DAY_ARIA, sentenceNumber(i + 1))) { day = i + 1 } }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.primary.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(hijriDayMonth(month, day), style = Type.bodyBold().copy(color = Ink.primary))
            BasicText(t(UiKey.HAWL_GREG_NA), style = Type.of(13))
            BasicText(t(UiKey.HAWL_REMIND), style = Type.caption().copy(color = Ink.muted))
        }
        BasicText(t(UiKey.HAWL_SAVE_BLOCKED), style = Type.captionBold().copy(color = Ink.focus))
        PrimaryButton(t(UiKey.HAWL_SAVE), {}, Modifier.fillMaxWidth(), enabled = false, height = 52.dp)
    }
}

/** خانة في الشبكة (44، زاوية 14): المختارة `#DCEBD6` بحد أخضر 1.5 — زي النموذج (حشوة 2 عشان «جمادى الأولى» تكفي في ربع السطر). */
@Composable
private fun GridChip(label: String, selected: Boolean, size: Int, aria: String? = null, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().height(44.dp).pressScale(press).clip(shape)
            .background(if (selected) Ink.selected else Ink.text.copy(alpha = 0.06f))
            .then(if (selected) Modifier.insetRing(shape, 1.5.dp, Ink.primary) else Modifier)
            .tap(press, role = Role.RadioButton, label = aria ?: label, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(size, if (selected) FontWeight.Bold else FontWeight.Normal).copy(color = if (selected) Ink.primary else Ink.text), maxLines = 1)
    }
}

/** شبكة [count] خانة على [columns] أعمدة بنفس العرض (5 بينهم). */
@Composable
private fun ChipGrid(count: Int, columns: Int, cell: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in 0 until (count + columns - 1) / columns) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (col in 0 until columns) {
                    val i = row * columns + col
                    Column(Modifier.weight(1f)) { if (i < count) cell(i) }
                }
            }
        }
    }
}
