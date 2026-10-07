import { makeLoadBudgetScreen } from '../../src/application/useCases/loadBudgetScreen'
import { makeLoadNotifications } from '../../src/application/useCases/loadNotifications'
import { makeReviewHistory } from '../../src/application/useCases/reviewHistory'
import { MemoryTransactionRepository, MemoryAllocationRepository } from '../../src/infrastructure/memory/memoryRepositories'
import { MemoryCategoryRepository, MemoryMerchantRepository, MemoryRuleRepository } from '../../src/infrastructure/memory/memoryReferenceRepositories'
import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
import { MemoryNotificationReceiptRepository } from '../../src/infrastructure/memory/memoryNotificationRepository'
import { MemoryUnitOfWork, FixedClock } from '../../src/infrastructure/memory/memorySupport'
import { buildPeriod } from '../../src/domain/period'
import type { NotificationReceipt } from '../../src/domain/notifications'
import type { EconomicKind } from '../../src/domain/entities/economicKind'
import type { Budget, CategoryBudget } from '../../src/domain/entities/budgetEntities'
import type { Category, ClassificationRule, Merchant, Transaction } from '../../src/domain/entities/types'
import { recordAsync, type GoldenCase } from './goldenKit'

/**
 * التنبيهات جوه التطبيق + مراجعة السجل القديم (تصنيف جماعي ونوع لمجموعة). ⚠️ بيانات وهمية بالكامل.
 * التنبيهات بتتبني من ناتج شاشة الميزانية **الحقيقي** — مفيش حساب مصروف ولا سقف من جديد.
 */

const NOW = '2026-09-22T10:00:00.000Z'
const cats: Category[] = [
  { id: 'food', parentId: null, name: 'أكل', iconKey: 'chef-hat', lightColor: '#a4451f', darkColor: '#f0a68c', active: true, order: 1, groupKey: 'food' },
  { id: 'fuel', parentId: null, name: 'بنزين', iconKey: 'fuel', lightColor: '#1f5aa4', darkColor: '#8cbcf0', active: true, order: 2, groupKey: 'transport' },
  { id: 'rent', parentId: null, name: 'إيجار', iconKey: 'house-heart', lightColor: '#1f7a43', darkColor: '#8cd5a8', active: true, order: 3, groupKey: 'home' },
]

let n = 0
function txn(occurredAt: string, amountMinor: number, over: Partial<Transaction> = {}): Transaction {
  n++
  return {
    id: `t-${String(n).padStart(3, '0')}`, occurredAt, datePrecision: 'day', sourceOrder: n % 4,
    economicKind: 'purchase', economicKindConfirmed: true, observedDirection: 'out', amountMinor, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
    createdAt: '2026-09-01T00:00:00.000Z', updatedAt: '2026-09-01T00:00:00.000Z',
    ...over,
  }
}

