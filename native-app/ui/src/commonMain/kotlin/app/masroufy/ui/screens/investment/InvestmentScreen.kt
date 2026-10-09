package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import app.masroufy.ui.screens.common.TabHeader
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** تبويب «الاستثمار» (لوحة `Investment`): البطل · العقار و«كم ستساوي» · الأصول · الزكاة والتحليلات والخطط · الحاسبات. */
@Composable
fun InvestmentScreen() {
    val space = LocalSpace.current
    val deps = space.investment
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var load by remember(space) { mutableStateOf<InvestmentLoad?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(space) { load = loadInvestment(deps, space.space) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            val next = loadInvestment(deps, space.space, force = true)
            load = next
            refreshing = false
            if (next.feedProblem == null) toaster.show(refreshedText(next.skipped))
        }
    }
    val title = t(TextKey.TAB_INVESTMENT)
    TabScaffold(title, header = {
        TabHeader(title, actions = {
            // «تحديث الأسعار» و«إضافة أصل» (أفعال `Investment` في SCREENS.md) — بين العنوان والترس، والترس آخر حاجة على الشمال
            SurfaceIconButton(Lucide.REFRESH_CW, t(TextKey.INVEST_REFRESH), ::refresh)
            SurfaceIconButton(Lucide.PLUS, t(TextKey.INVEST_ADD_ASSET), { addOpen = true })
        })
    }) {
        val l = load
        if (l == null) {
            item(key = "loading") { LoadingBlocks() }
            return@TabScaffold
        }
        // الأصول ما اتحمّلتش ⇒ «تعذّر التحميل» · الملف ما نزلش ⇒ «تعذّر تحديث الأسعار» والمعروض بآخر نسخة (حالة «خطأ» في النموذج)
        if (l.failed) item(key = "failed") { AlertBanner(t(TextKey.SHELL_LOAD_FAILED), null, t(TextKey.SHELL_RETRY), ::refresh) }
        l.feedProblem?.let { problem -> item(key = "error") { AlertBanner(t(TextKey.INVEST_ERROR_TITLE), problem, t(TextKey.SHELL_RETRY), ::refresh) } }
        val ui = l.ui
        if (ui != null && ui.empty) {
            item(key = "empty") {
                EmptyState(t(TextKey.INVEST_EMPTY_TITLE), t(TextKey.INVEST_EMPTY_BODY), action = {
                    PrimaryButton(t(TextKey.INVEST_ADD_ASSET), { addOpen = true }, Modifier.padding(top = 10.dp))
                })
            }
        } else if (ui != null) {
            item(key = "hero") { PortfolioHero(ui) }
            for (e in ui.estates) item(key = "estate-${e.assetId}") { EstateCardView(e, ui) { nav.push(AssetDetailRoute(e.assetId)) } }
            if (ui.others.isNotEmpty()) item(key = "assets") { AssetsCard(ui) { nav.push(AssetDetailRoute(it)) } }
        }
        item(key = "tools") { ToolsCard(l.zakatHint, l.goals) }
        item(key = "calcs") { CalculatorsGrid() }
    }
    AssetTradeSheet(addOpen, TradeMode.ADD, null, onDismiss = { addOpen = false }) { id -> nav.push(AssetDetailRoute(id)) }
}

/** رسالة «تحديث الأسعار» بعد ما الملف نزل: «تحدّثت الأسعار» — أو ومعاها عدد الأصول المربوطة اللي ما لقتش سعرها. */
fun refreshedText(skipped: Int): String =
    if (skipped > 0) t(TextKey.INVEST_REFRESH_SKIPPED, sentenceNumber(skipped)) else t(TextKey.INVEST_REFRESHED)

@Composable
private fun LoadingBlocks() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(132.dp), radius = 28.dp, strong = true)
        Skeleton(Modifier.fillMaxWidth().height(210.dp))
        Skeleton(Modifier.fillMaxWidth().height(112.dp))
    }
}

