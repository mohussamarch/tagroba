import { describe, expect, it } from 'vitest'
import { AssetsError, makeManageAssets } from '../../src/application/useCases/manageAssets'
import {
  MemoryAssetLotRepository,
  MemoryAssetPriceRepository,
  MemoryAssetRepository,
  MemoryAssetSaleRepository,
} from '../../src/infrastructure/memory/memoryAssetRepositories'
import { FixedClock, SequentialIdGenerator } from '../../src/infrastructure/memory/memoryRepositories'
import { formatQuantity, parseQuantity } from '../../src/domain/quantity'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'

/**
 * الاستثمار عبر التخزين لا في المنطق وحده.
 *
 * الحساب مختبَر في `assets.test.ts`. هنا نتأكد أن الربط بالتخزين
 * يحفظ نفس القواعد: البيع تسجيل فقط، لا بيع لأكتر من المملوك،
 * والحصيلة لا تُكتب أبدًا كدخل.
 */

function build() {
  const deps = {
    assets: new MemoryAssetRepository(),
    lots: new MemoryAssetLotRepository(),
    sales: new MemoryAssetSaleRepository(),
    prices: new MemoryAssetPriceRepository(),
    ids: new SequentialIdGenerator(),
    clock: new FixedClock('2025-09-07T10:00:00.000Z'),
  }
  return { deps, useCase: makeManageAssets(deps) }
}

