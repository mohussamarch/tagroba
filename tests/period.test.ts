import { describe, it, expect } from 'vitest'
import {
  buildPeriod,
  periodForDate,
  clampPaydayToMonth,
  daysInMonth,
  isLeapYear,
  parseIsoDate,
  isValidIsoDate,
  remainingDaysInPeriod,
  daysBetween,
  dayNumberToIso,
  toDayNumber,
  PeriodError,
  DEFAULT_PAYDAY,
} from '../src/domain/period'

describe('التقويم — أساس الحساب', () => {
  it('يعرف السنوات الكبيسة', () => {
    expect(isLeapYear(2028)).toBe(true)
    expect(isLeapYear(2026)).toBe(false)
    expect(isLeapYear(2000)).toBe(true) // قابلة للقسمة على 400
    expect(isLeapYear(1900)).toBe(false) // قرن غير قابل للقسمة على 400
  })

  it('يحسب أيام الشهر بما فيها فبراير الكبيس', () => {
    expect(daysInMonth(2028, 2)).toBe(29)
    expect(daysInMonth(2026, 2)).toBe(28)
    expect(daysInMonth(2026, 9)).toBe(30)
    expect(daysInMonth(2026, 1)).toBe(31)
  })

  it('يرفض التواريخ المستحيلة ولا يكتمها — spec/05', () => {
    expect(() => parseIsoDate('2026-02-30')).toThrow(PeriodError) // من invalid.csv
    expect(() => parseIsoDate('2026-13-01')).toThrow(PeriodError)
    expect(() => parseIsoDate('2026-04-31')).toThrow(PeriodError)
    expect(() => parseIsoDate('2026/09/01')).toThrow(PeriodError)
    expect(isValidIsoDate('2026-02-29')).toBe(false)
    expect(isValidIsoDate('2028-02-29')).toBe(true)
  })

  it('تحويل التاريخ لرقم يوم وبالعكس متطابق تمامًا', () => {
    for (const d of ['1970-01-01', '2025-01-01', '2026-09-04', '2028-02-29', '2100-12-31']) {
      expect(dayNumberToIso(toDayNumber(parseIsoDate(d)))).toBe(d)
    }
    expect(daysBetween('2025-01-01', '2025-01-02')).toBe(1)
    expect(daysBetween('2028-02-28', '2028-03-01')).toBe(2) // يمر بـ 29 فبراير
  })
})

describe('حدود الفترة المالية — يوم الراتب 28', () => {
  it('الفترة العادية تبدأ يوم 28 وتنتهي 27 من الشهر التالي', () => {
    const p = buildPeriod(2026, 9, 28)
    expect(p.start).toBe('2026-09-28')
    expect(p.end).toBe('2026-10-27')
    expect(p.days).toBe(30)
    expect(p.key).toBe('2026-09')
  })

  it('يوم الراتب الافتراضي 28 حسب CLAUDE.md', () => {
    expect(DEFAULT_PAYDAY).toBe(28)
  })

  it('حالة spec/06: فبراير 2028 ويوم راتب 31 — لا يوم 31 فبراير', () => {
    expect(clampPaydayToMonth(2028, 2, 31)).toBe(29) // كبيسة
    expect(clampPaydayToMonth(2026, 2, 31)).toBe(28) // عادية
    expect(clampPaydayToMonth(2026, 4, 31)).toBe(30) // أبريل 30 يومًا

    const feb = buildPeriod(2028, 2, 31)
    expect(feb.start).toBe('2028-02-29')
    expect(feb.end).toBe('2028-03-30') // مارس فيه 31، فالبداية التالية 31 مارس
    expect(feb.days).toBe(31)
    expect(isValidIsoDate(feb.start)).toBe(true)
    expect(isValidIsoDate(feb.end)).toBe(true)
  })

  it('الفترة السابقة لفبراير الكبيس تسلّم له بلا فجوة ولا تداخل', () => {
    const jan = buildPeriod(2028, 1, 31)
    const feb = buildPeriod(2028, 2, 31)
    expect(jan.start).toBe('2028-01-31')
    expect(jan.end).toBe('2028-02-28') // اليوم السابق لـ 29 فبراير
    expect(daysBetween(jan.end, feb.start)).toBe(1) // متلاصقتان بالضبط
  })

  it('يرفض يوم راتب خارج المدى', () => {
    expect(() => clampPaydayToMonth(2026, 1, 0)).toThrow(PeriodError)
    expect(() => clampPaydayToMonth(2026, 1, 32)).toThrow(PeriodError)
  })

  it('لا فجوة ولا تداخل بين أي فترتين متتاليتين على مدى 5 سنوات', () => {
    for (const payday of [1, 15, 28, 29, 30, 31]) {
      let previous = buildPeriod(2024, 1, payday)
      for (let i = 1; i <= 60; i++) {
        const y = 2024 + Math.floor(i / 12)
        const m = (i % 12) + 1
        const current = buildPeriod(y, m, payday)
        expect(daysBetween(previous.end, current.start)).toBe(1)
        previous = current
      }
    }
  })
})

describe('الفترة التي يقع فيها تاريخ', () => {
  it('التاريخ قبل يوم الراتب ينتمي لفترة الشهر السابق', () => {
    expect(periodForDate('2026-09-27', 28).key).toBe('2026-08')
    expect(periodForDate('2026-09-28', 28).key).toBe('2026-09')
    expect(periodForDate('2026-10-01', 28).key).toBe('2026-09')
  })

  it('يعمل عبر حدود السنة', () => {
    expect(periodForDate('2026-01-05', 28).key).toBe('2025-12')
    expect(periodForDate('2025-12-28', 28).key).toBe('2025-12')
  })

  it('كل تاريخ في السنة يقع في فترة واحدة بالضبط', () => {
    for (const payday of [1, 28, 31]) {
      for (let day = 0; day < 365; day++) {
        const iso = dayNumberToIso(toDayNumber(parseIsoDate('2026-01-01')) + day)
        const p = periodForDate(iso, payday)
        const d = toDayNumber(parseIsoDate(iso))
        expect(d).toBeGreaterThanOrEqual(toDayNumber(parseIsoDate(p.start)))
        expect(d).toBeLessThanOrEqual(toDayNumber(parseIsoDate(p.end)))
      }
    }
  })
})

describe('الأيام المتبقية — لا 30 يومًا ثابتة', () => {
  it('يحسب من نطاق الفترة المعلن', () => {
    const p = buildPeriod(2026, 9, 28) // 2026-09-28 → 2026-10-27، 30 يومًا
    expect(remainingDaysInPeriod('2026-09-28', p)).toBe(30) // أول يوم
    expect(remainingDaysInPeriod('2026-10-27', p)).toBe(1) // آخر يوم
    expect(remainingDaysInPeriod('2026-10-28', p)).toBe(0) // بعدها
    expect(remainingDaysInPeriod('2026-09-01', p)).toBe(p.days) // قبلها
  })

  it('فترة فبراير 2028 عدد أيامها ليس 30', () => {
    // 2028-02-28 → 2028-03-27 = يومان في فبراير الكبيس (28، 29) + 27 في مارس
    const p = buildPeriod(2028, 2, 28)
    expect(p.start).toBe('2028-02-28')
    expect(p.end).toBe('2028-03-27')
    expect(p.days).toBe(29)
    expect(p.days).not.toBe(30) // الشاهد: لا يجوز افتراض 30 يومًا
  })
})
