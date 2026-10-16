package app.masroufy.ui.screens.investment.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.InvestHoldKey
import app.masroufy.core.uiText
import app.masroufy.core.withCurrency
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * التشارت المشترك لشاشة «الاستثمار» (مواصفة r05 §4.1 — **التوقيع ثابت من الخطوة 0**؛ حزمة FUTURE صاحبة شكله النهائي، وHOLDINGS
 * بتناديه بس). ⚠️ الجسم ده رسم خطوط بسيط اتعمل في حزمة HOLDINGS لأن الخطوة 0 ما اتعملتش قبل الحزم — **وقت الدمج نسخة FUTURE تغلب**.
 * القيم هللات؛ تحويلها لنقط على الشاشة هندسة رسم بس (مش حساب فلوس).
 */
data class ChartSeries(val key: String, val label: String, val color: Color, val values: List<Halalas>)
data class ChartMark(val seriesKey: String, val index: Int, val label: String)
data class ChartPin(val index: Int, val label: String)
data class ChartFlag(val monthsFromStart: Int /* drawn at monthsFromStart / 12 on the year axis — geometry only */, val label: String)

@Composable
fun InvestChart(
    title: String,
    series: List<ChartSeries>,
    startYear: Int,
    currency: Currency,
    modifier: Modifier = Modifier,
    focusKey: String? = null,
    marks: List<ChartMark> = emptyList(),
    pins: List<ChartPin> = emptyList(),
    flags: List<ChartFlag> = emptyList(),
    ageStart: Int? = null,
    legend: Boolean = true,
    mini: Boolean = false,
) {
    val count = series.maxOfOrNull { it.values.size } ?: 0
    val top = niceTop(series.flatMap { it.values }.maxOrNull() ?: 0L)
    val measurer = rememberTextMeasurer()
    val axis = TextStyle(fontSize = 10.sp, color = Ink.muted)
    val focus = focusKey ?: series.lastOrNull()?.key
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(if (mini) 120.dp else 200.dp).semantics { contentDescription = title }) {
            val left = 40.dp.toPx()
            val right = 8.dp.toPx()
            val bottom = 22.dp.toPx()
            val topPad = 8.dp.toPx()
            val w = size.width - left - right
            val h = size.height - bottom - topPad
            fun x(i: Int) = left + if (count <= 1) 0f else w * i / (count - 1)
            fun y(v: Halalas) = topPad + h - (if (top <= 0L) 0f else (v.toFloat() / top.toFloat()) * h)
            for (k in 0..3) {
                val v = top / 3 * k
                val yy = y(v)
                drawLine(Color(0x14193D33), Offset(left, yy), Offset(left + w, yy), 1.dp.toPx())
                val text = measurer.measure(axisLabel(v), axis)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), yy - text.size.height / 2))
            }
            val every = if (count > 12) 4 else 2
            for (i in 0 until count step every) {
                val text = measurer.measure((startYear + i).toString(), axis)
                drawText(text, topLeft = Offset(x(i) - text.size.width / 2, topPad + h + 6.dp.toPx()))
            }
            for (s in series) {
                if (s.values.isEmpty()) continue
                val path = Path()
                s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                if (s.key == focus) {
                    val fill = Path().apply { addPath(path); lineTo(x(s.values.lastIndex), topPad + h); lineTo(x(0), topPad + h); close() }
                    drawPath(fill, s.color.copy(alpha = 0.12f))
                }
                drawPath(path, s.color, style = Stroke(if (s.key == focus) 2.5.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(s.color, 3.5.dp.toPx(), Offset(x(s.values.lastIndex), y(s.values.last())))
            }
        }
        if (legend) for (s in series) LegendRow(s, currency)
    }
}

@Composable
private fun LegendRow(s: ChartSeries, currency: Currency) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(12.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(s.color))
        BasicText(s.label, Modifier.weight(1f), style = Type.of(13))
        s.values.lastOrNull()?.let { BasicText("≈ " + withCurrency(chartWhole(it), currency), style = Type.of(13, FontWeight.Bold)) }
    }
}

/** أعلى المحور: مضاعف «لطيف» (1 · 2 · 5 × 10^n) من الوحدة الكاملة × 3 خطوات — هندسة رسم. */
private fun niceTop(maxMinor: Halalas): Long {
    if (maxMinor <= 0L) return 300L * 100
    val whole = maxMinor / 100
    var step = 1L
    while (step * 3 < whole) step *= 10
    val candidates = listOf(step / 10, step / 5, step / 2, step).filter { it > 0 }
    val pick = candidates.firstOrNull { it * 3 >= whole } ?: step
    return pick * 3 * 100
}

private fun axisLabel(minor: Halalas): String {
    val whole = minor / 100
    return when {
        whole == 0L -> "0"
        whole % 1_000_000L == 0L -> uiText(InvestHoldKey.HOLD_AXIS_M, (whole / 1_000_000L).toString())
        whole % 1_000L == 0L -> uiText(InvestHoldKey.HOLD_AXIS_K, (whole / 1_000L).toString())
        else -> chartWhole(minor)
    }
}

private fun chartWhole(minor: Halalas): String {
    val abs = if (minor < 0) -minor else minor
    val digits = (abs / 100).toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, c -> if (i > 0 && (digits.length - i) % 3 == 0) out.append(','); out.append(c) }
    return (if (minor < 0) "−" else "") + out
}
