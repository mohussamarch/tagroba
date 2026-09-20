import { makeLoadHomeScreen } from '../../src/application/useCases/loadHomeScreen'
import { MemoryTransactionRepository, MemoryAllocationRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
import { buildPeriod } from '../../src/domain/period'
import { ALL_ECONOMIC_KINDS } from '../../src/domain/entities/economicKind'
import type { Budget } from '../../src/domain/entities/budgetEntities'
import type { Category, PersonAllocation, Transaction } from '../../src/domain/entities/types'
import { recordAsync, seeded } from './goldenKit'

/**
 * الشاشة الرئيسية كاملة — أول **حالة استخدام** في ملفات المرجع (مش دالة حساب لوحدها).
 * المدخل فيه **كل محتوى المستودعات**، فكوتلن بتبني نفس المستودعات ولازم تطلع نفس الناتج.
 * ⚠️ بيانات وهمية بالكامل.
 *
 * التلات حالات اللي الشاشة مبنية عليها (CLAUDE.md #10) محتاجة عمليات نوعها **«غير محدد ومؤكد»**:
 * `withEstimatedKinds` بتقدّر أي نوع غير محدد **إلا** لو المستخدم مؤكده، فده الشكل الوحيد
 * اللي بيوصل لـ«غير متاح» و«جزئي».
 */
export async function homeGolden() {
  const rnd = seeded(97)
  const cats: Category[] = [
    { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
    { id: 'fuel', parentId: null, name: 'بنزين', iconKey: 'fuel', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'transport' },
    { id: 'rent', parentId: null, name: 'إيجار', iconKey: 'house-heart', lightColor: '#1f7a43', darkColor: '#8cd5a8', active: true, order: 3, groupKey: 'home' },
    { id: 'transfers', parentId: null, name: 'تحويلات', iconKey: 'arrow-left-right', lightColor: '#6b1fa4', darkColor: '#c08cf0', active: true, order: 4, groupKey: 'movement' },
  ]

  /*
   * فترة «2026-09» بتختلف بيوم الراتب: يوم 28 ⇒ 09-28←10-27، ويوم 1 ⇒ 09-01←09-30.
   * لازم تواريخ جوه الفترة وبرّاها — أول نسخة من المولّد كانت كلها برّه فطلعت الحالات فاضية.
   */
  const days = [
    '2026-04-20', '2026-05-15', '2026-06-15', '2026-06-30', '2026-07-05', '2026-07-28',
    '2026-08-10', '2026-08-28', '2026-09-01', '2026-09-10', '2026-09-27', '2026-09-28',
    '2026-09-29', '2026-09-30', '2026-10-01', '2026-10-03', '2026-10-08', '2026-10-15',
    '2026-10-20', '2026-10-25', '2026-10-27', '2026-10-31',
  ]

  /** 'known' = نوعه معروف · 'unresolved' = غير محدد ومؤكد (ما بيتقدّرش) · 'estimated' = غير محدد وبيتقدّر. */
  type Kind = 'known' | 'unresolved' | 'estimated'

  const txn = (i: number, kind: Kind, periodStart: string): Transaction => {
    const unclassified = kind !== 'known'
    const t: Transaction = {
      id: `t-${i}`,
      // اللي مش معروف نوعه بيتحط جوه الفترة عشان الحالات التلاتة تحصل فعلًا
      occurredAt: unclassified ? periodStart : rnd.pick(days),
      datePrecision: 'day',
      sourceOrder: rnd.int(0, 4),
      economicKind: unclassified ? 'unclassified' : rnd.pick(ALL_ECONOMIC_KINDS),
      economicKindConfirmed: kind === 'unresolved' ? true : kind === 'estimated' ? false : rnd.next() < 0.7,
      // الوارد من غير تصنيف اقتراحه بيفضل غامض ⇒ بيتحسب بالنوع المفترض «محتاج تأكيد»
      observedDirection: unclassified ? 'in' : rnd.next() < 0.75 ? 'out' : 'in',
      amountMinor: rnd.int(1, 300_000),
      currency: 'SAR',
      categoryConfirmed: false,
      excludedFromBudget: rnd.next() < 0.12,
      reviewState: 'suggested',
      isCashTagged: false,
      createdAt: '2026-09-01T00:00:00.000Z',
      updatedAt: '2026-09-01T00:00:00.000Z',
    }
    if (!unclassified && rnd.next() < 0.85) t.categoryId = rnd.pick(cats).id
    return t
  }

  // حالات مقصودة: فاضية · كلها غير متاحة · جزئية · كلها معروفة · مخلوطة
  const shapes: { known: number; unresolved: number; estimated: number }[] = [
    { known: 0, unresolved: 0, estimated: 0 },
    { known: 0, unresolved: 5, estimated: 0 },
    { known: 6, unresolved: 3, estimated: 0 },
    { known: 12, unresolved: 0, estimated: 0 },
    { known: 14, unresolved: 4, estimated: 7 },
    { known: 3, unresolved: 0, estimated: 4 },
  ]

  const cases = []
  for (let s = 0; s < 18; s++) {
    const shape = shapes[s % shapes.length]!
    const payday = rnd.pick([28, 1, 31])
    const period = buildPeriod(2026, 9, payday)
    const plan: Kind[] = [
      ...Array<Kind>(shape.known).fill('known'),
      ...Array<Kind>(shape.unresolved).fill('unresolved'),
      ...Array<Kind>(shape.estimated).fill('estimated'),
    ]
    const transactions = plan.map((kind, i) => txn(s * 100 + i, kind, period.start))
    const allocations: PersonAllocation[] = transactions
      .filter(() => rnd.next() < 0.25)
      .map((t, i) => ({
        id: `a-${s}-${i}`,
        transactionId: t.id,
        personId: 'p-1',
        allocationKind: rnd.next() < 0.5 ? 'receivable' : 'gift',
        amountMinor: rnd.int(0, t.amountMinor),
        currency: 'SAR',
      }))
    const withBudget = s % 3 === 0
    const budgets: Budget[] = withBudget
      ? [{
          id: period.key, periodKey: period.key, periodStart: period.start, periodEnd: period.end,
          totalLimitMinor: rnd.pick([null, 500_000, 1_000_000]), thresholdPercent: 80,
          createdAt: 'x', updatedAt: 'x',
        }]
      : []
    const options = {
      year: 2026,
      month: 9,
      payday,
      today: rnd.pick(['2026-09-01', '2026-09-05', '2026-09-20', '2026-10-27', '2026-08-29']),
      budgetLimitMinor: withBudget ? null : rnd.pick([null, 250_000]),
      includeHistory: s % 5 !== 0,
      useBudgetRepository: withBudget,
    }

    const input = { transactions, categories: cats, allocations, budgets, options }
    cases.push(await recordAsync(input, async () => {
      // مستودعات الذاكرة في التطبيق الحالي بتتملّى بالحفظ مش بالمنشئ
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(transactions)
      const allocationRepo = new MemoryAllocationRepository()
      await allocationRepo.saveMany(allocations)
      const budgetRepo = new MemoryBudgetRepository()
      for (const b of budgets) await budgetRepo.save(b)
      const load = makeLoadHomeScreen({
        txns,
        categories: new MemoryCategoryRepository(cats),
        allocations: allocationRepo,
        budgets: options.useBudgetRepository ? budgetRepo : undefined,
      })
      const data = await load({
        period: buildPeriod(options.year, options.month, options.payday),
        today: options.today,
        payday: options.payday,
        budgetLimitMinor: options.budgetLimitMinor,
        includeHistory: options.includeHistory,
      })
      // التصنيفات وأحدث العمليات بتترجع زي ما هي من المستودع ⇒ المعرّفات تكفي (الترتيب هو المهم)
      return {
        periodKey: data.period.key,
        periodRange: data.periodRange,
        expenseMinor: data.expenseMinor,
        incomeMinor: data.incomeMinor,
        remainingMinor: data.remainingMinor,
        savingsRatePercent: data.savingsRatePercent,
        excludedExpenseMinor: data.excludedExpenseMinor,
        partial: data.partial,
        estimatedCount: data.estimatedCount,
        needsReviewCount: data.needsReviewCount,
        distribution: data.distribution,
        categoryIds: data.categories.map((c) => c.id),
        latestIds: data.latest.map((t) => t.id),
        transactionCount: data.transactionCount,
        allowance: data.allowance,
        forecast: data.forecast,
        coverage: data.coverage,
        recentPeriods: data.recentPeriods.map((p) => ({
          periodKey: p.period.key,
          expenseMinor: p.expenseMinor,
          incomeMinor: p.incomeMinor,
          transactionCount: p.transactionCount,
        })),
      }
    }))
  }

  return { loadHomeScreen: cases }
}
