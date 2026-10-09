package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.ASSET_KIND_LABELS
import app.masroufy.core.ASSET_UNIT_DEFAULTS
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * لوحة «أصل جديد · شراء · بيع · سعر يدوي» (`AssetTradeSheet` — تسجيل بس، مفيش تداول). الخانات ⇒ [tradeRequest] ⇒ `ManageAssets`
 * (`addAsset` · `recordPurchase` · `recordSale` · `setPrice`)، ورسالة حالة الاستخدام (بيع أكتر من المملوك …) بتظهر تحت الخانات زي ما هي.
 * [onSaved] بياخد معرّف الأصل (الجديد في «أصل جديد»).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssetTradeSheet(visible: Boolean, mode: TradeMode, asset: AssetDetailUi?, onDismiss: () -> Unit, onSaved: (Id) -> Unit) {
    val space = LocalSpace.current
    val deps = space.investment
    val currency = space.space.currency
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val today = deps.today()
    var form by remember(visible, mode) { mutableStateOf(TradeForm(day = today)) }
    var error by remember(visible, mode) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var ops by remember(visible, mode) { mutableStateOf<List<LinkableOp>>(emptyList()) }
    LaunchedEffect(visible, mode) {
        if (visible && (mode == TradeMode.BUY || mode == TradeMode.SELL)) {
            ops = linkableOps(runCatching { deps.recentOperations() }.getOrNull(), outgoing = mode == TradeMode.BUY, currency)
        }
    }
    val name = asset?.name.orEmpty()
    val title = when (mode) {
        TradeMode.ADD -> t(TextKey.ASSET_TRADE_ADD_TITLE)
        TradeMode.BUY -> t(TextKey.ASSET_TRADE_BUY_TITLE, name)
        TradeMode.SELL -> t(TextKey.ASSET_TRADE_SELL_TITLE, name)
        TradeMode.PRICE -> t(TextKey.ASSET_TRADE_PRICE_TITLE, name)
    }
    fun save() {
        val request = tradeRequest(mode, asset?.assetId, currency, form)
        if (request is TradeRequest.Invalid) { error = request.message; return }
        saving = true
        scope.launch {
            try {
                val id = when (request) {
                    is TradeRequest.Add -> deps.assets.addAsset(request.input).also { toaster.show(t(TextKey.ASSET_TRADE_DONE_ADD, it.name)) }.id
                    is TradeRequest.Buy -> deps.assets.recordPurchase(request.input).also { toaster.show(t(TextKey.ASSET_TRADE_DONE_BUY, qtyUnit(it.quantity, asset!!.unit))) }.assetId
                    is TradeRequest.Sell -> deps.assets.recordSale(request.input).also { toaster.show(t(TextKey.ASSET_TRADE_DONE_SELL, qtyUnit(it.sale.quantity, asset!!.unit))) }.sale.assetId
                    is TradeRequest.Price -> deps.assets.setPrice(asset!!.assetId, request.minor).also { toaster.show(t(TextKey.ASSET_TRADE_DONE_PRICE, moneyText(it.pricePerUnitMinor, currency))) }.assetId
                    is TradeRequest.Invalid -> return@launch
                }
                onSaved(id)
                onDismiss()
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (e: IllegalStateException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }
    Sheet(visible, onDismiss, title, closeLabel = t(TextKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        if (asset != null && mode != TradeMode.ADD) {
            BasicText(t(TextKey.ASSET_TRADE_SUB, asset.qtyText, moneyText(asset.costMinor, currency)), style = Type.of(13).copy(color = Ink.muted))
        }
        when (mode) {
            TradeMode.ADD -> {
                TextInput(form.name, { form = form.copy(name = it); error = null }, label = t(TextKey.ASSET_TRADE_NAME), placeholder = t(TextKey.ASSET_TRADE_NAME_HINT))
                BasicText(t(TextKey.ASSET_TRADE_KIND), style = Type.of(13, FontWeight.Bold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in TRADE_KINDS) SelectChip(ASSET_KIND_LABELS[k] ?: k, form.kind == k, { form = form.copy(kind = k); error = null }, height = 44.dp)
                }
                TextInput(form.unit, { form = form.copy(unit = it) }, label = t(TextKey.ASSET_TRADE_UNIT), placeholder = ASSET_UNIT_DEFAULTS[form.kind])
            }
            TradeMode.BUY, TradeMode.SELL -> TradeFields(form, ops, asset, mode == TradeMode.BUY, { form = it; error = null })
            TradeMode.PRICE -> TextInput(
                form.price, { form = form.copy(price = it); error = null }, label = t(TextKey.ASSET_TRADE_PRICE_LABEL, asset?.unit.orEmpty()),
                placeholder = "0", ltr = true, keyboard = KeyboardType.Decimal, height = 56.dp, textSize = 22,
                trailing = { FieldUnit(app.masroufy.core.currencySymbol(currency)) },
            )
        }
        val note = when (mode) {
            TradeMode.BUY -> TextKey.ASSET_TRADE_NOTE_BUY
            TradeMode.SELL -> TextKey.ASSET_TRADE_NOTE_SELL
            TradeMode.PRICE -> TextKey.ASSET_TRADE_NOTE_PRICE
            TradeMode.ADD -> null
        }
        if (note != null) BasicText(t(note), style = Type.caption().copy(color = Ink.muted))
        error?.let { FieldError(it) }
        val saveKey = when (mode) {
            TradeMode.ADD -> TextKey.ASSET_TRADE_SAVE_ADD
            TradeMode.BUY -> TextKey.ASSET_TRADE_SAVE_BUY
            TradeMode.SELL -> TextKey.ASSET_TRADE_SAVE_SELL
            TradeMode.PRICE -> TextKey.ASSET_TRADE_SAVE_PRICE
        }
        PrimaryButton(t(saveKey), ::save, Modifier.fillMaxWidth(), loading = saving)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TradeFields(form: TradeForm, ops: List<LinkableOp>, asset: AssetDetailUi?, buy: Boolean, onChange: (TradeForm) -> Unit) {
    val currency = LocalSpace.current.space.currency
    val today = LocalSpace.current.investment.today()
    val yesterday = yesterdayOf(today)
    val linked = ops.firstOrNull { it.id == form.linkId }
    BasicText(t(TextKey.ASSET_TRADE_WHEN), style = Type.of(13, FontWeight.Bold))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectChip(t(TextKey.ASSET_TRADE_TODAY), form.day == today, { onChange(form.copy(day = today)) }, height = 44.dp)
        SelectChip(t(TextKey.ASSET_TRADE_YESTERDAY), form.day == yesterday, { onChange(form.copy(day = yesterday)) }, height = 44.dp)
        if (linked != null && linked.date != today && linked.date != yesterday) {
            SelectChip(dateText(linked.date), form.day == linked.date, { onChange(form.copy(day = linked.date)) }, height = 44.dp)
        }
    }
    TextInput(form.qty, { onChange(form.copy(qty = it)) }, label = t(TextKey.ASSET_TRADE_QTY), placeholder = "0", ltr = true,
        keyboard = KeyboardType.Decimal, trailing = { FieldUnit(asset?.unit.orEmpty()) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput(form.amount, { onChange(form.copy(amount = it)) }, Modifier.weight(1f), label = t(if (buy) TextKey.ASSET_TRADE_PAID else TextKey.ASSET_TRADE_PROCEEDS),
            placeholder = "0", ltr = true, keyboard = KeyboardType.Decimal, trailing = { FieldUnit(app.masroufy.core.currencySymbol(currency)) })
        TextInput(form.fee, { onChange(form.copy(fee = it)) }, Modifier.weight(1f), label = t(TextKey.ASSET_TRADE_FEES),
            placeholder = "0", ltr = true, keyboard = KeyboardType.Decimal, trailing = { FieldUnit(app.masroufy.core.currencySymbol(currency)) })
    }
    BasicText(t(if (buy) TextKey.ASSET_TRADE_LINK_BUY else TextKey.ASSET_TRADE_LINK_SELL), style = Type.of(13, FontWeight.Bold))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OpChoice(t(TextKey.ASSET_TRADE_NO_LINK), null, form.linkId == null) { onChange(form.copy(linkId = null)) }
        for (op in ops) {
            OpChoice(t(TextKey.INVEST_ROW_SUB, op.title, dateText(op.date)), op, form.linkId == op.id) {
                onChange(form.copy(linkId = op.id, day = op.date, amount = form.amount.ifBlank { app.masroufy.ui.text.amount(op.amountMinor, currency) }))
            }
        }
    }
}

/** اختيار عملية للربط (حبة 48 بزاوية 14 — المختارة خلفيتها `#DCEBD6` بحد أخضر). */
@Composable
internal fun OpChoice(label: String, op: LinkableOp?, selected: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).clip(shape)
            .background(if (selected) Ink.selected else QuietFill).then(if (selected) Modifier.insetRing(shape, 1.5.dp, Ink.primary) else Modifier)
            .tap(press, role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = Type.body(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (op != null) {
            AmountText(op.amountMinor, LocalSpace.current.space.currency, tone = if (op.outgoing) AmountTone.EXPENSE else AmountTone.INCOME, size = 14, showCurrency = false)
        }
    }
}

/** الوحدة جوه الخانة على آخرها (13 رمادي). */
@Composable
internal fun FieldUnit(text: String) {
    BasicText(text, Modifier.padding(end = 14.dp), style = Type.of(13).copy(color = Ink.muted))
}
