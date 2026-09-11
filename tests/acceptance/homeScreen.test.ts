import { describe, it, expect } from 'vitest'
import { makeLoadHomeScreen } from '../../src/application/useCases/loadHomeScreen'
import { makeSetEconomicKind } from '../../src/application/useCases/setEconomicKind'
import {
  MemoryAllocationRepository,
  MemoryCategoryRepository,
  MemoryTransactionRepository,
  PassthroughUnitOfWork,
  FixedClock,
} from '../../src/infrastructure/memory/memoryRepositories'
import { buildPeriod } from '../../src/domain/period'
import { parseMoney } from '../../src/domain/money'
import { formatAmount, NOT_AVAILABLE, formatMoneyOrNA } from '../../src/domain/formatMoney'
import type { Category, Transaction } from '../../src/domain/entities/types'
import type { EconomicKind } from '../../src/domain/entities/economicKind'

/**
 * الشاشة الرئيسية — spec/01، و**قاعدة «لا صفر مؤكد مكان المجهول»**.
 *
 * الحالة الوسطى (بعض العمليات محددة وبعضها لا) هي الأخطر، لأن الرقم
 * الناتج **صحيح جزئيًا ومضلِّل كليًا** إن عُرض بلا بيان.
 */

const CATEGORIES: Category[] = [
  { id: 'cat-food', parentId: null, name: 'مطاعم وقهوة', iconKey: 'utensils', lightColor: '#CB428E', darkColor: '#CB428E', active: true, order: 0 },
  { id: 'cat-shop', parentId: null, name: 'تسوق إلكتروني', iconKey: 'bag', lightColor: '#8861CC', darkColor: '#8861CC', active: true, order: 1 },
]

const PERIOD = buildPeriod(2026, 9, 28) // 2026-09-28 → 2026-10-27

let n = 0
function txn(kind: EconomicKind, amount: string, categoryId?: string): Transaction {
  n++
  return {
    id: `t${n}`,
    occurredAt: '2026-10-01',
    datePrecision: 'day',
    sourceOrder: n,
    economicKind: kind,
    economicKindConfirmed: kind !== 'unclassified',
    observedDirection: kind === 'salary' ? 'in' : 'out',
    amountMinor: parseMoney(amount),
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'suggested',
    isCashTagged: false,
    createdAt: '',
    updatedAt: '',
    ...(categoryId ? { categoryId } : {}),
  }
}

async function loadWith(transactions: Transaction[], today = '2026-10-08') {
  const txns = new MemoryTransactionRepository()
  await txns.saveMany(transactions)
  const load = makeLoadHomeScreen({
    txns,
    categories: new MemoryCategoryRepository(CATEGORIES),
    allocations: new MemoryAllocationRepository(),
  })
  return load({ period: PERIOD, today, payday: 28 })
}

describe('الحالات الثلاث للمجاميع', () => {
  // OVERRIDES §18 (تعديل المالك): الأرقام دايمًا ظاهرة «تقريبي» مع عدد المحتاج تأكيد
  it('١. ولا عملية محددة ⇒ الأرقام تقريبية ومتاحة، ومعروض كام عملية محتاجة تأكيد', async () => {
    const data = await loadWith([
      txn('unclassified', '7000.00'),
      txn('unclassified', '720.00', 'cat-shop'),
    ])
    expect(data.expenseMinor).not.toBeNull()
    expect(data.incomeMinor).not.toBeNull()
    expect(data.remainingMinor).not.toBeNull()
    expect(formatMoneyOrNA(data.expenseMinor)).not.toBe(NOT_AVAILABLE)
    expect(data.estimatedCount).toBe(2)
    expect(data.needsReviewCount).toBe(1) // المشتريات بتصنيف واضحة؛ الـ7000 بلا دليل
    expect(data.forecast.caveat ?? '').not.toContain('تحدّد أنواع')
    expect(data.allowance.reason ?? '').not.toContain('حدّد أنواع')
  })

  it('٢. بعضها محدد ⇒ كل الأرقام متاحة، والمؤكَّد محسوب بنوعه، والباقي تقريبي معدود', async () => {
    const data = await loadWith([
      txn('unclassified', '7000.00'), // راتب لم يُحدَّد بعد
      txn('purchase', '720.00', 'cat-shop'),
      txn('purchase', '32.00', 'cat-food'),
    ])

    expect(data.partial).toBe(false) // مفيش حاجة مجهولة في الحساب
    expect(data.expenseMinor! >= 75200).toBe(true) // المحدد محسوب دايمًا
    expect(data.remainingMinor).not.toBeNull()
    expect(data.estimatedCount).toBe(1)
    expect(data.needsReviewCount).toBe(1)
  })

  it('٣. كلها محددة ⇒ كل شيء متاح وصحيح', async () => {
    const data = await loadWith([
      txn('salary', '7000.00'),
      txn('purchase', '720.00', 'cat-shop'),
      txn('purchase', '32.00', 'cat-food'),
    ])
    expect(data.partial).toBe(false)
    expect(formatAmount(data.incomeMinor!)).toBe('7,000.00')
    expect(formatAmount(data.expenseMinor!)).toBe('752.00')
    expect(formatAmount(data.remainingMinor!)).toBe('6,248.00')
    expect(data.savingsRatePercent).toBeCloseTo(89.3, 1)
    expect(data.coverage.note).toBeNull()
  })

  it('فترة فاضية ⇒ أصفار محسوبة لا مجهولة', async () => {
    const data = await loadWith([])
    expect(data.expenseMinor).toBe(0)
    expect(data.remainingMinor).toBe(0)
    expect(data.partial).toBe(false)
    expect(data.transactionCount).toBe(0)
  })
})

