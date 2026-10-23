package app.masroufy.ui.screens.people

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.overlay.Overlay
import app.masroufy.ui.overlay.Veil
import app.masroufy.ui.overlay.VeilLayer
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Type
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** نص قطر القوس (`PersonProfile.dc.html` — R = 165) وزوايا الأيقونات: من 165° لـ257° (من جنب الزرار لتحته). */
private const val FAN_RADIUS = 165f
private const val FAN_FROM = 165f
private const val FAN_SPAN = 92f

/** زاوية الأيقونة رقم [i] من [n] بالدرجات. */
internal fun fanAngle(i: Int, n: Int): Float = FAN_FROM + if (n <= 1) 0f else i * FAN_SPAN / (n - 1)

/**
 * «معاه» مفتوح (`PersonProfile.dc.html` — تعديل المالك 2026-10-23: «مش عايز قايمة، عايزها أيقونات بتتفرد»): ستارة القايمة (تمويه 8) ·
 * الزرار نفسه بقى × فوقها مكانه · 4 أيقونات بيضا 52 بأساميها بتتفرد من الزرار على قوس. الرجوع أو الضغط برّه بيقفل.
 */
@Composable
internal fun PersonFan(anchor: Anchor, onClose: () -> Unit, onAction: (FanAction) -> Unit) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) }
    Overlay(onBack = onClose) {
        Box(Modifier.fillMaxSize()) {
            VeilLayer(Veil.MENU, p.value.coerceIn(0f, 1f), onClose, t(R06Key.PH_CLOSE))
            val c = anchor.bounds.center
            val r = with(density) { FAN_RADIUS.dp.toPx() }
            val half = with(density) { 22.dp.toPx() }
            val closePress = rememberPress()
            Box(
                Modifier.offset { IntOffset((c.x - half).roundToInt(), (c.y - half).roundToInt()) }.size(44.dp).pressScale(closePress)
                    .clip(CircleShape).background(Ink.primary).tap(closePress, label = t(R06Key.PH_CLOSE), onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { LucideIcon(Lucide.X, size = 20.dp, tint = Ink.white(0xFF)) }
            val actions = fanActions()
            actions.forEachIndexed { i, a ->
                val ang = fanAngle(i, actions.size) * PI.toFloat() / 180f
                // الزرار يمين في العربي ⇒ القوس ناحية الشمال وتحت · في الإنجليزي بالعكس
                val dx = r * cos(ang) * (if (rtl) 1f else -1f)
                val dy = -r * sin(ang)
                val t0 = p.value
                val w = with(density) { 40.dp.toPx() }
                val hh = with(density) { 26.dp.toPx() }
                val press = rememberPress()
                Column(
                    Modifier.offset { IntOffset((c.x + dx * t0 - w).roundToInt(), (c.y + dy * t0 - hh).roundToInt()) }.width(80.dp)
                        .alpha(t0.coerceIn(0f, 1f)).scale(0.3f + 0.7f * t0.coerceIn(0f, 1.2f))
                        .pressScale(press).tap(press, label = t(a.label), onClick = { onAction(a) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Ink.surface), contentAlignment = Alignment.Center) {
                        LucideIcon(a.icon, size = 22.dp, tint = Ink.primary)
                    }
                    BasicText(
                        t(a.label),
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Ink.surface).padding(horizontal = 8.dp, vertical = 2.dp),
                        style = Type.of(12, FontWeight.Bold),
                    )
                }
            }
        }
    }
}
