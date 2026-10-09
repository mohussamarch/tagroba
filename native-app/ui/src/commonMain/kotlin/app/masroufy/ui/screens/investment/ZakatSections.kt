package app.masroufy.ui.screens.investment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** كارت «الوقائع — ليست آراء» ⇒ اللوحة. فيه واقعة ناقصة ⇒ حد كهرماني والشارة «ينقص N». */
@Composable
internal fun FactsCard(summary: FactsSummary, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val ring = if (summary.missing) Modifier.insetRing(shape, 1.5.dp, Ink.focus.copy(alpha = 0.35f)) else Modifier
    FloatingCard(Modifier.fillMaxWidth().then(ring), onClick = onOpen, clickLabel = t(TextKey.ZAKAT_SCREEN_FACTS)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(t(TextKey.ZAKAT_SCREEN_FACTS), style = Type.of(15, FontWeight.Bold))
                BasicText(t(TextKey.ZAKAT_SCREEN_FACTS_SUB), style = Type.caption().copy(color = Ink.muted))
            }
            if (summary.missing) ToneChip(summary.chip, Ink.focus, Ink.alertBg) else ToneChip(summary.chip, Ink.income, Ink.selected)
        }
    }
}

/** «تجب فيه الزكاة»: سطر لكل نوع بمطلوبه — المجهول «غير متاح» كهرماني، وجنبه الناقص. من غير سنة مؤكدة ⇒ «أكّد يوم زكاتك أولًا». */
@Composable
internal fun LinesSection(ui: ZakatUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.ZAKAT_SCREEN_LINES), style = Type.section())
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            when {
                !ui.hasYear -> BasicText(t(TextKey.ZAKAT_SCREEN_NO_YEAR), Modifier.padding(vertical = 14.dp), style = Type.of(13).copy(color = Ink.muted))
                ui.lines.isEmpty() -> BasicText(t(TextKey.ZAKAT_SCREEN_LINES_EMPTY), Modifier.padding(vertical = 14.dp), style = Type.of(13).copy(color = Ink.muted))
            }
            ui.lines.forEachIndexed { i, line ->
                if (i > 0) RowGap()
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(line.label, Modifier.weight(1f), style = Type.bodyBold())
                        AmountText(line.dueMinor, ui.currency, showCurrency = false, color = if (line.dueMinor == null) Ink.focus else null)
                    }
                    if (line.detail.isNotEmpty()) BasicText(line.detail, style = Type.caption().copy(color = Ink.soft))
                    if (line.source.isNotEmpty()) BasicText(line.source, style = Type.of(11).copy(color = Ink.muted))
                }
            }
        }
    }
}

/** «خارج الحساب»: كل حاجة بقاعدتها ومصدرها (المعفي · الدين اللي عليك · اللي المرجع ما حددش فيه · النوع اللي مش داخل). */
@Composable
internal fun OutsSection(ui: ZakatUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.ZAKAT_SCREEN_OUT), style = Type.of(15, FontWeight.Bold).copy(color = Ink.soft))
        for (o in ui.outs) {
            QuietBox {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(o.name, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
                    if (o.valueMinor != null) AmountText(o.valueMinor, ui.currency, size = 13, weight = FontWeight.Normal, showCurrency = false, color = Ink.muted)
                }
                BasicText(o.rule, style = Type.caption().copy(color = Ink.soft))
                if (o.source.isNotEmpty()) BasicText(o.source, style = Type.of(11).copy(color = Ink.muted))
            }
        }
    }
}
