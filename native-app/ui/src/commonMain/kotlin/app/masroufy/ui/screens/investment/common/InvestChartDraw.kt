package app.masroufy.ui.screens.investment.common

import app.masroufy.ui.theme.Ink

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import app.masroufy.ui.screens.investment.future.shortAxis

/** مقاسات التشارت بالـdp (النموذج: 318×210 · هوامش 50/12/14/28 · المصغّر 84 ارتفاع من غير محاور). */
internal class ChartGeometry(val scale: ChartScale, val n: Int, val mini: Boolean) {
    val left = if (mini) 4f else 50f
    val right = if (mini) 8f else 12f
    val top = if (mini) 8f else 14f
    val bottom = if (mini) 8f else 28f
    val height = if (mini) 84 else 210
}

private val GRID = Ink.tint(0x1A)
private val GUIDE = Ink.tint(0x59)
private val AMBER = Color(0xFFB7791F)
private val PIN_BG = Color(0xFFF6E7C8)
private val FLAG = Ink.muted
private val PILL_EDGE = Ink.tint(0x1F)

/** الرسمة كلها بالترتيب: الشبكة · المساحة · الخطوط · العلامات · الأهداف · المحطات · الصباع · الكتابة (المحاور والحبوب). */
internal fun DrawScope.drawChart(
    geo: ChartGeometry, series: List<ChartSeries>, main: ChartSeries, focused: Boolean, marks: List<ChartMark>, pins: List<ChartPin>,
    flags: List<ChartFlag>, startYear: Int, hover: Int?, measurer: TextMeasurer, axisStyle: TextStyle, pillStyle: TextStyle,
) {
    val d = density
    val l = geo.left * d
    val r = geo.right * d
    val tp = geo.top * d
    val pw = size.width - l - r
    val ph = size.height - tp - geo.bottom * d
    val sc = geo.scale
    val span = (sc.hi - sc.lo).toFloat()
    val n = geo.n
    fun x(i: Float) = l + i / n * pw
    fun y(v: Long) = tp + ((sc.hi - v).toFloat() / span) * ph
    fun linePath(values: List<Long>) = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(0f), y(v)) else lineTo(x(i.toFloat()), y(v)) } }
    fun dot(cx: Float, cy: Float, color: Color, radius: Float = 4f) {
        drawCircle(Color.White, (radius + 1f) * d, Offset(cx, cy))
        drawCircle(color, (radius - 1f) * d, Offset(cx, cy))
    }

    if (!geo.mini) for (v in sc.gridValues) drawLine(GRID, Offset(l, y(v)), Offset(size.width - r, y(v)), 1f * d)
    if (!geo.mini) {
        val base = y(maxOf(sc.lo, 0L))
        val area = linePath(main.values).apply { lineTo(x(n.toFloat()), base); lineTo(x(0f), base); close() }
        drawPath(area, main.color.copy(alpha = 0.10f))
    }
    for (s in series) {
        val isMain = s == main
        val w = if (isMain) 2.5f else if (focused) 1.5f else 2f
        val c = s.color.copy(alpha = if (!focused || isMain) 1f else 0.4f)
        drawPath(linePath(s.values), c, style = Stroke(w * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
        dot(x(n.toFloat()), y(s.values[n]), c)
    }
    if (geo.mini) return
    for (p in pins) {
        if (p.index !in 1..n) continue
        val px = x(p.index.toFloat())
        drawLine(AMBER, Offset(px, tp), Offset(px, tp + ph), 1.5f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * d, 3f * d)))
        dot(px, y(main.values[p.index]), AMBER)
    }
    for (f in flags) {
        val frac = flagFraction(f, n) ?: continue
        val fx = l + frac * pw
        val fy = tp + ph
        drawPath(Path().apply { moveTo(fx, fy - 5 * d); lineTo(fx + 4 * d, fy); lineTo(fx, fy + 5 * d); lineTo(fx - 4 * d, fy); close() }, FLAG)
    }
    val pills = mutableListOf<() -> Unit>()
    for (m in marks) {
        if (m.index !in 0..n) continue
        val s = series.firstOrNull { it.key == m.seriesKey } ?: main
        val mx = x(m.index.toFloat())
        val my = y(s.values[m.index])
        dot(mx, my, s.color, 4.5f)
        pills += {
            val layout = measurer.measure(m.label, pillStyle)
            val rw = layout.size.width + 12 * d
            val cx = (mx).coerceIn(l + rw / 2, size.width - r - rw / 2)
            val ty = maxOf(tp + 12 * d, my - 12 * d)
            drawRoundRect(Color.White, Offset(cx - rw / 2, ty - 13 * d), Size(rw, 16 * d), CornerRadius(8 * d))
            drawRoundRect(PILL_EDGE, Offset(cx - rw / 2, ty - 13 * d), Size(rw, 16 * d), CornerRadius(8 * d), style = Stroke(1 * d))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2, ty - 13 * d + (16 * d - layout.size.height) / 2))
        }
    }
    if (hover != null) {
        val hx = x(hover.toFloat())
        drawLine(GUIDE, Offset(hx, tp), Offset(hx, tp + ph), 1f * d)
        for (s in series) dot(hx, y(s.values[hover]), s.color)
    }
    // الكتابة: أرقام المحور الرأسي على الشمال · السنين تحت · حبوب المحطات والعلامات
    for (v in sc.gridValues) {
        val layout = measurer.measure(shortAxis(v), axisStyle)
        drawText(layout, topLeft = Offset(l - 6 * d - layout.size.width, y(v) - layout.size.height / 2))
    }
    for (i in xLabelIndices(n)) {
        val layout = measurer.measure((startYear + i).toString(), axisStyle)
        val lx = x(i.toFloat()) - when (i) {
            0 -> 0f
            n -> layout.size.width.toFloat()
            else -> layout.size.width / 2f
        }
        drawText(layout, topLeft = Offset(lx, size.height - 18 * d))
    }
    pills.forEach { it() }
    for (p in pins) {
        if (p.index !in 1..n) continue
        val layout = measurer.measure(p.label, pillStyle)
        val w = layout.size.width + 12 * d
        val px = x(p.index.toFloat())
        drawRoundRect(PIN_BG, Offset(px - w / 2, tp - 2 * d), Size(w, layout.size.height.toFloat()), CornerRadius(7 * d))
        drawText(layout, topLeft = Offset(px - layout.size.width / 2, tp - 2 * d))
    }
}
