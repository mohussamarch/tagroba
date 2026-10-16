package app.masroufy.ui.screens.investment.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.UiKey
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.TipView

/** أيقونات الحزمة (لوسيد — من `<svg>` النماذج نفسها). */
internal object InsightIcons {
    val BULB = Lucide("INSIGHT_BULB", "M9 18h6", "M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z")
    val HELP = Lucide("INSIGHT_HELP", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3", "M12 17h.01")
}

private val tipTop = Color(0xFFFFFDF6)
private val tipBottom = Color(0xFFFBF4E2)
private val tipEdge = Color(0x38B7791F)
private val amberInk = Color(0xFF956000)

/**
 * كارت النصيحة (`InvestTipCard.dc.html` وضع «واحدة»): خلفية كهرماني فاتح بحد رفيع · لمبة + «ليه دي» · النصيحة · «المصدر: …» ·
 * «كل النصايح ←» · «×» في الركن (بيتقفل وعمره ما يرجع).
 */
@Composable
internal fun TipCard(tip: TipView, onClose: () -> Unit, onAll: () -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Box(
        Modifier.fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color(0x121D3635), spotColor = Color(0x121D3635))
            .clip(shape)
            .background(Brush.linearGradient(listOf(tipTop, tipBottom)))
            .border(1.dp, tipEdge, shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.padding(end = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(InsightIcons.BULB, size = 18.dp, tint = amberInk)
                BasicText(tip.why, style = Type.of(13, FontWeight.Bold).copy(color = amberInk))
            }
            BasicText(tip.title, style = Type.of(14, FontWeight.Bold, 1.6).copy(color = Ink.text))
            // الكارت سطر النصيحة بس زي النموذج؛ الشرح الكامل («النص» و«ليه مهمة») في صفحة النصايح (Claude choice ❓)
            BasicText(tip.source, style = Type.of(12).copy(color = Ink.muted))
            LinkText(t(InvestInsightKey.TIP_ALL), onAll)
        }
        val press = rememberPress()
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp).clip(CircleShape)
                .tap(press, label = t(InvestInsightKey.TIP_CLOSE), onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.X, size = 18.dp, tint = Ink.muted, contentDescription = t(InvestInsightKey.TIP_CLOSE)) }
    }
}

/** رابط أخضر عريض بارتفاع لمس 36 على الأقل (زي «كل النصايح ←» و«مصادر الدخل ←»). */
@Composable
internal fun LinkText(text: String, onClick: () -> Unit) {
    val press = rememberPress()
    Box(Modifier.heightIn(min = 36.dp).tap(press, role = Role.Button, label = text, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        BasicText(text, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
    }
}

/** قسم القراية بتاعته فشلت: العنوان + «تعذّر التحميل.» (مش صفر ولا رقم). */
@Composable
internal fun FailedSection(title: InvestInsightKey) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(title), style = Type.of(16, FontWeight.Bold).copy(color = Ink.text))
            BasicText(t(UiKey.SHELL_LOAD_FAILED), style = Type.of(13).copy(color = Ink.muted))
        }
    }
}
