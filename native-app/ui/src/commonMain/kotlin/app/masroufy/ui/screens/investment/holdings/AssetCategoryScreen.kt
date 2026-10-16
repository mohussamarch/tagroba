package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.HoldingGroup
import app.masroufy.core.InvestHoldKey as K
import app.masroufy.core.describeEntry
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.QuickMenu
import app.masroufy.ui.overlay.QuickTarget
import app.masroufy.ui.screens.investment.assetQuickItems
import app.masroufy.ui.screens.investment.common.ChartSeries
import app.masroufy.ui.screens.investment.common.InvestChart
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.GroupProjection
import app.masroufy.usecase.PlanSlider

/** كل اللي صفحة النوع بتعرضه (عرض بس — الأرقام من `categoryUi` و`ProjectHoldingGroup`). */
internal class CategoryView(
    val ui: CategoryUi,
    val currency: Currency,
    val thisYear: Int,
    val span: Int,
    val projection: GroupProjection?,
    val slider: PlanSlider,
    val planMinor: Halalas,
)

internal class CategoryActions(
    val back: () -> Unit,
    val span: (Int) -> Unit,
    val plan: (Int) -> Unit,
    val open: (CategoryRow) -> Unit,
    val archive: (CategoryRow) -> Unit,
    val add: () -> Unit,
    val savingsPlan: () -> Unit,
)

/** محتوى صفحة النوع (النموذج `AssetCategory`) — عناصر للقايمة الطويلة. [quick] null ⇒ من غير ضغط طويل (الرسم في الاختبار). */
@Composable
internal fun CategoryHeader(v: CategoryView, a: CategoryActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.mirrorInLtr()) { SurfaceIconButton(Lucide.CHEVRON_RIGHT, app.masroufy.core.uiText(app.masroufy.core.UiKey.SHELL_BACK), a.back) }
        Column(Modifier.weight(1f)) {
            BasicText(t(categoryKey(v.ui.group)), style = Type.of(24, FontWeight.Bold, 1.4))
            BasicText(categorySub(v.ui.count), style = Type.of(12).copy(color = Ink.muted))
        }
        v.ui.totalMinor?.let { BasicText(wholeMoney(it, v.currency), style = Type.of(18, FontWeight.Bold)) }
    }
}

@Composable
internal fun CategoryChartCard(v: CategoryView, a: CategoryActions) {
    val p = v.projection
    if (v.ui.chart != CategoryChart.CHART || p == null) {
        val text = when (v.ui.chart) {
            CategoryChart.NO_AVERAGE -> K.HOLD_NO_AVERAGE
            CategoryChart.RATES_NA -> K.HOLD_RATES_NA
            else -> K.HOLD_VALUE_NA_CHART
        }
        BasicText(
            t(text), Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SOFT_FILL).padding(horizontal = 14.dp, vertical = 12.dp),
            style = Type.of(13, lineHeight = 1.6).copy(color = Ink.soft),
        )
        return
    }
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                BasicText(t(K.HOLD_CHART_TITLE), style = Type.of(16, FontWeight.Bold))
                val sub = t(K.HOLD_CHART_SUB) + if (v.ui.group == HoldingGroup.PROPERTY) t(K.HOLD_CHART_SUB_PROPERTY) else ""
                BasicText(sub, style = Type.of(12).copy(color = Ink.muted))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (span in CATEGORY_SPANS) SpanChip(spanLabel(span), span == v.span) { a.span(span) }
            }
            InvestChart(
                title = t(K.HOLD_CHART_TITLE),
                series = listOf(
                    ChartSeries("leave", t(K.HOLD_LINE_LEAVE), LEAVE_COLOR, p.leave),
                    ChartSeries("plan", app.masroufy.core.uiText(K.HOLD_LINE_PLAN, wholeText(v.planMinor)), PLAN_COLOR, p.plan),
                ),
                startYear = v.thisYear, currency = v.currency, focusKey = "plan",
            )
        }
    }
}

