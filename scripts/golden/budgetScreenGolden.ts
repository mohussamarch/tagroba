import { makeLoadBudgetScreen } from '../../src/application/useCases/loadBudgetScreen'
import { MemoryTransactionRepository, MemoryAllocationRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
import { buildPeriod, type Period } from '../../src/domain/period'
import { ALL_ECONOMIC_KINDS } from '../../src/domain/entities/economicKind'
import type { Budget, CategoryBudget } from '../../src/domain/entities/budgetEntities'
import type { Category, PersonAllocation, Transaction } from '../../src/domain/entities/types'
import { recordAsync, seeded } from './goldenKit'

/**
 * شاشة الميزانية — تالت حالة استخدام في ملفات المرجع. ⚠️ بيانات وهمية بالكامل.
 * الزيادة عن اللي فات: **تاريخ ست فترات سابقة** (المتوسط والشذوذ بيتحسبوا منهم)،
 * وسقوف التصنيفات، والفرق بين «مفيش سقف» و«مفيش مصروف معروف».
 */
export async function budgetScreenGolden() {
  const rnd = seeded(211)
  const cats: Category[] = [
    { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
    { id: 'fuel', parentId: null, name: 'بنزين', iconKey: 'fuel', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'transport' },
    { id: 'rent', parentId: null, name: 'إيجار', iconKey: 'house-heart', lightColor: '#1f7a43', darkColor: '#8cd5a8', active: true, order: 3, groupKey: 'home' },
  ]

  /** 'known' = نوعه معروف · 'unresolved' = غير محدد ومؤكد (بيخلي الفترة غير موثوقة) · 'estimated' = غير محدد وبيتقدّر. */
  type Kind = 'known' | 'unresolved' | 'estimated'

  let nextId = 0
  const txn = (kind: Kind, period: Period): Transaction => {
    const unclassified = kind !== 'known'
    const t: Transaction = {
      id: `t-${nextId++}`,
      // جوه الفترة المطلوبة بالظبط: أولها أو آخرها
      occurredAt: rnd.next() < 0.7 ? period.start : period.end,
      datePrecision: 'day',
      sourceOrder: rnd.int(0, 4),
      economicKind: unclassified ? 'unclassified' : rnd.pick(ALL_ECONOMIC_KINDS),
      economicKindConfirmed: kind === 'unresolved' ? true : kind === 'estimated' ? false : rnd.next() < 0.7,
      // التقديري الصادر بتصنيف معروف ممكن ياخد اقتراح واثق (تقديري من غير مراجعة)
      observedDirection: kind === 'estimated' ? (rnd.next() < 0.6 ? 'out' : 'in') : unclassified ? 'in' : rnd.next() < 0.75 ? 'out' : 'in',
      amountMinor: rnd.int(1, 300_000),
      currency: 'SAR',
      categoryConfirmed: false,
      excludedFromBudget: rnd.next() < 0.12,
      reviewState: 'suggested',
      isCashTagged: false,
      createdAt: '2026-09-01T00:00:00.000Z',
      updatedAt: '2026-09-01T00:00:00.000Z',
    }
    if (rnd.next() < 0.8) t.categoryId = rnd.pick(cats).id
    return t
  }

  const shiftPeriod = (period: Period, delta: number, payday: number): Period => {
    const [year, month] = period.key.split('-').map(Number)
    const total = year! * 12 + (month! - 1) + delta
    return buildPeriod(Math.floor(total / 12), (total % 12) + 1, payday)
  }

  // أشكال الفترة الجارية: فاضية · كلها غير محددة · جزئية · كلها معروفة · مخلوطة
  const currentShapes: { known: number; unresolved: number; estimated: number }[] = [
    { known: 0, unresolved: 0, estimated: 0 },
    { known: 0, unresolved: 4, estimated: 0 },
    { known: 5, unresolved: 2, estimated: 0 },
    { known: 9, unresolved: 0, estimated: 0 },
    { known: 7, unresolved: 2, estimated: 5 },
    { known: 3, unresolved: 0, estimated: 4 },
  ]

  const cases = []
  for (let s = 0; s < 15; s++) {
    nextId = 0
    const payday = rnd.pick([28, 1, 31])
    const period = buildPeriod(2026, 9, payday)
    const shape = currentShapes[s % currentShapes.length]!
    const plan: Kind[] = [
      ...Array<Kind>(shape.known).fill('known'),
      ...Array<Kind>(shape.unresolved).fill('unresolved'),
      ...Array<Kind>(shape.estimated).fill('estimated'),
    ]
    const transactions = plan.map((kind) => txn(kind, period))

    // تاريخ الفترات السابقة: موثوقة (كلها معروفة) · غير موثوقة (فيها غير محدد مؤكد) · فاضية
    for (let i = 1; i <= 6; i++) {
      const p = shiftPeriod(period, -i, payday)
      const kind = rnd.pick(['reliable', 'reliable', 'reliable', 'unreliable', 'empty'] as const)
      if (kind === 'empty') continue
      const count = rnd.int(2, 6)
      for (let k = 0; k < count; k++) transactions.push(txn('known', p))
      if (kind === 'unreliable') transactions.push(txn('unresolved', p))
    }

    const allocations: PersonAllocation[] = transactions
      .filter(() => rnd.next() < 0.15)
      .map((t, i) => ({
        id: `a-${s}-${i}`,
        transactionId: t.id,
        personId: 'p-1',
        allocationKind: rnd.next() < 0.5 ? 'receivable' : 'gift',
        amountMinor: rnd.int(0, t.amountMinor),
        currency: 'SAR',
      }))

    // الميزانية: مفيش · سقف إجمالي null · سقف فعلي (وعتبة أو لأ)
    const budgetShape = s % 3
    const budgets: Budget[] = budgetShape === 0
      ? []
      : [{
          id: `b-${period.key}`, periodKey: period.key, periodStart: period.start, periodEnd: period.end,
          totalLimitMinor: budgetShape === 1 ? null : rnd.pick([400_000, 900_000, 2_000_000]),
          thresholdPercent: rnd.pick([null, 50, 80]),
          createdAt: 'x', updatedAt: 'x',
        }]
    const categoryBudgets: CategoryBudget[] = budgets.length === 0
      ? []
      : cats.filter(() => rnd.next() < 0.6).map((c, i) => ({
          id: `cb-${s}-${i}`,
          budgetId: budgets[0]!.id,
          categoryId: c.id,
          limitMinor: rnd.int(50_000, 500_000),
          notifyEnabled: rnd.next() < 0.5,
          thresholdPercent: rnd.pick([null, 60, 90]),
        }))

    const options = {
      year: 2026,
      month: 9,
      payday,
      today: rnd.pick(['2026-09-05', '2026-09-20', '2026-10-10', '2026-10-27']),
    }

    const input = { transactions, categories: cats, allocations, budgets, categoryBudgets, options }
    cases.push(await recordAsync(input, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(transactions)
      const allocationRepo = new MemoryAllocationRepository()
      await allocationRepo.saveMany(allocations)
      const budgetRepo = new MemoryBudgetRepository()
      for (const b of budgets) await budgetRepo.save(b)
      for (const cb of categoryBudgets) await budgetRepo.saveCategoryBudget(cb)
      const load = makeLoadBudgetScreen({
        txns,
        categories: new MemoryCategoryRepository(cats),
        allocations: allocationRepo,
        budgets: budgetRepo,
      })
      const data = await load({ period: buildPeriod(options.year, options.month, payday), today: options.today, payday })
      return {
        periodKey: data.period.key,
        budgetId: data.budget?.id ?? null,
        totalStatus: data.totalStatus,
        spentMinor: data.spentMinor,
        spentReliable: data.spentReliable,
        spentNote: data.spentNote,
        spentKnown: data.spentKnown,
        average: data.average,
        anomaly: data.anomaly,
        allowance: data.allowance,
        categoryIds: data.categories.map((c) => c.id),
        lines: data.lines,
        categoryBudgetIds: data.categoryBudgets.map((cb) => cb.id),
      }
    }))
  }

  return { loadBudgetScreen: cases }
}
