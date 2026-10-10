package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.NOT_AVAILABLE
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.FeedState
import kotlinx.coroutines.launch

/** «تفاصيل الأصل» (لوحة `AssetDetail`) — التحميل في [loadAssetDetail]، والشراء والبيع والسعر اليدوي في `AssetTradeSheet`. */
@Composable
fun AssetDetailScreen(assetId: Id) {
    val space = LocalSpace.current
    val deps = space.investment
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var ui by remember(space, assetId) { mutableStateOf<AssetDetailUi?>(null) }
    var failed by remember(space, assetId) { mutableStateOf(false) }
    var tradeMode by remember { mutableStateOf(TradeMode.BUY) }
    var tradeOpen by remember { mutableStateOf(false) }
    fun openTrade(mode: TradeMode) { tradeMode = mode; tradeOpen = true }
    var picking by remember { mutableStateOf(false) }
    suspend fun reload() {
        val next = runCatching { loadAssetDetail(deps, space.space, assetId) }
        ui = next.getOrNull()
        failed = next.isFailure || next.getOrNull() == null
    }
    fun act(block: suspend () -> String?) {
        scope.launch {
            val msg = try { block() } catch (e: IllegalArgumentException) { e.message } catch (e: IllegalStateException) { e.message }
            reload()
            msg?.let { toaster.show(it) }
        }
    }
    LaunchedEffect(space, assetId) { reload() }
    val a = ui
    InnerScaffold(a?.name.orEmpty(), actions = { a?.let { ToneChip(it.kindLabel, Ink.primary, Ink.selected) } }) {
        if (a == null) {
            item(key = "state") {
                if (failed) AlertBanner(t(UiKey.SHELL_LOAD_FAILED), null, t(UiKey.SHELL_RETRY)) { scope.launch { reload() } }
                else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(150.dp), radius = 28.dp, strong = true)
                    Skeleton(Modifier.fillMaxWidth().height(160.dp))
                }
            }
            return@InnerScaffold
        }
        if (a.staleLinked) item(key = "stale") {
            AlertBanner(t(UiKey.INVEST_ERROR_TITLE), t(UiKey.ASSET_DETAIL_STALE_BODY), t(UiKey.SHELL_RETRY)) { act { refreshPrices(deps) } }
        }
        if (a.archived) item(key = "archived") {
            QuietBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(t(UiKey.ASSET_DETAIL_ARCHIVED), Modifier.weight(1f), style = Type.of(13).copy(color = Ink.soft))
                    TonalButton(t(UiKey.ASSET_DETAIL_UNARCHIVE), { act { deps.assets.archiveAsset(a.assetId, false); t(UiKey.ASSET_DETAIL_UNARCHIVED) } }, height = 44.dp)
                }
            }
        }
        item(key = "hero") { DetailHero(a, onPrice = { openTrade(TradeMode.PRICE) }, onArea = { nav.push(AssetProjectionRoute(a.assetId)) }) }
        item(key = "stats") { DetailStats(a) }
        if (a.realEstate) item(key = "full") { FullPictureLink { nav.push(AssetProjectionRoute(a.assetId)) } }
        item(key = "trade") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(t(UiKey.ASSET_DETAIL_BUY), { openTrade(TradeMode.BUY) }, Modifier.weight(1f))
                    SecondaryButton(t(UiKey.ASSET_DETAIL_SELL), { openTrade(TradeMode.SELL) }, Modifier.weight(1f))
                }
                BasicText(t(UiKey.ASSET_DETAIL_RECORD_ONLY), Modifier.fillMaxWidth(), style = Type.caption().copy(color = Ink.muted, textAlign = TextAlign.Center))
            }
        }
        item(key = "log") { TradeLog(a) }
        item(key = "source") { SourceCard(a, onManual = { openTrade(TradeMode.PRICE) }, onLink = { picking = true }) { act { deps.assets.linkToFeed(a.assetId, null); t(UiKey.ASSET_DETAIL_UNLINKED) } } }
        if (!a.archived) item(key = "archive") {
            TonalButton(t(UiKey.ASSET_DETAIL_ARCHIVE), { act { deps.assets.archiveAsset(a.assetId, true); t(UiKey.ASSET_DETAIL_ARCHIVED_TOAST) } }, Modifier.fillMaxWidth(), muted = true)
        }
    }
    AssetTradeSheet(tradeOpen, tradeMode, a, onDismiss = { tradeOpen = false }) { scope.launch { reload() } }
    FeedPickSheet(picking, a, onDismiss = { picking = false }) { symbol, name ->
        act {
            deps.assets.linkToFeed(assetId, symbol)
            (deps.feeds.prices() as? FeedState.Ready)?.let { deps.syncPrices.sync(it.feed) }
            t(UiKey.ASSET_DETAIL_LINKED_TO, name)
        }
    }
}

