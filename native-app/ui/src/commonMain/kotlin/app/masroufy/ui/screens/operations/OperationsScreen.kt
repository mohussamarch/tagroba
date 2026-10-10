package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.DayGroup
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.LocalRegistry
import app.masroufy.ui.nav.Slots
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.screens.common.TabHeader
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.screens.home.PeriodChoice
import app.masroufy.ui.screens.home.PeriodPickerRoute
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadTransactionsScreenRequest

/**
 * تبويب «العمليات» (`Operations` + `OperationMenu`): الرأس (الفلتر · الشهر · الترس) · المبدّل (العمليات · الميزانيات · المستحقات) ·
 * شرايط «مستنياك» · كارتين «دخل الشهر» و«المصروف الحقيقي» · العمليات بالأيام. كل الأرقام من `LoadTransactionsScreen` (CLAUDE.md #4).
 * الفترة = الشهر اللي اتختار في «اختر الشهر» ([PeriodChoice] للبلد الشغالة — المحاكي 2026-10-10: كانت بتتجاهله)، وإلا الشهر الحالي.
 */
@Composable
fun OperationsScreen() {
    val space = LocalSpace.current
    val deps = space.operations
    val nav = LocalNavigator.current
    var tab by rememberSaveable { mutableStateOf(OpsTab.OPERATIONS) }
    var ui by remember(deps) { mutableStateOf(OpsUi()) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<Pair<OpRow, Anchor>?>(null) }
    // قراية المختار في الرسم نفسه ⇒ الرجوع من «اختر الشهر» بيعيد القراية بالشهر الجديد
    val chosenKey = PeriodChoice.of(space.space.id)
    LaunchedEffect(deps, reload, chosenKey) {
        val today = space.shell.today()
        val view = attempt {
            val payday = deps.payday()
            val data = deps.transactions.load(LoadTransactionsScreenRequest(PeriodChoice.periodOf(chosenKey, payday), today, payday))
            operationsView(data, attempt { deps.wallets() }.orEmpty(), today, space.space.currency)
        }
        ui = ui.loaded(view, attempt { deps.transfers.zone().questions.size })
    }
    val title = t(UiKey.TAB_OPERATIONS)
    TabScaffold(title, header = {
        TabHeader(title, actions = {
            SurfaceIconButton(Lucide.FILTER, t(UiKey.OPERATIONS_FILTER), onClick = { nav.push(OperationFiltersRoute) }, iconSize = 20.dp)
            MonthButton(ui.view?.periodLabel ?: t(UiKey.OPERATIONS_PERIOD)) { nav.push(PeriodPickerRoute) }
        })
    }) {
        item(key = "tabs") {
            SegmentedTabs(
                listOf(OpsTab.OPERATIONS to t(UiKey.OPERATIONS_TAB_OPS), OpsTab.BUDGETS to t(UiKey.OPERATIONS_TAB_BUDGETS), OpsTab.DUES to t(UiKey.OPERATIONS_TAB_DUES)),
                tab, { tab = it }, Modifier.fillMaxWidth(),
            )
        }
        when (tab) {
            OpsTab.BUDGETS -> item(key = "budgets") { LocalRegistry.current.Slot(Slots.BUDGETS) }
            OpsTab.DUES -> item(key = "dues") { LocalRegistry.current.Slot(Slots.DUES) }
            OpsTab.OPERATIONS -> operationsTab(ui, onRetry = { ui = ui.retrying(); reload++ }, onOpen = { nav.push(OperationDetailRoute(it.id)) }, onMenu = { r, a -> menu = r to a })
        }
    }
    OperationMenu(
        menu,
        onDismiss = { menu = null },
        onDetail = { nav.push(OperationDetailRoute(it.id)) },
        onPerson = { nav.push(OperationDetailRoute(it.id, openPerson = true)) },
    )
}

