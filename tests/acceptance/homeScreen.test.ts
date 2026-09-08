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
  it('١. ولا عملية محددة ⇒ كل شيء غير متاح', async () => {
    const data = await loadWith([
      txn('unclassified', '7000.00'),
      txn('unclassified', '720.00', 'cat-shop'),
    ])
    expect(data.expenseMinor).toBeNull()
    expect(data.incomeMinor).toBeNull()
    expect(data.remainingMinor).toBeNull()
    expect(data.savingsRatePercent).toBeNull()
    expect(data.partial).toBe(false) // ليست جزئية، بل مجهولة كليًا
    expect(data.forecast.projectedMinor).toBeNull()
    expect(data.allowance.amountMinor).toBeNull()
    expect(formatMoneyOrNA(data.expenseMinor)).toBe(NOT_AVAILABLE)
  })

  it('٢. بعضها محدد ⇒ الدخل والمصروف جزئيان، والمتبقي **null** لا رقم مضلِّل', async () => {
    const data = await loadWith([
      txn('unclassified', '7000.00'), // راتب لم يُحدَّد بعد
      txn('purchase', '720.00', 'cat-shop'),
      txn('purchase', '32.00', 'cat-food'),
    ])

    expect(data.partial).toBe(true)
    expect(formatAmount(data.expenseMinor!)).toBe('752.00') // صحيح لحد دلوقتي
    expect(data.incomeMinor).toBe(0) // لا دخل مؤكد بعد

    // الحاسم: المتبقي ومعدل الادخار **لا يُعرضان**، لأن الدخل ناقص
    // وعرض «−752.00» يوحي بعجز غير موجود
    expect(data.remainingMinor).toBeNull()
    expect(data.savingsRatePercent).toBeNull()

    expect(data.forecast.projectedMinor).toBeNull()
    expect(data.allowance.amountMinor).toBeNull()
    expect(data.coverage.totalsReliable).toBe(false)
    expect(data.coverage.note).toContain('1 عملية من 3')
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

  it('المتاح اليومي غير متاح بلا سقف، والتوقع يذكر قصوره', async () => {
    const data = await loadWith([txn('purchase', '752.00', 'cat-food')])
    expect(data.allowance.amountMinor).toBeNull()
    expect(data.allowance.reason).toContain('سقف')
    expect(data.forecast.projectedMinor).not.toBeNull()
    expect(data.forecast.caveat.length).toBeGreaterThan(20)
  })
})

describe('دورة كاملة: استيراد ⇒ اقتراح ⇒ تأكيد ⇒ أرقام متاحة', () => {
  it('التأكيد الجماعي يحوّل «غير متاح» إلى رقم حقيقي', async () => {
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
    expect(before.expenseMinor).toBeNull()

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

    // لا شيء يُطبَّق قبل موافقة صريحة
    const stillNull = await load({ period: PERIOD, today: '2026-10-08', payday: 28 })
    expect(stillNull.expenseMinor).toBeNull()

    const result = await kinds.confirmBulk(
      rows,
      summary.confirmable.map((l) => l.transaction.id),
    )
    expect(result.applied).toBe(2)

    const after = await load({ period: PERIOD, today: '2026-10-08', payday: 28 })
    expect(formatAmount(after.expenseMinor!)).toBe('752.00')
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
