package app.masroufy.usecase

import app.masroufy.core.AssetPrice
import app.masroufy.core.PriceFeed
import app.masroufy.core.TextKey
import app.masroufy.core.indexFeed
import app.masroufy.core.uiText
import app.masroufy.port.AssetPriceRepository
import app.masroufy.port.AssetRepository

/**
 * SyncAssetPrices — نقل `syncAssetPrices.ts`: بيحط أسعار الملف على الأصول المربوطة بيه.
 * spec/01: «السعر المفقود/القديم واضح» ⇒ النتيجة بتقول كام اتحدّث وكام لأ **وليه**.
 * الأصل مش مربوط ما بيتلمسش: سعره اليدوي قرار المستخدم، والملف ما يدهسوش.
 */

data class PriceUpdated(val assetName: String, val symbol: String, val asOf: String)

data class PriceSkipped(val assetName: String, val reason: String)

data class SyncOutcome(
    val updated: List<PriceUpdated>,
    /** أصول مربوطة برمز مش موجود في الملف، أو سعره بعملة غير عملة الأصل. */
    val skipped: List<PriceSkipped>,
    /** أصول من غير ربط أصلًا — سعرها يدوي. */
    val manualCount: Int,
)

data class SyncAssetPricesDeps(val assets: AssetRepository, val prices: AssetPriceRepository)

class SyncAssetPrices(private val deps: SyncAssetPricesDeps) {
    suspend fun sync(feed: PriceFeed): SyncOutcome {
        val bySymbol = indexFeed(feed)
        val updated = mutableListOf<PriceUpdated>()
        val skipped = mutableListOf<PriceSkipped>()
        var manualCount = 0

        for (asset in deps.assets.listAll()) {
            if (asset.archived) continue
            val symbol = asset.feedSymbol
            if (symbol.isNullOrEmpty()) {
                manualCount += 1
                continue
            }
            val feedPrice = bySymbol[symbol]
            if (feedPrice == null) {
                skipped += PriceSkipped(asset.name, uiText(TextKey.PRICE_SYMBOL_MISSING, symbol))
                continue
            }
            // سعر بالجنيه ما يتحطش على أصل بالريال ولا العكس (§62) — مفيش تحويل بسعر صرف (§41)، والسعر القديم بيفضل زي ما هو
            if (feedPrice.currency != asset.currency.name) {
                skipped += PriceSkipped(asset.name, uiText(TextKey.PRICE_CURRENCY_MISMATCH, symbol, feedPrice.currency, asset.currency.name))
                continue
            }
            deps.prices.save(AssetPrice(asset.id, feedPrice.pricePerUnitMinor, feedPrice.asOf, "feed"))
            updated += PriceUpdated(asset.name, feedPrice.symbol, feedPrice.asOf)
        }
        return SyncOutcome(updated, skipped, manualCount)
    }
}
