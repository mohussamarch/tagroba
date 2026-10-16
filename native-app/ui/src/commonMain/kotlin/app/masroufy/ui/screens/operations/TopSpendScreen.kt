package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.budgets.ProgressBar
import app.masroufy.ui.screens.budgets.parseHexColor
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.home.ReadState
import app.masroufy.ui.screens.home.readState
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SpendRankRow
import app.masroufy.usecase.TopSpendView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «أكثر ما صرفت عليه» (طلب المالك 2026-10-16 — OVERRIDES §98): الترتيب كامل للصرف الحقيقي على التصنيفات في الشهر المالي (الشهر ده
 * أو اللي فات)، و«بلا تصنيف» سطر لوحده. الضغط على سطر ⇒ عمليات التصنيف ده في نفس الفترة (شاشة التصفية). الترتيب والمجاميع والنسب
 * من `LoadTopCategories.loadAll` (نفس مصدر كارت الرئيسية) — الشاشة بتعرض بس. القراية برّه الخيط الرئيسي ومرة واحدة لكل فتحة/فترة.
 */
@Composable
fun TopSpendScreen() {
    val space = LocalSpace.current
    val top = space.home.cards.top
    val ops = space.operations
    val nav = LocalNavigator.current
    val currency = space.space.currency
    var period by rememberSaveable { mutableStateOf(FilterPeriod.THIS) }
    var state by remember(top) { mutableStateOf<ReadState<TopSpendView>>(ReadState.Loading) }
    LaunchedEffect(top, period) {
        state = ReadState.Loading
        state = readState {
            val payday = attempt { ops.payday() } ?: DEFAULT_PAYDAY
            val today = space.shell.today()
            withContext(Dispatchers.Default) { top.loadAll(today, payday, period.skip) }
        }
    }
    InnerScaffold(t(UiKey.TOP_SPEND_TITLE)) {
        item(key = "periods") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TOP_SPEND_PERIODS.forEach { p -> SelectChip(periodName(p), period == p, { period = p }) }
            }
        }
        when (val s = state) {
            ReadState.Loading -> items(3, key = { "skeleton-$it" }) { Skeleton(Modifier.fillMaxWidth().height(76.dp)) }
            ReadState.Failed -> item(key = "failed") { EmptyState(t(UiKey.TOP_SPEND_FAILED)) }
            is ReadState.Ready -> {
                val v = s.value
                item(key = "total") { TotalCard(v, currency) }
                if (v.rows.isEmpty() && v.totalMinor != null) {
                    item(key = "empty") { EmptyState(t(UiKey.TOP_SPEND_EMPTY), t(UiKey.TOP_SPEND_EMPTY_BODY)) }
                }
                items(v.rows.size, key = { "row-${v.rows[it].categoryId ?: NO_CATEGORY}" }) { i ->
                    val r = v.rows[i]
                    SpendRow(r, currency) { nav.push(categoryOperationsRoute(r.categoryId, period)) }
                }
            }
        }
    }
}

@Composable
private fun TotalCard(v: TopSpendView, currency: Currency) {
    FloatingCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(t(UiKey.TOP_SPEND_TOTAL), style = Type.caption().copy(color = Ink.muted))
            AmountText(v.totalMinor, currency, size = 24)
            BasicText(v.periodRange, style = Type.caption().copy(color = Ink.muted))
            // CLAUDE.md #10: المجهول «غير متاح» بسببه، والجزئي «لحد دلوقتي»
            val note = when {
                v.totalMinor == null -> t(UiKey.TOP_SPEND_UNKNOWN)
                v.partial -> t(UiKey.TOP_SPEND_PARTIAL)
                else -> null
            }
            if (note != null) BasicText(note, style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun SpendRow(r: SpendRankRow, currency: Currency, onClick: () -> Unit) {
    val color = r.colorHex?.let(::parseHexColor) ?: Ink.muted
    val name = r.name ?: t(UiKey.HOME_TOP_UNCATEGORIZED)
    FloatingCard(onClick = onClick, clickLabel = name) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(color, size = 40.dp) { LucideIcon(categoryIcon(r.iconKey), size = 20.dp, tint = color) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(name, style = Type.of(14, FontWeight.Bold), maxLines = 1)
                    BasicText(t(UiKey.TOP_SPEND_COUNT, r.count.toString()), style = Type.caption().copy(color = Ink.muted))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AmountText(r.amountMinor, currency, size = 14)
                    // النسبة جوه عزل اتجاه عشان «42%» ما تتقلبش «%42»
                    BasicText("⁦${r.sharePercent}%⁩", style = Type.caption().copy(color = Ink.muted))
                }
            }
            ProgressBar(r.barPercent, color, height = 6.dp)
        }
    }
}
