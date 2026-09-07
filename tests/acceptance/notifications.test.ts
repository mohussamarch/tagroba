import { describe, expect, it } from 'vitest'
import {
  buildBudgetNotifications,
  filterUnseen,
  receiptFor,
  staleReceipts,
  type BudgetNotificationInput,
} from '../../src/domain/notifications'
import { budgetStatus } from '../../src/domain/budget'
import { makeLoadNotifications } from '../../src/application/useCases/loadNotifications'
import { MemoryNotificationReceiptRepository } from '../../src/infrastructure/memory/memoryNotificationRepository'
import { FixedClock } from '../../src/infrastructure/memory/memoryRepositories'
import type { BudgetScreenData } from '../../src/application/useCases/loadBudgetScreen'

/**
 * الإشعارات — `spec/06`:
 * «تصنيف بلا سقف/تاريخ ⇒ لا سقف أو متوسط مخترع **ولا تنبيه عتبة**»
 *
 * و`spec/03`: «NotificationReceipt… **لمنع تكرار التنبيه**».
 */

const base: BudgetNotificationInput = {
  periodStart: '2026-08-28',
  totalStatus: null,
  totalThresholdPercent: null,
  categories: [],
  spentKnown: true,
}

describe('متى يتولد تنبيه ومتى لأ', () => {
  it('مفيش سقف ⇒ مفيش تنبيه خالص', () => {
    expect(buildBudgetNotifications(base)).toEqual([])
  })

  it('تصنيف بلا سقف ما بيدخلش أصلًا فمستحيل ينبّه', () => {
    // المدخل نفسه بيقبل التصنيفات ذات السقف بس — القاعدة في النوع لا في شرط
    const events = buildBudgetNotifications({ ...base, categories: [] })
    expect(events).toEqual([])
  })

  it('سقف بلا عتبة مستخدم: بينبّه عند التجاوز بس', () => {
    const under = buildBudgetNotifications({
      ...base,
      totalStatus: budgetStatus(300000, 285000, null), // ٩٥٪
      totalThresholdPercent: null,
    })
    expect(under).toEqual([])

    const over = buildBudgetNotifications({
      ...base,
      totalStatus: budgetStatus(300000, 310000, null),
      totalThresholdPercent: null,
    })
    expect(over).toHaveLength(1)
    expect(over[0].threshold).toBe(100)
    expect(over[0].severity).toBe('over')
    expect(over[0].title).toBe('عدّيت الميزانية')
  })

  it('عتبة المستخدم بتنبّه لوحدها', () => {
    const events = buildBudgetNotifications({
      ...base,
      totalStatus: budgetStatus(300000, 255000, 80), // ٨٥٪
      totalThresholdPercent: 80,
    })
    expect(events).toHaveLength(1)
    expect(events[0].threshold).toBe(80)
    expect(events[0].title).toContain('80٪')
    // بالريال لا بالهللة
    expect(events[0].body).toContain('2,550.00')
    expect(events[0].body).toContain('فاضل 450.00')
  })

  it('التجاوز بيرجّع تنبيهي العتبة والتجاوز، فمحدش بيضيع', () => {
    const events = buildBudgetNotifications({
      ...base,
      totalStatus: budgetStatus(300000, 330000, 80),
      totalThresholdPercent: 80,
    })
    expect(events.map((e) => e.threshold)).toEqual([100, 80])
    expect(events[0].body).toContain('زيادة 300.00')
  })

  it('المصروف المجهول ما بينبّهش — رقم ناقص مش أساس تخويف', () => {
    const events = buildBudgetNotifications({
      ...base,
      totalStatus: budgetStatus(300000, 330000, 80),
      totalThresholdPercent: 80,
      spentKnown: false,
    })
    expect(events).toEqual([])
  })

  it('تنبيه التصنيف باسمه لا بمعرّفه', () => {
    const events = buildBudgetNotifications({
      ...base,
      categories: [
        {
          categoryId: 'cat-food',
          categoryName: 'مطاعم',
          status: budgetStatus(100000, 105000, null),
          thresholdPercent: null,
        },
      ],
    })
    expect(events[0].title).toBe('عدّيت سقف «مطاعم»')
    expect(events[0].categoryId).toBe('cat-food')
    expect(events[0].eventKey).toContain('cat-food')
  })
})

