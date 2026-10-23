package app.masroufy.ui.screens.investment.insights

import app.masroufy.ui.theme.Ink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.IncomeRope
import app.masroufy.core.InvestRatesState
import app.masroufy.core.Space
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.home.ReadState
import app.masroufy.ui.screens.more.IncomeHubRoute
import app.masroufy.ui.theme.Radius
import app.masroufy.usecase.ReturnsView
import app.masroufy.usecase.TipView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * أقسام INSIGHTS في شاشة «الاستثمار» (مواصفة النقل §4.3 · §4.4): النصيحة · الحبل · اللي رجعلك. كل قسم بيتحمّل في كوروتين لوحده على
 * `Dispatchers.Default` بهيكل تحميل لوحده — ولا قسم بيوقف التاني، والتحديث ما بيمسحش اللي معروض (آخر قيم لحد ما الجديدة توصل).
 */
@Stable
class InsightsState internal constructor(internal val deps: InvestInsightsDeps, internal val space: Space, private val scope: CoroutineScope) {
    internal var tip by mutableStateOf<ReadState<TipView?>>(ReadState.Loading)
    internal var rope by mutableStateOf<ReadState<IncomeRope?>>(ReadState.Loading)
    internal var returns by mutableStateOf<ReadState<ReturnsView?>>(ReadState.Loading)
    internal var holdings: HoldingsSnapshot? = null

    /** «×»: الكارت بيختفي على طول، والحفظ وبعده النصيحة اللي بعدها (لو فيه) في الخلفية. */
    internal fun closeTip(id: String) {
        tip = ReadState.Ready(null)
        scope.launch {
            val next = withContext(Dispatchers.Default) { runCatching { deps.tips.close(id); deps.tips.current(deps.today(), holdings) }.getOrNull() }
            tip = ReadState.Ready(next)
        }
    }
}

private suspend fun <T> loadSection(block: suspend () -> T): ReadState<T> =
    withContext(Dispatchers.Default) { runCatching { block() }.fold({ ReadState.Ready(it) }, { ReadState.Failed }) }

@Composable
fun rememberInsights(deps: InvestInsightsDeps, holdings: HoldingsSnapshot?, rates: InvestRatesState?): InsightsState {
    val space = LocalSpace.current.space
    val changes = LocalDataChanges.current
    val scope = rememberCoroutineScope()
    val state = remember(deps, space.id) { InsightsState(deps, space, scope) }
    state.holdings = holdings
    LaunchedEffect(state, holdings, changes.version) { state.tip = loadSection { deps.tips.current(deps.today(), holdings) } }
    LaunchedEffect(state, changes.version) { state.rope = loadSection { deps.rope.load(deps.today(), space.currency) } }
    LaunchedEffect(state, holdings, rates, changes.version) { state.returns = loadSection { deps.returns.load(deps.today(), space.currency, holdings, rates) } }
    return state
}

/** النصيحة (مفتاح "tip"): مفيش نصيحة باقية أو القراية فشلت ⇒ الكارت ما بيترسمش (مش رقم ولا وعد). */
fun LazyListScope.investTipItem(state: InsightsState) {
    when (val s = state.tip) {
        ReadState.Loading -> item(key = "tip") { SectionSkeleton(112) }
        is ReadState.Ready -> s.value?.let { tip ->
            item(key = "tip") { val nav = LocalNavigator.current; TipCard(tip, onClose = { state.closeTip(tip.id) }, onAll = { nav.push(InvestTipsRoute) }) }
        }
        ReadState.Failed -> Unit
    }
}

/** «حبل مصادر دخلك» (مفتاح "rope"). */
fun LazyListScope.incomeRopeItem(state: InsightsState) {
    item(key = "rope") {
        when (val s = state.rope) {
            ReadState.Loading -> SectionSkeleton(210)
            is ReadState.Ready -> { val nav = LocalNavigator.current; IncomeRopeCard(s.value) { nav.push(IncomeHubRoute) } }
            ReadState.Failed -> FailedSection(app.masroufy.core.InvestInsightKey.ROPE_TITLE)
        }
    }
}

/** «اللي رجعلك» (مفتاح "returns"). */
fun LazyListScope.investReturnsItem(state: InsightsState) {
    item(key = "returns") {
        when (val s = state.returns) {
            ReadState.Loading -> SectionSkeleton(180)
            is ReadState.Ready -> ReturnsCard(s.value)
            ReadState.Failed -> FailedSection(app.masroufy.core.InvestInsightKey.RET_TITLE)
        }
    }
}

/** هيكل التحميل (زي `Investment.dc.html` «بيحمّل»): كتلة فاتحة بزاوية الكارت. */
@Composable
internal fun SectionSkeleton(height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp).background(Ink.tint(0x0F), RoundedCornerShape(Radius.card)))
}
