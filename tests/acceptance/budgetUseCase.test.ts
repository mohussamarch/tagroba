import { describe, it, expect } from 'vitest'
import { makeLoadBudgetScreen } from '../../src/application/useCases/loadBudgetScreen'
import { makeSetBudget, BudgetError } from '../../src/application/useCases/setBudget'
import {
  MemoryAllocationRepository,
  MemoryBudgetRepository,
  MemoryCategoryRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  SequentialIdGenerator,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildPeriod } from '../../src/domain/period'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'
import type { Category, Transaction } from '../../src/domain/entities/types'
import type { EconomicKind } from '../../src/domain/entities/economicKind'

const CATEGORIES: Category[] = [
  { id: 'cat-food', parentId: null, name: 'مطاعم وقهوة', iconKey: 'u', lightColor: '#CB428E', darkColor: '#CB428E', active: true, order: 0 },
  { id: 'cat-shop', parentId: null, name: 'تسوق إلكتروني', iconKey: 'b', lightColor: '#8861CC', darkColor: '#8861CC', active: true, order: 1 },
]

const PERIOD = buildPeriod(2026, 9, 28) // 2026-09-28 → 2026-10-27
const TODAY = '2026-10-08'

let n = 0
function txn(
  amount: string,
  date: string,
  categoryId?: string,
  kind: EconomicKind = 'purchase',
): Transaction {
  n++
  return {
    id: `t${n}`,
    occurredAt: date,
    datePrecision: 'day',
    sourceOrder: n,
    economicKind: kind,
    economicKindConfirmed: kind !== 'unclassified',
    observedDirection: 'out',
    amountMinor: parseMoney(amount),
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'confirmed',
    isCashTagged: false,
    createdAt: '',
    updatedAt: '',
    ...(categoryId ? { categoryId } : {}),
  }
}

function makeSystem() {
  const txns = new MemoryTransactionRepository()
  const budgets = new MemoryBudgetRepository()
  const uow = new PassthroughUnitOfWork()
  return {
    txns,
    budgets,
    load: makeLoadBudgetScreen({
      txns,
      categories: new MemoryCategoryRepository(CATEGORIES),
      allocations: new MemoryAllocationRepository(),
      budgets,
    }),
    set: makeSetBudget({
      budgets,
      uow,
      ids: new SequentialIdGenerator(),
      clock: new FixedClock('2026-10-08T00:00:00.000Z'),
    }),
  }
}

describe('لا سقف بلا اختيار المستخدم — spec/01', () => {
  it('بلا ميزانية ⇒ لا حالة سقف ولا متاح يومي', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([txn('500.00', '2026-10-01', 'cat-food')])

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(data.budget).toBeNull()
    expect(data.totalStatus).toBeNull()
    expect(data.allowance.amountMinor).toBeNull()
    expect(formatAmount(data.spentMinor)).toBe('500.00') // المصروف معروف
  })

  it('**المتوسط لا يصير سقفًا أبدًا** حتى لو كان متاحًا', async () => {
    const sys = makeSystem()
    // ست فترات سابقة كاملة وموثوقة ⇒ متوسط متاح
    for (let i = 1; i <= 6; i++) {
      const p = buildPeriod(2026, 9 - i, 28)
      await sys.txns.saveMany([txn('1000.00', p.start, 'cat-food')])
    }
    await sys.txns.saveMany([txn('500.00', '2026-10-01', 'cat-food')])

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })

    expect(data.average.averageMinor).not.toBeNull() // المتوسط موجود
    expect(formatAmount(data.average.averageMinor!)).toBe('1,000.00')

    // ومع ذلك: لا سقف، ولا متاح يومي
    expect(data.totalStatus).toBeNull()
    expect(data.allowance.amountMinor).toBeNull()
    expect(data.allowance.reason).toContain('سقف تحدده انت')
  })

  it('السقف يُضبط بمبلغ يكتبه المستخدم فيظهر المتاح اليومي', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([txn('500.00', '2026-10-01', 'cat-food')])

    await sys.set.setTotalLimit(PERIOD, parseMoney('3000.00'), 80)
    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })

    expect(formatAmount(data.totalStatus!.limitMinor)).toBe('3,000.00')
    expect(formatAmount(data.totalStatus!.remainingMinor)).toBe('2,500.00')
    expect(data.totalStatus!.level).toBe('under')
    expect(data.allowance.amountMinor).not.toBeNull()
    expect(data.allowance.remainingDays).toBe(20) // 2026-10-08 → 2026-10-27
    expect(formatAmount(data.allowance.amountMinor!)).toBe('125.00') // 2500 ÷ 20
  })
})

