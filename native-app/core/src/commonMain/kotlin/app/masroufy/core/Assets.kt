package app.masroufy.core

/**
 * الاستثمار — نقل `src/domain/assets.ts` + `priceFeed.ts` + الكيانات (spec/01: تسجيل بس، مفيش تداول).
 * التكلفة بالمتوسط المرجّح؛ الرسوم جزء من التكلفة؛ حصيلة البيع **مش دخل**؛ السعر المفقود أو القديم واضح.
 */
data class Asset(
    val id: Id,
    val name: String,
    /** "gold" / "stock" / "fund" / "digital" / "other". */
    val kind: String,
    val unitLabel: String,
    val currency: Currency,
    val archived: Boolean,
    val feedSymbol: String? = null,
    val note: String? = null,
)

/** أسماء أنواع الأصول للعرض — بتتقرا وقت العرض عشان تتغير مع اللغة (Texts.kt). */
val ASSET_KIND_LABELS: Map<String, String>
    get() = linkedMapOf(
        "gold" to uiText(TextKey.ASSET_KIND_GOLD),
        "stock" to uiText(TextKey.ASSET_KIND_STOCK),
        "fund" to uiText(TextKey.ASSET_KIND_FUND),
        "digital" to uiText(TextKey.ASSET_KIND_DIGITAL),
        "other" to uiText(TextKey.ASSET_KIND_OTHER),
    )

// وحدات الأصول **بتتخزن** مع الأصل نفسه، فما تتترجمش — ترجمتها بتغيّر بيانات متخزنة
val ASSET_UNIT_DEFAULTS = linkedMapOf("gold" to "جرام", "stock" to "سهم", "fund" to "وحدة", "digital" to "وحدة", "other" to "وحدة")

data class AssetLot(val id: Id, val assetId: Id, val purchasedAt: IsoDate, val quantity: Quantity, val principalMinor: Halalas, val feeMinor: Halalas, val transactionId: Id? = null)
data class AssetSale(val id: Id, val assetId: Id, val soldAt: IsoDate, val quantity: Quantity, val grossProceedsMinor: Halalas, val feeMinor: Halalas, val transactionId: Id? = null)
/** "manual" أو "feed". التاريخ جزء من الرقم. */
data class AssetPrice(val assetId: Id, val pricePerUnitMinor: Halalas, val asOf: IsoDate, val source: String)

class AssetError(message: String) : IllegalArgumentException(message)

const val PRICE_STALE_AFTER_DAYS = 7

sealed interface PriceState {
    data object Missing : PriceState
    data class Fresh(val price: AssetPrice, val ageDays: Int) : PriceState
    data class Stale(val price: AssetPrice, val ageDays: Int) : PriceState
}

data class AssetPosition(
    val assetId: Id,
    val heldQuantity: Quantity,
    /** تكلفة المتبقي بس. */
    val costBasisMinor: Halalas,
    val realizedGainMinor: Halalas,
    val totalFeesMinor: Halalas,
    /** اللي اتقبض من البيع — **مش دخل**. */
    val grossProceedsMinor: Halalas,
    val priceState: PriceState,
    /** null لو فيه كمية ومفيش سعر — **مش صفر**. */
    val marketValueMinor: Halalas?,
    val unrealizedGainMinor: Halalas?,
)

