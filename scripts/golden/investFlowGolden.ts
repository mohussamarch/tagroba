import { makeManageAssets } from '../../src/application/useCases/manageAssets'
import { makeSyncAssetPrices } from '../../src/application/useCases/syncAssetPrices'
import {
  MemoryAssetLotRepository,
  MemoryAssetPriceRepository,
  MemoryAssetRepository,
  MemoryAssetSaleRepository,
} from '../../src/infrastructure/memory/memoryAssetRepositories'
import { FixedClock, SequentialIdGenerator } from '../../src/infrastructure/memory/memorySupport'
import { parsePriceFeed } from '../../src/domain/priceFeed'
import type { Asset, AssetLot, AssetPrice, AssetSale } from '../../src/domain/entities/assets'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * الاستثمار: إدارة الأصول وتحديث الأسعار من الملف. ⚠️ بيانات وهمية بالكامل.
 * حسابات المركز نفسها متغطية في `invest.json`؛ هنا فحص المدخلات والترتيب والتخزين.
 * «البيع تسجيل بس» (spec/01) والحصيلة **مش دخل** (spec/02).
 */

const NOW = '2026-09-22T10:00:00.000Z'
const Q = 100_000_000 // كمية 1 بالمقياس

interface Seed { assets: Asset[]; lots: AssetLot[]; sales: AssetSale[]; prices: AssetPrice[] }

const seed: Seed = {
  assets: [
    { id: 'as-gold', name: 'ذهب عيار 24', kind: 'gold', unitLabel: 'جرام', currency: 'SAR', archived: false, feedSymbol: 'XAU24' },
    { id: 'as-fund', name: 'صندوق أسهم', kind: 'fund', unitLabel: 'وحدة', currency: 'SAR', archived: false, note: 'اشتراك شهري' },
    { id: 'as-old', name: 'سهم قديم', kind: 'stock', unitLabel: 'سهم', currency: 'SAR', archived: true, feedSymbol: 'OLD' },
    { id: 'as-coin', name: 'عملة رقمية', kind: 'digital', unitLabel: 'وحدة', currency: 'SAR', archived: false, feedSymbol: 'COIN' },
  ],
  lots: [
    { id: 'lot-g1', assetId: 'as-gold', purchasedAt: '2026-03-10', quantity: 10 * Q, principalMinor: 250_000, feeMinor: 1_500 },
    { id: 'lot-g2', assetId: 'as-gold', purchasedAt: '2026-06-01', quantity: 5 * Q, principalMinor: 140_000, feeMinor: 0, transactionId: 't-gold' },
    { id: 'lot-f1', assetId: 'as-fund', purchasedAt: '2026-01-15', quantity: 12_345_678, principalMinor: 90_000, feeMinor: 250 },
    { id: 'lot-o1', assetId: 'as-old', purchasedAt: '2025-11-01', quantity: 3 * Q, principalMinor: 60_000, feeMinor: 100 },
  ],
  sales: [
    { id: 'sale-g1', assetId: 'as-gold', soldAt: '2026-07-01', quantity: 4 * Q, grossProceedsMinor: 120_000, feeMinor: 500 },
    { id: 'sale-o1', assetId: 'as-old', soldAt: '2026-02-01', quantity: 3 * Q, grossProceedsMinor: 75_000, feeMinor: 200 },
  ],
  prices: [
    { assetId: 'as-gold', pricePerUnitMinor: 28_500, asOf: '2026-09-20', source: 'feed' },
    { assetId: 'as-fund', pricePerUnitMinor: 800_000, asOf: '2026-08-01', source: 'manual' },
  ],
}

type Action =
  | { kind: 'list'; today?: string }
  | { kind: 'addAsset'; input: { name: string; kind: Asset['kind']; unitLabel?: string; feedSymbol?: string; note?: string } }
  | { kind: 'linkToFeed'; assetId: string; feedSymbol: string | null }
  | { kind: 'archive'; assetId: string; archived: boolean }
  | { kind: 'purchase'; input: { assetId: string; purchasedAt: string; quantity: number; principalMinor: number; feeMinor?: number; transactionId?: string } }
  | { kind: 'sale'; input: { assetId: string; soldAt: string; quantity: number; grossProceedsMinor: number; feeMinor?: number; transactionId?: string } }
  | { kind: 'setPrice'; input: { assetId: string; pricePerUnitMinor: number; asOf?: string } }

