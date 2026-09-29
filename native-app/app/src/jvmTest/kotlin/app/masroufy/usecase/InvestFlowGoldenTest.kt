package app.masroufy.usecase

import app.masroufy.core.Asset
import app.masroufy.core.AssetLot
import app.masroufy.core.AssetPosition
import app.masroufy.core.AssetPrice
import app.masroufy.core.AssetSale
import app.masroufy.core.Currency
import app.masroufy.core.EntityJson
import app.masroufy.core.Golden
import app.masroufy.core.PortfolioTotals
import app.masroufy.core.PriceState
import app.masroufy.core.field
import app.masroufy.core.json
import app.masroufy.core.parsePriceFeed
import app.masroufy.core.str
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAssetLotRepository
import app.masroufy.memory.MemoryAssetPriceRepository
import app.masroufy.memory.MemoryAssetRepository
import app.masroufy.memory.MemoryAssetSaleRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test

/** إدارة الأصول وتحديث الأسعار على `investFlow.json`. */
class InvestFlowGoldenTest {
    private fun nullable(value: Any?): JsonElement = if (value == null) JsonNull else json(value)

    private fun JsonElement.optStr(name: String): String? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun JsonElement.optLong(name: String): Long? = jsonObject[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.long

    private fun JsonElement.long(name: String): Long = field(name).jsonPrimitive.long

    private fun asset(e: JsonElement) = Asset(
        id = e.field("id").str, name = e.field("name").str, kind = e.field("kind").str, unitLabel = e.field("unitLabel").str,
        currency = Currency.valueOf(e.field("currency").str), archived = e.field("archived").jsonPrimitive.boolean,
        feedSymbol = e.optStr("feedSymbol"), note = e.optStr("note"),
    )

    private fun assetJson(a: Asset) = EntityJson.obj(
        "id" to a.id, "name" to a.name, "kind" to a.kind, "unitLabel" to a.unitLabel, "currency" to a.currency.name,
        "archived" to a.archived, "feedSymbol" to a.feedSymbol, "note" to a.note,
    )

    private fun lot(e: JsonElement) = AssetLot(
        e.field("id").str, e.field("assetId").str, e.field("purchasedAt").str, e.long("quantity"),
        e.long("principalMinor"), e.long("feeMinor"), e.optStr("transactionId"),
    )

    private fun lotJson(l: AssetLot) = EntityJson.obj(
        "id" to l.id, "assetId" to l.assetId, "purchasedAt" to l.purchasedAt, "quantity" to l.quantity,
        "principalMinor" to l.principalMinor, "feeMinor" to l.feeMinor, "transactionId" to l.transactionId,
    )

    private fun sale(e: JsonElement) = AssetSale(
        e.field("id").str, e.field("assetId").str, e.field("soldAt").str, e.long("quantity"),
        e.long("grossProceedsMinor"), e.long("feeMinor"), e.optStr("transactionId"),
    )

    private fun saleJson(s: AssetSale) = EntityJson.obj(
        "id" to s.id, "assetId" to s.assetId, "soldAt" to s.soldAt, "quantity" to s.quantity,
        "grossProceedsMinor" to s.grossProceedsMinor, "feeMinor" to s.feeMinor, "transactionId" to s.transactionId,
    )

    private fun price(e: JsonElement) = AssetPrice(e.field("assetId").str, e.long("pricePerUnitMinor"), e.field("asOf").str, e.field("source").str)

    private fun priceJson(p: AssetPrice) = EntityJson.obj("assetId" to p.assetId, "pricePerUnitMinor" to p.pricePerUnitMinor, "asOf" to p.asOf, "source" to p.source)

    private fun stateJson(s: PriceState): JsonObject = when (s) {
        PriceState.Missing -> EntityJson.obj("kind" to "missing")
        is PriceState.Fresh -> JsonObject(mapOf("kind" to json("fresh"), "price" to priceJson(s.price), "ageDays" to json(s.ageDays)))
        is PriceState.Stale -> JsonObject(mapOf("kind" to json("stale"), "price" to priceJson(s.price), "ageDays" to json(s.ageDays)))
    }

    private fun positionJson(p: AssetPosition) = JsonObject(
        mapOf(
            "assetId" to json(p.assetId), "heldQuantity" to json(p.heldQuantity), "costBasisMinor" to json(p.costBasisMinor),
            "realizedGainMinor" to json(p.realizedGainMinor), "totalFeesMinor" to json(p.totalFeesMinor),
            "grossProceedsMinor" to json(p.grossProceedsMinor), "priceState" to stateJson(p.priceState),
            "marketValueMinor" to nullable(p.marketValueMinor), "unrealizedGainMinor" to nullable(p.unrealizedGainMinor),
        ),
    )

    private fun totalsJson(t: PortfolioTotals) = JsonObject(
        mapOf(
            "costBasisMinor" to json(t.costBasisMinor), "realizedGainMinor" to json(t.realizedGainMinor),
            "marketValueMinor" to nullable(t.marketValueMinor), "unrealizedGainMinor" to nullable(t.unrealizedGainMinor),
            "assetsWithoutPrice" to json(t.assetsWithoutPrice),
        ),
    )

    /** JSON ⇒ Map/List/أرقام/نصوص زي اللي طبقة البيانات هتدّيه لـ`parsePriceFeed`. */
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

    private class Repos(seed: JsonElement, test: InvestFlowGoldenTest) {
        val assets = MemoryAssetRepository(seed.field("assets").jsonArray.map(test::asset))
        val lots = MemoryAssetLotRepository(seed.field("lots").jsonArray.map(test::lot))
        val sales = MemoryAssetSaleRepository(seed.field("sales").jsonArray.map(test::sale))
        val prices = MemoryAssetPriceRepository(seed.field("prices").jsonArray.map(test::price))
    }

    @Test
    fun manageAssets() {
        Golden.check("investFlow", "manageAssets") { input ->
            val r = Repos(input.field("seed"), this)
            val manage = ManageAssets(ManageAssetsDeps(r.assets, r.lots, r.sales, r.prices, SequentialIdGenerator(), FixedClock("2026-09-22T10:00:00.000Z")))
            val action = input.field("action")

            runBlocking {
                suspend fun stored() = mapOf(
                    "storedAssets" to JsonArray(r.assets.listAll().map(::assetJson)),
                    "storedLots" to JsonArray(r.lots.listAll().map(::lotJson)),
                    "storedSales" to JsonArray(r.sales.listAll().map(::saleJson)),
                    "storedPrices" to JsonArray(r.prices.listAll().map(::priceJson)),
                )
                when (action.field("kind").str) {
                    "list" -> {
                        val view = manage.listPortfolio(action.optStr("today"))
                        JsonObject(
                            mapOf(
                                "rows" to JsonArray(
                                    view.rows.map { row ->
                                        JsonObject(
                                            mapOf(
                                                "assetId" to json(row.asset.id),
                                                "lotIds" to json(row.lots.map { it.id }),
                                                "saleIds" to json(row.sales.map { it.id }),
                                                "position" to positionJson(row.position),
                                            ),
                                        )
                                    },
                                ),
                                "totals" to totalsJson(view.totals),
                            ),
                        )
                    }
                    "addAsset" -> {
                        val i = action.field("input")
                        val created = manage.addAsset(
                            NewAsset(
                                name = i.field("name").str, kind = i.field("kind").str, unitLabel = i.optStr("unitLabel"),
                                feedSymbol = i.optStr("feedSymbol"), note = i.optStr("note"),
                            ),
                        )
                        JsonObject(mapOf("asset" to assetJson(created)) + stored())
                    }
                    "linkToFeed" -> JsonObject(mapOf("asset" to assetJson(manage.linkToFeed(action.field("assetId").str, action.optStr("feedSymbol")))) + stored())
                    "archive" -> {
                        manage.archiveAsset(action.field("assetId").str, action.field("archived").jsonPrimitive.boolean)
                        JsonObject(stored())
                    }
                    "purchase" -> {
                        val i = action.field("input")
                        val created = manage.recordPurchase(
                            PurchaseInput(
                                assetId = i.field("assetId").str, purchasedAt = i.field("purchasedAt").str, quantity = i.long("quantity"),
                                principalMinor = i.long("principalMinor"), feeMinor = i.optLong("feeMinor"), transactionId = i.optStr("transactionId"),
                            ),
                        )
                        JsonObject(mapOf("lot" to lotJson(created)) + stored())
                    }
                    "sale" -> {
                        val i = action.field("input")
                        val result = manage.recordSale(
                            SaleInput(
                                assetId = i.field("assetId").str, soldAt = i.field("soldAt").str, quantity = i.long("quantity"),
                                grossProceedsMinor = i.long("grossProceedsMinor"), feeMinor = i.optLong("feeMinor"), transactionId = i.optStr("transactionId"),
                            ),
                        )
                        JsonObject(mapOf("sale" to saleJson(result.sale), "position" to positionJson(result.position)) + stored())
                    }
                    else -> {
                        val i = action.field("input")
                        val saved = manage.setPrice(i.field("assetId").str, i.long("pricePerUnitMinor"), i.optStr("asOf"))
                        JsonObject(mapOf("price" to priceJson(saved)) + stored())
                    }
                }
            }
        }
    }

    @Test
    fun syncAssetPrices() {
        Golden.check("investFlow", "syncAssetPrices") { input ->
            val r = Repos(input.field("seed"), this)
            runBlocking {
                val outcome = SyncAssetPrices(SyncAssetPricesDeps(r.assets, r.prices)).sync(parsePriceFeed(plain(input.field("feed"))))
                JsonObject(
                    mapOf(
                        "outcome" to JsonObject(
                            mapOf(
                                "updated" to JsonArray(outcome.updated.map { EntityJson.obj("assetName" to it.assetName, "symbol" to it.symbol, "asOf" to it.asOf) }),
                                "skipped" to JsonArray(outcome.skipped.map { EntityJson.obj("assetName" to it.assetName, "reason" to it.reason) }),
                                "manualCount" to json(outcome.manualCount),
                            ),
                        ),
                        "storedPrices" to JsonArray(r.prices.listAll().map(::priceJson)),
                    ),
                )
            }
        }
    }
}
