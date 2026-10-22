package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.CountriesView
import app.masroufy.core.FutureProjection
import app.masroufy.core.InvestFutureKey.*
import app.masroufy.core.MileState
import app.masroufy.core.NOT_AVAILABLE
import app.masroufy.core.formatBp
import app.masroufy.core.monthYear
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.screens.investment.calc.fullDate
import app.masroufy.ui.components.tabular
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

private val CARD = RoundedCornerShape(22.dp)
private val PAD = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
private val GOAL_GREY = Ink.muted
private val WARN = Ink.focus

/** الكروت التلاتة تحت التشارت (`InvestFutureCards` — مواصفة §1.6): الدخل من غير شغل · المحطات والأهداف · البلدين (لو فيه بلدين). */
@Composable
internal fun FutureCards(state: InvestFutureState) {
    val pr = state.projection ?: return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PassiveCard(pr)
        MilesCard(pr)
        state.countries?.let { CountriesCard(it, pr.choice.todayMoney) }
    }
}

@Composable
private fun PassiveCard(pr: FutureProjection) {
    val cur = pr.inputs.currency
    val r = pr.rates
    val note = t(FUT_PASSIVE_NOTE, formatBp(maxOf(0, r.goldBp - r.cpiBp)), formatBp(maxOf(0, r.sharesBp - r.cpiBp))) +
        if (pr.passive.knownMinor > 0) t(FUT_PASSIVE_RENT, wholeMoney(pr.passive.knownMinor, cur)) else ""
    FloatingCard(Modifier.fillMaxWidth(), shape = CARD, contentPadding = PAD) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(FUT_PASSIVE_TITLE), style = Type.of(16, FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PassiveBox(t(FUT_PASSIVE_TODAY), wholeMoney(pr.passive.nowMinor, cur), null, Ink.tint(0x0A), Modifier.weight(1f))
                PassiveBox(
                    t(FUT_YEAR_LINE, (pr.inputs.thisYear + pr.years).toString()), wholeMoney(pr.passive.atYearMinor, cur),
                    t(FUT_PASSIVE_ON_PATH, pathTitle(pr.selected, pr.inputs)), Color(0xFFE2EFE4), Modifier.weight(1f),
                )
            }
            BasicText(note, style = Type.of(12).copy(color = Ink.muted))
        }
    }
}

@Composable
private fun PassiveBox(label: String, value: String, sub: String?, bg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = Type.of(12).copy(color = if (sub != null) Ink.primary else Ink.muted))
        BasicText(value, style = Type.of(20, FontWeight.Bold).tabular(), maxLines = 1, softWrap = false)
        if (sub != null) BasicText(sub, style = Type.of(11).copy(color = Ink.soft))
    }
}

private enum class Dot { PASSED, FUTURE, GOAL }

@Composable
private fun MilesCard(pr: FutureProjection) {
    val thisYear = pr.inputs.thisYear
    FloatingCard(Modifier.fillMaxWidth(), shape = CARD, contentPadding = PAD) {
        Column {
            BasicText(t(FUT_MILES_TITLE), Modifier.padding(bottom = 4.dp), style = Type.of(18, FontWeight.Bold))
            var first = true
            for (m in pr.milestones) {
                if (!first) Divider()
                first = false
                val passed = m.state == MileState.PASSED
                MileRow(if (passed) Dot.PASSED else Dot.FUTURE, t(m.label), mileWhen(m.state, m.yearsFromNow, thisYear), passed)
            }
            for (g in pr.goals) {
                Divider()
                val whenText = g.targetDate?.let { monthYear(it.substring(0, 4).toInt(), it.substring(5, 7).toInt()) } ?: t(FUT_GOAL_NO_DATE)
                MileRow(Dot.GOAL, t(FUT_GOAL_LABEL, g.name), whenText, false)
            }
            BasicText(t(FUT_MILES_NOTE, pathTitle(pr.selected, pr.inputs)), Modifier.padding(top = 8.dp), style = Type.of(12).copy(color = Ink.muted))
        }
    }
}

@Composable
private fun MileRow(dot: Dot, label: String, whenText: String, passed: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (dot) {
            Dot.PASSED -> Box(Modifier.size(10.dp).clip(CircleShape).background(Ink.income))
            Dot.FUTURE -> Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, Ink.income, CircleShape))
            Dot.GOAL -> Canvas(Modifier.size(10.dp)) {
                drawPath(Path().apply { moveTo(size.width / 2, 0f); lineTo(size.width, size.height / 2); lineTo(size.width / 2, size.height); lineTo(0f, size.height / 2); close() }, GOAL_GREY)
            }
        }
        BasicText(label, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold))
        BasicText(whenText, style = Type.of(12, if (passed) FontWeight.Bold else FontWeight.Normal).copy(color = if (passed) Ink.income else Ink.soft))
    }
}

@Composable
private fun CountriesCard(c: CountriesView, todayMoney: Boolean) {
    FloatingCard(Modifier.fillMaxWidth(), shape = CARD, contentPadding = PAD) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(t(FUT_COUNTRIES_TITLE, c.year.toString()), style = Type.of(18, FontWeight.Bold))
            for (row in c.rows) CountryLine(t(if (row.countryCode.uppercase() == "EG") FUT_COUNTRY_EG else FUT_COUNTRY_SA), row.valueMinor?.let { approx(it, row.currency) }, false)
            Divider()
            CountryLine(t(FUT_COUNTRIES_TOTAL), c.totalMinor?.let { approx(it, c.currency) }, true)
            val fx = c.fx
            BasicText(
                if (fx == null) t(FUT_FX_MISSING) else t(FUT_FX_LINE, fxText(fx.egpPerSar4), fx.source, fullDate(fx.asOf)),
                style = Type.of(12).copy(color = Ink.muted),
            )
            if (!todayMoney && fx != null) BasicText(t(FUT_FX_WARN), style = Type.of(12, FontWeight.Bold).copy(color = WARN))
        }
    }
}

@Composable
private fun CountryLine(label: String, value: String?, bold: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = Type.of(if (bold) 16 else 14, if (bold) FontWeight.Bold else FontWeight.Normal))
        BasicText(
            value ?: NOT_AVAILABLE,
            style = Type.of(if (bold) 16 else 15, FontWeight.Bold).copy(color = if (value == null) Ink.muted else Ink.text).tabular(), maxLines = 1,
        )
    }
}