function repos(s: Seed) {
  return {
    assets: new MemoryAssetRepository(s.assets),
    lots: new MemoryAssetLotRepository(s.lots),
    sales: new MemoryAssetSaleRepository(s.sales),
    prices: new MemoryAssetPriceRepository(s.prices),
  }
}

async function manageAssetsCases() {
  const cases: GoldenCase[] = []
  async function run(action: Action) {
    cases.push(await recordAsync({ seed, action }, async () => {
      const r = repos(seed)
      const manage = makeManageAssets({ ...r, ids: new SequentialIdGenerator(), clock: new FixedClock(NOW) })
      const stored = async () => ({
        storedAssets: await r.assets.listAll(),
        storedLots: await r.lots.listAll(),
        storedSales: await r.sales.listAll(),
        storedPrices: await r.prices.listAll(),
      })
      switch (action.kind) {
        case 'list': {
          const view = await manage.listPortfolio(action.today)
          return {
            rows: view.rows.map((row) => ({
              assetId: row.asset.id,
              lotIds: row.lots.map((l) => l.id),
              saleIds: row.sales.map((s) => s.id),
              position: row.position,
            })),
            totals: view.totals,
          }
        }
        case 'addAsset': return { asset: await manage.addAsset(action.input), ...(await stored()) }
        case 'linkToFeed': return { asset: await manage.linkToFeed(action.assetId, action.feedSymbol), ...(await stored()) }
        case 'archive': await manage.archiveAsset(action.assetId, action.archived); return await stored()
        case 'purchase': return { lot: await manage.recordPurchase(action.input), ...(await stored()) }
        case 'sale': {
          const result = await manage.recordSale(action.input)
          return { sale: result.sale, position: result.position, ...(await stored()) }
        }
        case 'setPrice': return { price: await manage.setPrice(action.input), ...(await stored()) }
      }
    }))
  }

  // المحفظة: بتاريخ صريح (سعر الذهب طازة، والصندوق قديم) · من غير تاريخ ⇒ تاريخ الساعة
  await run({ kind: 'list', today: '2026-09-22' })
  await run({ kind: 'list' })
  await run({ kind: 'list', today: '2026-10-15' })

  await run({ kind: 'addAsset', input: { name: ' فضة ', kind: 'gold' } })
  await run({ kind: 'addAsset', input: { name: 'سهم أرامكو', kind: 'stock', unitLabel: '  سهم عادي ', feedSymbol: '2222', note: '  طويل الأجل  ' } })
  await run({ kind: 'addAsset', input: { name: 'صندوق ذهب', kind: 'fund', unitLabel: '   ', feedSymbol: '', note: '   ' } })
  await run({ kind: 'addAsset', input: { name: '   ', kind: 'other' } })
  await run({ kind: 'addAsset', input: { name: 'م'.repeat(81), kind: 'other' } })
  await run({ kind: 'addAsset', input: { name: ' صندوق أسهم ', kind: 'fund' } })

  await run({ kind: 'linkToFeed', assetId: 'as-fund', feedSymbol: 'FUND1' })
  // فك الربط ما بيمسحش آخر سعر
  await run({ kind: 'linkToFeed', assetId: 'as-gold', feedSymbol: null })
  await run({ kind: 'linkToFeed', assetId: 'as-ghost', feedSymbol: 'X' })
  await run({ kind: 'archive', assetId: 'as-coin', archived: true })
  await run({ kind: 'archive', assetId: 'as-ghost', archived: true })

  await run({ kind: 'purchase', input: { assetId: 'as-coin', purchasedAt: '2026-09-01', quantity: 150_000_000, principalMinor: 30_000, feeMinor: 120, transactionId: 't-coin' } })
  await run({ kind: 'purchase', input: { assetId: 'as-gold', purchasedAt: '2026-09-15', quantity: 2 * Q, principalMinor: 56_000 } })
  await run({ kind: 'purchase', input: { assetId: 'as-gold', purchasedAt: '2026-02-30', quantity: Q, principalMinor: 100 } })
  await run({ kind: 'purchase', input: { assetId: 'as-gold', purchasedAt: '2026-09-15', quantity: 0, principalMinor: 100 } })
  await run({ kind: 'purchase', input: { assetId: 'as-gold', purchasedAt: '2026-09-15', quantity: Q, principalMinor: 0 } })
  await run({ kind: 'purchase', input: { assetId: 'as-gold', purchasedAt: '2026-09-15', quantity: Q, principalMinor: 100, feeMinor: -1 } })
  await run({ kind: 'purchase', input: { assetId: 'as-ghost', purchasedAt: '2026-09-15', quantity: Q, principalMinor: 100 } })

  // البيع: جزئي · أكتر من المملوك · قبل أي شراء · رسوم أكبر من الحصيلة · كمية سالبة · حصيلة صفر
  await run({ kind: 'sale', input: { assetId: 'as-gold', soldAt: '2026-09-21', quantity: 3 * Q, grossProceedsMinor: 90_000, feeMinor: 300, transactionId: 't-sold' } })
  await run({ kind: 'sale', input: { assetId: 'as-gold', soldAt: '2026-09-21', quantity: 12 * Q, grossProceedsMinor: 90_000 } })
  await run({ kind: 'sale', input: { assetId: 'as-coin', soldAt: '2026-09-21', quantity: Q, grossProceedsMinor: 10_000 } })
  await run({ kind: 'sale', input: { assetId: 'as-gold', soldAt: '2026-09-21', quantity: Q, grossProceedsMinor: 1_000, feeMinor: 1_001 } })
  await run({ kind: 'sale', input: { assetId: 'as-gold', soldAt: '2026-09-21', quantity: -Q, grossProceedsMinor: 1_000 } })
  await run({ kind: 'sale', input: { assetId: 'as-gold', soldAt: '2026-09-21', quantity: Q, grossProceedsMinor: 0 } })

  await run({ kind: 'setPrice', input: { assetId: 'as-fund', pricePerUnitMinor: 815_000, asOf: '2026-09-21' } })
  await run({ kind: 'setPrice', input: { assetId: 'as-coin', pricePerUnitMinor: 21_000 } })
  await run({ kind: 'setPrice', input: { assetId: 'as-coin', pricePerUnitMinor: 0 } })
  await run({ kind: 'setPrice', input: { assetId: 'as-coin', pricePerUnitMinor: 100, asOf: '22-09-2026' } })
  return cases
}