/** مركز أصل: الشراء قبل البيع في نفس اليوم؛ تكلفة المتبقي بالطرح عشان مفيش هللة تضيع. */
fun computePosition(assetId: Id, lots: List<AssetLot>, sales: List<AssetSale> = emptyList(), price: AssetPrice? = null, today: IsoDate? = null): AssetPosition {
    data class Event(val at: IsoDate, val order: Int, val lot: AssetLot?, val sale: AssetSale?)
    val events = (lots.map { Event(it.purchasedAt, 0, it, null) } + sales.map { Event(it.soldAt, 1, null, it) })
        .sortedWith { a, b -> if (a.at == b.at) a.order - b.order else if (a.at < b.at) -1 else 1 }
    var held = 0L
    var cost = 0L
    var realized = 0L
    var fees = 0L
    var proceeds = 0L
    for (e in events) {
        if (e.lot != null) {
            if (e.lot.quantity <= 0) throw AssetError(uiText(TextKey.ASSET_BUY_QUANTITY_POSITIVE))
            held = addQuantity(held, e.lot.quantity)
            cost = addMoney(cost, e.lot.principalMinor, e.lot.feeMinor)
            fees = addMoney(fees, e.lot.feeMinor)
            continue
        }
        val sale = e.sale!!
        if (sale.quantity <= 0) throw AssetError(uiText(TextKey.ASSET_SELL_QUANTITY_POSITIVE))
        if (sale.quantity > held) throw AssetError(uiText(TextKey.ASSET_SELL_OVER_HELD, formatQuantity(sale.quantity), formatQuantity(held)))
        val costOfSold = shareOfAmount(cost, sale.quantity, held)
        val net = subtractMoney(sale.grossProceedsMinor, sale.feeMinor)
        realized = addMoney(realized, subtractMoney(net, costOfSold))
        cost = subtractMoney(cost, costOfSold)
        held = subtractQuantity(held, sale.quantity)
        fees = addMoney(fees, sale.feeMinor)
        proceeds = addMoney(proceeds, sale.grossProceedsMinor)
    }
    val state = assessPrice(price, today)
    val market = when {
        held <= 0 -> 0L
        state is PriceState.Fresh -> valueOfQuantity(held, state.price.pricePerUnitMinor)
        state is PriceState.Stale -> valueOfQuantity(held, state.price.pricePerUnitMinor)
        else -> null
    }
    return AssetPosition(assetId, held, cost, realized, fees, proceeds, state, market, market?.let { subtractMoney(it, cost) })
}

/** السعر القديم بيتعرض بتاريخه ومش بيتخبى؛ سعر بتاريخ في المستقبل ما بيتصدقش كطازة. */
fun assessPrice(price: AssetPrice?, today: IsoDate?): PriceState {
    if (price == null) return PriceState.Missing
    if (today == null) return PriceState.Stale(price, 0)
    val age = daysBetween(price.asOf, today)
    if (age < 0) return PriceState.Stale(price, age)
    return if (age > PRICE_STALE_AFTER_DAYS) PriceState.Stale(price, age) else PriceState.Fresh(price, age)
}

fun describePriceState(state: PriceState): String = when (state) {
    PriceState.Missing -> uiText(TextKey.PRICE_MISSING)
    is PriceState.Fresh -> uiText(TextKey.PRICE_FRESH, formatMoney(state.price.pricePerUnitMinor), state.price.asOf)
    is PriceState.Stale ->
        if (state.ageDays < 0) uiText(TextKey.PRICE_FUTURE, state.price.asOf)
        else uiText(TextKey.PRICE_STALE, formatMoney(state.price.pricePerUnitMinor), state.price.asOf, state.ageDays.toString())
}

data class PortfolioTotals(
    val costBasisMinor: Halalas,
    val realizedGainMinor: Halalas,
    /** null لو **أي** أصل مملوك من غير سعر — الناقص ما يتعرضش كأنه كامل. */
    val marketValueMinor: Halalas?,
    val unrealizedGainMinor: Halalas?,
    val assetsWithoutPrice: Int,
)

fun computePortfolioTotals(positions: List<AssetPosition>): PortfolioTotals {
    var cost = 0L
    var realized = 0L
    var market = 0L
    var missing = 0
    for (p in positions) {
        cost = addMoney(cost, p.costBasisMinor)
        realized = addMoney(realized, p.realizedGainMinor)
        if (p.marketValueMinor == null) missing += 1 else market = addMoney(market, p.marketValueMinor)
    }
    val complete = missing == 0
    return PortfolioTotals(cost, realized, if (complete) market else null, if (complete) subtractMoney(market, cost) else null, missing)
}

fun formatAssetValue(value: Halalas?): String = if (value == null) NOT_AVAILABLE else formatMoney(value)

/* ───────────────────────── ملف الأسعار (بيتولد برا التطبيق — CLAUDE.md #12) ───────────────────────── */

