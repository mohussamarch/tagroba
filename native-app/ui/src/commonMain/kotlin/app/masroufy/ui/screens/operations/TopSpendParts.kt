package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.UiKey
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.budgets.ProgressBar
import app.masroufy.ui.screens.budgets.parseHexColor
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SpendRankRow
import app.masroufy.usecase.TopSpendView

/*
 * أجزاء شاشة «أكتر ما صرفت عليه» زي تصميم `TopSpending` (0.5.1): شرايح الفترة · كارت المجموع بشريط الحصص · كارت واحد فيه السطور
 * مترتبة. كل الأرقام والنسب جاهزة من حالة الاستخدام — الأجزاء دي بتعرض بس.
 */

private val SHARE_REST = Color(0x2E193D33)
private val ROW_TRACK = Color(0x12193D33)
private val CHIP_IDLE = Color(0xB3FFFFFF)

/**
 * شرايح الفترة: المختارة أخضر مليان بكتابة بيضا عريضة، والباقي أبيض شفاف بحد رفيع (التصميم). مجموعة اختيار اسمها «الفترة» (زي
 * `aria-label` في النموذج)، والشرايح بتنزل سطر تاني لو العرض ما كفاش (`flex-wrap` في النموذج — شاشة ضيقة أو خط كبير أو الإنجليزي).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopSpendPeriodChips(selected: FilterPeriod, onPick: (FilterPeriod) -> Unit) {
    val label = t(UiKey.TOP_SPEND_PERIOD)
    FlowRow(
        Modifier.selectableGroup().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TOP_SPEND_PERIODS.forEach { p -> PeriodChip(periodName(p), p == selected) { onPick(p) } }
    }
}

@Composable
private fun PeriodChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val press = rememberPress()
    val surface = if (on) Modifier.background(Glass.primary) else Modifier.background(CHIP_IDLE).insetRing(shape, 1.dp, Ink.fieldEdge)
    Box(
        Modifier.height(40.dp).pressScale(press).clip(shape).then(surface)
            .tap(press, role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Type.of(13, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Ink.onPrimary else Ink.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * كارت المجموع: المدى · المجموع الكبير · «على N تصنيفات · M عملية» · شريط الحصص (أول 5 بألوانهم من اليمين والباقي رمادي) · سطر
 * «مصروفك الحقيقي بس» · وملاحظة «غير متاح» أو «لحد دلوقتي» لو فيه (CLAUDE.md #10).
 */
@Composable
internal fun TopSpendTotalCard(v: TopSpendView, currency: Currency) {
    FloatingCard(shape = RoundedCornerShape(26.dp), contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(topSpendRange(v), style = Type.caption().copy(color = Ink.muted))
                AmountText(v.totalMinor, currency, size = 28)
                if (v.totalMinor != null) BasicText(topSpendSummary(v), style = Type.of(13).copy(color = Ink.muted))
            }
            if (v.rows.isNotEmpty()) ShareBar(v)
            BasicText(t(UiKey.TOP_SPEND_EXCLUDED), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
            val note = when {
                v.totalMinor == null -> t(UiKey.TOP_SPEND_UNKNOWN)
                v.partial -> t(UiKey.TOP_SPEND_PARTIAL)
                else -> null
            }
            if (note != null) BasicText(note, style = Type.captionBold().copy(color = Ink.focus))
        }
    }
}

/** الحصص بعرض نسبتها (بالعُشر من المية من حالة الاستخدام) — الأولى على اليمين في العربي. */
@Composable
private fun ShareBar(v: TopSpendView) {
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        v.shares.filter { it.tenthPercent > 0 }.forEach { s ->
            Box(Modifier.weight(s.tenthPercent.toFloat()).fillMaxHeight().background(s.colorHex?.let(::parseHexColor) ?: Ink.muted))
        }
        if (v.restTenthPercent > 0) Box(Modifier.weight(v.restTenthPercent.toFloat()).fillMaxHeight().background(SHARE_REST))
    }
}

/** كارت واحد فيه السطور مترتبة، بخط رفيع بين كل سطرين. */
@Composable
internal fun TopSpendRanks(rows: List<SpendRankRow>, currency: Currency, onOpen: (SpendRankRow) -> Unit) {
    FloatingCard(shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        rows.forEachIndexed { i, r ->
            RankRow(i + 1, r, currency) { onOpen(r) }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
        }
    }
}

/**
 * سطر: الترتيب · مربع الأيقونة 40 بلون التصنيف · الاسم وتحته العدد والفرق عن الشهر اللي فات (↑ أحمر · ↓ أخضر — «الشهر ده» بس) · المبلغ
 * والنسبة على الشمال · شريط بالنسبة لأكبر تصنيف تحت، بيبدأ بعد عمود الترتيب.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RankRow(rank: Int, r: SpendRankRow, currency: Currency, onClick: () -> Unit) {
    val color = r.colorHex?.let(::parseHexColor) ?: Ink.muted
    val name = r.name ?: t(UiKey.HOME_TOP_UNCATEGORIZED)
    val change = topSpendChange(r)
    val shape = RoundedCornerShape(16.dp)
    val press = rememberPress()
    Column(
        Modifier.fillMaxWidth().pressScale(press).clip(shape).tap(press, label = name, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(rank.toString(), Modifier.width(22.dp), style = Type.of(12, FontWeight.Bold).copy(color = Ink.muted, textAlign = TextAlign.Center))
            IconTile(color, size = 40.dp, radius = 13.dp) { LucideIcon(categoryIcon(r.iconKey), size = 20.dp, tint = color) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // الاسم بيلف على سطرين لو طويل (النموذج من غير حد سطور) — مش بيتقص من غير «…»
                BasicText(name, style = Type.of(15, FontWeight.Bold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(t(UiKey.TOP_SPEND_COUNT, r.count.toString()), style = Type.caption().copy(color = Ink.muted))
                    if (change != null) {
                        BasicText(change.first, style = Type.captionBold().copy(color = if (change.second) Ink.expense else Ink.income))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AmountText(r.amountMinor, currency, size = 15)
                // النسبة جوه عزل اتجاه عشان «42%» ما تتقلبش «%42»
                BasicText("⁦${r.sharePercent}%⁩", style = Type.caption().copy(color = Ink.muted))
            }
        }
        ProgressBar(r.barPercent, color, Modifier.padding(start = 34.dp), height = 6.dp, track = ROW_TRACK)
    }
}
