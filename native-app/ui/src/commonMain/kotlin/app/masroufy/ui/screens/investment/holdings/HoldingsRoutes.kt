package app.masroufy.ui.screens.investment.holdings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.HoldingGroup
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestRatesState
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.rememberEntryLazyListState
import app.masroufy.ui.overlay.QuickMenuHost
import app.masroufy.ui.overlay.rememberQuickMenu
import app.masroufy.ui.screens.budgets.SavingsGoalsRoute
import app.masroufy.ui.screens.investment.AssetDetailRoute
import app.masroufy.ui.theme.Space
import app.masroufy.usecase.planSliderFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** صفحة نوع من «حاجاتي» (`AssetCategory` — §1.9). [group] = `HoldingGroup.wire`. */
data class AssetCategoryRoute(val group: String) : Route {
    override val name = "AssetCategory"
}

fun RouteRegistry.registerHoldings() {
    screen<AssetCategoryRoute> { r -> AssetCategoryScreen(r.group) }
}

/**
 * صفحة النوع: التحميل (الصورة + المعدلات) في الخلفية (`Dispatchers.Default`) ⇒ الهيدر · التشارت أو سببه · «اللي معاك» · «ضيف لـ…» ·
 * «خطتي» · المصدر. الحفظ من اللوحة أو الأرشفة ⇒ تحميل تاني.
 */
@Composable
internal fun AssetCategoryScreen(groupWire: String) {
    val group = HoldingGroup.fromWire(groupWire) ?: HoldingGroup.OTHER
    val space = LocalSpace.current
    val inv = space.investment
    val deps = inv.holdings
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<HoldingsSnapshot?>(null) }
    var rates by remember { mutableStateOf<InvestRatesState?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(inv, version) {
        val today = deps.today()
        val (s, r) = withContext(Dispatchers.Default) {
            runCatching { inv.holdingsSnapshot.load(today) }.getOrNull() to runCatching { inv.investRates.load(space.space, today) }.getOrNull()
        }
        snapshot = s
        rates = r
    }
    var span by remember { mutableIntStateOf(10) }
    val currency = snapshot?.currency ?: space.space.currency
    val slider = remember(currency) { planSliderFor(currency) }
    var plan by remember(currency) { mutableStateOf(slider.defaultMinor) }
    val holdings = rememberHoldings(deps, snapshot) { version++ }
    val quick = rememberQuickMenu()
    val s = snapshot
    val ui = remember(s, rates) { s?.let { categoryUi(it, group, rates) } }
    val projection = remember(ui, plan, span, rates) { ui?.let { deps.group.project(it.startMinor, plan, group, rates, span) } }
    val thisYear = deps.today().take(4).toIntOrNull() ?: 0
    val actions = CategoryActions(
        back = { nav.pop() },
        span = { span = it },
        plan = { plan = slider.atStep(it) },
        open = { nav.push(AssetDetailRoute(it.assetId)) },
        archive = { row -> scope.launch { runCatching { inv.assets.archiveAsset(row.assetId, true) }; version++ } },
        add = { holdings.openAdd(group) },
        savingsPlan = { nav.push(SavingsGoalsRoute) },
    )
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberEntryLazyListState(),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.gutter + statusTop, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (ui == null) {
            item(key = "loading") { Skeleton(Modifier.fillMaxWidth().height(330.dp)) }
            return@LazyColumn
        }
        val v = CategoryView(ui, currency, thisYear, span, projection, slider, plan)
        item(key = "header") { CategoryHeader(v, actions) }
        item(key = "chart") { CategoryChartCard(v, actions) }
        item(key = "list") { CategoryList(v, actions, quick) }
        if (projection != null) item(key = "plan") { CategoryPlanCard(v, actions) }
        if (projection != null) item(key = "source") { CategorySource(v) }
    }
    HoldingsOverlays(holdings)
    QuickMenuHost(quick)
}
