package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.FutureProjection
import app.masroufy.core.InvestFutureKey.*
import app.masroufy.core.MileState
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tabular
import app.masroufy.ui.components.tap
import app.masroufy.ui.screens.investment.common.ChartFlag
import app.masroufy.ui.screens.investment.common.ChartMark
import app.masroufy.ui.screens.investment.common.ChartPin
import app.masroufy.ui.screens.investment.common.ChartSeries
import app.masroufy.ui.screens.investment.common.InvestChart
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

private val AMBER = Color(0xFFB7791F)

/** خطوط التشارت من التوقع: خط لكل طريق لحد المدة المختارة. */
internal fun chartSeries(pr: FutureProjection): List<ChartSeries> =
    pr.lines.map { ChartSeries(it.path.wire, pathTitle(it.path, pr.inputs), pathColor(it.path), it.values.take(pr.years + 1)) }

/** المحطات اللي بتتوصل جوه المدة (على الخط المختار). */
internal fun chartMarks(pr: FutureProjection): List<ChartMark> = pr.milestones
    .filter { it.state == MileState.REACHED && it.yearsFromNow!! <= pr.years }
    .map { ChartMark(pr.selected.wire, it.yearsFromNow!!, t(it.label)) }

/**
 * كارت «هيبقى معاك كام» (`InvestFuture` 73-86 — مواصفة §1.4): العنوان و«بفلوس النهارده / زي ما هيكون» · التشارت (من غير مفتاح — كارت
 * الطرق هو المفتاح) · الملاحظة · «علاماتك» بقيمة الطريق المختار في كل علامة.
 */
@Composable
internal fun FutureChartCard(state: InvestFutureState) {
    val pr = state.projection ?: return
    val i = pr.inputs
    val title = t(FUT_CHART_TITLE)
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, Modifier.weight(1f), style = Type.of(16, FontWeight.Bold))
                ModeToggle(pr.choice.todayMoney) { state.setTodayMoney(it) }
            }
            InvestChart(
                title, chartSeries(pr), i.thisYear, i.currency, focusKey = pr.selected.wire, marks = chartMarks(pr),
                pins = pr.choice.marks.map { ChartPin(it - i.thisYear, it.toString()) },
                flags = pr.goals.mapNotNull { g -> g.monthsFromToday?.let { ChartFlag(it, t(FUT_GOAL_LABEL, g.name)) } },
                ageStart = i.ageNow, legend = false,
            )
            BasicText(t(FUT_CHART_NOTE), style = Type.of(12).copy(color = Ink.muted))
            if (pr.pins.isNotEmpty()) {
                Divider()
                BasicText(t(FUT_PINS_TITLE), style = Type.of(13, FontWeight.Bold))
                for ((year, value) in pr.pins) Row(
                    Modifier.fillMaxWidth().heightIn(min = 28.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AMBER))
                        BasicText(year.toString(), style = Type.of(13))
                    }
                    BasicText(approx(value, i.currency), style = Type.of(13, FontWeight.Bold).tabular(), maxLines = 1)
                }
            }
        }
    }
}

/** «بفلوس النهارده» (الافتراضي §87-5) · «زي ما هيكون». */
@Composable
private fun ModeToggle(todayMoney: Boolean, onPick: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.tint(0x0F)).padding(3.dp).semantics { contentDescription = t(FUT_MODE_A11Y) },
    ) {
        for ((value, key) in listOf(true to FUT_MODE_TODAY, false to FUT_MODE_THEN)) {
            val on = value == todayMoney
            val press = rememberPress()
            val shape = RoundedCornerShape(11.dp)
            val surface = if (on) Modifier.shadow(3.dp, shape, ambientColor = Color(0x1A1D3635), spotColor = Color(0x1A1D3635)).clip(shape).background(Ink.card) else Modifier.clip(shape)
            Box(
                Modifier.height(34.dp).then(surface).tap(press, role = Role.RadioButton) { onPick(value) }.semantics { selected = on }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(t(key), style = Type.of(12, FontWeight.Bold).copy(color = if (on) Ink.primary else Ink.muted), maxLines = 1, softWrap = false) }
        }
    }
}
