import { describe, it, expect } from 'vitest'
import { withEstimatedKinds } from '../../src/domain/estimatedKinds'
import { computePeriodTotals } from '../../src/domain/ledger'
import { assessCoverage } from '../../src/domain/analytics'
import type { Transaction } from '../../src/domain/entities/types'

/** OVERRIDES §18: الأرقام دايمًا ظاهرة (تقريبي)، وعدد اللي محتاج تأكيد معروض. */

const names = new Map([['cat-grocery', 'بقالة وسوبرماركت'], ['cat-wallets', 'محافظ رقمية']])

function txn(id: string, patch: Partial<Transaction>): Transaction {
  return {
    id, occurredAt: '2026-08-30', datePrecision: 'day', sourceOrder: 1, economicKind: 'unclassified',
    economicKindConfirmed: false, observedDirection: 'out', amountMinor: 10000, currency: 'SAR',
    categoryConfirmed: false, excludedFromBudget: false, reviewState: 'suggested', isCashTagged: false,
    createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z', ...patch,
  } as Transaction
}

describe('الأنواع التقريبية', () => {
  it('كل عملية غير محددة تتحسب، والمجاميع كلها متاحة، والمحتاج تأكيد معدود', () => {
    const rows = [
      txn('grocery', { categoryId: 'cat-grocery', amountMinor: 12999 }), // واضح ⇒ شراء
      txn('wallet', { categoryId: 'cat-wallets', amountMinor: 50000 }), // غامض ⇒ أول بديل مقترح
      txn('incoming', { observedDirection: 'in', amountMinor: 700000 }), // وارد غامض ⇒ بديل مقترح
      txn('nocat', { amountMinor: 3000 }), // صادر بلا تصنيف ⇒ بديل مقترح
    ]
    const view = withEstimatedKinds(rows, names)
    expect(view.estimatedCount).toBe(4)
    expect(view.needsReviewCount).toBe(3)
    expect(view.transactions.every((t) => t.economicKind !== 'unclassified')).toBe(true)
    const coverage = assessCoverage(view.transactions)
    expect(coverage.unclassified).toBe(0)
    const totals = computePeriodTotals(view.transactions, [])
    expect(totals.personalExpenseMinor).toBeGreaterThanOrEqual(12999)
    expect(totals.incomeMinor + totals.personalExpenseMinor).toBeGreaterThan(0)
  })

  it('من غير أي اقتراح: الصادر مصروف والوارد دخل', () => {
    const view = withEstimatedKinds([txn('in', { observedDirection: 'in', amountMinor: 5000, rawDescription: 'x' })], new Map())
    // الوارد الغامض له بدائل؛ لو اختفت كلها يفضل دخل. في كل الأحوال يُحسب ولا يبقى مجهول.
    expect(view.transactions[0].economicKind).not.toBe('unclassified')
    expect(view.needsReviewCount).toBe(1)
  })

  it('لا يلمس نوعًا أكّده المستخدم، ولا يغيّر العمليات الأصلية', () => {
    const confirmed = txn('mine', { categoryId: 'cat-grocery', economicKind: 'support_gift', economicKindConfirmed: true })
    const original = txn('orig', { categoryId: 'cat-grocery' })
    const view = withEstimatedKinds([confirmed, original], names)
    expect(view.transactions[0]).toBe(confirmed)
    expect(view.transactions[1].economicKind).toBe('purchase')
    expect(original.economicKind).toBe('unclassified')
    expect(view).toMatchObject({ estimatedCount: 1, needsReviewCount: 0 })
  })
})