@Composable
private fun SpanChip(label: String, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) Modifier.background(Brush.linearGradient(listOf(Color(0xFF0A7A62), Ink.primary))) else Modifier.background(CHIP_FILL)
    Box(Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).then(bg).holdTap(label, onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = if (on) Color.White else Ink.text))
    }
}

private fun spanLabel(span: Int): String = app.masroufy.core.uiText(
    when (span) {
        5 -> K.HOLD_SPAN_5
        20 -> K.HOLD_SPAN_20
        else -> K.HOLD_SPAN_10
    },
)

@Composable
internal fun CategoryList(v: CategoryView, a: CategoryActions, quick: QuickMenu?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(K.HOLD_LIST_TITLE), style = Type.of(18, FontWeight.Bold))
        FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            for (row in v.ui.rows) {
                if (quick == null) RowView(row, v.currency, null) { a.open(row) } else {
                    QuickTarget(quick, row.name, { assetQuickItems(false, { a.open(row) }, { a.archive(row) }) }) { long ->
                        RowView(row, v.currency, long, quick.guardTap { a.open(row) })
                    }
                }
            }
        }
        val label = app.masroufy.core.uiText(K.HOLD_ADD_TO, t(categoryKey(v.ui.group)))
        Row(
            Modifier.fillMaxWidth().height(52.dp).dashedBorder(18.dp).clip(RoundedCornerShape(18.dp)).background(ADD_FILL).holdTap(label, a.add),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(HoldIcons.plus, size = 20.dp, tint = Ink.primary)
            BasicText(label, style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RowView(row: CategoryRow, currency: Currency, onLong: (() -> Unit)?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .combinedClickable(onClickLabel = row.name, role = androidx.compose.ui.semantics.Role.Button, onLongClick = onLong, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (bg, fg) = groupBox(row.type.group)
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            LucideIcon(typeIcon(row.type), size = 18.dp, tint = fg)
        }
        Column(Modifier.weight(1f)) {
            BasicText(row.name, style = Type.of(14, FontWeight.Bold))
            BasicText(row.sub, style = Type.of(12).copy(color = Ink.muted))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val value = row.valueMinor
            BasicText(
                if (value == null) t(K.HOLD_NA) else wholeMoney(value, currency),
                style = Type.of(14, FontWeight.Bold).copy(color = if (value == null) Ink.muted else Ink.text),
            )
            PurposeTag(row.purpose)
        }
    }
}

@Composable
internal fun CategoryPlanCard(v: CategoryView, a: CategoryActions) {
    val p = v.projection ?: return
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(K.HOLD_PLAN_TITLE), style = Type.of(16, FontWeight.Bold))
            val amount = wholeMoney(v.planMinor, v.currency)
            BasicText(amount, style = Type.of(22, FontWeight.Bold))
            StepSlider(v.slider.stepOf(v.planMinor), v.slider.steps, t(K.HOLD_PLAN_TITLE), amount, a.plan)
            val year = (v.thisYear + v.span).toString()
            val result = app.masroufy.core.uiText(
                K.HOLD_PLAN_RESULT, year, wholeMoney(p.plan.last(), v.currency), wholeMoney(p.leave.last(), v.currency),
            )
            BasicText(result, style = Type.of(13, lineHeight = 1.6))
            BasicText(
                t(K.HOLD_PLAN_LINK), Modifier.heightIn(min = 40.dp).holdTap(t(K.HOLD_PLAN_LINK), a.savingsPlan).padding(vertical = 10.dp),
                style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary),
            )
        }
    }
}

/** سطر المصدر تحت الصفحة: مصدر المتوسط نفسه + «متوسط اللي فات ومش وعد» (القاعدة 10). */
@Composable
internal fun CategorySource(v: CategoryView) {
    val p = v.projection ?: return
    BasicText(describeEntry(p.source) + t(K.HOLD_SOURCE_SUFFIX), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
}

internal val LEAVE_COLOR = Color(0xFF3866A7)
internal val PLAN_COLOR = Color(0xFF0E8A6E)
