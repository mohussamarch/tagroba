package app.masroufy.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Type

private val STILL_EASE = CubicBezierEasing(0.3f, 1.5f, 0.6f, 1f)
private val MUTED = Color(0xFF637570)

/**
 * علامة التحميل «عملات بتترص» (قرار المالك 2026-10-22 — بدل دايرة أندرويد). **مكوّن واحد** لكل الأماكن: شاشة بتحمّل (112 + [text]) ·
 * السحب للتحديث · جوه الزرار الأساسي أثناء الحفظ. [count] = -1 ⇒ الحركة شغالة · 0..3 ⇒ عدد العملات المرصوصة ثابتة (السحب للتحديث).
 * [row] = الكلام جنبها بدل تحتها · [onGreen] = الكلام أبيض (جوه الزرار). «تقليل الحركة» (`LocalReduceMotion`) ⇒ العملات ثابتة.
 * الحركة transform وشفافية بس، والتوقيت دالة صافية ([CoinLoaderTiming]).
 */
@Composable
fun CoinLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    count: Int = -1,
    text: String? = null,
    row: Boolean = false,
    onGreen: Boolean = false,
    description: String? = t(R06Key.LOADER_A11Y),
) {
    val gap = if (row) 8.dp else (size.value * 0.08f).dp
    val a11y = (text ?: description)?.let { d -> Modifier.semantics { contentDescription = d } } ?: Modifier
    val label: @Composable () -> Unit = {
        if (!text.isNullOrEmpty()) {
            val big = size >= 80.dp
            BasicText(text, style = Type.of(if (big) 14 else 12, if (big || onGreen) FontWeight.Bold else FontWeight.Medium).copy(color = if (onGreen) Color.White else MUTED))
        }
    }
    val stack: @Composable () -> Unit = { CoinStack(size, count) }
    if (row) Row(modifier.then(a11y), Arrangement.spacedBy(gap, Alignment.CenterHorizontally), Alignment.CenterVertically) { stack(); label() }
    else Column(modifier.then(a11y), Arrangement.spacedBy(gap, Alignment.Top), Alignment.CenterHorizontally) { stack(); label() }
}

@Composable
private fun CoinStack(size: Dp, count: Int) {
    val reduce = LocalReduceMotion.current
    val live = count < 0 && !reduce
    val time: State<Float> = if (!live) remember { mutableStateOf(0f) } else rememberInfiniteTransition(label = "coins")
        .animateFloat(0f, CoinLoaderTiming.CYCLE_MS.toFloat(), infiniteRepeatable(tween(CoinLoaderTiming.CYCLE_MS, easing = LinearEasing)), label = "coinsTime")
    // الثابتة: كل عملة بتطلع/بتختفي بنابض خفيف (220ms)
    val shown = if (count < 0) CoinLoaderTiming.COINS else count.coerceIn(0, CoinLoaderTiming.COINS)
    val still = List(CoinLoaderTiming.COINS) { i -> animateFloatAsState(if (i < shown) 1f else 0f, tween(if (reduce) 0 else 220, easing = STILL_EASE), label = "still$i") }
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        if (live) {
            val ms = time.value
            drawCoinStack(List(CoinLoaderTiming.COINS) { CoinLoaderTiming.frame(ms, it) }, List(CoinLoaderTiming.COINS) { CoinLoaderTiming.shineX(ms, it) }, CoinLoaderTiming.floor(ms))
        } else {
            drawCoinStack(List(CoinLoaderTiming.COINS) { i -> still[i].value.let { p -> CoinFrame(-16f * (1f - p), p.coerceIn(0f, 1f)) } }, null, FloorFrame(1f, if (shown > 0) 1f else 0.4f))
        }
    }
}
