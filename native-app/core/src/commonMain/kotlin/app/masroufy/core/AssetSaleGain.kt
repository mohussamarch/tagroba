package app.masroufy.core

/**
 * §112-4 — مكسب أو خسارة بيعة واحدة: **الحصيلة − الرسوم − حصة التكلفة**.
 *
 * حصة التكلفة بالمتوسط المرجّح للكمية المباعة = تكلفة المملوك وقت البيع × الكمية المباعة ÷ المملوك وقتها، **مقرّبة مرة واحدة نص
 * لفوق بعيد عن الصفر** (`shareOfAmount`)، وبيع كل المملوك بياخد التكلفة كلها (مفيش هللة تضيع). ده نفس حساب [computePosition] بالظبط:
 * مشتريات يوم البيع قبله، والمبيعات اللي في نفس اليوم بترتيب تسجيلها (البيعة الجديدة آخرهم). أعداد صحيحة بس — مفيش كسور عشرية.
 */
data class SaleOutcome(val costShareMinor: Halalas, val gainMinor: Halalas, val heldBefore: Quantity)

fun saleOutcome(lots: List<AssetLot>, priorSales: List<AssetSale>, sale: AssetSale): SaleOutcome {
    val before = computePosition(
        sale.assetId,
        lots.filter { it.purchasedAt <= sale.soldAt },
        priorSales.filter { it.id != sale.id && it.soldAt <= sale.soldAt },
    )
    if (sale.quantity <= 0) throw AssetError(uiText(TextKey.ASSET_SELL_QUANTITY_POSITIVE))
    if (sale.quantity > before.heldQuantity) {
        throw AssetError(uiText(TextKey.ASSET_SELL_OVER_HELD, formatQuantity(sale.quantity), formatQuantity(before.heldQuantity)))
    }
    val share = shareOfAmount(before.costBasisMinor, sale.quantity, before.heldQuantity)
    val gain = subtractMoney(subtractMoney(sale.grossProceedsMinor, sale.feeMinor), share)
    return SaleOutcome(share, gain, before.heldQuantity)
}