@Composable
private fun DetailHero(a: AssetDetailUi, onPrice: () -> Unit, onArea: () -> Unit) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.ASSET_DETAIL_HERO), style = Type.body().copy(color = Ink.onHeroMuted))
            if (a.valueMinor == null) {
                BasicText(NOT_AVAILABLE, style = Type.of(30, FontWeight.Bold).copy(color = Color.White))
                BasicText(a.naReason, style = Type.caption().copy(color = Ink.onHeroMuted))
                val label = t(if (a.realEstate) UiKey.ASSET_DETAIL_WRITE_AREA else UiKey.ASSET_DETAIL_WRITE_PRICE)
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.selected),
                ) { TonalButton(label, if (a.realEstate) onArea else onPrice, height = 44.dp) }
            } else {
                HeroAmount(a.valueMinor, a.currency, Modifier.fillMaxWidth(), size = 32)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(a.priceLine, Modifier.weight(1f, fill = false), style = Type.caption().copy(color = Ink.onHeroMuted))
                val (ink, bg) = when (a.chip) {
                    PriceChip.FRESH -> Ink.heroStart to Ink.mint
                    PriceChip.STALE -> Ink.focus to Ink.amber
                    PriceChip.MANUAL -> Ink.heroStart to Ink.sky
                    PriceChip.MISSING -> Color.White to Ink.heroTrack
                }
                ToneChip(t(a.chip.key), ink, bg)
            }
            HeroDivider(Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t(UiKey.INVEST_UNREALIZED), Modifier.weight(1f), style = Type.of(13).copy(color = Ink.onHeroMuted))
                AmountText(a.unrealizedMinor, a.currency, tone = if ((a.unrealizedMinor ?: 0L) < 0) AmountTone.EXPENSE else AmountTone.INCOME, size = 13, showCurrency = false, color = Color.White)
            }
        }
    }
}

@Composable
private fun DetailStats(a: AssetDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(t(UiKey.ASSET_DETAIL_QTY), a.qtySub, Modifier.weight(1f)) { BasicText(a.qtyText, style = Type.of(17, FontWeight.Bold)) }
            // متوسط التكلفة للوحدة مالوش حالة استخدام ⇒ «غير متاح» (مش محسوب في الشاشة — CLAUDE.md #4)
            StatTile(t(UiKey.ASSET_DETAIL_AVG), a.avgSub, Modifier.weight(1f)) { BasicText(NOT_AVAILABLE, style = Type.of(17, FontWeight.Bold).copy(color = Ink.muted)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(t(UiKey.ASSET_DETAIL_COST), t(UiKey.ASSET_DETAIL_COST_SUB), Modifier.weight(1f)) { AmountText(a.costMinor, a.currency, size = 17, showCurrency = false) }
            StatTile(t(UiKey.INVEST_REALIZED), a.realizedSub, Modifier.weight(1f)) {
                val r = a.realizedMinor
                if (r == null) BasicText(t(UiKey.ASSET_DETAIL_NO_SALES), style = Type.of(17, FontWeight.Bold).copy(color = Ink.muted))
                else AmountText(r, a.currency, tone = if (r < 0) AmountTone.EXPENSE else AmountTone.INCOME, size = 17, showCurrency = false)
            }
        }
    }
}

@Composable
private fun FullPictureLink(onOpen: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), onClick = onOpen, clickLabel = t(UiKey.ASSET_DETAIL_FULL_TITLE)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(UiKey.ASSET_DETAIL_FULL_TITLE), style = Type.of(15, FontWeight.Bold).copy(color = Ink.primary))
                BasicText(t(UiKey.ASSET_DETAIL_FULL_BODY), style = Type.caption().copy(color = Ink.soft))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 20.dp, tint = Ink.primary, modifier = Modifier.mirrorInLtr())
        }
    }
}

@Composable
private fun TradeLog(a: AssetDetailUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.ASSET_DETAIL_LOG), style = Type.section())
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (a.log.isEmpty()) BasicText(t(UiKey.ASSET_DETAIL_LOG_EMPTY), Modifier.padding(vertical = 14.dp), style = Type.of(13).copy(color = Ink.muted))
            a.log.forEachIndexed { i, r ->
                if (i > 0) RowGap()
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val buy = r.kind == TradeKind.BUY
                        ToneChip(t(if (buy) UiKey.ASSET_DETAIL_BUY else UiKey.ASSET_DETAIL_SELL), if (buy) Ink.transfer else Ink.income,
                            if (buy) Ink.transfer.copy(alpha = 0.10f) else Ink.selected)
                        BasicText(r.date, Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted))
                        AmountText(r.amountMinor, a.currency, tone = if (buy) AmountTone.EXPENSE else AmountTone.INCOME, size = 14, showCurrency = false)
                    }
                    BasicText(r.line, style = Type.of(13))
                    if (r.linked) BasicText(t(UiKey.ASSET_DETAIL_LINKED), style = Type.caption().copy(color = Ink.muted))
                }
            }
        }
        BasicText(t(UiKey.ASSET_DETAIL_METHOD), style = Type.caption().copy(color = Ink.muted))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(a: AssetDetailUi, onManual: () -> Unit, onLink: () -> Unit, onUnlink: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(a.sourceTitle, style = Type.bodyBold())
            BasicText(a.sourceBody, style = Type.caption().copy(color = Ink.muted))
            if (!a.realEstate) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(t(UiKey.ASSET_DETAIL_ACT_MANUAL), onManual, height = 44.dp)
                if (a.linked) TonalButton(t(UiKey.ASSET_DETAIL_ACT_UNLINK), onUnlink, height = 44.dp)
                else TonalButton(t(UiKey.ASSET_DETAIL_ACT_LINK), onLink, height = 44.dp)
            }
        }
    }
}
