package app.masroufy.ui.screens.investment

import app.masroufy.ui.overlay.QuickMenuHost
import app.masroufy.ui.overlay.QuickTarget
import app.masroufy.ui.overlay.rememberQuickMenu
import app.masroufy.ui.overlay.MenuItem
import app.masroufy.ui.overlay.QuickMenu
import app.masroufy.ui.screens.common.QuickAct
import app.masroufy.ui.screens.common.assetQuickActs
import app.masroufy.ui.screens.common.quickItems
import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.SurfaceIconButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.budgets.SavingsGoalsRoute
import app.masroufy.ui.screens.common.TabHeader
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestInsightKey
import app.masroufy.core.InvestRatesState
import app.masroufy.core.uiText
import app.masroufy.ui.screens.investment.future.InvestFutureOverlays
import app.masroufy.ui.screens.investment.future.investFutureBody
import app.masroufy.ui.screens.investment.future.investFutureHero
import app.masroufy.ui.screens.investment.future.rememberInvestFuture
import app.masroufy.ui.screens.investment.holdings.HoldingsOverlays
import app.masroufy.ui.screens.investment.holdings.holdingsSection
import app.masroufy.ui.screens.investment.holdings.rememberHoldings
import app.masroufy.ui.screens.investment.insights.InvestTipsRoute
import app.masroufy.ui.screens.investment.insights.incomeRopeItem
import app.masroufy.ui.screens.investment.insights.investReturnsItem
import app.masroufy.ui.screens.investment.insights.investTipItem
import app.masroufy.ui.screens.investment.insights.rememberInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * تبويب «الاستثمار» الجديد (مواصفة r05 §1 — بالترتيب من فوق لتحت): «إنت كمان» · «حاجاتي» · «اختار طريقك» · التشارت · الكروت التلاتة ·
 * نصيحة واحدة · «حبل مصادر دخلك» · «اللي رجعلك» · الأدوات · الحاسبات. «حاجاتي» والمعدلات بيتحمّلوا **مرة** هنا برّه خيط الواجهة وبيتدّوا
 * للأقسام التلاتة (§1.0 الأداء)، والأرقام القديمة بتفضل لحد ما الجديدة تيجي. البطل القديم وكروت العقار والأصول اتشالوا (رد المالك Q14) —
 * «العقار هيبقى بكام» بيفضل من صفحة الأصل نفسه.
 */
@Composable
fun InvestmentScreen() {
    val quick = rememberQuickMenu()
    val space = LocalSpace.current
    val deps = space.investment
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var load by remember(space) { mutableStateOf<InvestmentLoad?>(null) }
    var snapshot by remember(space) { mutableStateOf<HoldingsSnapshot?>(null) }
    var rates by remember(space) { mutableStateOf<InvestRatesState?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    suspend fun loadShared() {
        val today = deps.today()
        val (s, r) = withContext(Dispatchers.Default) {
            runCatching { deps.holdingsSnapshot.load(today) }.getOrNull() to runCatching { deps.investRates.load(space.space, today) }.getOrNull()
        }
        if (s != null) snapshot = s
        rates = r ?: InvestRatesState.Unavailable(emptyList())
    }
    LaunchedEffect(space) {
        launch { loadShared() }
        load = loadInvestment(deps, space.space)
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val next = loadInvestment(deps, space.space, force = true)
            load = next
            loadShared()
            refreshing = false
            if (next.feedProblem == null) toaster.show(refreshedText(next.skipped))
        }
    }
    val future = rememberInvestFuture(deps.future, space.space.id, snapshot, rates)
    val holdings = rememberHoldings(deps.holdings, snapshot) { scope.launch { loadShared() } }
    val insights = rememberInsights(deps.insights, snapshot, rates)
    val title = t(UiKey.TAB_INVESTMENT)
    val pull = app.masroufy.ui.screens.common.rememberPullRefresh(refreshing, ::refresh)
    TabScaffold(title, refresh = pull, header = {
        // «تحديث الأسعار» بس — «ضيف حاجة» بقت جوه «حاجاتي» (مواصفة §1.0 — اختيار Claude ❓)
        TabHeader(title, actions = { SurfaceIconButton(Lucide.REFRESH_CW, t(UiKey.INVEST_REFRESH), ::refresh) })
    }) {
        val l = load
        if (l == null && snapshot == null) {
            item(key = "loading") { LoadingBlocks() }
            return@TabScaffold
        }
        // الأصول ما اتحمّلتش ⇒ «تعذّر التحميل» · الملف ما نزلش ⇒ «تعذّر تحديث الأسعار» والمعروض بآخر نسخة (حالة «خطأ»)
        if (l?.failed == true) item(key = "failed") { AlertBanner(t(UiKey.SHELL_LOAD_FAILED), null, t(UiKey.SHELL_RETRY), ::refresh) }
        l?.feedProblem?.let { problem -> item(key = "error") { AlertBanner(t(UiKey.INVEST_ERROR_TITLE), problem, t(UiKey.SHELL_RETRY), ::refresh) } }
        // «فاضي» (§1.0): مفيش حاجات ولا فلوس معاك ولا متوسط تحويش ⇒ «حاجاتي» الفاضية + الأدوات بس
        val i = future.inputs
        val empty = snapshot?.items?.isEmpty() == true && i != null && i.cashMinor <= 0 && i.saveMinor == null
        if (empty) {
            holdingsSection(holdings)
        } else {
            investFutureHero(future)
            holdingsSection(holdings)
            investFutureBody(future)
            investTipItem(insights)
            incomeRopeItem(insights)
            investReturnsItem(insights)
        }
        item(key = "tools") { ToolsCard(l?.zakatHint, l?.goals) }
        item(key = "calcs") { CalculatorsGrid() }
    }
    InvestFutureOverlays(future)
    HoldingsOverlays(holdings)
    QuickMenuHost(quick)
}

