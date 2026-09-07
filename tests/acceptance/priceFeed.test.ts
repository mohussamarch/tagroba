import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { describeFeed, parsePriceFeed, PriceFeedError } from '../../src/domain/priceFeed'
import { makeSyncAssetPrices } from '../../src/application/useCases/syncAssetPrices'
import { makeManageAssets } from '../../src/application/useCases/manageAssets'
import {
  MemoryAssetLotRepository,
  MemoryAssetPriceRepository,
  MemoryAssetRepository,
  MemoryAssetSaleRepository,
} from '../../src/infrastructure/memory/memoryAssetRepositories'
import {
  FixedClock,
  SequentialIdGenerator,
} from '../../src/infrastructure/memory/memoryRepositories'
import { parseQuantity } from '../../src/domain/quantity'
import { formatAmount } from '../../src/domain/formatMoney'

/**
 * ملف الأسعار: القراءة والتحقق والربط.
 *
 * القاعدة المختبَرة (CLAUDE.md #10): **لا رقم بلا مصدر.**
 * أي مدخل ناقص يُرفض ويُسجَّل سببه، ولا يتحول لصفر ولا لتخمين.
 */

const good = {
  generatedAt: '2026-09-07T11:00:00.000Z',
  baseCurrency: 'SAR',
  prices: {
    GOLD_21K_GRAM: {
      name: 'ذهب عيار 21',
      unit: 'جرام',
      pricePerUnitMinor: 46414,
      asOf: '2026-09-07',
      source: 'gold-api.com',
    },
  },
  failures: [],
}

describe('قراءة ملف الأسعار', () => {
  it('يقرا ملفًا سليمًا', () => {
    const feed = parsePriceFeed(good)
    expect(feed.prices).toHaveLength(1)
    expect(feed.prices[0].symbol).toBe('GOLD_21K_GRAM')
    expect(feed.rejected).toHaveLength(0)
  })

  it('يرفض الملف كله لو شكله غلط أصلًا', () => {
    expect(() => parsePriceFeed(null)).toThrow(PriceFeedError)
    expect(() => parsePriceFeed({ prices: {} })).toThrow(PriceFeedError)
    expect(() => parsePriceFeed({ ...good, prices: undefined })).toThrow(PriceFeedError)
  })

  it('يرفض المدخل الناقص وحده ويكمل الباقي، وبسبب مكتوب', () => {
    const feed = parsePriceFeed({
      ...good,
      prices: {
        ...good.prices,
        NO_SOURCE: { name: 'بلا مصدر', pricePerUnitMinor: 100, asOf: '2026-09-07' },
        NO_DATE: { name: 'بلا تاريخ', pricePerUnitMinor: 100, source: 'x' },
        BAD_DATE: { pricePerUnitMinor: 100, asOf: '2026-02-30', source: 'x' },
        DECIMAL: { pricePerUnitMinor: 100.5, asOf: '2026-09-07', source: 'x' },
        ZERO: { pricePerUnitMinor: 0, asOf: '2026-09-07', source: 'x' },
      },
    })

    expect(feed.prices).toHaveLength(1)
    expect(feed.rejected.map((r) => r.symbol).sort()).toEqual([
      'BAD_DATE',
      'DECIMAL',
      'NO_DATE',
      'NO_SOURCE',
      'ZERO',
    ])
    // كل رفض معاه سبب مكتوب لا كود ولا فراغ
    for (const r of feed.rejected) expect(r.reason.length).toBeGreaterThan(5)
  })

  it('الوصف بيقول التاريخ وعدد اللي مجاش', () => {
    const feed = parsePriceFeed({
      ...good,
      failures: [{ source: 'الفضة', reason: 'الخدمة مردتش' }],
    })
    const text = describeFeed(feed)
    expect(text).toContain('2026-09-07')
    expect(text).toContain('1 مصدر مجابش سعر')
  })
})

describe('الملف الحقيقي المولَّد', () => {
  it('يعدّي التحقق كله بلا رفض واحد', () => {
    const raw = JSON.parse(readFileSync('public/prices.json', 'utf8'))
    const feed = parsePriceFeed(raw)
    expect(feed.baseCurrency).toBe('SAR')
    expect(feed.prices.length).toBeGreaterThan(0)
    expect(feed.rejected).toEqual([])
    // كل سعر عدد صحيح موجب بالهللة ومعاه مصدره
    for (const price of feed.prices) {
      expect(Number.isInteger(price.pricePerUnitMinor)).toBe(true)
      expect(price.pricePerUnitMinor).toBeGreaterThan(0)
      expect(price.source).not.toBe('')
    }
  })
})

