package app.masroufy.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.min

/**
 * اللوحة اللي بتطلع من تحت (كل اللوحات — KOTLIN-MAP §٣ «اللوحات المنبثقة»):
 * - لازقة في تحت، أول عنصر المقبض 40×4، ومساحة لمس المقبض 14 فوق وتحت و64 على الجنبين.
 * - **الشد لتحت:** بتنزل مع الصباع وتتقفل بعد `min(140, 30%)` من طولها، وإلا بترجع.
 * - **الشد لفوق (مطاط):** ما بتتفصلش عن تحت — بتطوّل لفوق بمقاومة `60·(1−e^(dy/140))` (أقصى 60) وترجع بنطّة. ⚠️ المنحنى المكتوب
 *   `cubic-bezier(.3,1.5,.5,1) 460ms` اتنقل للنابض **المرن** (أقرب نابض بالاسم — ممنوع نابض رابع، DESIGN-SYSTEM).
 * - **طولها ثابت** وإنت بتبدّل جواها: [minHeight] (المحتوى نفسه بيحجز مكانه — زي التصنيف على صفين 88).
 * - الظهور بالنابض الهادي من تحت، والستارة بالسريع. الرجوع والضغط على الستارة بيقفلوها ([onDismiss]).
 */
@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    veil: Veil = Veil.NORMAL,
    closeLabel: String? = null,
    corner: Dp = Radius.sheet,
    minHeight: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
    spacing: Dp = 14.dp,
    onExited: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val enter = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(visible) }
    val enterSpec = motion<Float>(Springs.GENTLE)
    val exitSpec = motion<Float>(Springs.SNAPPY)
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            enter.animateTo(1f, enterSpec)
        } else if (mounted) {
            enter.animateTo(0f, exitSpec)
            mounted = false
            onExited?.invoke()
        }
    }
    if (!mounted) return
    Overlay(onBack = onDismiss) {
        SheetFrame(enter.value, onDismiss, title, modifier, veil, closeLabel, corner, minHeight, contentPadding, spacing, content)
    }
}

/**
 * لوحة متسجّلة في الجدول (`RouteRegistry.sheet<…>` — بتتفتح من أي منطقة بـ`navigator.open(…)`): بتطلع لوحدها، وأي قفل (السحب · الستارة ·
 * الرجوع · [content] بينادي `dismiss`) بيشغّل حركة الخروج **الأول** وبعدها [close] بيشيلها من رصة اللوحات.
 * ```
 * sheet<AddPersonRoute> { _, close -> RouteSheet(t(TextKey.X_TITLE), close) { dismiss -> …; PrimaryButton(…, onClick = { save(); dismiss() }) } }
 * ```
 */
@Composable
fun RouteSheet(
    title: String,
    close: () -> Unit,
    modifier: Modifier = Modifier,
    veil: Veil = Veil.NORMAL,
    minHeight: Dp = 0.dp,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    val dismiss = { visible = false }
    Sheet(visible, dismiss, title, modifier, veil, minHeight = minHeight, onExited = close) { content(dismiss) }
}

@Composable
private fun SheetFrame(
    enter: Float,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier,
    veil: Veil,
    closeLabel: String?,
    corner: Dp,
    minHeight: Dp,
    contentPadding: PaddingValues,
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var height by remember { mutableFloatStateOf(0f) }
    val down = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    var total by remember { mutableFloatStateOf(0f) }
    val back = motion<Float>(Springs.GENTLE)
    val bounce = motion<Float>(Springs.BOUNCY)
    val shape = RoundedCornerShape(topStart = corner, topEnd = corner)
    val stretchMax = with(density) { 60.dp.toPx() }
    val stretchScale = with(density) { 140.dp.toPx() }
    val closeAt = with(density) { 140.dp.toPx() }
    val fade = (1f - (down.value / height.coerceAtLeast(1f))).coerceIn(0.25f, 1f)

    Box(Modifier.fillMaxSize()) {
        VeilLayer(veil, enter * fade, onDismiss, closeLabel)
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { height = it.height.toFloat() }
                .graphicsLayer { translationY = (1f - enter) * height + down.value }
                .layeredShadow(shape, Shadows.sheet)
                .clip(shape)
                .background(Glass.card)
                .innerSheen(shape, Shadows.sheet)
                .pointerInput(Unit) { detectTapGestures { } }
                .sheetDrag(
                    zone = with(density) { 44.dp.toPx() },
                    onStart = { total = 0f },
                    onDelta = { dy ->
                        total += dy
                        scope.launch {
                            if (total >= 0f) {
                                down.snapTo(total)
                                stretch.snapTo(0f)
                            } else {
                                down.snapTo(0f)
                                stretch.snapTo(stretchMax * (1f - exp(total / stretchScale)))
                            }
                        }
                    },
                    onEnd = {
                        if (total > min(closeAt, height * 0.3f)) onDismiss()
                        scope.launch { down.animateTo(0f, back) }
                        scope.launch { stretch.animateTo(0f, bounce) }
                    },
                )
                .semantics { dialog(); paneTitle = title }
                .navigationBarsPadding()
                .imePadding()
                .padding(contentPadding)
                .padding(bottom = with(density) { stretch.value.toDp() })
                .heightIn(min = minHeight),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            // المقبض 40×4 — السحب من أول 44 نقطة في اللوحة (زي النموذج: App.dc.html)
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x59637570)))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        }
    }
}

/** سحب رأسي يبدأ بس من أول [zone] بكسل في اللوحة (الضغطة على زرار جوه المنطقة بتتلغي لما السحب يبدأ). */
private fun Modifier.sheetDrag(zone: Float, onStart: () -> Unit, onDelta: (Float) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        if (first.position.y > zone) return@awaitEachGesture
        var started = false
        val slop = awaitVerticalTouchSlopOrCancellation(first.id) { change, over ->
            change.consume()
            if (!started) { started = true; onStart() }
            onDelta(over)
        }
        if (slop == null) { if (started) onEnd(); return@awaitEachGesture }
        verticalDrag(slop.id) { change ->
            onDelta(change.positionChange().y)
            change.consume()
        }
        onEnd()
    }
}