/** رسالة «تحديث الأسعار» بعد ما الملف نزل: «تحدّثت الأسعار» — أو ومعاها عدد الأصول المربوطة اللي ما لقتش سعرها. */
fun refreshedText(skipped: Int): String =
    if (skipped > 0) t(UiKey.INVEST_REFRESH_SKIPPED, sentenceNumber(skipped)) else t(UiKey.INVEST_REFRESHED)

@Composable
private fun LoadingBlocks() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(132.dp), radius = 28.dp, strong = true)
        Skeleton(quiet = true, modifier = Modifier.fillMaxWidth().height(210.dp))
        Skeleton(quiet = true, modifier = Modifier.fillMaxWidth().height(112.dp))
    }
}

@Composable
private fun ToolsCard(zakatHint: String?, goals: Int?) {
    val nav = LocalNavigator.current
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        val chevron: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
        // الزكاة بتظهر بس لو «المحتوى الإسلامي: ظاهر» وبلد ليها مرجع (`ManageZakat.visible` — §62-د)
        if (zakatHint != null) {
            ToolRow(t(UiKey.INVEST_TOOL_ZAKAT), zakatHint, { nav.push(ZakatRoute) }, trailing = chevron)
            RowGap()
        }
        ToolRow(t(UiKey.INVEST_TOOL_ADVISOR), t(UiKey.INVEST_TOOL_ADVISOR_HINT), { nav.push(AdvisorRoute) }, trailing = chevron)
        RowGap()
        ToolRow(t(UiKey.INVEST_TOOL_GOALS), goals?.let(::goalsHint), { nav.push(SavingsGoalsRoute) }, trailing = chevron)
        RowGap()
        // «النصايح» (مواصفة §1.10b) — من جهات رسمية بس (رد المالك ٢ج)
        ToolRow(uiText(InvestInsightKey.TIPS_TITLE), uiText(InvestInsightKey.TIPS_TOOL_HINT), { nav.push(InvestTipsRoute) }, trailing = chevron)
    }
}

@Composable
private fun CalculatorsGrid() {
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.INVEST_CALCS), style = Type.section())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcTile(t(UiKey.INVEST_CALC_SAVINGS), InvestmentIcons.SAVINGS, Modifier.weight(1f)) { nav.push(SavingsCalculatorRoute) }
            CalcTile(t(UiKey.INVEST_CALC_RETIREMENT), InvestmentIcons.RETIREMENT, Modifier.weight(1f)) { nav.push(RetirementCalculatorRoute) }
            CalcTile(t(UiKey.INVEST_CALC_INHERITANCE), InvestmentIcons.INHERITANCE, Modifier.weight(1f)) { nav.push(InheritanceCalculatorRoute) }
        }
    }
}

@Composable
private fun CalcTile(label: String, icon: Lucide, modifier: Modifier, onClick: () -> Unit) {
    FloatingCard(modifier.heightIn(min = 84.dp), shape = RoundedCornerShape(18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), onClick = onClick, clickLabel = label) {
        Column(Modifier.heightIn(min = 60.dp), verticalArrangement = Arrangement.SpaceBetween) {
            LucideIcon(icon, size = 22.dp, tint = Ink.primary)
            BasicText(label, style = Type.bodyBold())
        }
    }
}

/** القايمة السريعة للأصل (§6.5-13): تفاصيله · أرشفة/رجوع. **المسح** (فرع r04-delete): [delete] = null لحد ما يتوصل. */
internal fun assetQuickItems(archived: Boolean, open: () -> Unit, setArchived: (Boolean) -> Unit, delete: (() -> Unit)? = null): List<MenuItem> =
    quickItems(assetQuickActs(archived, canDelete = delete != null)) { act ->
        when (act) {
            QuickAct.OPEN -> open()
            QuickAct.ARCHIVE -> setArchived(true)
            QuickAct.UNARCHIVE -> setArchived(false)
            QuickAct.DELETE -> delete?.invoke()
            else -> Unit
        }
    }
