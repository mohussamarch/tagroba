import { describe, it, expect } from 'vitest'
import {
  budgetStatus,
  averageCompletedSpend,
  detectAnomaly,
  buildCategoryLines,
  MIN_PERIODS_FOR_AVERAGE,
  MIN_HISTORY_FOR_ANOMALY,
  type CompletedPeriodSpend,
} from '../../src/domain/budget'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'

/**
 * spec/01: «**لا تُخلق ميزانيات من متوسطات دون اختيار المستخدم**»
 * spec/06: «تصنيف بلا سقف/تاريخ ⇒ لا سقف أو متوسط مخترع ولا تنبيه عتبة»
 */

const sar = parseMoney

describe('حالة السقف', () => {
  it('تحسب المستهلك والمتبقي والنسبة', () => {
    const s = budgetStatus(sar('3000.00'), sar('750.00'), null)
    expect(formatAmount(s.remainingMinor)).toBe('2,250.00')
    expect(s.usedTenthPercent).toBe(250) // ٢٥٪
    expect(s.level).toBe('under')
  })

  it('تجاوز السقف يظهر سالبًا ولا يُخفى', () => {
    const s = budgetStatus(sar('1000.00'), sar('1500.00'), null)
    expect(formatAmount(s.remainingMinor)).toBe('-500.00')
    expect(s.level).toBe('over')
    expect(s.usedTenthPercent).toBe(1500)
  })

  it('الاقتراب من السقف حالة مستقلة', () => {
    expect(budgetStatus(sar('1000.00'), sar('920.00'), null).level).toBe('near')
    expect(budgetStatus(sar('1000.00'), sar('899.00'), null).level).toBe('under')
    expect(budgetStatus(sar('1000.00'), sar('1000.00'), null).level).toBe('over')
  })

  it('بلا عتبة يحددها المستخدم ⇒ لا تنبيه مهما بلغ الصرف — spec/06', () => {
    const s = budgetStatus(sar('1000.00'), sar('9999.00'), null)
    expect(s.thresholdCrossed).toBe(false)
  })

  it('بعتبة محددة ⇒ التنبيه عند تجاوزها فقط', () => {
    expect(budgetStatus(sar('1000.00'), sar('790.00'), 80).thresholdCrossed).toBe(false)
    expect(budgetStatus(sar('1000.00'), sar('800.00'), 80).thresholdCrossed).toBe(true)
  })

  it('سقف صفر أو سالب مرفوض — لا قسمة على صفر', () => {
    expect(() => budgetStatus(0, sar('10.00'), null)).toThrow()
    expect(() => budgetStatus(-100, sar('10.00'), null)).toThrow()
  })
})

describe('المتوسط من الفترات المكتملة — spec/01', () => {
  const p = (key: string, amount: string, reliable = true, count = 5): CompletedPeriodSpend => ({
    periodKey: key,
    spentMinor: sar(amount),
    reliable,
    transactionCount: count,
  })

  it('أقل من ثلاث فترات ⇒ غير متاح، لا متوسط مخترع', () => {
    const r = averageCompletedSpend([p('2026-01', '1000.00'), p('2026-02', '2000.00')])
    expect(r.averageMinor).toBeNull()
    expect(r.reason).toContain(String(MIN_PERIODS_FOR_AVERAGE))
  })

  it('ثلاث فترات موثوقة ⇒ متوسط صحيح', () => {
    const r = averageCompletedSpend([
      p('2026-01', '1000.00'),
      p('2026-02', '2000.00'),
      p('2026-03', '3000.00'),
    ])
    expect(formatAmount(r.averageMinor!)).toBe('2,000.00')
    expect(r.usedPeriods).toHaveLength(3)
  })

  it('الفترة نصف المحددة **تُستبعد** ويُذكر سبب استبعادها', () => {
    const r = averageCompletedSpend([
      p('2026-01', '1000.00'),
      p('2026-02', '2000.00'),
      p('2026-03', '3000.00'),
      p('2026-04', '100.00', false), // ناقصة
    ])
    expect(r.usedPeriods).not.toContain('2026-04')
    expect(formatAmount(r.averageMinor!)).toBe('2,000.00') // لم تسحبه لأسفل
    expect(r.excluded[0].periodKey).toBe('2026-04')
    expect(r.excluded[0].reason).toContain('ناقص')
  })

  it('الفترة الفاضية تُستبعد — صفرها غامض لا مؤكد', () => {
    const r = averageCompletedSpend([
      p('2026-01', '1000.00'),
      p('2026-02', '2000.00'),
      p('2026-03', '3000.00'),
      p('2026-04', '0.00', true, 0),
    ])
    expect(r.usedPeriods).toHaveLength(3)
    expect(r.excluded[0].reason).toContain('مش واضح')
  })

  it('استبعاد يهبط بالعدد تحت الحد ⇒ المتوسط غير متاح', () => {
    const r = averageCompletedSpend([
      p('2026-01', '1000.00'),
      p('2026-02', '2000.00', false),
      p('2026-03', '3000.00', false),
    ])
    expect(r.averageMinor).toBeNull()
    expect(r.excluded).toHaveLength(2)
  })

  it('قائمة فاضية ⇒ غير متاح', () => {
    expect(averageCompletedSpend([]).averageMinor).toBeNull()
  })
})