describe('محتويات الشاشة', () => {
  it('التوزيع والأحدث وآخر ست فترات', async () => {
    const data = await loadWith([
      txn('salary', '7000.00'),
      txn('purchase', '720.00', 'cat-shop'),
      txn('purchase', '32.00', 'cat-food'),
    ])

    expect(data.distribution).toHaveLength(2)
    expect(data.distribution[0].categoryId).toBe('cat-shop') // الأكبر أولًا
    expect(data.latest.length).toBeGreaterThan(0)
    expect(data.recentPeriods).toHaveLength(6)
    expect(data.recentPeriods[0].period.key).toBe(PERIOD.key)
    // الفترات السابقة فاضية ومحسوبة صفرًا لا مجهولة
    expect(data.recentPeriods[1].transactionCount).toBe(0)
    expect(data.recentPeriods[1].expenseMinor).toBe(0)
    expect(data.periodRange).toBe(`${PERIOD.start} ← ${PERIOD.end}`)
  })

  /* OVERRIDES §19: بلا سقف الرقم بيظهر تقريبي من المتبقي — مش «غير متاح». */
  it('المتاح اليومي تقريبي بلا سقف، والتوقع يذكر قصوره', async () => {
    const data = await loadWith([txn('purchase', '752.00', 'cat-food')])
    expect(data.allowance.amountMinor).not.toBeNull()
    expect(data.allowance.approximate).toBe(true)
    expect(data.allowance.reason).toContain('تقريبي')
    expect(data.forecast.projectedMinor).not.toBeNull()
    expect(data.forecast.caveat.length).toBeGreaterThan(20)
  })
})

