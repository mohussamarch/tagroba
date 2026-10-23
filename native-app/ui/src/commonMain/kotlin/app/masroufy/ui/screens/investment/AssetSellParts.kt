package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.SaleOutcome
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SellDestination
import app.masroufy.usecase.SellWalletKind

/**
 * أجزاء وضع البيع في `AssetTradeSheet` (§112-4 — `AssetTradeSheet.dc.html`): مربعات «الفلوس راحت فين؟» بأيقونات · قايمة «اربطه بالتحويل
 * اللي وصل» (بنك بس) · كارت «مكسب +X» أو «خسارة −X». الحساب كله من `SellAsset` — هنا عرض بس.
 */
private val BANK = Lucide("SELL_BANK", "M3 10h18M5 10v8M9 10v8M15 10v8M19 10v8M3 20h18M12 3 3 8h18z")
private val CASH = Lucide("SELL_CASH", "M3 7h18v10H3zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6M6 10v.01M18 14v.01")
private val PHONE = Lucide("SELL_PHONE", "M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM11 18h2")
private val UP = Lucide("SELL_UP", "M3 17l6-6 4 4 8-8M15 7h6v6")
private val DOWN = Lucide("SELL_DOWN", "M3 7l6 6 4-4 8 8M15 17h6v-6")

/** حالة وضع البيع في اللوحة: المحافظ · التحويلات القريبة · المكسب أو الخسارة (أو سبب إنه مش متاح — زي بيع أكتر من المملوك). */
internal class SellUi(
    val destinations: List<SellDestination>, val credits: List<LinkableOp>, val outcome: SaleOutcome?, val problem: String?, val bankPicked: Boolean,
)

@Composable
internal fun rememberSellState(active: Boolean, assetId: Id?, currency: Currency, form: TradeForm): SellUi {
    val deps = app.masroufy.ui.app.LocalSpace.current.investment
    var dests by remember(active, assetId) { mutableStateOf(emptyList<SellDestination>()) }
    var credits by remember(active, assetId) { mutableStateOf(emptyList<LinkableOp>()) }
    var outcome by remember(active, assetId) { mutableStateOf<SaleOutcome?>(null) }
    var problem by remember(active, assetId) { mutableStateOf<String?>(null) }
    LaunchedEffect(active, assetId) {
        if (active && assetId != null) dests = runCatching { deps.sell.destinations(assetId) }.getOrDefault(emptyList())
    }
    LaunchedEffect(active, assetId, form.qty, form.amount, form.fee, form.day) {
        outcome = null
        problem = null
        if (!active || assetId == null) return@LaunchedEffect
        // المحفظة مش لازمة للمعاينة — بتتفحص وقت الحفظ
        val req = tradeRequest(TradeMode.SELL, assetId, currency, form.copy(toWalletId = form.toWalletId ?: "-"))
        if (req !is TradeRequest.Sell) return@LaunchedEffect
        try {
            outcome = deps.sell.preview(req.input.sale)
        } catch (e: IllegalArgumentException) {
            problem = e.message
        }
    }
    val bank = dests.firstOrNull { it.wallet.id == form.toWalletId }?.kind == SellWalletKind.BANK
    LaunchedEffect(active, bank, form.toWalletId, form.day, form.amount, form.fee) {
        val to = form.toWalletId
        credits = if (!active || !bank || to == null) emptyList() else {
            val gross = form.amount.takeIf { it.isNotBlank() }?.let { runCatching { app.masroufy.core.parseMoney(it, currency) }.getOrNull() }
            val fee = form.fee.takeIf { it.isNotBlank() }?.let { runCatching { app.masroufy.core.parseMoney(it, currency) }.getOrNull() }
            creditOps(runCatching { deps.sell.matchingCredits(to, form.day, gross, fee) }.getOrDefault(emptyList()))
        }
    }
    return SellUi(dests, credits, outcome, problem, bank)
}

/** «الفلوس راحت فين؟» — مربع لكل محفظة (3 في الصف، 84 ارتفاع): النوع فوق واسم المحفظة تحته. */
@Composable
internal fun SellDestinations(options: List<SellDestination>, selected: Id?, onPick: (Id) -> Unit) {
    BasicText(t(R06Key.SELL_TO), style = Type.of(13, FontWeight.Bold))
    if (options.isEmpty()) {
        BasicText(t(R06Key.SELL_NO_WALLETS), style = Type.caption().copy(color = Ink.expense))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in options.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in row) DestinationTile(o, o.wallet.id == selected, Modifier.weight(1f)) { onPick(o.wallet.id) }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DestinationTile(o: SellDestination, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(16.dp)
    val ink = if (on) Ink.onPrimary else Ink.text
    val (icon, label) = when (o.kind) {
        SellWalletKind.BANK -> BANK to R06Key.SELL_TO_BANK
        SellWalletKind.CASH -> CASH to R06Key.SELL_TO_CASH
        SellWalletKind.OTHER -> PHONE to R06Key.SELL_TO_OTHER
    }
    Column(
        modifier.heightIn(min = 84.dp).pressScale(press).clip(shape).background(if (on) Ink.primary else QuietFill)
            .tap(press, role = Role.RadioButton, onClick = onClick).semantics { this.selected = on }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LucideIcon(icon, size = 20.dp, tint = ink)
        BasicText(t(label), style = Type.of(13, FontWeight.Bold).copy(color = ink))
        BasicText(o.wallet.name, style = Type.of(11).copy(color = ink.copy(alpha = 0.8f), textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** «اربطه بالتحويل اللي وصل (لو وصل)» — بيظهر لما البنك يتختار بس. الاختيار اختياري، و«من غير ربط» الافتراضي. */
@Composable
internal fun SellLinkList(ops: List<LinkableOp>, linkId: Id?, onPick: (LinkableOp?) -> Unit) {
    BasicText(t(R06Key.SELL_LINK), style = Type.of(13, FontWeight.Bold))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OpChoice(t(R06Key.SELL_NO_LINK), null, linkId == null) { onPick(null) }
        for (op in ops) OpChoice(t(app.masroufy.core.UiKey.INVEST_ROW_SUB, op.title, dateText(op.date)), op, linkId == op.id) { onPick(op) }
        if (ops.isEmpty()) BasicText(t(R06Key.SELL_NO_MATCH), style = Type.caption().copy(color = Ink.muted))
    }
}

/** كارت كبير: «مكسب +X» أخضر بسهم طالع أو «خسارة −X» أحمر بسهم نازل، وتحته حصة التكلفة. */
@Composable
internal fun SellGainCard(outcome: SaleOutcome, currency: Currency) {
    val loss = outcome.gainMinor < 0
    val ink = if (loss) Ink.expense else Ink.income
    val bg = if (loss) Ink.expense.copy(alpha = 0.10f) else Ink.selected
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(if (loss) DOWN else UP, size = 22.dp, tint = ink)
            BasicText(t(if (loss) R06Key.SELL_LOSS else R06Key.SELL_GAIN), Modifier.weight(1f), style = Type.of(14, FontWeight.Bold).copy(color = ink))
            val sign = if (loss) "−" else "+"
            BasicText("⁦$sign${moneyText(kotlin.math.abs(outcome.gainMinor), currency)}⁩", style = Type.of(18, FontWeight.Bold).copy(color = ink))
        }
        BasicText(t(R06Key.SELL_COST_SHARE, moneyText(outcome.costShareMinor, currency)), style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
    }
}
