package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «قسمة كل شيء» + «ملاحظات» (لوحة `InheritanceSplit` جوه `InheritanceResult`): كل شيء في التركة بيتفتح ويبيّن نصيب كل طرف منه
 * (التجهيز · الديون · الوصية الواجبة · الوصية · كل وارث) — مجموعه = قيمته بالظبط من المحرك · والملاحظات بموادها.
 */
@Composable
fun InheritanceSplitPanel(v: InheritanceView.Computed, ui: InheritanceResultUi) {
    var open by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BasicText(t(TextKey.INHSPLIT_TITLE), Modifier.padding(top = 4.dp), style = Type.of(17, FontWeight.Bold))
        v.items.forEachIndexed { k, item ->
            val isOpen = open == k
            FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                val press = rememberPress()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(press).tap(press, role = Role.Button, label = item.name) { open = if (isOpen) -1 else k },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(item.name, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
                    AmountText(item.valueMinor, ui.currency, size = 14, showCurrency = false)
                    LucideIcon(Lucide.CHEVRON_DOWN, Modifier.rotate(if (isOpen) 180f else 0f), size = 18.dp, tint = Ink.muted)
                }
                if (isOpen) {
                    Column(Modifier.padding(bottom = 10.dp)) {
                        item.parts.forEach { p ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Dot(p.color)
                                BasicText(p.label, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.soft))
                                AmountText(p.amountMinor, ui.currency, size = 13, weight = FontWeight.Normal, showCurrency = false, color = Ink.soft)
                            }
                        }
                        Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(t(TextKey.INHSPLIT_SUM), Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
                            AmountText(item.valueMinor, ui.currency, size = 13, showCurrency = false)
                        }
                    }
                }
            }
        }
        BasicText(v.itemsNote, style = Type.caption().copy(color = Ink.muted))
        if (v.notes.isNotEmpty()) {
            BasicText(t(TextKey.INHSPLIT_NOTES), Modifier.padding(top = 4.dp), style = Type.of(17, FontWeight.Bold))
            FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                v.notes.forEachIndexed { i, n ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(n.text, style = if (n.warn) Type.of(13, FontWeight.Bold, 1.7).copy(color = warnInk) else Type.of(13, lineHeight = 1.7))
                        n.cite?.let { BasicText(it, style = Type.of(11).copy(color = Ink.muted)) }
                    }
                }
            }
        }
    }
}