describe('دورة كاملة: استيراد ⇒ اقتراح ⇒ تأكيد ⇒ أرقام متاحة', () => {
  it('التأكيد الجماعي يحوّل الرقم التقريبي إلى مؤكد', async () => {
    const txns = new MemoryTransactionRepository()
    const categories = new MemoryCategoryRepository(CATEGORIES)
    const allocations = new MemoryAllocationRepository()

    const rows = [
      txn('unclassified', '720.00', 'cat-shop'),
      txn('unclassified', '32.00', 'cat-food'),
    ]
    await txns.saveMany(rows)

    const load = makeLoadHomeScreen({ txns, categories, allocations })
    const before = await load({ period: PERIOD, today: '2026-10-08', payday: 28 })
    // تقريبي قبل التأكيد: الرقم ظاهر ومعلَّم إن نوعه اتحدد تلقائي (OVERRIDES §18)
    expect(formatAmount(before.expenseMinor!)).toBe('752.00')
    expect(before.estimatedCount).toBe(2)

    const kinds = makeSetEconomicKind({
      txns,
      categories,
      uow: new PassthroughUnitOfWork(),
      clock: new FixedClock('2026-10-08T00:00:00.000Z'),
    })

    // الاقتراح مبني على التصنيف الذي يعرفه التطبيق
    const summary = await kinds.summarize(rows)
    expect(summary.confirmable).toHaveLength(2)
    expect(summary.confirmable.every((l) => l.suggestion.kind === 'purchase')).toBe(true)

    // لا شيء يُكتب في العمليات قبل موافقة صريحة — التقريبي للعرض فقط
    const stored = await txns.listByDateRange(PERIOD.start, PERIOD.end)
    expect(stored.every((t) => t.economicKind === 'unclassified')).toBe(true)

    const result = await kinds.confirmBulk(
      rows,
      summary.confirmable.map((l) => l.transaction.id),
    )
    expect(result.applied).toBe(2)

    const after = await load({ period: PERIOD, today: '2026-10-08', payday: 28 })
    expect(formatAmount(after.expenseMinor!)).toBe('752.00')
    expect(after.estimatedCount).toBe(0) // بعد التأكيد الرقم مؤكد مش تقريبي
    expect(after.partial).toBe(false)
    expect(after.coverage.totalsReliable).toBe(true)
  })

  it('التأكيد الجماعي لا يلمس الغامض حتى لو طُلب', async () => {
    const txns = new MemoryTransactionRepository()
    const categories = new MemoryCategoryRepository(CATEGORIES)
    const incoming = txn('unclassified', '7000.00')
    incoming.observedDirection = 'in'
    await txns.saveMany([incoming])

    const kinds = makeSetEconomicKind({
      txns,
      categories,
      uow: new PassthroughUnitOfWork(),
      clock: new FixedClock('2026-10-08T00:00:00.000Z'),
    })

    const summary = await kinds.summarize([incoming])
    expect(summary.ambiguous).toHaveLength(1)
    expect(summary.confirmable).toHaveLength(0)

    // نطلب تأكيده صراحةً — ومع ذلك لا يُطبَّق
    const result = await kinds.confirmBulk([incoming], [incoming.id])
    expect(result.applied).toBe(0)
    expect(result.skipped).toBe(1)

    const stored = (await txns.findByIds([incoming.id]))[0]
    expect(stored.economicKind).toBe('unclassified')
  })

  it('التحديد الفردي يرفض ما يناقض اتجاه الحركة', async () => {
    const txns = new MemoryTransactionRepository()
    const outgoing = txn('unclassified', '100.00')
    await txns.saveMany([outgoing])

    const kinds = makeSetEconomicKind({
      txns,
      categories: new MemoryCategoryRepository(CATEGORIES),
      uow: new PassthroughUnitOfWork(),
      clock: new FixedClock('2026-10-08T00:00:00.000Z'),
    })

    // حركة صادرة لا يجوز وسمها «مرتب»
    await expect(kinds.setOne(outgoing.id, 'salary')).rejects.toThrow(/صادر/)

    // والصحيح يمر
    await kinds.setOne(outgoing.id, 'purchase')
    const stored = (await txns.findByIds([outgoing.id]))[0]
    expect(stored.economicKind).toBe('purchase')
    expect(stored.economicKindConfirmed).toBe(true)
  })
})

import { MemoryBudgetRepository } from '../../src/infrastructure/memory/memoryBudgetRepository'
it('uses the saved budget for the selected period, including edits and removal',async()=>{
 const budgets=new MemoryBudgetRepository(), txns=new MemoryTransactionRepository()
 const load=makeLoadHomeScreen({txns,budgets,categories:new MemoryCategoryRepository(),allocations:new MemoryAllocationRepository()})
 const budget={id:'budget',periodKey:PERIOD.key,periodStart:PERIOD.start,periodEnd:PERIOD.end,totalLimitMinor:30000,thresholdPercent:80,createdAt:'',updatedAt:''}
 await budgets.save(budget)
 const options={period:PERIOD,today:PERIOD.end,payday:28}
 expect((await load(options)).allowance.amountMinor).toBe(30000)
 await budgets.save({...budget,totalLimitMinor:12000})
 expect((await load(options)).allowance.amountMinor).toBe(12000)
 // فترة تانية بلا سقف: مفيش عمليات ⇒ المتبقي صفر ⇒ رقم تقريبي صفر (OVERRIDES §19)
 const other=buildPeriod(2026,8,28)
 const otherAllowance=(await load({...options,period:other,today:other.end})).allowance
 expect(otherAllowance.amountMinor).toBe(0)
 expect(otherAllowance.approximate).toBe(true)
 await budgets.remove(PERIOD.key)
 const afterRemove=(await load(options)).allowance
 expect(afterRemove.amountMinor).toBe(0)
 expect(afterRemove.approximate).toBe(true)
})