data class FeedPrice(val symbol: String, val name: String, val unit: String, val pricePerUnitMinor: Halalas, val asOf: IsoDate, val source: String)
data class FeedIssue(val key: String, val reason: String)
data class PriceFeed(
    val generatedAt: String,
    val baseCurrency: String,
    val prices: List<FeedPrice>,
    /** مدخلات اترفضت وقت القراية بسببها (key = الرمز). */
    val rejected: List<FeedIssue>,
    /** مصادر فشلت وقت التوليد (key = المصدر). */
    val failures: List<FeedIssue>,
)

class PriceFeedError(message: String) : IllegalArgumentException(message)

private fun feedText(value: Any?): String? = (value as? String)?.let(JsText::trim)?.ifEmpty { null }

/** عدد صحيح موجب بالهللة (Long أو Double من غير كسر). */
private fun feedAmount(value: Any?): Long? = when (value) {
    is Int -> value.toLong()
    is Long -> value
    is Double -> if (value.isFinite() && value % 1.0 == 0.0) value.toLong() else null
    else -> null
}?.takeIf { it > 0 }

/**
 * بيقرا ملف الأسعار (JSON متحوّل لـ Map/List/أرقام/نصوص). الشكل الغلط بيرمي؛
 * المدخل الغلط بيترفض لوحده بسببه ومش بيوقف الباقي — **مفيش صفر مكان المجهول**.
 */
fun parsePriceFeed(raw: Any?): PriceFeed {
    val root = raw as? Map<*, *> ?: throw PriceFeedError(uiText(TextKey.FEED_BAD_SHAPE))
    val generatedAt = feedText(root["generatedAt"]) ?: throw PriceFeedError(uiText(TextKey.FEED_NO_GENERATED_AT))
    val baseCurrency = feedText(root["baseCurrency"]) ?: throw PriceFeedError(uiText(TextKey.FEED_NO_CURRENCY))
    val entries = root["prices"] as? Map<*, *> ?: throw PriceFeedError(uiText(TextKey.FEED_NO_PRICES))
    val prices = mutableListOf<FeedPrice>()
    val rejected = mutableListOf<FeedIssue>()
    for ((key, entry) in entries) {
        val symbol = key.toString()
        if (entry !is Map<*, *>) { rejected += FeedIssue(symbol, uiText(TextKey.FEED_ENTRY_NOT_OBJECT)); continue }
        val amount = feedAmount(entry["pricePerUnitMinor"])
        if (amount == null) { rejected += FeedIssue(symbol, uiText(TextKey.FEED_PRICE_NOT_INTEGER)); continue }
        val asOf = feedText(entry["asOf"])
        if (asOf == null || !isValidIsoDate(asOf)) { rejected += FeedIssue(symbol, uiText(TextKey.FEED_NO_VALID_DATE)); continue }
        val source = feedText(entry["source"])
        if (source == null) { rejected += FeedIssue(symbol, uiText(TextKey.FEED_NO_SOURCE)); continue }
        prices += FeedPrice(symbol, feedText(entry["name"]) ?: symbol, feedText(entry["unit"]) ?: "", amount, asOf, source)
    }
    val failures = (root["failures"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        .map { FeedIssue(feedText(it["source"]) ?: uiText(TextKey.FEED_UNKNOWN_SOURCE), feedText(it["reason"]) ?: uiText(TextKey.FEED_NO_REASON)) }
    return PriceFeed(generatedAt, baseCurrency, prices, rejected, failures)
}

fun indexFeed(feed: PriceFeed): Map<String, FeedPrice> = feed.prices.associateBy { it.symbol }

/** جملة واحدة للمستخدم: كام سعر وإمتى، وكام مصدر ما جابش. */
fun describeFeed(feed: PriceFeed): String {
    val day = feed.generatedAt.take(10)
    val missing = feed.failures.size + feed.rejected.size
    val base = uiText(TextKey.FEED_SUMMARY, feed.prices.size.toString(), day)
    return if (missing == 0) base else uiText(TextKey.FEED_SUMMARY_WITH_MISSING, base, missing.toString())
}