describe('السقف يُضبط ويُمسح — معيار ARCHITECTURE §7', () => {
  it('المسح يعيده null لا صفرًا — والفرق كل شيء', async () => {
    const sys = makeSystem()
    await sys.set.setTotalLimit(PERIOD, parseMoney('3000.00'), 80)
    expect((await sys.load({ period: PERIOD, today: TODAY, payday: 28 })).totalStatus).not.toBeNull()

    await sys.set.clearTotalLimit(PERIOD)
    const after = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })

    expect(after.budget).not.toBeNull() // الميزانية باقية
    expect(after.budget!.totalLimitMinor).toBeNull() // بلا سقف
    expect(after.totalStatus).toBeNull() // لا حالة سقف
    expect(after.totalStatus).not.toBe(0) // ليس صفرًا يمنع كل صرف
  })

  it('يرفض السقف الصفري أو السالب أو غير الصحيح', async () => {
    const sys = makeSystem()
    await expect(sys.set.setTotalLimit(PERIOD, 0, null)).rejects.toThrow(BudgetError)
    await expect(sys.set.setTotalLimit(PERIOD, -100, null)).rejects.toThrow(BudgetError)
    await expect(sys.set.setTotalLimit(PERIOD, 100.5, null)).rejects.toThrow(BudgetError)
  })

  it('يرفض عتبة خارج المدى', async () => {
    const sys = makeSystem()
    await expect(sys.set.setTotalLimit(PERIOD, parseMoney('100.00'), 0)).rejects.toThrow(BudgetError)
    await expect(sys.set.setTotalLimit(PERIOD, parseMoney('100.00'), 101)).rejects.toThrow(BudgetError)
    await expect(sys.set.setTotalLimit(PERIOD, parseMoney('100.00'), null)).resolves.toBeDefined()
  })

  it('سقوف التصنيفات تُضبط وتُمسح مستقلة عن الإجمالي', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([
      txn('1200.00', '2026-10-01', 'cat-food'),
      txn('300.00', '2026-10-02', 'cat-shop'),
    ])

    await sys.set.setCategoryLimit(PERIOD, 'cat-food', parseMoney('1000.00'), {
      thresholdPercent: 90,
    })

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    const food = data.lines.find((l) => l.categoryId === 'cat-food')!
    const shop = data.lines.find((l) => l.categoryId === 'cat-shop')!

    expect(food.status!.level).toBe('over')
    expect(food.status!.thresholdCrossed).toBe(true)
    expect(shop.status).toBeNull() // بلا سقف
    expect(shop.noLimitReason).toContain('مفيش سقف')
    expect(data.totalStatus).toBeNull() // الإجمالي لم يُضبط

    await sys.set.clearCategoryLimit(PERIOD, 'cat-food')
    const after = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(after.lines.find((l) => l.categoryId === 'cat-food')!.status).toBeNull()
  })

  it('مسح الميزانية كلها يشيل سقوف التصنيفات معها — لا سطور يتيمة', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([txn('100.00', '2026-10-01', 'cat-food')])
    await sys.set.setTotalLimit(PERIOD, parseMoney('3000.00'), null)
    await sys.set.setCategoryLimit(PERIOD, 'cat-food', parseMoney('500.00'))

    await sys.set.clearAll(PERIOD)
    const after = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })

    expect(after.budget).toBeNull()
    expect(after.categoryBudgets).toEqual([])
    expect(after.lines.find((l) => l.categoryId === 'cat-food')!.status).toBeNull()
  })

  it('النسخ من فترة سابقة فعل صريح لا تلقائي', async () => {
    const sys = makeSystem()
    const previous = buildPeriod(2026, 8, 28)
    await sys.set.setTotalLimit(previous, parseMoney('2500.00'), 75)
    await sys.set.setCategoryLimit(previous, 'cat-food', parseMoney('800.00'))

    // الفترة الحالية لسه بلا سقف رغم وجود سابقة
    expect((await sys.load({ period: PERIOD, today: TODAY, payday: 28 })).totalStatus).toBeNull()

    const copied = await sys.set.copyFrom(previous.key, PERIOD)
    expect(copied).toBe(1)

    const after = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(formatAmount(after.totalStatus!.limitMinor)).toBe('2,500.00')
    expect(after.budget!.thresholdPercent).toBe(75)
    expect(after.categoryBudgets).toHaveLength(1)
  })

  it('النسخ من فترة بلا ميزانية يُرفض بتفسير', async () => {
    const sys = makeSystem()
    await expect(sys.set.copyFrom('2020-01', PERIOD)).rejects.toThrow(/مفيش ميزانية/)
  })
})

