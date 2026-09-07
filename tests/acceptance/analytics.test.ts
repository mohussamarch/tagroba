import { describe, it, expect } from 'vitest'
import {
  categoryDistribution,
  dailyAllowance,
  forecastPeriodSpend,
  assessCoverage,
  MIN_DAYS_FOR_FORECAST,
} from '../../src/domain/analytics'
import { buildPeriod } from '../../src/domain/period'
import { parseMoney, sumMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'
import type { PersonAllocation, Transaction } from '../../src/domain/entities/types'
import type { EconomicKind } from '../../src/domain/entities/economicKind'

let n = 0
function txn(
  kind: EconomicKind,
  amount: string,
  categoryId?: string,
  extra: Partial<Transaction> = {},
): Transaction {
  n++
  return {
    id: `t${n}`,
    occurredAt: '2026-09-01',
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
    ...extra,
  }
}

describe('توزيع التصنيفات — لا مضاعفة ولا فجوة', () => {
  it('مجموع الشرائح يساوي إجمالي المصروف بالضبط', () => {
    const items = [
      txn('purchase', '720.00', 'cat-shopping'),
      txn('purchase', '32.00', 'cat-food'),
      txn('purchase', '18.00', 'cat-food'),
      txn('purchase', '100.00'), // بلا تصنيف
    ]
    const { slices, totalMinor } = categoryDistribution(items)

    expect(formatAmount(totalMinor)).toBe('870.00')
    expect(sumMoney(slices.map((s) => s.amountMinor))).toBe(totalMinor)

    const food = slices.find((s) => s.categoryId === 'cat-food')!
    expect(formatAmount(food.amountMinor)).toBe('50.00') // 32 + 18 مرة واحدة
    expect(food.count).toBe(2)
  })

  it('العمليات بلا تصنيف تُجمع منفصلة ولا تُلحق بتصنيف مخترع', () => {
    const { slices } = categoryDistribution([
      txn('purchase', '100.00'),
      txn('purchase', '50.00', 'cat-food'),
    ])
    const unlabelled = slices.find((s) => s.categoryId === null)!
    expect(formatAmount(unlabelled.amountMinor)).toBe('100.00')
  })

  it('ما ليس مصروفًا لا يدخل التوزيع', () => {
    const { totalMinor, slices } = categoryDistribution([
      txn('purchase', '100.00', 'cat-food'),
      txn('internal_transfer', '5000.00', 'cat-transfers'),
      txn('asset_buy', '2448.06', 'cat-gold'),
      txn('salary', '7000.00'),
      txn('unclassified', '999.00', 'cat-food'),
    ])
    expect(formatAmount(totalMinor)).toBe('100.00')
    expect(slices).toHaveLength(1)
  })

  it('المستبعد من الميزانية لا يدخل التوزيع', () => {
    const { totalMinor } = categoryDistribution([
      txn('purchase', '100.00', 'cat-food'),
      txn('purchase', '900.00', 'cat-food', { excludedFromBudget: true }),
    ])
    expect(formatAmount(totalMinor)).toBe('100.00')
  })

  it('نصيب الشخص يُخصم من التوزيع كما يُخصم من المصروف', () => {
    const bill = txn('purchase', '32.00', 'cat-food')
    const allocations: PersonAllocation[] = [
      {
        id: 'a1',
        transactionId: bill.id,
        personId: 'p1',
        allocationKind: 'receivable',
        amountMinor: parseMoney('20.00'),
        currency: 'SAR',
      },
    ]
    const { totalMinor } = categoryDistribution([bill], allocations)
    expect(formatAmount(totalMinor)).toBe('12.00')
  })

  it('النسب مجموعها 1000 من الألف تقريبًا ولا تتجاوزها', () => {
    const { slices } = categoryDistribution([
      txn('purchase', '750.00', 'a'),
      txn('purchase', '250.00', 'b'),
    ])
    expect(slices[0].shareTenthPercent).toBe(750)
    expect(slices[1].shareTenthPercent).toBe(250)
  })

  it('فترة فاضية ⇒ صفر شرائح لا شريحة صفرية', () => {
    const { slices, totalMinor } = categoryDistribution([])
    expect(slices).toHaveLength(0)
    expect(totalMinor).toBe(0)
  })
})

describe('المتاح اليومي — لا 30 يومًا ثابتة', () => {
  const period = buildPeriod(2026, 9, 28) // 2026-09-28 → 2026-10-27، 30 يومًا

  it('بلا سقف ⇒ غير متاح، ولا سقف مخترع من المتوسط', () => {
    const result = dailyAllowance(null, parseMoney('500.00'), '2026-10-01', period)
    expect(result.amountMinor).toBeNull()
    expect(result.reason).toContain('سقف تحدده انت')
  })

  it('يقسّم المتبقي على الأيام الباقية فعلًا', () => {
    // في 2026-10-01 باقي 27 يوم من 30
    const result = dailyAllowance(parseMoney('3000.00'), parseMoney('300.00'), '2026-10-01', period)
    expect(result.remainingDays).toBe(27)
    expect(formatAmount(result.amountMinor!)).toBe('100.00') // 2700 ÷ 27
  })

  it('تجاوز السقف ⇒ صفر لا سالب', () => {
    const result = dailyAllowance(parseMoney('1000.00'), parseMoney('1500.00'), '2026-10-01', period)
    expect(result.amountMinor).toBe(0)
    expect(result.reason).toContain('خلصت السقف')
  })

  it('بعد نهاية الفترة ⇒ غير متاح لا قسمة على صفر', () => {
    const result = dailyAllowance(parseMoney('3000.00'), 0, '2026-11-01', period)
    expect(result.remainingDays).toBe(0)
    expect(result.amountMinor).toBeNull()
    expect(Number.isNaN(result.amountMinor as number)).toBe(false)
  })

  it('عدد الأيام يتبع الفترة الحقيقية لا رقمًا ثابتًا', () => {
    const feb = buildPeriod(2028, 2, 28) // 29 يومًا
    expect(feb.days).toBe(29)
    const result = dailyAllowance(parseMoney('2900.00'), 0, feb.start, feb)
    expect(result.remainingDays).toBe(29)
    expect(formatAmount(result.amountMinor!)).toBe('100.00')
  })
})

describe('التوقع — بعد تاريخ كافٍ فقط', () => {
  const period = buildPeriod(2026, 9, 28) // 30 يومًا

  it('قبل مرور أيام كافية ⇒ غير متاح، لا رقم مخمَّن', () => {
    const result = forecastPeriodSpend(parseMoney('200.00'), '2026-09-29', period)
    expect(result.projectedMinor).toBeNull()
    expect(result.elapsedDays).toBe(2)
    expect(result.caveat).toContain(String(MIN_DAYS_FOR_FORECAST))
  })

  it('بعد أيام كافية ⇒ توقع من المعدل، مع ذكر قصوره', () => {
    // 10 أيام عدوا من 30، صُرف 1000 ⇒ التوقع 3000
    const result = forecastPeriodSpend(parseMoney('1000.00'), '2026-10-07', period)
    expect(result.elapsedDays).toBe(10)
    expect(result.totalDays).toBe(30)
    expect(formatAmount(result.projectedMinor!)).toBe('3,000.00')
    expect(result.caveat).toContain('نفس المعدل') // القصور مذكور دائمًا
  })

  it('بعد نهاية الفترة ⇒ الرقم فعلي لا توقع، ويُقال ذلك', () => {
    const result = forecastPeriodSpend(parseMoney('2500.00'), '2026-10-27', period)
    expect(formatAmount(result.projectedMinor!)).toBe('2,500.00')
    expect(result.caveat).toContain('مش توقع')
  })

  it('صفر مصروف ⇒ توقع صفر محسوب لا مجهول', () => {
    const result = forecastPeriodSpend(0, '2026-10-07', period)
    expect(result.projectedMinor).toBe(0)
  })

  it('لا يتجاوز عدد الأيام المنقضية طول الفترة', () => {
    const result = forecastPeriodSpend(parseMoney('100.00'), '2027-01-01', period)
    expect(result.elapsedDays).toBe(period.days)
  })
})

describe('تغطية البيانات — تقيس المعروف لا تفترض المجهول', () => {
  it('كل العمليات بلا نوع ⇒ المجاميع غير موثوقة', () => {
    const c = assessCoverage([txn('unclassified', '100.00'), txn('unclassified', '50.00')])
    expect(c.totalsReliable).toBe(false)
    expect(c.unclassified).toBe(2)
    expect(c.note).toContain('غير متاحة')
  })

  it('بعضها بلا نوع ⇒ ناقصة مع ذكر العدد', () => {
    const c = assessCoverage([txn('purchase', '100.00'), txn('unclassified', '50.00')])
    expect(c.totalsReliable).toBe(false)
    expect(c.note).toContain('1 عملية من 2')
  })

  it('كلها محددة ⇒ موثوقة بلا ملاحظة', () => {
    const c = assessCoverage([txn('purchase', '100.00'), txn('salary', '7000.00')])
    expect(c.totalsReliable).toBe(true)
    expect(c.note).toBeNull()
  })

  it('فترة فاضية ⇒ موثوقة، الصفر هنا محسوب', () => {
    const c = assessCoverage([])
    expect(c.totalsReliable).toBe(true)
    expect(c.note).toBeNull()
  })
})
