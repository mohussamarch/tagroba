package app.masroufy.ui.screens.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.theme.Ink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * «اسحب لتحت عشان تحدّث» (قايمة المالك بند 9 — جولة 0.4) من غير مكتبة: `nestedScroll` على القايمة. السحب لتحت وإنت فوق خالص
 * بينزّل الدايرة (بنص المسافة — [PULL_RESISTANCE])، والسيب بعد [PullRefreshState.threshold] ⇒ [rememberPullRefresh] `onRefresh`.
 * الشاشة هي اللي بتقول إمتى خلص (`refreshing`) — الدايرة بتفضل ظاهرة لحد كده.
 */
internal const val PULL_RESISTANCE = 0.5f

/** Indicator position after a finger move [dy] (down = positive), clamped to 0..[max] — never negative (same lesson as the sheet crash). */
internal fun pullAfterDrag(pull: Float, dy: Float, max: Float): Float = (pull + dy * PULL_RESISTANCE).coerceIn(0f, max)

internal fun releaseRefreshes(pull: Float, threshold: Float, refreshing: Boolean): Boolean = !refreshing && pull >= threshold

@Stable
class PullRefreshState internal constructor(private val scope: CoroutineScope, internal val threshold: Float, private val max: Float) {
    internal var drag by mutableFloatStateOf(0f)
    internal var refreshing by mutableStateOf(false)
    internal var onRefresh: () -> Unit = {}
    private var settle: Job? = null

    private fun move(dy: Float): Float {
        settle?.cancel()
        val before = drag
        drag = pullAfterDrag(before, dy, max)
        return (drag - before) / PULL_RESISTANCE
    }

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source != NestedScrollSource.UserInput || available.y >= 0f || drag <= 0f) Offset.Zero else Offset(0f, move(available.y))

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source != NestedScrollSource.UserInput || available.y <= 0f || refreshing) Offset.Zero else Offset(0f, move(available.y))

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (drag <= 0f) return Velocity.Zero
            if (releaseRefreshes(drag, threshold, refreshing)) onRefresh()
            val from = drag
            settle?.cancel()
            settle = scope.launch { animate(from, 0f) { v, _ -> drag = v } }
            return available
        }
    }
}

@Composable
fun rememberPullRefresh(refreshing: Boolean, onRefresh: () -> Unit): PullRefreshState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latest by rememberUpdatedState(onRefresh)
    val state = remember(scope, density) { PullRefreshState(scope, with(density) { 72.dp.toPx() }, with(density) { 120.dp.toPx() }) }
    state.refreshing = refreshing
    state.onRefresh = { latest() }
    return state
}

fun Modifier.pullRefresh(state: PullRefreshState): Modifier = nestedScroll(state.connection)

/** الدايرة فوق: بتنزل مع الصباع، وتلف وتفضل ظاهرة طول ما [PullRefreshState.refreshing] (لحد ما القراية تخلص). */
@Composable
fun PullRefreshIndicator(state: PullRefreshState, label: String, modifier: Modifier = Modifier) {
    val rest by animateFloatAsState(if (state.refreshing) state.threshold else 0f, label = "refreshRest")
    val shown = maxOf(state.drag, rest)
    // من غير «return» بدري: الدايرة في دالة لوحدها جوه «if» (درس وقعة «تحويل» في لوحة «+»)
    if (shown > 0.5f) Spinner(state, shown, label, modifier)
}

@Composable
private fun Spinner(state: PullRefreshState, shown: Float, label: String, modifier: Modifier) {
    val progress = (shown / state.threshold).coerceIn(0f, 1f)
    val spin by rememberInfiniteTransition(label = "refreshSpin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "refreshAngle")
    val spinning = state.refreshing
    Box(
        modifier
            .graphicsLayer { translationY = shown - size.height / 2f; alpha = progress }
            .size(36.dp)
            .clip(CircleShape)
            .background(Glass.card)
            .semantics { if (spinning) { contentDescription = label; liveRegion = LiveRegionMode.Polite } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = if (spinning) spin else progress * 180f }) {
            drawArc(
                color = Ink.primary,
                startAngle = -90f,
                sweepAngle = if (spinning) 270f else 300f * progress,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
