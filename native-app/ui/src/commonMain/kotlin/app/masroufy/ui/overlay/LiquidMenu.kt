package app.masroufy.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.BackdropBlur
import app.masroufy.ui.glass.BlurRadius
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.blurSupported
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion
import kotlin.math.roundToInt

/** بند في القائمة السائلة. [highlight] = البند المميّز («كل الإجراءات ←»). */
data class MenuItem(val label: String, val icon: Lucide? = null, val highlight: Boolean = false, val onClick: () -> Unit)

/**
 * قائمة الضغط المطوّل — 3 طبقات (DESIGN-SYSTEM «وصفات الزجاج 4»): (1) ستارة بتموّه الشاشة كلها (`rgba(32,59,48,.16)` + بلور 8 وتشبّع .88)
 * (2) **العملية المرفوعة** = نفس الصف في مكانه مكبّر 1.025 بسطح زجاجي وظل بعيد (3) القائمة زجاج سائل زاوية 28 تحتها (أو فوقها لو مفيش مكان).
 * الفتح بالنابض **المرن** (420ms في النموذج). الضغط المطوّل نفسه (440ms ويتلغي بحركة > 9) شغل الصف: `Modifier.tap(onLongClick = …)`.
 * المالك اختار «الاتنين» (§73): ضغط مطوّل **و** 3 نقط على كل صف — الاتنين بيفتحوا نفس القائمة.
 */
@Composable
fun LiquidMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    anchor: Anchor,
    title: String,
    items: List<MenuItem>,
    closeLabel: String? = null,
    lifted: @Composable () -> Unit,
) {
    val enter = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(visible) }
    val inSpec = motion<Float>(Springs.BOUNCY)
    val outSpec = motion<Float>(Springs.SNAPPY)
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            enter.animateTo(1f, inSpec)
        } else if (mounted) {
            enter.animateTo(0f, outSpec)
            mounted = false
        }
    }
    if (!mounted) return
    Overlay(onBack = onDismiss) {
        val density = LocalDensity.current
        val a = anchor.bounds
        val p = enter.value
        var menuHeight by remember { mutableStateOf(0) }
        // الأماكن بالبكسل من شمال الشاشة (`absoluteOffset`) ⇒ البداية لازم تبقى فوق-شمال حتى في العربي (`TopStart` في العربي = فوق-يمين)
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
            val screenH = with(density) { maxHeight.toPx() }
            val gap = with(density) { 10.dp.toPx() }
            VeilLayer(Veil.MENU, p.coerceIn(0f, 1f), onDismiss, closeLabel)
            // (2) الصف المرفوع في مكانه
            val rowShape = RoundedCornerShape(Radius.card)
            Box(
                Modifier.absoluteOffset { IntOffset(a.left.roundToInt(), a.top.roundToInt()) }
                    .size(with(density) { a.width.toDp() }, with(density) { a.height.toDp() })
                    .graphicsLayer {
                        val s = 1f + 0.025f * p
                        scaleX = s
                        scaleY = s
                    }
                    .layeredShadow(rowShape, Shadows.liftedRow, p.coerceIn(0f, 1f))
                    .clip(rowShape)
                    .background(Glass.liftedRow)
                    .innerSheen(rowShape, Shadows.liftedRow)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { lifted() }
            // (3) القائمة: تحت الصف لو فيه مكان، وإلا فوقه
            val below = a.bottom + gap + menuHeight < screenH - with(density) { 24.dp.toPx() }
            val top = if (below) a.bottom + gap else a.top - gap - menuHeight
            val shape = RoundedCornerShape(Radius.menu)
            Box(
                Modifier.absoluteOffset { IntOffset(a.left.roundToInt(), top.roundToInt()) }
                    .width(with(density) { a.width.toDp() })
                    .onSizeChanged { menuHeight = it.height }
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        val s = 0.92f + 0.08f * p
                        scaleX = s
                        scaleY = s
                    }
                    .layeredShadow(shape, Shadows.liquidMenu)
                    .clip(shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title },
            ) {
                BackdropBlur(LocalBackdrop.current, BlurRadius.nav, shape, saturation = 1.3f)
                Column(
                    Modifier.fillMaxWidth().background(Glass.liquidMenu(!blurSupported())).innerSheen(shape, Shadows.liquidMenu).padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (item in items) MenuRow(item) { onDismiss(); item.onClick() }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(Radius.control)
    val surface = if (item.highlight) Modifier.layeredShadow(shape, Shadows.chip).clip(shape).background(Glass.menuHighlight) else Modifier.clip(shape)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).pressScale(press).then(surface).tap(press, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) LucideIcon(item.icon, size = 20.dp, tint = Ink.primary)
        BasicText(item.label, Modifier.weight(1f), style = Type.of(14, if (item.highlight) FontWeight.Bold else FontWeight.Normal))
        if (item.highlight) LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
    }
}
