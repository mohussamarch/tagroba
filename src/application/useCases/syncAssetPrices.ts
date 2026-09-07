import { indexFeed, type PriceFeed } from '../../domain/priceFeed'
import type { AssetPrice } from '../../domain/entities/assets'
import type { AssetPriceRepository, AssetRepository } from '../ports/repositories'

/**
 * SyncAssetPrices — يحط أسعار الملف على الأصول المربوطة بيه.
 *
 * `spec/01`: «السعر المفقود/القديم واضح» — فالنتيجة بتقول بالظبط
 * كام أصل اتحدّث وكام لأ **وليه**، مش بس «تم».
 *
 * الأصل غير المربوط ما بيتلمسش: سعره اليدوي قرار المستخدم،
 * والملف ما يدهسوش.
 */

export interface SyncOutcome {
  updated: { assetName: string; symbol: string; asOf: string }[]
  /** أصول مربوطة برمز مش موجود في الملف — الاسم والسبب. */
  skipped: { assetName: string; reason: string }[]
  /** أصول بلا ربط أصلًا — سعرها يدوي بإرادة المستخدم. */
  manualCount: number
}

export interface SyncAssetPricesDeps {
  assets: AssetRepository
  prices: AssetPriceRepository
}

export function makeSyncAssetPrices(deps: SyncAssetPricesDeps) {
  return async function syncAssetPrices(feed: PriceFeed): Promise<SyncOutcome> {
    const bySymbol = indexFeed(feed)
    const assets = await deps.assets.listAll()

    const outcome: SyncOutcome = { updated: [], skipped: [], manualCount: 0 }

    for (const asset of assets) {
      if (asset.archived) continue

      if (!asset.feedSymbol) {
        outcome.manualCount += 1
        continue
      }

      const feedPrice = bySymbol.get(asset.feedSymbol)
      if (!feedPrice) {
        outcome.skipped.push({
          assetName: asset.name,
          reason: `الرمز «${asset.feedSymbol}» مش موجود في ملف الأسعار النهارده`,
        })
        continue
      }

      const price: AssetPrice = {
        assetId: asset.id,
        pricePerUnitMinor: feedPrice.pricePerUnitMinor,
        asOf: feedPrice.asOf,
        source: 'feed',
      }
      await deps.prices.save(price)
      outcome.updated.push({
        assetName: asset.name,
        symbol: feedPrice.symbol,
        asOf: feedPrice.asOf,
      })
    }

    return outcome
  }
}