describe('ربط الأصول بالأسعار', () => {
  function build() {
    const deps = {
      assets: new MemoryAssetRepository(),
      lots: new MemoryAssetLotRepository(),
      sales: new MemoryAssetSaleRepository(),
      prices: new MemoryAssetPriceRepository(),
      ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-09-07T10:00:00.000Z'),
    }
    return {
      deps,
      assets: makeManageAssets(deps),
      sync: makeSyncAssetPrices({ assets: deps.assets, prices: deps.prices }),
    }
  }

  it('المربوط بياخد السعر، وغير المربوط ما بيتلمسش', async () => {
    const { assets, sync } = build()
    const gold = await assets.addAsset({ name: 'ذهب', kind: 'gold' })
    await assets.addAsset({ name: 'صندوق خاص', kind: 'fund' })
    await assets.linkToFeed(gold.id, 'GOLD_21K_GRAM')
    // سعر يدوي للصندوق — الملف مالوش حق يدهسه
    const fundId = (await assets.listPortfolio('2026-09-07')).rows.find(
      (r) => r.asset.name === 'صندوق خاص',
    )!.asset.id
    await assets.setPrice({ assetId: fundId, pricePerUnitMinor: 5000, asOf: '2026-09-01' })

    const outcome = await sync(parsePriceFeed(good))

    expect(outcome.updated).toHaveLength(1)
    expect(outcome.updated[0].assetName).toBe('ذهب')
    expect(outcome.manualCount).toBe(1)

    const view = await assets.listPortfolio('2026-09-07')
    const goldRow = view.rows.find((r) => r.asset.name === 'ذهب')!
    const fundRow = view.rows.find((r) => r.asset.name === 'صندوق خاص')!
    expect(goldRow.position.priceState.kind).toBe('fresh')
    // السعر اليدوي زي ما هو بتاريخه ومصدره — الملف مدهسوش
    const fundPrice = fundRow.position.priceState
    expect(fundPrice.kind).not.toBe('missing')
    if (fundPrice.kind !== 'missing') {
      expect(fundPrice.price.pricePerUnitMinor).toBe(5000)
      expect(fundPrice.price.asOf).toBe('2026-09-01')
      expect(fundPrice.price.source).toBe('manual')
    }
  })

  it('رمز مش في الملف: بيتسجّل السبب ولا بيتحط سعر', async () => {
    const { assets, sync } = build()
    const stock = await assets.addAsset({ name: 'سهم نادر', kind: 'stock' })
    await assets.linkToFeed(stock.id, '9999.SR')
    await assets.recordPurchase({
      assetId: stock.id,
      purchasedAt: '2026-01-10',
      quantity: parseQuantity('10'),
      principalMinor: 100000,
    })

    const outcome = await sync(parsePriceFeed(good))
    expect(outcome.updated).toHaveLength(0)
    expect(outcome.skipped[0].assetName).toBe('سهم نادر')
    expect(outcome.skipped[0].reason).toContain('9999.SR')

    const view = await assets.listPortfolio('2026-09-07')
    expect(view.rows[0].position.priceState.kind).toBe('missing')
    expect(view.rows[0].position.marketValueMinor).toBeNull()
  })

  it('السعر من الملف بيتحسب صح على الكمية', async () => {
    const { assets, sync } = build()
    const gold = await assets.addAsset({ name: 'ذهب', kind: 'gold' })
    await assets.linkToFeed(gold.id, 'GOLD_21K_GRAM')
    await assets.recordPurchase({
      assetId: gold.id,
      purchasedAt: '2026-01-10',
      quantity: parseQuantity('12.5'),
      principalMinor: 500000,
    })
    await sync(parsePriceFeed(good))

    const view = await assets.listPortfolio('2026-09-07')
    // 12.5 × 464.14 = 5,801.75
    expect(formatAmount(view.rows[0].position.marketValueMinor!)).toBe('5,801.75')
  })

  it('فكّ الربط ما بيمسحش آخر سعر', async () => {
    const { assets, sync } = build()
    const gold = await assets.addAsset({ name: 'ذهب', kind: 'gold' })
    await assets.linkToFeed(gold.id, 'GOLD_21K_GRAM')
    await sync(parsePriceFeed(good))
    await assets.linkToFeed(gold.id, null)

    const view = await assets.listPortfolio('2026-09-07')
    expect(view.rows[0].asset.feedSymbol).toBeUndefined()
    expect(view.rows[0].position.priceState.kind).toBe('fresh')
  })

  it('المؤرشف ما بيتحدّثش', async () => {
    const { assets, sync } = build()
    const gold = await assets.addAsset({ name: 'ذهب', kind: 'gold' })
    await assets.linkToFeed(gold.id, 'GOLD_21K_GRAM')
    await assets.archiveAsset(gold.id, true)

    const outcome = await sync(parsePriceFeed(good))
    expect(outcome.updated).toHaveLength(0)
    expect(outcome.manualCount).toBe(0)
  })
})
