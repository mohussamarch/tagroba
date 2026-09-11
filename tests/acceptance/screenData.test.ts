import { describe, it, expect } from 'vitest'
import { makeLoadTransactionsScreen } from '../../src/application/useCases/loadTransactionsScreen'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryTransactionRepository,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildCategories } from '../../src/infrastructure/import/referenceLoader'
import { parseMoney } from '../../src/domain/money'
import { formatMoneyOrNA, NOT_AVAILABLE } from '../../src/domain/formatMoney'
import { buildPeriod } from '../../src/domain/period'
import type { Transaction } from '../../src/domain/entities/types'
import type { EconomicKind } from '../../src/domain/entities/economicKind'

/**
 * CLAUDE.md #10: «لا رقم بلا مصدر. لا صفر مؤكد مكان المجهول.»
 *
 * العملية المستوردة تبدأ بنوع اقتصادي غير محدد (spec/02 يمنع استنتاجه
 * من اتجاه السيولة)، فلو كل عمليات الفترة كذلك فالمجاميع **مجهولة لا صفر**.
 */

let n = 0
function txn(kind: EconomicKind, amount: string, direction: 'in' | 'out', date = '2026-09-01'): Transaction {
  n++
  return {
    id: `t${n}`,
    occurredAt: date,
    datePrecision: 'day',
    sourceOrder: n,
    economicKind: kind,
    economicKindConfirmed: kind !== 'unclassified',
    observedDirection: direction,
    amountMinor: parseMoney(amount),
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'suggested',
    isCashTagged: false,
    createdAt: '',
    updatedAt: '',
  }
}

async function loadWith(transactions: Transaction[]) {
  const txns = new MemoryTransactionRepository()
  await txns.saveMany(transactions)
  const load = makeLoadTransactionsScreen({
    txns,
    categories: new MemoryCategoryRepository(buildCategories([])),
    allocations: new MemoryAllocationRepository(),
  })
  return load({ period: buildPeriod(2026, 8, 28) }) // 2026-08-28 → 2026-09-27
}

describe('بيانات شاشة العمليات — لا صفر مكان المجهول', () => {
  it('فترة فاضية ⇒ صفر محسوب لا مجهول', async () => {
    const data = await loadWith([])
    expect(data.totalCount).toBe(0)
    expect(data.incomeMinor).toBe(0) // صفر حقيقي: مفيش عمليات أصلًا
    expect(data.remainingMinor).toBe(0)
    expect(data.savingsRatePercent).toBeNull() // دخل صفر ⇒ النسبة غير متاحة
  })

  it('كل العمليات غير محددة النوع ⇒ الأرقام غير متاحة لا صفر', async () => {
    const data = await loadWith([
      txn('unclassified', '7000.00', 'in'),
      txn('unclassified', '32.00', 'out'),
    ])
    expect(data.totalCount).toBe(2)
    expect(data.unclassifiedCount).toBe(2)

    expect(data.incomeMinor).toBeNull()
    expect(data.expenseMinor).toBeNull()
    expect(data.remainingMinor).toBeNull()
    expect(data.savingsRatePercent).toBeNull()

    // العرض يقول «غير متاح» صراحةً، لا 0.00
    expect(formatMoneyOrNA(data.incomeMinor)).toBe(NOT_AVAILABLE)
    expect(formatMoneyOrNA(data.remainingMinor)).toBe(NOT_AVAILABLE)
  })

  it('تحديد نوع عملية واحدة ⇒ الأرقام تُحسب وتُعلَن ناقصة', async () => {
    const data = await loadWith([
      txn('salary', '7000.00', 'in'),
      txn('unclassified', '32.00', 'out'),
    ])
    expect(data.unclassifiedCount).toBe(1)
    expect(data.totalCount).toBe(2)

    expect(data.incomeMinor).toBe(parseMoney('7000.00'))
    // OVERRIDES §18: غير المحدد يتحسب تقريبي (صادر بلا دليل ⇒ مصروف) ومعدود «محتاج تأكيد»
    expect(data.expenseMinor).toBe(parseMoney('32.00'))
    expect(data.remainingMinor).toBe(parseMoney('6968.00'))
    expect(data).toMatchObject({ estimatedCount: 1, needsReviewCount: 1 })
  })

  it('كل العمليات محددة ⇒ لا تنبيه نقص', async () => {
    const data = await loadWith([
      txn('salary', '7000.00', 'in'),
      txn('purchase', '852.00', 'out'),
    ])
    expect(data.unclassifiedCount).toBe(0)
    expect(data.incomeMinor).toBe(parseMoney('7000.00'))
    expect(data.expenseMinor).toBe(parseMoney('852.00'))
    expect(data.remainingMinor).toBe(parseMoney('6148.00'))
    expect(data.savingsRatePercent).toBeCloseTo(87.8, 1)
  })

  it('القراءة محدودة بالفترة — عملية خارجها لا تظهر', async () => {
    const data = await loadWith([
      txn('salary', '7000.00', 'in', '2026-09-01'), // داخل
      txn('salary', '9999.00', 'in', '2026-10-15'), // خارج
    ])
    expect(data.totalCount).toBe(1)
    expect(data.incomeMinor).toBe(parseMoney('7000.00'))
  })
})
