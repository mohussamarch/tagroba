package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.HeirKind
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** الورثة الأساسيين (أول كارت) والباقي تحت «ورثة آخرون» — اللي المحرك بيحسبهم (§69.1). ذوو الأرحام ما اترسموش في النموذج لسه. */
val MAIN_HEIRS = listOf(HeirKind.HUSBAND, HeirKind.WIFE, HeirKind.SON, HeirKind.DAUGHTER, HeirKind.FATHER, HeirKind.MOTHER)

val MORE_HEIRS = listOf(
    HeirKind.GRANDFATHER, HeirKind.PATERNAL_GRANDMOTHER, HeirKind.MATERNAL_GRANDMOTHER, HeirKind.SON_SON, HeirKind.SON_DAUGHTER,
    HeirKind.FULL_BROTHER, HeirKind.FULL_SISTER, HeirKind.PATERNAL_BROTHER, HeirKind.PATERNAL_SISTER, HeirKind.MATERNAL_BROTHER,
    HeirKind.MATERNAL_SISTER, HeirKind.FULL_NEPHEW, HeirKind.PATERNAL_NEPHEW, HeirKind.FULL_UNCLE, HeirKind.PATERNAL_UNCLE,
    HeirKind.FULL_COUSIN, HeirKind.PATERNAL_COUSIN,
)

/** «الزوجة، الابن ٢، البنت» — null لو مفيش ورثة. */
fun heirsSummary(heirs: Map<HeirKind, Int>): String? {
    val picked = HeirKind.entries.filter { (heirs[it] ?: 0) > 0 }
    if (picked.isEmpty()) return null
    return picked.joinToString("، ") { k -> heirs.getValue(k).let { n -> if (n > 1) t(TextKey.INHRES_HEIR_NUMBER, k.label, sentenceNumber(n)) else k.label } }
}

/** الخطوة ٣ (لوحة `InheritanceHeirs`): مَن الورثة؟ بالعدد، والأسماء اختيارية. */
@Composable
fun InheritanceHeirsStep(d: InheritanceDraft, onChange: (InheritanceDraft) -> Unit) {
    var moreChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val moreOpen = moreChoice ?: MORE_HEIRS.any { (d.heirs[it] ?: 0) > 0 }
    val summary = heirsSummary(d.heirs)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(TextKey.INHHEIRS_TITLE), style = Type.section())
            BasicText(t(TextKey.INHHEIRS_INTRO), style = Type.of(13).copy(color = Ink.muted))
        }
        BasicText(
            summary ?: t(TextKey.INHHEIRS_NONE),
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (summary != null) Ink.selected else Ink.alertBg).padding(horizontal = 12.dp, vertical = 8.dp),
            style = Type.of(13, FontWeight.Bold).copy(color = if (summary != null) Ink.primary else warnInk),
        )
        HeirsCard(MAIN_HEIRS, d, onChange)
        MoreToggle(moreOpen) { moreChoice = !moreOpen }
        if (moreOpen) HeirsCard(MORE_HEIRS, d, onChange)
        BasicText(t(TextKey.INHHEIRS_FOOT), style = Type.caption().copy(color = Ink.muted))
    }
}

@Composable
private fun HeirsCard(kinds: List<HeirKind>, d: InheritanceDraft, onChange: (InheritanceDraft) -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        kinds.forEachIndexed { i, k ->
            if (i > 0) Divider()
            val c = d.heirs[k] ?: 0
            val max = maxCount(k)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(k.label, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
                Stepper(
                    c,
                    onMinus = { if (c > 0) onChange(d.copy(heirs = d.heirs + (k to c - 1))) },
                    onPlus = { if (c < max) onChange(d.copy(heirs = d.heirs + (k to c + 1))) },
                    minusLabel = t(TextKey.INHHEIRS_LESS, k.label),
                    plusLabel = t(TextKey.INHHEIRS_MORE_ONE, k.label),
                    atMin = c <= 0,
                    atMax = c >= max,
                )
            }
            if (c > 0) {
                TextInput(
                    d.names[k].orEmpty(), { v -> onChange(d.copy(names = d.names + (k to v.take(60)))) }, Modifier.padding(bottom = 10.dp),
                    placeholder = t(TextKey.INHHEIRS_NAMES), height = 44.dp, textSize = 14,
                )
            }
        }
    }
}

@Composable
private fun MoreToggle(open: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(Color(0x1408634F))
            .tap(press, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(t(TextKey.INHHEIRS_MORE), Modifier.weight(1f), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        LucideIcon(Lucide.CHEVRON_DOWN, Modifier.rotate(if (open) 180f else 0f), size = 18.dp, tint = Ink.primary)
    }
}