describe('الشذوذ — «ما نعرفش» غير «مفيش شذوذ»', () => {
  it('تاريخ غير كافٍ ⇒ null لا false', () => {
    const r = detectAnomaly(sar('9000.00'), [sar('100.00'), sar('110.00')])
    expect(r.isAnomaly).toBeNull() // ليست false
    expect(r.reason).toContain(String(MIN_HISTORY_FOR_ANOMALY))
  })

  it('قيمة بعيدة جدًا عن تاريخ مستقر ⇒ شذوذ', () => {
    const r = detectAnomaly(sar('5000.00'), [
      sar('1000.00'),
      sar('1050.00'),
      sar('980.00'),
      sar('1020.00'),
    ])
    expect(r.isAnomaly).toBe(true)
    expect(r.reason).toContain('أعلى')
    expect(formatAmount(r.medianMinor!)).toBe('1,010.00')
  })

  it('قيمة داخل التقلب المعتاد ⇒ ليست شذوذًا', () => {
    const r = detectAnomaly(sar('1100.00'), [
      sar('1000.00'),
      sar('1050.00'),
      sar('980.00'),
      sar('1020.00'),
    ])
    expect(r.isAnomaly).toBe(false)
  })

  it('فرق أقل من ٢٠٪ ليس شذوذًا مهما كان التاريخ ثابتًا', () => {
    const r = detectAnomaly(sar('1100.00'), [sar('1000.00'), sar('1000.00'), sar('1000.00')])
    expect(r.isAnomaly).toBe(false)
    expect(r.reason).toContain('أقل من ٢٠٪')
  })

  it('تاريخ ثابت تمامًا وفرق كبير ⇒ شذوذ', () => {
    const r = detectAnomaly(sar('2000.00'), [sar('1000.00'), sar('1000.00'), sar('1000.00')])
    expect(r.isAnomaly).toBe(true)
  })

  it('الوسيط لا يتأثر بشهر استثنائي واحد — ولذلك اختير', () => {
    // شهر سفر 20 ألف وسط أشهر ألف
    const history = [sar('1000.00'), sar('20000.00'), sar('1000.00'), sar('1000.00')]
    const r = detectAnomaly(sar('5000.00'), history)
    // المتوسط الحسابي 5750 كان سيخفي هذا؛ الوسيط 1000 يكشفه
    expect(formatAmount(r.medianMinor!)).toBe('1,000.00')
    expect(r.isAnomaly).toBe(true)
  })

  it('وسيط صفر ⇒ لا حكم، لا قسمة على صفر', () => {
    const r = detectAnomaly(sar('500.00'), [0, 0, 0])
    expect(r.isAnomaly).toBeNull()
    expect(r.reason).toContain('صفر')
  })

  it('الانخفاض الكبير شذوذ أيضًا، لا الارتفاع وحده', () => {
    const r = detectAnomaly(sar('100.00'), [
      sar('1000.00'),
      sar('1050.00'),
      sar('980.00'),
      sar('1020.00'),
    ])
    expect(r.isAnomaly).toBe(true)
    expect(r.reason).toContain('أقل')
    expect(r.deviationMinor).toBeLessThan(0)
  })
})

describe('سطور شاشة الميزانية', () => {
  const spend = new Map([
    ['cat-food', sar('1200.00')],
    ['cat-shop', sar('300.00')],
  ])

  it('تصنيف بلا سقف يُعرض بمصروفه، بلا حالة سقف وبلا تنبيه — spec/06', () => {
    const lines = buildCategoryLines(spend, new Map(), new Map(), new Map())
    const food = lines.find((l) => l.categoryId === 'cat-food')!
    expect(food.status).toBeNull()
    expect(food.noLimitReason).toContain('مفيش سقف')
    expect(formatAmount(food.spentMinor)).toBe('1,200.00')
    expect(food.averageMinor).toBeNull() // لا متوسط مخترع
    expect(food.anomaly.isAnomaly).toBeNull() // لا حكم بلا تاريخ
  })

  it('تصنيف بسقف يُعرض بحالته', () => {
    const lines = buildCategoryLines(
      spend,
      new Map([['cat-food', { limitMinor: sar('1000.00'), thresholdPercent: 80 }]]),
      new Map(),
      new Map(),
    )
    const food = lines.find((l) => l.categoryId === 'cat-food')!
    expect(food.status!.level).toBe('over')
    expect(food.status!.thresholdCrossed).toBe(true)
    expect(formatAmount(food.status!.remainingMinor)).toBe('-200.00')
  })

  it('السقف بلا صرف يظهر بصفر محسوب لا مجهول', () => {
    const lines = buildCategoryLines(
      new Map(),
      new Map([['cat-new', { limitMinor: sar('500.00'), thresholdPercent: null }]]),
      new Map(),
      new Map(),
    )
    expect(lines).toHaveLength(1)
    expect(lines[0].spentMinor).toBe(0)
    expect(lines[0].status!.level).toBe('under')
  })

  it('السطور مرتبة بالأكبر صرفًا', () => {
    const lines = buildCategoryLines(spend, new Map(), new Map(), new Map())
    expect(lines[0].categoryId).toBe('cat-food')
    expect(lines[1].categoryId).toBe('cat-shop')
  })
})