describe('المتوسط والشذوذ على بيانات حقيقية الشكل', () => {
  it('فترة سابقة نصف محددة تُستبعد من المتوسط', async () => {
    const sys = makeSystem()
    for (let i = 1; i <= 4; i++) {
      const p = buildPeriod(2026, 9 - i, 28)
      await sys.txns.saveMany([txn('1000.00', p.start, 'cat-food')])
    }
    // فترة خامسة فيها عملية غير محددة النوع
    const p5 = buildPeriod(2026, 4, 28)
    await sys.txns.saveMany([
      txn('9999.00', p5.start, 'cat-food', 'unclassified'),
    ])

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(data.average.usedPeriods).not.toContain(p5.key)
    expect(formatAmount(data.average.averageMinor!)).toBe('1,000.00') // لم تلوثه
    expect(data.average.excluded.some((e) => e.periodKey === p5.key)).toBe(true)
  })

  it('صرف شاذ في الفترة الجارية يُعلَّم', async () => {
    const sys = makeSystem()
    for (let i = 1; i <= 4; i++) {
      const p = buildPeriod(2026, 9 - i, 28)
      await sys.txns.saveMany([txn('1000.00', p.start, 'cat-food')])
    }
    await sys.txns.saveMany([txn('5000.00', '2026-10-01', 'cat-food')])

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(data.anomaly.isAnomaly).toBe(true)
    expect(data.anomaly.reason).toContain('أعلى')
  })

  it('بلا تاريخ كافٍ ⇒ لا حكم بالشذوذ ولا متوسط', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([txn('5000.00', '2026-10-01', 'cat-food')])

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(data.anomaly.isAnomaly).toBeNull()
    expect(data.average.averageMinor).toBeNull()
  })

  it('مصروف الفترة الجارية يُعلَّم غير موثوق لو فيه عمليات بلا نوع', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([
      txn('500.00', '2026-10-01', 'cat-food'),
      txn('700.00', '2026-10-02', 'cat-shop', 'unclassified'),
    ])

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(data.spentReliable).toBe(false)
    expect(data.spentNote).toContain('1 عملية من 2')
    expect(formatAmount(data.spentMinor)).toBe('500.00') // الجزئي صحيح
    expect(data.spentKnown).toBe(true) // فيه عملية محددة، فالجزئي معروف
  })
})

describe('المصروف المجهول لا يُعرض صفرًا — CLAUDE.md #10', () => {
  it('كل العمليات بلا نوع ⇒ المصروف مجهول ولا مقارنة سقف', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([
      txn('500.00', '2026-10-01', 'cat-food', 'unclassified'),
      txn('700.00', '2026-10-02', 'cat-shop', 'unclassified'),
    ])
    await sys.set.setTotalLimit(PERIOD, parseMoney('3000.00'), 80)

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })

    expect(data.spentKnown).toBe(false)
    // السقف محفوظ، لكن لا شريط مقارنة ولا متاح يومي: «0 من 3000» راحة كاذبة
    expect(data.budget!.totalLimitMinor).toBe(parseMoney('3000.00'))
    expect(data.totalStatus).toBeNull()
    expect(data.allowance.amountMinor).toBeNull()
  })

  it('تحديد نوع عملية واحدة يعيد المقارنة', async () => {
    const sys = makeSystem()
    await sys.txns.saveMany([
      txn('500.00', '2026-10-01', 'cat-food'),
      txn('700.00', '2026-10-02', 'cat-shop', 'unclassified'),
    ])
    await sys.set.setTotalLimit(PERIOD, parseMoney('3000.00'), 80)

    const data = await sys.load({ period: PERIOD, today: TODAY, payday: 28 })
    expect(data.spentKnown).toBe(true)
    expect(data.totalStatus).not.toBeNull()
    expect(formatAmount(data.totalStatus!.spentMinor)).toBe('500.00')
    expect(data.allowance.amountMinor).not.toBeNull()
  })
})
