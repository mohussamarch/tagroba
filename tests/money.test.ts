import { describe, it, expect } from 'vitest'
import {
  parseMoney,
  tryParseMoney,
  addMoney,
  subtractMoney,
  sumMoney,
  splitMoney,
  rateOfMoney,
  multiplyMoneyByInt,
  withinRelativeTolerance,
  normalizeDigits,
  MoneyError,
} from '../src/domain/money'
import {
  formatAmount,
  formatMoney,
  formatMoneyOrNA,
  savingsRatePercent,
  formatPercentOrNA,
  NOT_AVAILABLE,
} from '../src/domain/formatMoney'

describe('parseMoney — التحويل إلى هللة', () => {
  it('يحوّل المبالغ العادية بدقة', () => {
    expect(parseMoney('96.47')).toBe(9647)
    expect(parseMoney('0.01')).toBe(1)
    expect(parseMoney('0')).toBe(0)
    expect(parseMoney('1000')).toBe(100000)
    expect(parseMoney('4837.83')).toBe(483783)
  })

  it('يتجنّب خطأ العشري الذي يصيب ٩٪ من المبالغ', () => {
    // البرهان: الطريقة الساذجة تفشل على قيم حقيقية
    expect(parseFloat('0.29') * 100).not.toBe(29) // = 28.999999999999996
    expect(parseFloat('0.07') * 100).not.toBe(7) // = 7.000000000000001
    // وطريقتنا تنجح على كل مبلغ حتى 2000 ريال بلا استثناء
    let naiveFailures = 0
    for (let h = 1; h <= 200_000; h++) {
      const text = (h / 100).toFixed(2)
      if (parseFloat(text) * 100 !== h) naiveFailures++
      expect(parseMoney(text)).toBe(h)
    }
    expect(naiveFailures).toBeGreaterThan(18_000)
  })

  it('الحالة الكلاسيكية 0.1 + 0.2', () => {
    expect(0.1 + 0.2).not.toBe(0.3)
    expect(addMoney(parseMoney('0.10'), parseMoney('0.20'))).toBe(parseMoney('0.30'))
  })

  it('يكمّل خانة كسرية ناقصة', () => {
    expect(parseMoney('12.5')).toBe(1250)
    expect(parseMoney('12.')).toBe(1200)
    expect(parseMoney('.5')).toBe(50)
  })

  it('يقبل فواصل الآلاف', () => {
    expect(parseMoney('1,234.56')).toBe(123456)
    expect(parseMoney('302,171.45')).toBe(30217145)
  })

  it('يقبل الأرقام العربية والفاصلة العربية', () => {
    expect(parseMoney('٩٦٫٤٧')).toBe(9647)
    expect(parseMoney('٨٥٫٩٩')).toBe(8599)
    expect(normalizeDigits('٠١٢٣٤٥٦٧٨٩')).toBe('0123456789')
  })

  it('يتعامل مع الإشارة السالبة بكل صيغها', () => {
    expect(parseMoney('-12.40')).toBe(-1240)
    expect(parseMoney('12.40-')).toBe(-1240)
    expect(parseMoney('(12.40)')).toBe(-1240)
    expect(parseMoney('+12.40')).toBe(1240)
  })

  it('يرفض المدخلات الفاسدة ولا يبتلعها', () => {
    // ملاحظة: فواصل الآلاف تُزال قبل التحقق عمدًا، فـ '12,,' مقبول كـ 12.
    for (const bad of ['', '   ', 'NaN', 'Infinity', 'abc', '1.2.3', '5-3', '١٢أ']) {
      expect(() => parseMoney(bad)).toThrow(MoneyError)
      expect(tryParseMoney(bad)).toBeNull()
    }
  })

  it('يرفض خانة كسرية ثالثة غير صفرية ولا يقرّب بصمت', () => {
    expect(() => parseMoney('12.345')).toThrow(MoneyError)
    expect(parseMoney('12.340')).toBe(1234) // الصفر الزائد مقبول
  })
})

