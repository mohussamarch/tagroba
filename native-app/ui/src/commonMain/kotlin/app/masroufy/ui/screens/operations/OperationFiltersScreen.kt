package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Wallet
import app.masroufy.core.currencySymbol
import app.masroufy.core.periodForDate
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.ListRow
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadTransactionsScreenRequest
import app.masroufy.usecase.TransactionsScreenData

/**
 * تصفية العمليات (`OperationFilters`): بحث · المبلغ و«قريب منه ±٥٪» · الفترة · النوع · التصنيف · المحفظة · المراجعة ⇒ «اعرض N» بالعدد الحقيقي
 * ⇒ النتايج بشرايح بتتشال. القراية من `LoadTransactionsScreen` لكل شهر في الفترة، والاختيار بدوال `core` (`searchTransactions` …).
 * ⚠️ «المصدر» (رسالة · كشف · يدوي) و«الشخص» ومجموع النتايج مالهمش حالة استخدام لسه ⇒ مش معروضين.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OperationFiltersScreen() {
    val space = LocalSpace.current
    val deps = space.operations
    val nav = LocalNavigator.current
    val currency = space.space.currency
    var filters by remember { mutableStateOf(Filters()) }
    var results by rememberSaveable { mutableStateOf(false) }
    var data by remember(deps) { mutableStateOf<List<TransactionsScreenData>?>(null) }
    var wallets by remember(deps) { mutableStateOf<List<Wallet>>(emptyList()) }
    LaunchedEffect(deps, filters.period) {
        data = null
        val payday = attempt { deps.payday() } ?: app.masroufy.core.DEFAULT_PAYDAY
        val current = periodForDate(space.shell.today(), payday)
        wallets = attempt { deps.wallets() }.orEmpty()
        data = filterPeriods(current, filters.period, payday).mapNotNull { p -> attempt { deps.transactions.load(LoadTransactionsScreenRequest(period = p)) } }
    }
    val loaded = data
    val hits = loaded?.let { applyFilters(it, filters, currency) }
    val categories = loaded?.let(::filterCategories).orEmpty()
    val chips = activeChips(filters, categories, wallets, currency)
    val title = t(if (results) UiKey.OPERATION_FILTERS_RESULTS else UiKey.OPERATION_FILTERS_TITLE)
    Box(Modifier.fillMaxSize()) {
        InnerScaffold(title, actions = {
            if (chips.isNotEmpty()) TonalButton(t(UiKey.OPERATION_FILTERS_CLEAR), { filters = Filters() }, height = 40.dp)
        }) {
            if (!results) {
                item(key = "search") {
                    TextInput(filters.query, { filters = filters.copy(query = it) }, placeholder = t(UiKey.OPERATION_FILTERS_SEARCH), height = 52.dp,
                        trailing = { LucideIcon(Lucide.SEARCH, Modifier.padding(end = 14.dp), size = 20.dp, tint = Ink.muted) })
                }
                item(key = "amount") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupTitle(t(UiKey.OPERATION_FILTERS_AMOUNT, currencySymbol(currency)))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextInput(filters.amountText, { filters = filters.copy(amountText = it) }, Modifier.weight(1f), placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal)
                            SelectChip(t(UiKey.OPERATION_FILTERS_NEAR), filters.near, { filters = filters.copy(near = !filters.near) }, height = 48.dp)
                        }
                    }
                }
                item(key = "period") {
                    Group(t(UiKey.OPERATION_FILTERS_PERIOD)) {
                        for (p in FilterPeriod.entries) SelectChip(periodName(p), filters.period == p, { filters = filters.copy(period = p) })
                    }
                }
                item(key = "kind") {
                    Group(t(UiKey.OPERATION_FILTERS_KIND)) {
                        for (k in KindGroup.entries) SelectChip(kindName(k), k in filters.kinds, { filters = filters.copy(kinds = filters.kinds.toggle(k)) })
                    }
                }
                item(key = "category") {
                    Group(t(UiKey.OPERATION_DETAIL_CATEGORY)) {
                        for ((id, name) in categories) SelectChip(name, id in filters.categories, { filters = filters.copy(categories = filters.categories.toggle(id)) })
                    }
                }
                if (wallets.isNotEmpty()) item(key = "wallet") {
                    Group(t(UiKey.OPERATION_DETAIL_WALLET)) {
                        for (w in wallets) SelectChip(w.name, w.id in filters.wallets, { filters = filters.copy(wallets = filters.wallets.toggle(w.id)) })
                    }
                }
                item(key = "review") {
                    Group(t(UiKey.OPERATION_FILTERS_REVIEW)) {
                        for (r in ReviewFilter.entries) SelectChip(reviewName(r), filters.review == r, { filters = filters.copy(review = r) })
                    }
                }
            } else {
                item(key = "chips") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in chips) DropChip(c.label, t(UiKey.OPERATION_FILTERS_DROP, c.label)) { filters = c.clear(filters) }
                    }
                }
                item(key = "count") { BasicText(hits?.let { operationsCount(it.size) }.orEmpty(), style = Type.of(13).copy(color = Ink.muted)) }
                if (hits.isNullOrEmpty()) item(key = "none") { EmptyState(t(UiKey.OPERATION_FILTERS_NONE), t(UiKey.OPERATION_FILTERS_NONE_BODY)) }
                else item(key = "list") {
                    val all = loaded.orEmpty()
                    val ctx = RowContext(all.flatMap { it.categories }.distinctBy { it.id }, all.fold(emptyMap()) { a, d -> a + d.merchantNamesByTransaction }, wallets)
                    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        hits.forEachIndexed { i, tx ->
                            if (i > 0) Divider()
                            val row = opRow(tx, ctx)
                            ListRow(
                                row.title, subtitle = row.subtitle,
                                leading = { Box(Modifier.size(10.dp).clip(CircleShape).background(row.color)) },
                                trailing = { AmountText(row.amountMinor, row.currency, tone = row.tone, showCurrency = false) },
                                onClick = { nav.push(OperationDetailRoute(tx.id)) }, chevron = false,
                            )
                        }
                    }
                }
            }
            item(key = "bottom-space") { Box(Modifier.height(72.dp)) }
        }
        BottomAction(Modifier.align(Alignment.BottomCenter), results, hits) { results = !results }
    }
}

/** الزرار الثابت تحت: «اعرض N» (معطّل لو مفيش نتايج) أو «عدّل التصفية». */
@Composable
private fun BottomAction(modifier: Modifier, results: Boolean, hits: List<app.masroufy.core.Transaction>?, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0x00EFF3ED), 0.3f to Color(0xF0EFF3ED)))
            .navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp),
    ) {
        if (results) SecondaryButton(t(UiKey.OPERATION_FILTERS_EDIT), onClick, Modifier.fillMaxWidth(), height = 52.dp)
        else {
            val n = hits?.size
            when {
                n == null -> Skeleton(Modifier.fillMaxWidth().height(52.dp), radius = 18.dp)
                else -> PrimaryButton(
                    if (n > 0) t(UiKey.OPERATION_FILTERS_SHOW, operationsCount(n)) else t(UiKey.OPERATION_FILTERS_NONE),
                    onClick = onClick, enabled = n > 0, height = 52.dp, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) = BasicText(text, style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupTitle(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

/** شريحة فلتر شغال في النتايج: أخضر فاتح و«×» — الضغط بيشيله. */
@Composable
private fun DropChip(label: String, a11y: String, onDrop: () -> Unit) {
    val press = rememberPress()
    Row(
        Modifier.height(36.dp).pressScale(press).clip(RoundedCornerShape(12.dp)).background(Ink.selected)
            .tap(press, label = a11y, onClick = onDrop).padding(start = 12.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
        LucideIcon(Lucide.X, size = 14.dp, tint = Ink.primary)
    }
}
