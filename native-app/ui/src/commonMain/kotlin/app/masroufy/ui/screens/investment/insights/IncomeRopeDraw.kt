package app.masroufy.ui.screens.investment.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.masroufy.core.IncomeRope
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.IsoDate
import app.masroufy.core.daysBetween
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlin.math.PI
import kotlin.math.sin

/** مقاس الرسمة في النموذج (318×96، من الشمال لليمين زي التشارت) — بتصغر بنفس النسبة على الشاشات الأضيق. */
private const val ROPE_W = 318f
private const val ROPE_H = 96f
private const val TRACK_Y = 44f
private val trackColor get() = Ink.tint(0x14)
private val tickColor get() = Ink.tint(0xFF)

/** مكان التاريخ على المحور (هندسة رسم بس): 4 + (شهور من أول الفترة ÷ 12) × 310. */
private fun ropeX(date: IsoDate, rope: IncomeRope): Float {
    val span = daysBetween(rope.windowStart, rope.windowEnd).coerceAtLeast(1)
    val d = daysBetween(rope.windowStart, date).coerceIn(0, span)
    return 4f + d.toFloat() / span * 310f
}

/** الحبل: مسار رمادي عريض · خيط موجة لكل مصدر بطور مختلف (فيبانوا متلفّفين) · خط متقطّع عند «غيّرت الشغل» · عقدة للبيع. */
@Composable
internal fun RopeDrawing(rope: IncomeRope, ui: RopeUi, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w: Dp = min(maxWidth, ROPE_W.dp)
        val scale = w.value / ROPE_W
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.width(w).height((ROPE_H * scale).dp).semantics { contentDescription = ui.aria }) {
                Canvas(Modifier.fillMaxSize()) { drawRope(rope, density * scale) }
                val label = Type.of(10).copy(color = Ink.muted)
                for (tick in rope.ticks) {
                    BasicText(t(InvestInsightKey.ROPE_JOB_CHANGE), Modifier.placeAt(ropeX(tick, rope) * scale, 6f * scale, center = true), style = label.copy(color = Ink.soft), maxLines = 1)
                }
                BasicText(ui.fromLabel, Modifier.placeAt(4f * scale, 80f * scale), style = label, maxLines = 1)
                BasicText(ui.toLabel, Modifier.placeAt(314f * scale, 80f * scale, end = true), style = label, maxLines = 1)
            }
        }
    }
}

/** يحط النص عند (x, y) بالـdp: [center] = منتصفه عند x · [end] = آخره عند x. */
private fun Modifier.placeAt(x: Float, y: Float, center: Boolean = false, end: Boolean = false) = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, maxWidth = Int.MAX_VALUE))
    layout(0, 0) {
        val px = x.dp.roundToPx() - when { center -> p.width / 2; end -> p.width; else -> 0 }
        p.place(px, y.dp.roundToPx())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRope(rope: IncomeRope, unit: Float) {
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    drawLine(trackColor, p(4f, TRACK_Y), p(314f, TRACK_Y), strokeWidth = 18f * unit, cap = StrokeCap.Round)
    val k = rope.strands.size
    val amp = 4f + 3f * minOf(k, 4)
    rope.strands.forEachIndexed { i, s ->
        val from = ropeX(s.from, rope)
        val to = ropeX(s.to, rope)
        val phase = (i * 2 * PI / maxOf(k, 1)).toFloat()
        val path = Path()
        var x = from
        var first = true
        while (x <= to + 0.1f) {
            val y = TRACK_Y + amp * sin(x / 11f + phase)
            if (first) path.moveTo(x * unit, y * unit) else path.lineTo(x * unit, y * unit)
            first = false
            x += 3f
        }
        drawPath(path, ropeColor(i), style = Stroke(width = 3f * unit, cap = StrokeCap.Round))
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f * unit, 3f * unit))
    for (tick in rope.ticks) {
        val x = ropeX(tick, rope)
        drawLine(tickColor, p(x, 26f), p(x, 62f), strokeWidth = 1f * unit, pathEffect = dash)
    }
    for (knot in rope.knots) {
        val c = p(ropeX(knot.date, rope), TRACK_Y)
        drawCircle(Color.White, 5f * unit, c)
        drawCircle(Ink.muted, 5f * unit, c, style = Stroke(width = 2f * unit))
    }
}