describe('الحساب — لا فقد ولا خلق هللة', () => {
  it('يجمع ويطرح بدقة', () => {
    expect(addMoney(9647, 7955, 1400)).toBe(19002)
    expect(subtractMoney(483783, 9647)).toBe(474136) // أول سطر في الكشف
    expect(sumMoney([])).toBe(0)
  })

  it('القسمة تحفظ المجموع بالضبط', () => {
    for (const [amount, parts] of [
      [3200, 3],
      [10000, 7],
      [1, 3],
      [100, 3],
      [30217145, 13],
    ] as const) {
      const shares = splitMoney(amount, parts)
      expect(shares).toHaveLength(parts)
      expect(sumMoney(shares)).toBe(amount)
    }
  })

  it('القسمة السالبة تحفظ المجموع أيضًا', () => {
    const shares = splitMoney(-100, 3)
    expect(sumMoney(shares)).toBe(-100)
  })

  it('النسبة تُقرَّب مرة واحدة عند حد معلن', () => {
    expect(rateOfMoney(10000, 5, 100)).toBe(500) // ٥٪ من 100 ريال = 5 ريال
    expect(rateOfMoney(101, 50, 100)).toBe(51) // 50.5 → 51 (نصف لأعلى)
    expect(rateOfMoney(0, 50, 100)).toBe(0)
    expect(() => rateOfMoney(100, 1, 0)).toThrow(MoneyError)
  })

  it('الضرب يقبل الأعداد الصحيحة فقط', () => {
    expect(multiplyMoneyByInt(2999, 12)).toBe(35988) // اشتراك شهري × 12
    expect(() => multiplyMoneyByInt(100, 1.5)).toThrow(MoneyError)
  })

  it('يرفض المبالغ غير الصحيحة', () => {
    expect(() => addMoney(1.5)).toThrow(MoneyError)
    expect(() => addMoney(NaN)).toThrow(MoneyError)
    expect(() => addMoney(Infinity)).toThrow(MoneyError)
  })
})

describe('البحث بالمبلغ ±5% — spec/06', () => {
  it('يطابق داخل الهامش ويرفض خارجه', () => {
    const target = parseMoney('85.99') // 8599
    expect(withinRelativeTolerance(8599, target, 50)).toBe(true)
    expect(withinRelativeTolerance(8600, target, 50)).toBe(true)
    expect(withinRelativeTolerance(9028, target, 50)).toBe(true) // +4.99%
    expect(withinRelativeTolerance(9200, target, 50)).toBe(false) // +7%
    expect(withinRelativeTolerance(8000, target, 50)).toBe(false) // -7%
  })
})

describe('العرض — التحويل للريال يحدث هنا فقط', () => {
  it('ينسّق بدقة الهللة', () => {
    expect(formatAmount(9647)).toBe('96.47')
    expect(formatAmount(0)).toBe('0.00')
    expect(formatAmount(1)).toBe('0.01')
    expect(formatAmount(-9647)).toBe('-96.47')
    expect(formatAmount(30217145)).toBe('302,171.45')
    expect(formatAmount(10046827)).toBe('100,468.27')
    expect(formatAmount(270725)).toBe('2,707.25')
  })

  it('لا يظهر أي خطأ عشري في أي مبلغ حتى 10 آلاف ريال', () => {
    for (let h = 0; h <= 1_000_000; h += 1) {
      if (h % 997 !== 0) continue // عينة كثيفة لكن سريعة
      const text = formatAmount(h, 'SAR', { grouping: false })
      expect(parseMoney(text)).toBe(h)
    }
  })

  it('يضيف رمز العملة ويخفي المبالغ عند الطلب', () => {
    expect(formatMoney(9647)).toBe('96.47 ر.س')
    expect(formatMoney(9647, 'SAR', { showCurrency: false })).toBe('96.47')
    expect(formatMoney(9647, 'SAR', { hidden: true })).toBe('•••• ر.س')
    expect(formatMoney(9647, 'SAR', { alwaysSign: true })).toBe('+96.47 ر.س')
  })

  it('غير المتاح يُعرض نصًا لا صفرًا', () => {
    expect(formatMoneyOrNA(null)).toBe(NOT_AVAILABLE)
    expect(formatMoneyOrNA(undefined)).toBe(NOT_AVAILABLE)
    expect(formatMoneyOrNA(0)).toBe('0.00 ر.س') // الصفر المحسوب يُعرض صفرًا
  })
})

describe('معدل الادخار — spec/06', () => {
  it('دخل صفر ⇒ غير متاح، لا Infinity ولا NaN', () => {
    expect(savingsRatePercent(0, 5000)).toBeNull()
    expect(formatPercentOrNA(savingsRatePercent(0, 5000))).toBe(NOT_AVAILABLE)
  })

  it('يحسب النسبة الموجبة', () => {
    // fixture الأساسي: دخل 7000، متبقي 6148
    expect(savingsRatePercent(700000, 614800)).toBeCloseTo(87.8, 1)
  })

  it('لا يخفي المعدل السالب', () => {
    const rate = savingsRatePercent(100000, -50000)
    expect(rate).toBe(-50)
    expect(formatPercentOrNA(rate)).toBe('-50.0%')
  })
})
