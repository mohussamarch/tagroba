package app.masroufy.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.BackdropBlur
import app.masroufy.ui.glass.BlurRadius
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.blurSupported
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.motion
import kotlin.math.roundToInt

/**
 * مكان عنصر على الشاشة (بيتسجل بـ`onGloballyPositioned { anchor.update(it) }`) — النوافذ والقائمة السائلة بتطلع منه.
 */
class Anchor {
    var bounds by mutableStateOf(Rect.Zero)
        private set

    fun update(coordinates: LayoutCoordinates) {
        bounds = coordinates.boundsInRoot()
    }
}

/**
 * نافذة زجاج طالعة من زرار (`BellPopover` · `HeroBanks`): زجاج سائل زاوية 28 تحت [anchor] بـ12، ومحاذية لناحية الزرار
 * (لو الزرار في نص الشاشة الشمال ⇒ من الشمال 20). أي ضغطة برّاها بتقفلها. [anchorContent] = الزرار نفسه **فوق** الستارة
 * (النموذج: «الجرس نفسه فوق الستارة مش تحتها»). الظهور بالنابض **المرن** (زجاج سائل).
 */
@Composable
fun GlassPopover(
    visible: Boolean,
    onDismiss: () -> Unit,
    anchor: Anchor,
    title: String,
    modifier: Modifier = Modifier,
    veil: Veil = Veil.LIGHT,
    width: Dp = 320.dp,
    closeLabel: String? = null,
    anchorContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
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
        val progress = enter.value
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenW = with(density) { maxWidth.toPx() }
            val w = with(density) { minOf(width, maxWidth - 40.dp).toPx() }
            val margin = with(density) { 20.dp.toPx() }
            val left = if (a.center.x < screenW / 2f) margin else screenW - margin - w
            val top = a.bottom + with(density) { 12.dp.toPx() }
            VeilLayer(veil, progress.coerceIn(0f, 1f), onDismiss, closeLabel)
            if (anchorContent != null) {
                Box(Modifier.offset { IntOffset(a.left.roundToInt(), a.top.roundToInt()) }.size(with(density) { a.width.toDp() }, with(density) { a.height.toDp() })) {
                    anchorContent()
                }
            }
            val shape = RoundedCornerShape(Radius.menu)
            Column(
                modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .width(with(density) { w.toDp() })
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        val s = 0.9f + 0.1f * progress
                        scaleX = s
                        scaleY = s
                        translationY = (1f - progress) * -10.dp.toPx()
                        transformOrigin = TransformOrigin(((a.center.x - left) / w).coerceIn(0f, 1f), 0f)
                    }
                    .layeredShadow(shape, Shadows.liquidMenu)
                    .clip(shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { paneTitle = title },
            ) {
                Box {
                    BackdropBlur(LocalBackdrop.current, BlurRadius.nav, shape, saturation = 1.3f)
                    Column(
                        Modifier.background(Glass.popover(!blurSupported())).innerSheen(shape, Shadows.liquidMenu).padding(PaddingValues(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