async function syncAssetPricesCases() {
  const cases: GoldenCase[] = []
  const feeds: unknown[] = [
    // الذهب موجود · العملة مش في الملف · القديم مؤرشف ما بيتلمسش · الصندوق يدوي
    {
      generatedAt: '2026-09-22T03:00:00Z', baseCurrency: 'SAR',
      prices: {
        XAU24: { name: 'ذهب 24', unit: 'جرام', pricePerUnitMinor: 29_100, asOf: '2026-09-22', source: 'test-source' },
        OLD: { pricePerUnitMinor: 99_999, asOf: '2026-09-22', source: 'test-source' },
        BAD: { pricePerUnitMinor: 12.5, asOf: '2026-09-22', source: 'test-source' },
      },
    },
    { generatedAt: '2026-09-22T03:00:00Z', baseCurrency: 'SAR', prices: {} },
    {
      generatedAt: '2026-09-22T03:00:00Z', baseCurrency: 'SAR',
      prices: {
        XAU24: { pricePerUnitMinor: 29_300, asOf: '2026-09-21', source: 'test-source' },
        COIN: { pricePerUnitMinor: 20_500, asOf: '2026-09-22', source: 'test-source' },
      },
    },
  ]
  for (const raw of feeds) {
    cases.push(await recordAsync({ seed, feed: raw }, async () => {
      const r = repos(seed)
      const outcome = await makeSyncAssetPrices({ assets: r.assets, prices: r.prices })(parsePriceFeed(raw))
      return { outcome, storedPrices: await r.prices.listAll() }
    }))
  }
  return cases
}

export async function investFlowGolden() {
  return { manageAssets: await manageAssetsCases(), syncAssetPrices: await syncAssetPricesCases() }
}
