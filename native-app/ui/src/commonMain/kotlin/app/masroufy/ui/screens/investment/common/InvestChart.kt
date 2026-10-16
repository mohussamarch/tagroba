package app.masroufy.ui.screens.investment.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.InvestFutureKey
import app.masroufy.core.uiText
import app.masroufy.core.withCurrency
import app.masroufy.ui.components.tabular
import app.masroufy.ui.screens.investment.future.whole
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * تشارت «هيبقى معاك كام» (`InvestChart` — مواصفة §1.4، عرض بس — الحساب في `ProjectInvestFuture`): خط لكل طريق، والمختار ([focusKey])
 * عريض ومعتم وتحته مساحة 10%، والباقي أرفع وباهت · محطات على الخط المختار · علامات المستخدم (خط كهرماني متقطع) · الأهداف معيّنات على
 * محور الزمن · الصباع على التشارت ⇒ خط رأسي ونقطة لكل خط وكارت فيه السنة والعمر وكل القيم. الزمن من الشمال لليمين زي الأرقام.
 * **كل الكلام برا الرسمة** ما عدا أرقام المحاور والحبوب والكارت (KOTLIN-MAP 46). [legend] = المفتاح تحت (الشاشة بتقفله — كارت الطرق هو المفتاح).
 */
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
    if (series.isEmpty() || series[0].values.isEmpty()) return
    val n = maxOf(1, series[0].values.size - 1)
    val main = series.firstOrNull { it.key == focusKey } ?: series[0]
    val geo = ChartGeometry(chartScale(series), n, mini)
    var hover by remember(series) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axisStyle = Type.of(10, lineHeight = 1.4).copy(color = Ink.muted).tabular()
    val pillStyle = Type.of(10, FontWeight.Bold, 1.4).copy(color = Ink.text)
    val a11y = title + ": " + series.joinToString("، ") {
        uiText(InvestFutureKey.FUT_CHART_A11Y_ITEM, it.label, withCurrency(whole(it.values[n]), currency), (startYear + n).toString())
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(geo.height.dp).semantics { contentDescription = a11y }) {
                val widthPx = constraints.maxWidth.toFloat()
                val dens = androidx.compose.ui.platform.LocalDensity.current.density
                fun indexAt(x: Float): Int {
                    val d = dens
                    val pw = widthPx - (geo.left + geo.right) * d
                    return (((x - geo.left * d) / pw) * n).let { kotlin.math.round(it).toInt() }.coerceIn(0, n)
                }
                val touch = if (mini) Modifier else Modifier
                    .pointerInput(n, widthPx) { detectTapGestures(onPress = { hover = indexAt(it.x) }) }
                    .pointerInput(n, widthPx) { detectHorizontalDragGestures { change, _ -> hover = indexAt(change.position.x) } }
                Canvas(Modifier.fillMaxWidth().height(geo.height.dp).then(touch)) {
                    drawChart(geo, series, main, focusKey != null, marks, pins, flags, startYear, hover, measurer, axisStyle, pillStyle)
                }
                val h = hover
                if (h != null && !mini) Tooltip(geo, h, n, widthPx, series, startYear, ageStart)
            }
        }
        if (legend && !mini) Legend(series, n, currency)
    }
}

private val TIP_WIDTH = 168

@Composable
private fun Tooltip(geo: ChartGeometry, h: Int, n: Int, widthPx: Float, series: List<ChartSeries>, startYear: Int, ageStart: Int?) {
    val d = androidx.compose.ui.platform.LocalDensity.current.density
    val widthDp = widthPx / d
    val x = geo.left + (h.toFloat() / n) * (widthDp - geo.left - geo.right)
    val left = (x - TIP_WIDTH / 2f).coerceIn(0f, maxOf(0f, widthDp - TIP_WIDTH))
    val title = uiText(InvestFutureKey.FUT_YEAR_LINE, (startYear + h).toString()) + (ageStart?.let { uiText(InvestFutureKey.FUT_TIP_AGE, (it + h).toString()) } ?: "")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            Modifier.offset(x = left.dp).width(TIP_WIDTH.dp).shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x291D3635), spotColor = Color(0x291D3635))
                .clip(RoundedCornerShape(12.dp)).background(Color(0xF5FFFFFF)).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText(title, style = Type.of(12, FontWeight.Bold))
            for (s in series) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(12.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(s.color))
                BasicText(whole(s.values[h]), style = Type.of(12, FontWeight.Bold).tabular(), maxLines = 1)
                BasicText(s.label, style = Type.of(12).copy(color = Ink.muted), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Legend(series: List<ChartSeries>, n: Int, currency: Currency) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in series) Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(16.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(s.color))
            BasicText(s.label, Modifier.weight(1f), style = Type.of(13))
            BasicText("≈ " + withCurrency(whole(s.values[n]), currency), style = Type.of(13, FontWeight.Bold).tabular(), maxLines = 1)
        }
    }
}