@Composable
private fun PortfolioHero(ui: InvestmentUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(TextKey.INVEST_TOTAL_LABEL), style = Type.body().copy(color = Ink.onHeroMuted))
            HeroAmount(ui.totalMinor, ui.currency, Modifier.fillMaxWidth(), size = 34)
            ui.totalNa?.let { BasicText(it, style = Type.caption().copy(color = Ink.onHeroMuted)) }
            HeroDivider(Modifier.padding(top = 2.dp))
            AmountLine(t(TextKey.INVEST_COST), ui.costMinor, ui.currency, ink = Color.White, labelInk = Ink.onHeroMuted)
            HeroSigned(t(TextKey.INVEST_UNREALIZED), ui.unrealizedMinor, ui)
            ui.realizedMinor?.let { HeroSigned(t(TextKey.INVEST_REALIZED), it, ui) }
        }
    }
}

/** سطر مكسب على البطل: «+1,200.00» (من غير لون — النص أبيض على البترولي). */
@Composable
private fun HeroSigned(label: String, minor: app.masroufy.core.Halalas?, ui: InvestmentUi) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.onHeroMuted))
        AmountText(minor, ui.currency, tone = if (minor != null && minor < 0) AmountTone.EXPENSE else AmountTone.INCOME, size = 13, showCurrency = false, color = Color.White)
    }
}

@Composable
private fun EstateCardView(e: EstateCard, ui: InvestmentUi, onOpen: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), onClick = onOpen, clickLabel = e.name) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                KindTile("realEstate", size = 40)
                Column(Modifier.weight(1f)) {
                    BasicText(e.name, style = Type.of(15, FontWeight.Bold), maxLines = 1)
                    BasicText(e.subtitle, style = Type.caption().copy(color = Ink.muted), maxLines = 1)
                }
                AmountText(e.valueMinor, ui.currency, size = 16, showCurrency = false)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.selected).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(e.ask, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
                AmountLine(t(TextKey.INVEST_RE_PRICE_THEN), e.saleMinor, ui.currency)
                AmountLine(e.rentLabel, e.rentMinor, ui.currency)
                AmountLine(t(TextKey.INVEST_RE_GAIN), e.gainMinor, ui.currency, bold = true)
                BasicText(e.rateLine, style = Type.caption().copy(color = Ink.soft))
            }
        }
    }
}

@Composable
private fun AssetsCard(ui: InvestmentUi, onOpen: (Id) -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        ui.others.forEachIndexed { i, line ->
            if (i > 0) RowGap()
            ToolRow(line.name, line.subtitle, onClick = { onOpen(line.assetId) }, leading = { KindTile(line.kind) }) {
                AmountText(line.valueMinor, ui.currency, showCurrency = false, color = if (line.valueMinor == null || line.archived) Ink.muted else null)
            }
        }
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
            ToolRow(t(TextKey.INVEST_TOOL_ZAKAT), zakatHint, { nav.push(ZakatRoute) }, trailing = chevron)
            RowGap()
        }
        ToolRow(t(TextKey.INVEST_TOOL_ADVISOR), t(TextKey.INVEST_TOOL_ADVISOR_HINT), { nav.push(AdvisorRoute) }, trailing = chevron)
        RowGap()
        ToolRow(t(TextKey.INVEST_TOOL_GOALS), goals?.let(::goalsHint), { nav.push(SavingsGoalsLink) }, trailing = chevron)
    }
}

@Composable
private fun CalculatorsGrid() {
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(TextKey.INVEST_CALCS), style = Type.section())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CalcTile(t(TextKey.INVEST_CALC_SAVINGS), InvestmentIcons.SAVINGS, Modifier.weight(1f)) { nav.push(SavingsCalculatorLink) }
            CalcTile(t(TextKey.INVEST_CALC_RETIREMENT), InvestmentIcons.RETIREMENT, Modifier.weight(1f)) { nav.push(RetirementCalculatorLink) }
            CalcTile(t(TextKey.INVEST_CALC_INHERITANCE), InvestmentIcons.INHERITANCE, Modifier.weight(1f)) { nav.push(InheritanceCalculatorLink) }
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