describe('منع التكرار — NotificationReceipt', () => {
  const events = buildBudgetNotifications({
    ...base,
    totalStatus: budgetStatus(300000, 255000, 80),
    totalThresholdPercent: 80,
  })

  it('اللي اتشاف ما بيرجعش', () => {
    const receipts = events.map((e) => receiptFor(e, '2026-09-01T10:00:00.000Z'))
    expect(filterUnseen(events, receipts)).toEqual([])
  })

  it('عبور عتبة أعلى بعدين حدث جديد مش تكرار', () => {
    const seen = events.map((e) => receiptFor(e, '2026-09-01T10:00:00.000Z'))
    const later = buildBudgetNotifications({
      ...base,
      totalStatus: budgetStatus(300000, 330000, 80),
      totalThresholdPercent: 80,
    })
    const unseen = filterUnseen(later, seen)
    expect(unseen).toHaveLength(1)
    expect(unseen[0].threshold).toBe(100)
  })

  it('نفس التنبيه في فترة تانية بينبّه من جديد', () => {
    const seen = events.map((e) => receiptFor(e, '2026-09-01T10:00:00.000Z'))
    const nextPeriod = buildBudgetNotifications({
      ...base,
      periodStart: '2026-09-28',
      totalStatus: budgetStatus(300000, 255000, 80),
      totalThresholdPercent: 80,
    })
    expect(filterUnseen(nextPeriod, seen)).toHaveLength(1)
  })

  it('إيصالات الفترات القديمة بتتحدد للتنضيف', () => {
    const receipts = [
      receiptFor(events[0], '2026-09-01T10:00:00.000Z'),
      { ...receiptFor(events[0], '2026-07-01T10:00:00.000Z'), eventKey: 'old', periodStart: '2026-06-28' },
    ]
    const stale = staleReceipts(receipts, '2026-08-28')
    expect(stale.map((r) => r.eventKey)).toEqual(['old'])
  })
})

describe('عبر التخزين', () => {
  function budgetData(overrides: Partial<BudgetScreenData> = {}): BudgetScreenData {
    return {
      period: { key: '2026-09', start: '2026-08-28', end: '2026-09-27', days: 31 },
      budget: {
        id: '2026-09',
        periodKey: '2026-09',
        periodStart: '2026-08-28',
        periodEnd: '2026-09-27',
        totalLimitMinor: 300000,
        thresholdPercent: 80,
        createdAt: '',
        updatedAt: '',
      },
      totalStatus: budgetStatus(300000, 255000, 80),
      spentMinor: 255000,
      spentReliable: true,
      spentNote: null,
      spentKnown: true,
      categories: [],
      lines: [],
      categoryBudgets: [],
      ...overrides,
    } as BudgetScreenData
  }

  it('بيرجّع التنبيه مرة واحدة، وبعد التعليم ما بيرجعش', async () => {
    const receipts = new MemoryNotificationReceiptRepository()
    const useCase = makeLoadNotifications({
      receipts,
      clock: new FixedClock('2026-09-07T10:00:00.000Z'),
    })

    const first = await useCase.load(budgetData())
    expect(first.unseen).toHaveLength(1)
    expect(first.all).toHaveLength(1)

    await useCase.markSeen(first.unseen)

    const second = await useCase.load(budgetData())
    expect(second.unseen).toHaveLength(0)
    // المقروء بيفضل في القايمة الكاملة — الصفحة بتعرضه، بس مش كجديد
    expect(second.all).toHaveLength(1)
  })

  it('التصنيف اللي قافل تنبيهه ما بينبّهش', async () => {
    const receipts = new MemoryNotificationReceiptRepository()
    const useCase = makeLoadNotifications({
      receipts,
      clock: new FixedClock('2026-09-07T10:00:00.000Z'),
    })

    const data = budgetData({
      totalStatus: null,
      budget: null,
      categories: [{ id: 'c1', name: 'مطاعم' }] as BudgetScreenData['categories'],
      lines: [
        {
          categoryId: 'c1',
          status: budgetStatus(100000, 120000, null),
          spentMinor: 120000,
          averageMinor: null,
          anomaly: { isAnomaly: null, reason: '', medianMinor: null, deviationMinor: null },
          noLimitReason: null,
        },
      ] as BudgetScreenData['lines'],
      categoryBudgets: [
        {
          id: 'cb1',
          budgetId: '2026-09',
          categoryId: 'c1',
          limitMinor: 100000,
          notifyEnabled: false,
          thresholdPercent: 80,
        },
      ],
    })

    expect((await useCase.load(data)).all).toEqual([])
  })

  it('التنضيف بيشيل القديم بس', async () => {
    const receipts = new MemoryNotificationReceiptRepository([
      {
        eventKey: 'قديم',
        threshold: 80,
        periodStart: '2026-06-28',
        sentAt: '2026-07-01T00:00:00.000Z',
      },
      {
        eventKey: 'حالي',
        threshold: 80,
        periodStart: '2026-08-28',
        sentAt: '2026-09-01T00:00:00.000Z',
      },
    ])
    const useCase = makeLoadNotifications({
      receipts,
      clock: new FixedClock('2026-09-07T10:00:00.000Z'),
    })

    expect(await useCase.pruneOldReceipts('2026-08-28')).toBe(1)
    expect((await receipts.listAll()).map((r) => r.eventKey)).toEqual(['حالي'])
  })
})
