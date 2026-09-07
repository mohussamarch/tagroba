import { describe, expect, it } from 'vitest'
import {
  AssetError,
  PRICE_STALE_AFTER_DAYS,
  assessPrice,
  computePortfolioTotals,
  computePosition,
  describePriceState,
  formatAssetValue,
} from '../../src/domain/assets'
import { formatQuantity, parseQuantity } from '../../src/domain/quantity'
import type { AssetLot, AssetPrice, AssetSale } from '../../src/domain/entities/assets'

const lot = (o: Partial<AssetLot> & Pick<AssetLot, 'quantity' | 'principalMinor'>): AssetLot => ({
  id: o.id ?? 'lot1',
  assetId: 'gold',
  purchasedAt: o.purchasedAt ?? '2025-01-10',
  feeMinor: o.feeMinor ?? 0,
  ...o,
})

const sale = (
  o: Partial<AssetSale> & Pick<AssetSale, 'quantity' | 'grossProceedsMinor'>,
): AssetSale => ({
  id: o.id ?? 'sale1',
  assetId: 'gold',
  soldAt: o.soldAt ?? '2025-06-10',
  feeMinor: o.feeMinor ?? 0,
  ...o,
})

describe('spec/06 — بيع أصل جزئي', () => {
  it('الكمية والتكلفة المتبقية والربح المحقق صحيحة', () => {
    // شراء 10 جرام بـ 3,000.00 ريال ⇒ 300.00 للجرام
    // بيع 4 جرام بـ 1,400.00 ⇒ تكلفة المباع 1,200.00 ⇒ ربح محقق 200.00
    const position = computePosition(
      'gold',
      [lot({ quantity: parseQuantity('10'), principalMinor: 300000 })],
      [sale({ quantity: parseQuantity('4'), grossProceedsMinor: 140000 })],
    )

    expect(formatQuantity(position.heldQuantity)).toBe('6')
    expect(position.costBasisMinor).toBe(180000) // 1,800.00 تكلفة الستة الباقية
    expect(position.realizedGainMinor).toBe(20000) // 200.00
  })

  it('لا دخل من كامل الحصيلة — الحصيلة مستقلة عن الربح', () => {
    const position = computePosition(
      'gold',
      [lot({ quantity: parseQuantity('10'), principalMinor: 300000 })],
      [sale({ quantity: parseQuantity('4'), grossProceedsMinor: 140000 })],
    )
    // 1,400.00 قُبضت، لكن الربح 200.00 فقط. الحصيلة رقم منفصل معروض،
    // وحقل الدخل في مجاميع الفترة لا يمسّه أي من الاتنين
    expect(position.grossProceedsMinor).toBe(140000)
    expect(position.realizedGainMinor).not.toBe(position.grossProceedsMinor)
  })

  it('الرسوم تدخل التكلفة عند الشراء وتخصم من الحصيلة عند البيع', () => {
    const position = computePosition(
      'gold',
      [lot({ quantity: parseQuantity('10'), principalMinor: 300000, feeMinor: 5000 })],
      [sale({ quantity: parseQuantity('10'), grossProceedsMinor: 340000, feeMinor: 3000 })],
    )
    // تكلفة 3,050.00 وصافي حصيلة 3,370.00 ⇒ ربح 320.00
    expect(position.realizedGainMinor).toBe(32000)
    expect(position.totalFeesMinor).toBe(8000)
    expect(position.heldQuantity).toBe(0)
    expect(position.costBasisMinor).toBe(0)
  })

  it('لا هلل يتسرب في التقريب مهما تكرر البيع الجزئي', () => {
    // 7 جرام بـ 1,000.00 — القسمة لا تنتهي
    const position = computePosition(
      'gold',
      [lot({ quantity: parseQuantity('7'), principalMinor: 100000 })],
      [
        sale({
          id: 's1',
          quantity: parseQuantity('1'),
          grossProceedsMinor: 15000,
          soldAt: '2025-03-01',
        }),
        sale({
          id: 's2',
          quantity: parseQuantity('1'),
          grossProceedsMinor: 15000,
          soldAt: '2025-04-01',
        }),
        sale({
          id: 's3',
          quantity: parseQuantity('1'),
          grossProceedsMinor: 15000,
          soldAt: '2025-05-01',
        }),
      ],
    )
    const costOfSold = 100000 - position.costBasisMinor
    // الربح المحقق = صافي الحصيلة − تكلفة المباع، بالهلل بالضبط
    expect(position.realizedGainMinor).toBe(45000 - costOfSold)
    expect(formatQuantity(position.heldQuantity)).toBe('4')
  })

  it('البيع بخسارة يظهر سالبًا لا صفرًا', () => {
    const position = computePosition(
      'gold',
      [lot({ quantity: parseQuantity('5'), principalMinor: 200000 })],
      [sale({ quantity: parseQuantity('5'), grossProceedsMinor: 150000 })],
    )
    expect(position.realizedGainMinor).toBe(-50000)
  })

  it('يرفض بيع كمية أكبر من المملوك', () => {
    expect(() =>
      computePosition(
        'gold',
        [lot({ quantity: parseQuantity('2'), principalMinor: 60000 })],
        [sale({ quantity: parseQuantity('3'), grossProceedsMinor: 90000 })],
      ),
    ).toThrow(AssetError)
  })

  it('يرفض بيعًا قبل الشراء ولو كان مجموع اليوم كافيًا', () => {
    expect(() =>
      computePosition(
        'gold',
        [lot({ quantity: parseQuantity('5'), principalMinor: 150000, purchasedAt: '2025-05-01' })],
        [sale({ quantity: parseQuantity('5'), grossProceedsMinor: 160000, soldAt: '2025-02-01' })],
      ),
    ).toThrow(AssetError)
  })
})

