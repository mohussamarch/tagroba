import { addQuantity, formatQuantity, parseQuantity, shareOfAmount, subtractQuantity, unitPriceOf, valueOfQuantity } from '../../src/domain/quantity'
import { assessPrice, computePortfolioTotals, computePosition, describePriceState, formatAssetValue } from '../../src/domain/assets'
import { describeFeed, parsePriceFeed } from '../../src/domain/priceFeed'
import type { AssetLot, AssetPrice, AssetSale } from '../../src/domain/entities/assets'
import { record, seeded } from './goldenKit'

/** الكميات والاستثمار وملف الأسعار — أرقام وهمية، ومنها أرقام أكبر من 64 بت في خطوة الضرب. */
export function investGolden() {
  const rnd = seeded(5150)
  const MAX = Number.MAX_SAFE_INTEGER
  const quantities = ['1', '0.5', '١٫٢٥', '1,234.5', '0.00000001', '0.000000001', '1.2.3', '', '  ', '-3', '+2', '.', '-', 'abc', '12a', '90071992.54740991', '90071992.54740992', '1'.repeat(400), '٠٫١']
  const bigPairs: [number, number][] = [[1, 1], [100_000_000, 150_000_000], [MAX, 1], [123_456_789, 987_654_321], [-5, 7], [5, -7], [0, 99], [99, 0], [-3, -3]]
  for (let i = 0; i < 40; i++) bigPairs.push([rnd.int(-(2 ** 40), 2 ** 40), rnd.int(-(2 ** 30), 2 ** 30)])
  const today = '2026-09-19'
  const price = (asOf: string, v = rnd.int(1, 5_000_00)): AssetPrice => ({ assetId: 'a', pricePerUnitMinor: v, asOf, source: 'manual' })
  const scenarios = Array.from({ length: 30 }, (_, s) => {
    const lots: AssetLot[] = Array.from({ length: rnd.int(0, 4) }, (_, i) => ({ id: `l${i}`, assetId: 'a', purchasedAt: `2026-0${rnd.int(1, 9)}-1${rnd.int(0, 9)}`, quantity: rnd.int(1, 5) * rnd.pick([1, 100_000_000, 12_345_678]), principalMinor: rnd.int(100, 900_000), feeMinor: rnd.int(0, 500) }))
    const sales: AssetSale[] = Array.from({ length: rnd.int(0, 2) }, (_, i) => ({ id: `s${i}`, assetId: 'a', soldAt: `2026-0${rnd.int(1, 9)}-1${rnd.int(0, 9)}`, quantity: rnd.int(1, 3) * rnd.pick([1, 50_000_000]), grossProceedsMinor: rnd.int(100, 900_000), feeMinor: rnd.int(0, 300) }))
    const p = s % 4 === 0 ? undefined : price(rnd.pick(['2026-09-19', '2026-09-12', '2026-09-11', '2026-10-01', '2025-01-01']))
    return { lots, sales, price: p, today: s % 7 === 0 ? undefined : today }
  })
  const feeds: unknown[] = [null, [], {}, { generatedAt: ' ' }, { generatedAt: '2026-09-19T00:00:00Z' }, { generatedAt: '2026-09-19T00:00:00Z', baseCurrency: 'SAR' },
    { generatedAt: '2026-09-19T00:00:00Z', baseCurrency: 'SAR', prices: [] },
    { generatedAt: '2026-09-19T06:00:00Z', baseCurrency: 'SAR', prices: {
      gold: { name: ' ذهب ', unit: 'جرام', pricePerUnitMinor: 39_500, asOf: '2026-09-19', source: 'feed-x' }, silver: { pricePerUnitMinor: 12.5, asOf: '2026-09-19', source: 's' },
      btc: { pricePerUnitMinor: 0, asOf: '2026-09-19', source: 's' }, eth: { pricePerUnitMinor: 1000, asOf: '2026-02-30', source: 's' }, sp: { pricePerUnitMinor: 5, asOf: '2026-09-19', source: '  ' },
      bad: 'x', arr: [1], noName: { pricePerUnitMinor: 7.0, asOf: '2026-09-18', source: 'y' },
    }, failures: [{ source: 'yahoo', reason: 'timeout' }, 'x', {}, { reason: ' ' }] },
  ]

  return {
    parseQuantity: quantities.map((q) => record(q, () => parseQuantity(q))),
    formatQuantity: [0, 1, 100_000_000, 150_000_000, 12_345_678, -250_000_000, 100_000_001, MAX].map((q) => record(q, () => formatQuantity(q))),
    addSubtractQuantity: [[1, 2], [MAX, 1], [-MAX, 0]].map(([a, b]) => record([a, b], () => [subtractQuantity(a!, b!), addQuantity(a!, b!)])),
    valueOfQuantity: bigPairs.map(([q, p]) => record({ quantity: q, price: p }, () => valueOfQuantity(q, p))),
    shareOfAmount: bigPairs.map(([a, q]) => record({ amount: a, part: Math.abs(q) % 1000, whole: 1000 }, () => shareOfAmount(a, Math.abs(q) % 1000, 1000)))
      .concat([[100, 3, 2], [100, -1, 2], [100, 1, 0], [MAX, 1, 3], [MAX, 2, 3], [999_999, 12_345_678, 100_000_000]].map(([a, p, w]) => record({ amount: a, part: p, whole: w }, () => shareOfAmount(a!, p!, w!)))),
    unitPriceOf: [[100, 0], [100, 50_000_000], [MAX, 1], [12_345, 100_000_000], [-5, 3]].map(([a, q]) => record({ amount: a, quantity: q }, () => unitPriceOf(a!, q!))),
    computePosition: scenarios.map((s) => record(s, () => {
      const p = computePosition('a', s.lots, s.sales, s.price, s.today)
      return { ...p, priceDescription: describePriceState(p.priceState), valueText: formatAssetValue(p.marketValueMinor) }
    })),
    assessPrice: [undefined, price('2026-09-19', 5), price('2026-09-12', 5), price('2026-09-11', 5), price('2026-10-01', 5)].flatMap((p) => [today, undefined].map((t) => record({ price: p ?? null, today: t ?? null }, () => {
      const state = assessPrice(p, t)
      return { state, description: describePriceState(state) }
    }))),
    computePortfolioTotals: Array.from({ length: 10 }, () => scenarios.filter(() => rnd.next() < 0.3)).map((picked) => record(picked, () => {
      const positions = picked.flatMap((s) => { try { return [computePosition('a', s.lots, s.sales, s.price, s.today)] } catch { return [] } })
      return computePortfolioTotals(positions)
    })),
    parsePriceFeed: feeds.map((f) => record(f, () => { const feed = parsePriceFeed(f); return { feed, description: describeFeed(feed) } })),
  }
}
