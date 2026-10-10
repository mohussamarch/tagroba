package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tabular
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** «مقارنة بما تدّخره فعلًا»: الحكم + أعمدة آخر 3 أشهر مالية + السطر والشرح + «حدّد نوع هذه العمليات» لو شهر غير معروف. */
@Composable
internal fun CompareCard(ui: SavingsResultUi, onReview: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.SAVCALC_COMPARE_TITLE), Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
                VerdictChip(ui.verdict, ui.verdictTone)
            }
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                ui.bars.forEach { b -> Bar(b, Modifier.weight(1f)) }
            }
            BasicText(ui.compareLine, style = Type.of(13))
            BasicText(ui.compareNote, style = Type.caption().copy(color = Ink.muted))
            if (ui.compareUnknown) ReviewLink(onReview)
        }
    }
}

@Composable
private fun Bar(b: SavingBar, modifier: Modifier) {
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(b.valueText, style = Type.of(11).copy(color = Ink.muted).tabular(), maxLines = 1)
        val fill = if (b.unknown) Modifier.background(Ink.alertBg).insetRing(shape, 1.5.dp, Color(0x8C956000))
        else Modifier.background(Brush.verticalGradient(listOf(Ink.mint, CategoryInk.savings)))
        Box(Modifier.widthIn(max = 56.dp).fillMaxWidth().height(b.heightDp.dp).clip(shape).then(fill))
        BasicText(b.label, style = Type.of(11).copy(color = Ink.muted), maxLines = 1)
    }
}

@Composable
private fun VerdictChip(text: String, tone: VerdictTone) {
    val (ink, bg) = when (tone) {
        VerdictTone.UNKNOWN -> Ink.muted to Color(0x1F637570)
        VerdictTone.ENOUGH -> Ink.income to Ink.selected
        VerdictTone.SHORT -> Ink.expense to Color(0x1ABE3D48)
    }
    BasicText(
        text,
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        style = Type.of(12, FontWeight.Bold).copy(color = ink),
        maxLines = 1,
    )
}

/** «حدّد نوع هذه العمليات» ⇒ `ReviewQueue` (كهرماني — النموذج). */
@Composable
private fun ReviewLink(onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.height(44.dp).pressScale(press).clip(shape).background(Ink.alertBg).tap(press, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(t(UiKey.SAVCALC_REVIEW), style = Type.of(13, FontWeight.Bold).copy(color = warnInk)) }
}