describe('السعر المفقود والقديم واضح — spec/01', () => {
  const price: AssetPrice = {
    assetId: 'gold',
    pricePerUnitMinor: 32000,
    asOf: '2025-06-01',
    source: 'manual',
  }
  const lots = [lot({ quantity: parseQuantity('10'), principalMinor: 300000 })]

  it('بلا سعر: القيمة غير متاحة، لا صفر', () => {
    const position = computePosition('gold', lots, [])
    expect(position.marketValueMinor).toBeNull()
    expect(position.unrealizedGainMinor).toBeNull()
    expect(formatAssetValue(position.marketValueMinor)).toBe('غير متاح')
    expect(describePriceState(position.priceState)).toContain('مفيش سعر')
  })

  it('سعر حديث: القيمة محسوبة والربح غير المحقق ظاهر', () => {
    const position = computePosition('gold', lots, [], price, '2025-06-03')
    expect(position.priceState.kind).toBe('fresh')
    expect(position.marketValueMinor).toBe(320000)
    expect(position.unrealizedGainMinor).toBe(20000)
  })

  it('سعر قديم: يُعرض بتاريخه وعمره لا يُخفى', () => {
    const position = computePosition('gold', lots, [], price, '2025-07-01')
    expect(position.priceState.kind).toBe('stale')
    const text = describePriceState(position.priceState)
    expect(text).toContain('2025-06-01')
    expect(text).toContain('30 يوم')
    // القيمة تتحسب بالسعر القديم لكن مع إعلان قدمه — لا تختفي ولا تُلفَّق
    expect(position.marketValueMinor).toBe(320000)
  })

  it('حد القدم معلن ومحترم', () => {
    expect(assessPrice(price, '2025-06-08').kind).toBe('fresh')
    expect(assessPrice(price, '2025-06-09').kind).toBe('stale')
    expect(PRICE_STALE_AFTER_DAYS).toBe(7)
  })

  it('سعر بتاريخ في المستقبل لا يُعتبر طازجًا', () => {
    expect(assessPrice(price, '2025-05-01').kind).toBe('stale')
  })

  it('بعد بيع كل الكمية القيمة صفر معلوم لا مجهول', () => {
    const position = computePosition('gold', lots, [
      sale({ quantity: parseQuantity('10'), grossProceedsMinor: 350000 }),
    ])
    expect(position.marketValueMinor).toBe(0)
  })
})

describe('مجاميع المحفظة', () => {
  const priced = computePosition(
    'gold',
    [lot({ quantity: parseQuantity('10'), principalMinor: 300000 })],
    [],
    { assetId: 'gold', pricePerUnitMinor: 32000, asOf: '2025-06-01', source: 'manual' },
    '2025-06-02',
  )
  const unpriced = computePosition('stock', [
    lot({ id: 'l2', assetId: 'stock', quantity: parseQuantity('50'), principalMinor: 500000 }),
  ])

  it('كل الأسعار موجودة ⇒ إجمالي كامل', () => {
    const totals = computePortfolioTotals([priced])
    expect(totals.marketValueMinor).toBe(320000)
    expect(totals.unrealizedGainMinor).toBe(20000)
    expect(totals.assetsWithoutPrice).toBe(0)
  })

  it('أصل واحد بلا سعر ⇒ الإجمالي غير متاح ويقول عددهم', () => {
    const totals = computePortfolioTotals([priced, unpriced])
    expect(totals.marketValueMinor).toBeNull()
    expect(totals.unrealizedGainMinor).toBeNull()
    expect(totals.assetsWithoutPrice).toBe(1)
    // التكلفة معروفة دائمًا لأنها مدفوعة فعلًا لا مقدَّرة
    expect(totals.costBasisMinor).toBe(800000)
  })
})
