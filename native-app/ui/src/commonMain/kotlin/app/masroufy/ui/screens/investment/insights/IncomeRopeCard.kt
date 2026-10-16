package app.masroufy.ui.screens.investment.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IncomeRope
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.IsoDate
import app.masroufy.core.RopeStrand
import app.masroufy.core.dayMonth
import app.masroufy.core.monthName
import app.masroufy.core.monthYear
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceNumber
import app.masroufy.core.shiftMonths
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** سطر في المفتاح: [knot] = دايرة فاضية (بيع مرة واحدة) بدل الخط الملوّن. */
internal data class RopeLegend(val color: Color, val name: String, val period: String, val knot: Boolean = false)

/** شكل الكارت من [IncomeRope] — عرض بس (مفيش فلوس هنا). الألوان بترتيب الخيوط زي النموذج (أخضر · أزرق · كهرماني …). */
internal data class RopeUi(
    val count: String,
    val countLabel: String,
    val sub: String,
    val aria: String,
    val legend: List<RopeLegend>,
    val line: String,
    val fromLabel: String,
    val toLabel: String,
)

internal val ROPE_COLORS = listOf(Color(0xFF0E8A6E), Color(0xFF3866A7), Color(0xFFB7791F), Color(0xFFA55060), Color(0xFF4D747C), Color(0xFF6B5BA8))

internal fun ropeColor(i: Int): Color = ROPE_COLORS[i % ROPE_COLORS.size]

private fun kindName(kind: IncomeSourceKind): String = t(
    when (kind) {
        IncomeSourceKind.JOB -> InvestInsightKey.ROPE_KIND_SALARY
        IncomeSourceKind.PART_TIME -> InvestInsightKey.ROPE_KIND_PART_TIME
        IncomeSourceKind.CLIENT -> InvestInsightKey.ROPE_KIND_CLIENT
        IncomeSourceKind.RENT -> InvestInsightKey.ROPE_KIND_RENT
        IncomeSourceKind.INVESTMENT -> InvestInsightKey.ROPE_KIND_INVESTMENT
        IncomeSourceKind.PENSION -> InvestInsightKey.ROPE_KIND_PENSION
        IncomeSourceKind.OTHER -> InvestInsightKey.ROPE_KIND_OTHER
    },
)

private fun monthOf(date: IsoDate) = monthName(parseIsoDate(date).month)

/** «منذ 10 أغسطس» لو البداية جوه آخر 12 شهر، وإلا «منذ أغسطس 2024». */
private fun sinceText(date: IsoDate, rope: IncomeRope): String =
    if (date > rope.windowStart) dayMonth(date) else parseIsoDate(date).let { monthYear(it.year, it.month) }

private fun periodOf(s: RopeStrand, rope: IncomeRope): String {
    if (s.jobs.size >= 2) {
        val a = s.jobs[s.jobs.size - 2]
        val b = s.jobs.last()
        return t(InvestInsightKey.ROPE_PERIOD_CHAIN, a.name, dayMonth(a.endedAt ?: b.startedAt), b.name)
    }
    val end = s.endedAt
    return if (s.active || end == null) t(InvestInsightKey.ROPE_PERIOD_ACTIVE, s.name, sinceText(s.startedAt, rope))
    else t(InvestInsightKey.ROPE_PERIOD_ENDED, s.name, monthOf(s.startedAt), monthOf(end))
}

private fun countWord(n: Int): String = when (n) {
    1 -> t(InvestInsightKey.ROPE_WORD_ONE)
    2 -> t(InvestInsightKey.ROPE_WORD_TWO)
    else -> t(InvestInsightKey.ROPE_WORD_FEW, sentenceNumber(n))
}

internal fun ropeUi(rope: IncomeRope): RopeUi {
    val now = rope.activeCount
    val legend = rope.strands.mapIndexed { i, s -> RopeLegend(ropeColor(i), kindName(s.kind), periodOf(s, rope)) } +
        rope.knots.map { k ->
            val label = k.label?.let { t(InvestInsightKey.ROPE_SALE, it) } ?: t(InvestInsightKey.ROPE_SALE_DEFAULT)
            RopeLegend(Ink.muted, label, t(InvestInsightKey.ROPE_SALE_PERIOD, monthOf(k.date)), knot = true)
        }
    val first = parseIsoDate(shiftMonths(rope.windowEnd, -(app.masroufy.core.ROPE_MONTHS - 1)))
    val last = parseIsoDate(rope.windowEnd)
    return RopeUi(
        count = sentenceNumber(now),
        countLabel = t(if (now == 1) InvestInsightKey.ROPE_NOW_ONE else InvestInsightKey.ROPE_NOW_MANY),
        sub = t(InvestInsightKey.ROPE_SUB, countWord(rope.strands.size)) + if (rope.knots.isNotEmpty()) t(InvestInsightKey.ROPE_ONE_OFF) else "",
        aria = t(InvestInsightKey.ROPE_ARIA, rope.strands.joinToString("، ") { kindName(it.kind) }),
        legend = legend,
        line = t(if (now <= 1) InvestInsightKey.ROPE_LINE_ONE else InvestInsightKey.ROPE_LINE_MANY),
        fromLabel = monthYear(first.year, first.month),
        toLabel = monthYear(last.year, last.month),
    )
}

/** «حبل مصادر دخلك» (`InvestIncomeRope.dc.html`): العدد · الحبل · المفتاح · سطر الخلاصة · «مصادر الدخل ←» (دايمًا). */
@Composable
internal fun IncomeRopeCard(rope: IncomeRope?, onLink: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(InvestInsightKey.ROPE_TITLE), style = Type.of(16, FontWeight.Bold).copy(color = Ink.text))
            if (rope == null) {
                BasicText(t(InvestInsightKey.ROPE_NONE), style = Type.of(13).copy(color = Ink.muted))
            } else {
                val ui = ropeUi(rope)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    BasicText(ui.count, style = Type.of(28, FontWeight.Bold, 1.2).copy(color = Ink.text))
                    BasicText(ui.countLabel, Modifier.padding(bottom = 5.dp), style = Type.of(14, FontWeight.Bold).copy(color = Ink.text))
                }
                BasicText(ui.sub, Modifier.offset(y = (-6).dp), style = Type.of(12).copy(color = Ink.muted))
                RopeDrawing(rope, ui, Modifier.align(Alignment.CenterHorizontally))
                for (g in ui.legend) LegendRow(g)
                BasicText(ui.line, style = Type.of(12, lineHeight = 1.6).copy(color = Ink.soft))
            }
            LinkText(t(InvestInsightKey.ROPE_LINK), onLink)
        }
    }
}

@Composable
private fun LegendRow(g: RopeLegend) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (g.knot) Box(Modifier.padding(horizontal = 3.dp).size(10.dp).border(2.dp, Ink.muted, CircleShape))
        else Box(Modifier.size(width = 16.dp, height = 3.dp).background(g.color, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f)) {
            BasicText(g.name, style = Type.of(13, FontWeight.Bold).copy(color = Ink.text))
            BasicText(g.period, style = Type.of(11).copy(color = Ink.muted))
        }
    }
}
