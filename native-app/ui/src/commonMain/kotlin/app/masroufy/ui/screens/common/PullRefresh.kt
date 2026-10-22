package app.masroufy.ui.screens.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.masroufy.ui.components.CoinLoader
import app.masroufy.ui.text.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * «اسحب لتحت عشان تحدّث» من غير مكتبة: `nestedScroll` على القايمة. السحب لتحت وإنت فوق خالص بينزّل الصفحة (بنص المسافة — [PULL_RESISTANCE])
 * وعملات «عملات بتترص» بتترص واحدة لكل تلت من المسافة؛ السيب بعد الحد (64) ⇒ [rememberPullRefresh] `onRefresh` والصفحة بتفضل نازلة 60 والحركة شغالة
 * «بنحدّث…»؛ الشاشة هي اللي بتقول إمتى خلص (`refreshing`) ⇒ 3 عملات ثابتة «اتحدّث دلوقتي» 0.9 ثانية وبعدين بترجع في 320ms.
 */
internal const val PULL_RESISTANCE = PullMetrics.RESISTANCE

private val returnEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Indicator position after a finger move [dy] (down = positive), clamped to 0..[max] — never negative (same lesson as the sheet crash). */
internal fun pullAfterDrag(pull: Float, dy: Float, max: Float): Float = (pull + dy * PULL_RESISTANCE).coerceIn(0f, max)

internal fun releaseRefreshes(pull: Float, threshold: Float, refreshing: Boolean): Boolean = !refreshing && pull >= threshold

@Stable
class PullRefreshState internal constructor(
    private val scope: CoroutineScope,
    internal val threshold: Float,
    private val max: Float,
    private val busyAt: Float,
) {
    /** مكان الصفحة (بكسل): بيتحرك مع الإصبع وبعدين بيتحرك لـ[busyAt] أو صفر. */
    internal var drag by mutableFloatStateOf(0f)
    internal var phase by mutableStateOf(PullPhase.IDLE)
    internal var onRefresh: () -> Unit = {}
    private var settle: Job? = null

    private val locked get() = phase == PullPhase.BUSY || phase == PullPhase.DONE

    private fun move(dy: Float): Float {
        settle?.cancel()
        val before = drag
        drag = pullAfterDrag(before, dy, max)
        phase = PullMetrics.draggingPhase(drag, threshold)
        return (drag - before) / PULL_RESISTANCE
    }

    private fun slideTo(target: Float, then: () -> Unit = {}) {
        val from = drag
        settle?.cancel()
        settle = scope.launch {
            animate(from, target, animationSpec = tween(PullMetrics.RETURN_MS, easing = returnEase)) { v, _ -> drag = v }
            then()
        }
    }

    /** الشاشة قالت «بيحدّث» أو «خلص» (من [rememberPullRefresh]). */
    internal suspend fun sync(refreshing: Boolean) {
        if (refreshing) {
            if (phase != PullPhase.BUSY) { phase = PullPhase.BUSY; slideTo(busyAt) }
        } else if (phase == PullPhase.BUSY) {
            phase = PullPhase.DONE
            delay(PullMetrics.DONE_HOLD_MS.toLong())
            slideTo(0f) { phase = PullPhase.IDLE }
        }
    }

    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source != NestedScrollSource.UserInput || locked || available.y >= 0f || drag <= 0f) Offset.Zero else Offset(0f, move(available.y))

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source != NestedScrollSource.UserInput || locked || available.y <= 0f) Offset.Zero else Offset(0f, move(available.y))

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (drag <= 0f || locked) return Velocity.Zero
            if (releaseRefreshes(drag, threshold, false)) {
                phase = PullPhase.BUSY
                onRefresh()
                slideTo(busyAt)
            } else slideTo(0f) { phase = PullPhase.IDLE }
            return available
        }
    }
}

@Composable
fun rememberPullRefresh(refreshing: Boolean, onRefresh: () -> Unit): PullRefreshState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latest by rememberUpdatedState(onRefresh)
    val state = remember(scope, density) {
        with(density) { PullRefreshState(scope, PullMetrics.THRESHOLD_DP.dp.toPx(), PullMetrics.MAX_DP.dp.toPx(), PullMetrics.BUSY_DP.dp.toPx()) }
    }
    state.onRefresh = { latest() }
    LaunchedEffect(state, refreshing) { state.sync(refreshing) }
    return state
}

/** الصفحة بتنزل مع الإصبع (وبتفضل نازلة 60 وقت التحديث) — `nestedScroll` + إزاحة طبقة بس (transform). */
fun Modifier.pullRefresh(state: PullRefreshState): Modifier = nestedScroll(state.connection).graphicsLayer { translationY = state.drag }

/** شريط العملات فوق الصفحة النازلة: العملات بتترص مع السحب وبتشتغل وقت التحديث، والكلام جنبها حسب [PullPhase]. */
@Composable
fun PullRefreshIndicator(state: PullRefreshState, modifier: Modifier = Modifier) {
    val shown = state.drag
    // من غير «return» بدري: الشريط في «if» (درس وقعة «تحويل» في لوحة «+»)
    if (shown > 0.5f) {
        val phase = state.phase
        val h = with(LocalDensity.current) { shown.toDp() }
        Box(
            modifier.fillMaxWidth().height(h).clipToBounds()
                .graphicsLayer { alpha = (shown / 30.dp.toPx()).coerceIn(0f, 1f) }
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            CoinLoader(size = 34.dp, count = PullMetrics.coinsFor(phase, shown / state.pxPerDp), text = t(PullMetrics.labelKey(phase)), row = true, description = null)
        }
    }
}

private val PullRefreshState.pxPerDp get() = threshold / PullMetrics.THRESHOLD_DP
