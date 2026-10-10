package app.masroufy.ui.screens.investment.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** ألوان الورثة (النموذج: نقطة غامقة + شريط فاتح بنفس الترتيب). */
internal val HEIR_DOT = listOf(
    Color(0xFF13764D), Color(0xFF2469BA), Color(0xFF956000), Color(0xFFBE3D48), Color(0xFF08634F), Color(0xFF6B5BA8), Color(0xFF4D747C), Color(0xFFA55060),
)
internal val HEIR_BAR = listOf(
    Color(0xFFA6DEC1), Color(0xFF9BC5FF), Color(0xFFF3C96B), Color(0xFFFFA8AC), Color(0xFF7FC8AE), Color(0xFFC9BEF0), Color(0xFFA9C7CC), Color(0xFFE9B4C0),
)
private val GRAY_DOT = Color(0xFFCCD8CC)

/**
 * النتيجة (لوحة `InheritanceResult`): يُقسَم على الورثة + الشريط · ما يُخرج قبل القسمة · نصيب كل وارث (الكسر والمبلغ) · `InheritanceSplit` ·
 * أو «هل للمتوفى أقارب آخرون؟» · أو التوقف («غير مدعوم» · «مدخلات غير صحيحة» · «لا نص — اسأل المحكمة»). وتحت كل نتيجة التنبيه بالبلد.
 */
@Composable
fun InheritanceResultPanel(ui: InheritanceResultUi, onDistantAnswer: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (val v = ui.view) {
            is InheritanceView.Computed -> Computed(v, ui)
            InheritanceView.AskDistant -> AskDistant(onDistantAnswer)
            is InheritanceView.Stop -> StopCard(v)
        }
        FootNote(ui.disclaimer)
    }
}

@Composable
private fun Computed(v: InheritanceView.Computed, ui: InheritanceResultUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(TextKey.INHRES_HERO_LABEL), style = Type.of(14).copy(color = Ink.onHeroMuted))
            HeroAmount(v.poolMinor, ui.currency, Modifier.fillMaxWidth(), size = 32)
            if (v.segments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // طول كل جزء بنسبة المبلغ (رسم بس — المبالغ من المحرك)
                    v.segments.forEach { (color, amount) -> Box(Modifier.weight(amount.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(HEIR_BAR[color])) }
                }
            }
            BasicText(v.heroSub, style = Type.caption().copy(color = Ink.onHeroMuted))
        }
    }
    if (v.claims.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x0D193D33)).padding(horizontal = 16.dp, vertical = 6.dp)) {
            v.claims.forEach { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(c.label, Modifier.weight(1f), style = Type.of(13))
                    AmountText(c.amountMinor, ui.currency, size = 13, showCurrency = false)
                }
            }
        }
    }
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        v.heirs.forEachIndexed { i, h ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(h.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(h.name, style = Type.bodyBold())
                    BasicText(h.basis, style = Type.caption().copy(color = Ink.muted))
                    if (h.sub.isNotEmpty()) BasicText(h.sub, style = Type.caption().copy(color = Ink.muted))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AmountText(h.amountMinor, ui.currency, showCurrency = false, color = if (h.amountMinor > 0) Ink.text else Ink.muted)
                    BasicText(h.fraction, style = Type.captionBold().copy(color = Ink.primary))
                }
            }
        }
    }
    InheritanceSplitPanel(v, ui)
}

@Composable
internal fun Dot(color: Int?) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color?.let { HEIR_DOT[it] } ?: GRAY_DOT))
}

@Composable
private fun AskDistant(onAnswer: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.alertBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(t(TextKey.INHERIT_UNSUPPORTED_ASK_DISTANT), style = Type.of(15, FontWeight.Bold).copy(color = warnDeep))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(t(TextKey.CALCUI_NO), { onAnswer(false) }, Modifier.weight(1f))
            SecondaryButton(t(TextKey.CALCUI_YES), { onAnswer(true) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StopCard(v: InheritanceView.Stop) {
    val (ink, bg) = when (v.kind) {
        StopKind.INVALID -> Ink.expense to Color(0x14BE3D48)
        StopKind.UNSUPPORTED -> Ink.muted to Color(0x0F193D33)
        StopKind.COURT -> warnInk to Ink.alertBg
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bg).semantics { liveRegion = LiveRegionMode.Polite }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(stopKindLabel(v.kind), style = Type.of(13, FontWeight.Bold).copy(color = ink))
        v.title?.let { BasicText(it, style = Type.of(18, FontWeight.Bold)) }
        BasicText(v.text, style = Type.of(14, lineHeight = 1.7))
        v.cite?.let { BasicText(it, style = Type.caption().copy(color = Ink.muted)) }
    }
}
