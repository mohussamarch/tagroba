package app.masroufy.core

import app.masroufy.core.EntityJson.obj
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test

/** الكميات والاستثمار وملف الأسعار (native-app/golden/invest.json). */
class InvestGoldenTest {
    private fun check(fn: String, run: (JsonElement) -> Any?) = Golden.check("invest", fn) { json(run(it)) }
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)
    private fun JsonElement.orNull() = takeIf { it !is JsonNull }

    private fun price(e: JsonElement) = AssetPrice(e.field("assetId").str, e.field("pricePerUnitMinor").num, e.field("asOf").str, e.field("source").str)
    private fun AssetPrice.toJson() = obj("assetId" to assetId, "pricePerUnitMinor" to pricePerUnitMinor, "asOf" to asOf, "source" to source)
    private fun PriceState.toJson(): JsonObject = when (this) {
        PriceState.Missing -> obj("kind" to "missing")
        is PriceState.Fresh -> obj("kind" to "fresh", "price" to price.toJson(), "ageDays" to ageDays)
        is PriceState.Stale -> obj("kind" to "stale", "price" to price.toJson(), "ageDays" to ageDays)
    }
    private fun AssetPosition.toJson(extra: Map<String, JsonElement> = emptyMap()) = JsonObject(
        mapOf(
            "assetId" to json(assetId), "heldQuantity" to json(heldQuantity), "costBasisMinor" to json(costBasisMinor),
            "realizedGainMinor" to json(realizedGainMinor), "totalFeesMinor" to json(totalFeesMinor), "grossProceedsMinor" to json(grossProceedsMinor),
            "priceState" to priceState.toJson(), "marketValueMinor" to nullable(marketValueMinor), "unrealizedGainMinor" to nullable(unrealizedGainMinor),
        ) + extra,
    )
    private fun scenario(e: JsonElement): AssetPosition = computePosition(
        "a",
        e.field("lots").jsonArray.map { AssetLot(it.field("id").str, "a", it.field("purchasedAt").str, it.field("quantity").num, it.field("principalMinor").num, it.field("feeMinor").num) },
        e.field("sales").jsonArray.map { AssetSale(it.field("id").str, "a", it.field("soldAt").str, it.field("quantity").num, it.field("grossProceedsMinor").num, it.field("feeMinor").num) },
        e.field("price").orNull()?.let(::price),
        e.field("today").orNull()?.str,
    )
    /** JSON ⇒ Map/List/أرقام/نصوص زي اللي طبقة البيانات هتدّيه لـ `parsePriceFeed`. */
    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonArray -> e.map(::plain)
        is JsonObject -> LinkedHashMap(e.mapValues { (_, v) -> plain(v) })
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            e.longOrNull != null -> e.longOrNull
            else -> e.double
        }
    }

    @Test fun quantities() {
        check("parseQuantity") { parseQuantity(it.str) }
        check("formatQuantity") { formatQuantity(it.num) }
        check("addSubtractQuantity") { val (a, b) = it.jsonArray.map { x -> x.num }; listOf(subtractQuantity(a, b), addQuantity(a, b)) }
        check("valueOfQuantity") { valueOfQuantity(it.field("quantity").num, it.field("price").num) }
        check("shareOfAmount") { shareOfAmount(it.field("amount").num, it.field("part").num, it.field("whole").num) }
        check("unitPriceOf") { unitPriceOf(it.field("amount").num, it.field("quantity").num) }
    }

    @Test fun positions() {
        check("computePosition") {
            val p = scenario(it)
            p.toJson(mapOf("priceDescription" to json(describePriceState(p.priceState)), "valueText" to json(formatAssetValue(p.marketValueMinor))))
        }
        check("assessPrice") {
            val state = assessPrice(it.field("price").orNull()?.let(::price), it.field("today").orNull()?.str)
            obj("state" to state.toJson(), "description" to describePriceState(state))
        }
        check("computePortfolioTotals") {
            val t = computePortfolioTotals(it.jsonArray.mapNotNull { s -> runCatching { scenario(s) }.getOrNull() })
            JsonObject(
                mapOf(
                    "costBasisMinor" to json(t.costBasisMinor), "realizedGainMinor" to json(t.realizedGainMinor), "marketValueMinor" to nullable(t.marketValueMinor),
                    "unrealizedGainMinor" to nullable(t.unrealizedGainMinor), "assetsWithoutPrice" to json(t.assetsWithoutPrice),
                ),
            )
        }
    }

    @Test fun priceFeed() {
        check("parsePriceFeed") {
            val feed = parsePriceFeed(plain(it))
            obj(
                "feed" to obj(
                    "generatedAt" to feed.generatedAt, "baseCurrency" to feed.baseCurrency,
                    "prices" to feed.prices.map { p -> obj("symbol" to p.symbol, "name" to p.name, "unit" to p.unit, "pricePerUnitMinor" to p.pricePerUnitMinor, "asOf" to p.asOf, "source" to p.source) },
                    "rejected" to feed.rejected.map { r -> obj("symbol" to r.key, "reason" to r.reason) },
                    "failures" to feed.failures.map { f -> obj("source" to f.key, "reason" to f.reason) },
                ),
                "description" to describeFeed(feed),
            )
        }
    }
}