private fun LazyListScope.operationsTab(ui: OpsUi, onRetry: () -> Unit, onOpen: (OpRow) -> Unit, onMenu: (OpRow, Anchor) -> Unit) {
    if (ui.skeleton) {
        item(key = "loading") { LoadingBlocks() }
        return
    }
    if (ui.failed) item(key = "error") {
        ErrorBanner(t(UiKey.OPERATIONS_ERROR_TITLE), t(UiKey.OPERATIONS_ERROR_BODY), t(UiKey.SHELL_RETRY), onRetry)
    }
    val v = ui.view ?: return
    if (v.empty) {
        item(key = "empty") { EmptyState(t(UiKey.OPERATIONS_EMPTY_TITLE, v.periodLabel), t(UiKey.OPERATIONS_EMPTY_BODY)) }
        return
    }
    // شريط رسايل البنك: عددها مالوش حالة استخدام في «العمليات» لسه (صندوق الرسايل شغل منطقة الاستيراد) ⇒ `null` ⇒ ما بيظهرش
    items(banners(bankSmsWaiting = null, reviewCount = v.reviewCount, partiesWaiting = ui.partiesWaiting), key = { "banner-${it.kind}" }) { b -> BannerItem(b) }
    // «دفعت الإيجار؟» (`QuickAddStrip`) بيستنى حالة استخدام «المتوقع ولم يُسجَّل» — من غيرها ما بيظهرش
    item(key = "summary") { Summary(v.incomeMinor, v.expenseMinor, v.currency, v.approx) }
    items(v.days, key = { "day-${it.date}" }) { day ->
        DayGroup(day.label) {
            day.rows.forEachIndexed { i, row ->
                if (i > 0) Divider()
                OperationListRow(row, onOpen = { onOpen(row) }, onMenu = { a -> onMenu(row, a) })
            }
        }
    }
}

@Composable
private fun BannerItem(b: Banner) {
    val nav = LocalNavigator.current
    when (b.kind) {
        BannerKind.BANK_SMS -> WaitingBanner(b.text, Ink.focus, Ink.alertBg) { nav.push(BankSmsRoute) }
        BannerKind.REVIEW -> WaitingBanner(b.text, Ink.transfer, Color(0x142469BA)) { nav.push(ReviewQueueRoute) }
        BannerKind.TRANSFERS -> WaitingBanner(b.text, Ink.primary, Color(0x1408634F)) { nav.push(TransfersRoute) }
    }
}

/** كارتين «دخل الشهر» و«المصروف الحقيقي» (18 عريض) — `null` ⇒ «غير متاح» (عمره ما يبقى صفر). */
@Composable
private fun Summary(income: Halalas?, expense: Halalas?, currency: Currency, approx: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard(t(UiKey.OPERATIONS_INCOME), income, currency, AmountTone.INCOME, Modifier.weight(1f))
            SummaryCard(t(UiKey.OPERATIONS_EXPENSE), expense, currency, AmountTone.EXPENSE, Modifier.weight(1f))
        }
        if (approx) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ApproxBadge()
            BasicText(t(UiKey.OPERATIONS_APPROX_NOTE), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

@Composable
private fun SummaryCard(label: String, minor: Halalas?, currency: Currency, tone: AmountTone, modifier: Modifier) {
    FloatingCard(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), contentPadding = PaddingValues(12.dp)) {
        BasicText(label, style = Type.caption().copy(color = Ink.muted))
        AmountText(minor, currency, Modifier.fillMaxWidth(), tone = tone, size = 18, showCurrency = false)
    }
}

/** «بيحمّل»: نفس شكل المحتوى رمادي (مش دوّامة) — الكارتين · عنوان اليوم · كارت الصفوف. */
@Composable
private fun LoadingBlocks() {
    val label = t(UiKey.SHELL_LOADING)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(Modifier.weight(1f).height(66.dp), radius = 18.dp)
            Skeleton(Modifier.weight(1f).height(66.dp), radius = 18.dp)
        }
        Skeleton(Modifier.size(48.dp, 16.dp), radius = 8.dp, strong = true)
        Skeleton(Modifier.fillMaxWidth().height(176.dp))
    }
}