async function loadNotificationsCases() {
  const cases: GoldenCase[] = []
  const period = buildPeriod(2026, 9, 28)
  n = 0
  // مصروف الفترة: أكل 950 (من سقف 1000) · بنزين 700 (من 500) · إيجار 100 (سقف بتنبيه مقفول)
  const transactions: Transaction[] = [
    txn('2026-09-29', 60_000, { categoryId: 'food' }), txn('2026-10-02', 35_000, { categoryId: 'food' }),
    txn('2026-10-03', 70_000, { categoryId: 'fuel' }), txn('2026-10-05', 10_000, { categoryId: 'rent' }),
  ]
  const unknownOnly: Transaction[] = [txn('2026-09-29', 50_000, { economicKind: 'unclassified', economicKindConfirmed: true, observedDirection: 'in' })]
  const budget = (total: number | null, threshold: number | null): Budget => ({
    id: period.key, periodKey: period.key, periodStart: period.start, periodEnd: period.end,
    totalLimitMinor: total, thresholdPercent: threshold, createdAt: 'x', updatedAt: 'x',
  })
  const lines: CategoryBudget[] = [
    { id: 'cb-food', budgetId: period.key, categoryId: 'food', limitMinor: 100_000, notifyEnabled: true, thresholdPercent: 90 },
    { id: 'cb-fuel', budgetId: period.key, categoryId: 'fuel', limitMinor: 50_000, notifyEnabled: true, thresholdPercent: null },
    { id: 'cb-rent', budgetId: period.key, categoryId: 'rent', limitMinor: 5_000, notifyEnabled: false, thresholdPercent: 50 },
  ]
  const seen: NotificationReceipt = { eventKey: `${period.start}|cat:food|90`, threshold: 90, periodStart: period.start, sentAt: '2026-10-02T09:00:00.000Z', categoryId: 'food' }
  const old: NotificationReceipt = { eventKey: '2026-08-28|total|100', threshold: 100, periodStart: '2026-08-28', sentAt: '2026-09-20T09:00:00.000Z' }

  type Step = { kind: 'load' } | { kind: 'markSeen'; count: number } | { kind: 'prune'; keepFrom: string }
  const runs: { transactions: Transaction[]; budgets: Budget[]; lines: CategoryBudget[]; receipts: NotificationReceipt[]; steps: Step[] }[] = [
    { transactions, budgets: [budget(150_000, 80)], lines, receipts: [], steps: [{ kind: 'load' }, { kind: 'markSeen', count: 2 }, { kind: 'load' }] },
    { transactions, budgets: [budget(null, null)], lines, receipts: [seen, old], steps: [{ kind: 'load' }, { kind: 'prune', keepFrom: period.start }, { kind: 'prune', keepFrom: period.start }] },
    { transactions, budgets: [], lines: [], receipts: [], steps: [{ kind: 'load' }, { kind: 'markSeen', count: 0 }] },
    // مفيش ولا عملية نوعها معروف ⇒ المصروف مجهول ⇒ مفيش تنبيه على رقم ناقص
    { transactions: unknownOnly, budgets: [budget(1_000, 80)], lines, receipts: [], steps: [{ kind: 'load' }] },
  ]
  for (const r of runs) {
    cases.push(await recordAsync({ ...r, categories: cats, period: { year: 2026, month: 9, payday: 28 }, today: '2026-10-06' }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(r.transactions)
      const budgets = new MemoryBudgetRepository()
      for (const b of r.budgets) await budgets.save(b)
      for (const l of r.lines) await budgets.saveCategoryBudget(l)
      const screen = await makeLoadBudgetScreen({ txns, categories: new MemoryCategoryRepository(cats), allocations: new MemoryAllocationRepository(), budgets })({ period, today: '2026-10-06', payday: 28 })
      const receipts = new MemoryNotificationReceiptRepository(r.receipts)
      const notices = makeLoadNotifications({ receipts, clock: new FixedClock(NOW) })
      const out: unknown[] = []
      let last: Awaited<ReturnType<typeof notices.load>> | null = null
      for (const step of r.steps) {
        if (step.kind === 'load') { last = await notices.load(screen); out.push(last) }
        else if (step.kind === 'markSeen') { await notices.markSeen((last?.unseen ?? []).slice(0, step.count)); out.push(null) }
        else out.push({ pruned: await notices.pruneOldReceipts(step.keepFrom) })
      }
      return { steps: out, storedReceipts: await receipts.listAll() }
    }))
  }
  return cases
}

