package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.home.ReadState
import app.masroufy.ui.screens.home.readState
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.TopSpendView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * «أكتر ما صرفت عليه» (طلب المالك 2026-10-16 — OVERRIDES §100، وتصميم `TopSpending` في 0.5.1): الترتيب كامل للصرف الحقيقي على
 * التصنيفات في الشهر ده أو اللي فات أو آخر 3 شهور، و«بلا تصنيف» سطر لوحده. الضغط على سطر ⇒ عمليات التصنيف ده في نفس الفترة (شاشة
 * التصفية). الترتيب والمجاميع والنسب والفرق عن الشهر اللي فات من `LoadTopCategories.loadAll` (نفس مصدر كارت الرئيسية) — الشاشة بتعرض
 * بس. القراية برّه الخيط الرئيسي ومرة واحدة لكل فترة في الفتحة (الرجوع لفترة اتقرت بيعرض المحفوظ).
 */
@Composable
fun TopSpendScreen() {
    val space = LocalSpace.current
    val top = space.home.cards.top
    val ops = space.operations
    val nav = LocalNavigator.current
    val currency = space.space.currency
    var period by rememberSaveable { mutableStateOf(FilterPeriod.THIS) }
    val read = remember(top) { mutableStateMapOf<FilterPeriod, TopSpendView>() }
    var state by remember(top) { mutableStateOf<ReadState<TopSpendView>>(ReadState.Loading) }
    LaunchedEffect(top, period) {
        read[period]?.let { state = ReadState.Ready(it); return@LaunchedEffect }
        state = ReadState.Loading
        val asked = period
        state = readState {
            val payday = attempt { ops.payday() } ?: DEFAULT_PAYDAY
            val today = space.shell.today()
            withContext(Dispatchers.Default) { top.loadAll(today, payday, asked.skip, asked.months) }.also { read[asked] = it }
        }
    }
    InnerScaffold(t(UiKey.TOP_SPEND_TITLE), subtitle = t(UiKey.TOP_SPEND_CRUMB)) {
        item(key = "periods") { TopSpendPeriodChips(period) { period = it } }
        when (val s = state) {
            ReadState.Loading -> {
                item(key = "skeleton-total") { Skeleton(Modifier.fillMaxWidth().height(150.dp)) }
                item(key = "skeleton-rows") { Skeleton(quiet = true, modifier = Modifier.fillMaxWidth().height(260.dp)) }
            }
            ReadState.Failed -> item(key = "failed") { EmptyState(t(UiKey.TOP_SPEND_FAILED)) }
            is ReadState.Ready -> {
                val v = s.value
                item(key = "total") { TopSpendTotalCard(v, currency) }
                if (v.rows.isEmpty() && v.totalMinor != null) {
                    item(key = "empty") { EmptyState(t(UiKey.TOP_SPEND_EMPTY), t(UiKey.TOP_SPEND_EMPTY_BODY)) }
                }
                if (v.rows.isNotEmpty()) {
                    item(key = "rows") { TopSpendRanks(v.rows, currency) { r -> nav.push(categoryOperationsRoute(r.categoryId, period)) } }
                    item(key = "foot") { BasicText(t(UiKey.TOP_SPEND_FOOT), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted)) }
                }
            }
        }
    }
}
