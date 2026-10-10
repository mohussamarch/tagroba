package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.parseMoney
import app.masroufy.core.parseQuantity
import app.masroufy.core.toDayNumber
import app.masroufy.core.uiText
import app.masroufy.usecase.NewAsset
import app.masroufy.usecase.PurchaseInput
import app.masroufy.usecase.SaleInput
import app.masroufy.usecase.TransactionsScreenData

/**
 * لوحة «أصل جديد · شراء · بيع · سعر يدوي» (`AssetTradeSheet`): من الخانات المكتوبة لمدخل حالة الاستخدام (`ManageAssets`).
 * **قراية بس، من غير حساب:** الكمية بـ`parseQuantity` والمبالغ بـ`parseMoney` (من `core` — بيرفضوا الكسر الزيادة بدل ما يقرّبوا بصمت)،
 * والفحص الحقيقي (بيع أكتر من المملوك · رسوم أكبر من مبلغ البيع · الاسم مكرر) في حالة الاستخدام نفسها ورسالتها بتظهر زي ما هي.
 * ⚠️ المعاينة الحية في النموذج (سعر الوحدة · التكلفة بعده · حصة التكلفة) مالهاش حالة استخدام ⇒ مش معروضة (HANDOVER).
 */
enum class TradeMode { ADD, BUY, SELL, PRICE }

/** الخانات زي ما المستخدم كتبها. [day] تاريخ العملية (النهارده · امبارح · يوم العملية المربوطة). */
data class TradeForm(
    val qty: String = "",
    val amount: String = "",
    val fee: String = "",
    val day: IsoDate = "",
    val linkId: Id? = null,
    val price: String = "",
    val name: String = "",
    val kind: String = "gold",
    val unit: String = "",
)

sealed interface TradeRequest {
    data class Add(val input: NewAsset) : TradeRequest
    data class Buy(val input: PurchaseInput) : TradeRequest
    data class Sell(val input: SaleInput) : TradeRequest
    data class Price(val minor: Halalas) : TradeRequest
    data class Invalid(val message: String) : TradeRequest
}

/** الأنواع اللي بتتختار في «أصل جديد» (SCREENS.md: ذهب · سهم · صندوق · رقمي · فضة · عقار · أخرى). */
val TRADE_KINDS = listOf("gold", "stock", "fund", "digital", "silver", "realEstate", "other")

fun tradeRequest(mode: TradeMode, assetId: Id?, currency: Currency, form: TradeForm): TradeRequest = try {
    when (mode) {
        TradeMode.ADD -> TradeRequest.Add(
            NewAsset(form.name, form.kind, unitLabel = form.unit.trim().ifEmpty { null }, currency = currency),
        )
        TradeMode.PRICE -> {
            val price = form.price.trim().takeIf { it.isNotEmpty() }?.let { parseMoney(it, currency) }
            if (price == null || price <= 0) TradeRequest.Invalid(uiText(UiKey.ASSET_TRADE_ERR_PRICE)) else TradeRequest.Price(price)
        }
        TradeMode.BUY, TradeMode.SELL -> {
            val buy = mode == TradeMode.BUY
            when {
                form.qty.isBlank() -> TradeRequest.Invalid(uiText(UiKey.ASSET_TRADE_ERR_QTY))
                form.amount.isBlank() -> TradeRequest.Invalid(uiText(if (buy) UiKey.ASSET_TRADE_ERR_PAID else UiKey.ASSET_TRADE_ERR_PROCEEDS))
                else -> {
                    val q = parseQuantity(form.qty)
                    val v = parseMoney(form.amount, currency)
                    val fee = form.fee.trim().takeIf { it.isNotEmpty() }?.let { parseMoney(it, currency) }
                    val id = assetId ?: ""
                    if (buy) TradeRequest.Buy(PurchaseInput(id, form.day, q, v, fee, form.linkId))
                    else TradeRequest.Sell(SaleInput(id, form.day, q, v, fee, form.linkId))
                }
            }
        }
    }
} catch (e: IllegalArgumentException) {
    TradeRequest.Invalid(e.message ?: uiText(UiKey.SHELL_LOAD_FAILED))
}

/** امبارح (تاريخ بس — مش فلوس). */
fun yesterdayOf(today: IsoDate): IsoDate = dayNumberToIso(toDayNumber(parseIsoDate(today)) - 1)

/** عملية ممكن تتربط (شراء أصل · بيع أصل · دفعة زكاة). */
data class LinkableOp(val id: Id, val title: String, val date: IsoDate, val amountMinor: Halalas, val outgoing: Boolean)

/**
 * عمليات الشهر المالي اللي ممكن تتربط: الصرف للشراء ولدفع الزكاة، والوارد للبيع — الأحدث الأول، و[limit] بس.
 * الربط نفسه وفحصه (العملية ما تتربطش بحاجتين) في حالة الاستخدام.
 */
fun linkableOps(data: TransactionsScreenData?, outgoing: Boolean, currency: Currency, limit: Int = 4): List<LinkableOp> {
    if (data == null) return emptyList()
    val dir = if (outgoing) Direction.OUT else Direction.IN
    return data.transactions
        .filter { it.observedDirection == dir && it.currency == currency && it.transferToWalletId == null }
        .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.sourceOrder })
        .take(limit)
        .map { LinkableOp(it.id, opTitle(it, data), it.occurredAt, it.amountMinor, outgoing) }
}

/** اسم العملية للعرض: التاجر · الوصف · الملاحظة · التصنيف. */
fun opTitle(t: Transaction, data: TransactionsScreenData): String =
    data.merchantNamesByTransaction[t.id]?.firstOrNull()
        ?: t.rawMerchantName?.takeIf { it.isNotBlank() }
        ?: t.rawDescription?.takeIf { it.isNotBlank() }
        ?: t.note?.takeIf { it.isNotBlank() }
        ?: data.categories.firstOrNull { it.id == t.categoryId }?.name
        ?: uiText(TextKey.NOT_AVAILABLE)