describe('إضافة أصل', () => {
  it('يأخذ وحدة القياس الافتراضية لنوعه', async () => {
    const { useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب عيار 21', kind: 'gold' })
    expect(gold.unitLabel).toBe('جرام')
    expect(gold.currency).toBe('SAR')
    expect(gold.archived).toBe(false)
  })

  it('يرفض الاسم الفاضي والمكرر', async () => {
    const { useCase } = build()
    await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    await expect(useCase.addAsset({ name: '  ', kind: 'gold' })).rejects.toThrow(AssetsError)
    await expect(useCase.addAsset({ name: 'ذهب', kind: 'gold' })).rejects.toThrow(AssetsError)
  })

  it('الأرشفة لا الحذف', async () => {
    const { deps, useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    await useCase.archiveAsset(gold.id, true)
    const all = await deps.assets.listAll()
    expect(all).toHaveLength(1)
    expect(all[0].archived).toBe(true)
  })
})

describe('شراء وبيع — البيع تسجيل فقط', () => {
  it('رحلة كاملة: شراء ثم بيع جزئي ثم عرض المحفظة', async () => {
    const { useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب عيار 21', kind: 'gold' })

    await useCase.recordPurchase({
      assetId: gold.id,
      purchasedAt: '2025-01-15',
      quantity: parseQuantity('20'),
      principalMinor: parseMoney('6000.00'),
      feeMinor: parseMoney('50.00'),
    })
    await useCase.recordSale({
      assetId: gold.id,
      soldAt: '2025-06-20',
      quantity: parseQuantity('8'),
      grossProceedsMinor: parseMoney('2600.00'),
      feeMinor: parseMoney('20.00'),
    })
    await useCase.setPrice({
      assetId: gold.id,
      pricePerUnitMinor: parseMoney('330.00'),
      asOf: '2025-09-05',
    })

    const view = await useCase.listPortfolio('2025-09-07')
    const row = view.rows[0]

    expect(formatQuantity(row.position.heldQuantity)).toBe('12')
    // تكلفة 6,050.00 على 20 جرام ⇒ 2,420.00 لثمانية، والباقي 3,630.00
    expect(formatAmount(row.position.costBasisMinor)).toBe('3,630.00')
    // صافي حصيلة 2,580.00 − تكلفة 2,420.00 = 160.00 ربح محقق
    expect(formatAmount(row.position.realizedGainMinor)).toBe('160.00')
    // 12 × 330.00 = 3,960.00 ⇒ ربح غير محقق 330.00
    expect(formatAmount(row.position.marketValueMinor!)).toBe('3,960.00')
    expect(formatAmount(row.position.unrealizedGainMinor!)).toBe('330.00')
    expect(row.position.priceState.kind).toBe('fresh')
  })

  it('لا يكتب أي عملية — البيع تسجيل فقط، والحصيلة مش دخل', async () => {
    const { deps, useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    await useCase.recordPurchase({
      assetId: gold.id,
      purchasedAt: '2025-01-15',
      quantity: parseQuantity('10'),
      principalMinor: parseMoney('3000.00'),
    })
    const { position } = await useCase.recordSale({
      assetId: gold.id,
      soldAt: '2025-06-20',
      quantity: parseQuantity('10'),
      grossProceedsMinor: parseMoney('3500.00'),
    })

    // مفيش أي جسر تلقائي للعمليات: الاستثمار ما بيكتبش دخلًا ولا مصروفًا
    expect(Object.keys(deps)).not.toContain('txns')
    // الحصيلة 3,500.00 مسجلة، والربح 500.00 — والاتنين برّه مجاميع الفترة
    expect(formatAmount(position.grossProceedsMinor)).toBe('3,500.00')
    expect(formatAmount(position.realizedGainMinor)).toBe('500.00')
  })

  it('البيع الزائد يُرفض قبل أي كتابة — لا سجل يتيم', async () => {
    const { deps, useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    await useCase.recordPurchase({
      assetId: gold.id,
      purchasedAt: '2025-01-15',
      quantity: parseQuantity('5'),
      principalMinor: parseMoney('1500.00'),
    })

    await expect(
      useCase.recordSale({
        assetId: gold.id,
        soldAt: '2025-02-01',
        quantity: parseQuantity('6'),
        grossProceedsMinor: parseMoney('1900.00'),
      }),
    ).rejects.toThrow()

    // ولا سجل بيع اتكتب
    expect(await deps.sales.listAll()).toHaveLength(0)
  })

  it('يرفض المدخلات المستحيلة', async () => {
    const { useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    const base = { assetId: gold.id, purchasedAt: '2025-01-15' }

    await expect(
      useCase.recordPurchase({ ...base, quantity: 0, principalMinor: 1000 }),
    ).rejects.toThrow(AssetsError)
    await expect(
      useCase.recordPurchase({ ...base, quantity: parseQuantity('1'), principalMinor: -1 }),
    ).rejects.toThrow(AssetsError)
    await expect(
      useCase.recordPurchase({
        ...base,
        purchasedAt: '2025-02-30',
        quantity: parseQuantity('1'),
        principalMinor: 1000,
      }),
    ).rejects.toThrow(AssetsError)
    await expect(
      useCase.recordPurchase({
        assetId: 'مش-موجود',
        purchasedAt: '2025-01-15',
        quantity: parseQuantity('1'),
        principalMinor: 1000,
      }),
    ).rejects.toThrow(AssetsError)
    await expect(
      useCase.recordSale({
        assetId: gold.id,
        soldAt: '2025-01-16',
        quantity: parseQuantity('1'),
        grossProceedsMinor: parseMoney('100.00'),
        feeMinor: parseMoney('200.00'),
      }),
    ).rejects.toThrow(AssetsError)
  })
})

describe('السعر في المحفظة', () => {
  it('أصل بلا سعر يخلي إجمالي المحفظة غير متاح ويقول عددهم', async () => {
    const { useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    const stock = await useCase.addAsset({ name: 'أرامكو', kind: 'stock' })

    for (const id of [gold.id, stock.id]) {
      await useCase.recordPurchase({
        assetId: id,
        purchasedAt: '2025-01-15',
        quantity: parseQuantity('10'),
        principalMinor: parseMoney('1000.00'),
      })
    }
    await useCase.setPrice({
      assetId: gold.id,
      pricePerUnitMinor: parseMoney('120.00'),
      asOf: '2025-09-06',
    })

    const view = await useCase.listPortfolio('2025-09-07')
    expect(view.totals.marketValueMinor).toBeNull()
    expect(view.totals.assetsWithoutPrice).toBe(1)
    // التكلفة معروفة دائمًا لأنها مدفوعة فعلًا
    expect(formatAmount(view.totals.costBasisMinor)).toBe('2,000.00')
  })

  it('السعر بلا تاريخ ياخد تاريخ النهارده من الساعة الممرَّرة', async () => {
    const { useCase } = build()
    const gold = await useCase.addAsset({ name: 'ذهب', kind: 'gold' })
    const price = await useCase.setPrice({ assetId: gold.id, pricePerUnitMinor: 12000 })
    expect(price.asOf).toBe('2025-09-07')
    expect(price.source).toBe('manual')
  })
})