async function reviewHistoryCases() {
  const cases: GoldenCase[] = []
  n = 0
  const merchants: Merchant[] = [{ id: 'm-cafe', displayName: 'TEST CAFE', normalizedName: 'test cafe', verifiedCategoryId: 'food' }]
  const rules: ClassificationRule[] = [{ id: 'r-fuel', priority: 1, matchText: 'station', matchMode: 'contains', categoryId: 'fuel', enabled: true }]
  const unconfirmed = { economicKind: 'unclassified' as EconomicKind, economicKindConfirmed: false }
  const transactions: Transaction[] = [
    txn('2026-01-05', 2_000, { rawMerchantName: 'Test Cafe', ...unconfirmed }),
    txn('2026-02-10', 2_500, { rawMerchantName: 'TEST  CAFE', ...unconfirmed }),
    txn('2026-03-15', 3_000, { rawMerchantName: 'test cafe', categoryId: 'food', categoryConfirmed: true }),
    txn('2026-01-20', 9_000, { rawMerchantName: 'Station 5', ...unconfirmed }),
    txn('2026-04-01', 8_000, { rawMerchantName: 'Station 5', ...unconfirmed }),
    txn('2026-04-02', 50_000, { rawMerchantName: 'Station 5', observedDirection: 'in', ...unconfirmed }),
    txn('2026-04-03', 1_000, { rawMerchantName: 'محل وحيد', ...unconfirmed }),
    txn('2026-05-01', 1_200, { ...unconfirmed }),
    txn('2021-01-01', 700, { rawMerchantName: 'Test Cafe', ...unconfirmed }),
  ]

  type Step =
    | { kind: 'preview'; from: string; to: string }
    | { kind: 'applyCategories'; ids?: string[]; allFromPreview?: boolean; staleRule?: boolean }
    | { kind: 'setGroup'; ids: string[]; economicKind: EconomicKind }
  const runs: Step[][] = [
    [{ kind: 'preview', from: '2026-01-01', to: '2026-06-30' }],
    [{ kind: 'preview', from: '2021-01-01', to: '2026-01-04' }],
    [{ kind: 'preview', from: '2020-01-01', to: '2026-06-30' }],
    [{ kind: 'preview', from: '2026-06-30', to: '2026-01-01' }],
    [{ kind: 'preview', from: '2026-02-31', to: '2026-06-30' }],
    // كل عمليات المعاينة (ومعاها تكرار) ⇒ بيتطبق · جزء منها بس ⇒ الخطة مختلفة ⇒ رفض
    [{ kind: 'preview', from: '2026-01-01', to: '2026-06-30' }, { kind: 'applyCategories', allFromPreview: true }],
    [{ kind: 'preview', from: '2026-01-01', to: '2026-06-30' }, { kind: 'applyCategories', ids: ['t-001', 't-002', 't-003', 't-004', 't-001'] }],
    // القواعد اتغيرت بعد المعاينة ⇒ رفض وطلب معاينة جديدة
    [{ kind: 'preview', from: '2026-01-01', to: '2026-06-30' }, { kind: 'applyCategories', allFromPreview: true, staleRule: true }],
    [{ kind: 'setGroup', ids: ['t-004', 't-005', 't-004'], economicKind: 'purchase' }],
    [{ kind: 'setGroup', ids: ['t-004', 't-005'], economicKind: 'loan_granted' }],
    [{ kind: 'setGroup', ids: ['t-ghost'], economicKind: 'purchase' }],
    [{ kind: 'setGroup', ids: ['t-004', 't-006'], economicKind: 'purchase' }],
    [{ kind: 'setGroup', ids: ['t-006'], economicKind: 'purchase' }],
    [{ kind: 'setGroup', ids: ['t-001', 't-003'], economicKind: 'purchase' }],
  ]
  for (const steps of runs) {
    cases.push(await recordAsync({ transactions, categories: cats, merchants, rules, steps }, async () => {
      const txns = new MemoryTransactionRepository()
      await txns.saveMany(transactions)
      const ruleRepo = new MemoryRuleRepository(rules)
      const review = makeReviewHistory({
        txns, merchants: new MemoryMerchantRepository(merchants), categories: new MemoryCategoryRepository(cats),
        rules: ruleRepo, uow: new MemoryUnitOfWork([txns]), clock: new FixedClock(NOW),
      })
      const out: unknown[] = []
      let plan: Awaited<ReturnType<typeof review.preview>>['categoryPlan'] | null = null
      let previewIds: string[] = []
      for (const step of steps) {
        if (step.kind === 'preview') {
          const p = await review.preview(step.from, step.to)
          plan = p.categoryPlan
          previewIds = p.rows.map((t) => t.id)
          out.push({ rowIds: p.rows.map((t) => t.id), categoryPlan: p.categoryPlan, groups: p.groups.map((g) => g.map((t) => t.id)) })
        } else if (step.kind === 'applyCategories') {
          if (step.staleRule) await ruleRepo.saveMany([{ ...rules[0]!, categoryId: 'rent' }])
          out.push(await review.applyCategories(step.allFromPreview ? [...previewIds, previewIds[0]!] : step.ids!, plan!))
        } else {
          out.push(await review.setGroup(step.ids, step.economicKind))
        }
      }
      return {
        steps: out,
        storedTransactions: [...txns.snapshot().values()].map((t) => ({
          id: t.id, economicKind: t.economicKind, economicKindConfirmed: t.economicKindConfirmed,
          categoryId: t.categoryId ?? null, categoryConfirmed: t.categoryConfirmed, reviewState: t.reviewState,
        })),
      }
    }))
  }
  return cases
}

export async function reviewFlowGolden() {
  return { loadNotifications: await loadNotificationsCases(), reviewHistory: await reviewHistoryCases() }
}
