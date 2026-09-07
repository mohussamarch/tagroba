import { describe, expect, it } from 'vitest'
import {
  QUANTITY_SCALE,
  QuantityError,
  addQuantity,
  formatQuantity,
  parseQuantity,
  shareOfAmount,
  unitPriceOf,
  valueOfQuantity,
} from '../src/domain/quantity'

describe('parseQuantity', () => {
  it('يقرأ كمية صحيحة وكسرية', () => {
    expect(parseQuantity('1')).toBe(QUANTITY_SCALE)
    expect(parseQuantity('0.5')).toBe(QUANTITY_SCALE / 2)
    expect(parseQuantity('12.345')).toBe(1_234_500_000)
  })

  it('يقرأ الأرقام العربية والفاصلة العربية', () => {
    expect(parseQuantity('٢٫٥')).toBe(2.5 * QUANTITY_SCALE)
    expect(parseQuantity('١٬٠٠٠')).toBe(1000 * QUANTITY_SCALE)
  })

  it('يرفض أكتر من ثمانية أرقام عشرية بدل ما يقرّب بصمت', () => {
    expect(() => parseQuantity('0.123456789')).toThrow(QuantityError)
    expect(parseQuantity('0.12345678')).toBe(12_345_678)
  })

  it('يرفض النص غير الرقمي', () => {
    expect(() => parseQuantity('جرام')).toThrow(QuantityError)
    expect(() => parseQuantity('')).toThrow(QuantityError)
    expect(() => parseQuantity('1.2.3')).toThrow(QuantityError)
  })
})

describe('formatQuantity', () => {
  it('يشيل الأصفار الزائدة ويرجّع النص الأصلي', () => {
    expect(formatQuantity(parseQuantity('12.5'))).toBe('12.5')
    expect(formatQuantity(parseQuantity('3'))).toBe('3')
    expect(formatQuantity(parseQuantity('0.00000001'))).toBe('0.00000001')
    expect(formatQuantity(-parseQuantity('1.25'))).toBe('-1.25')
  })

  it('رحلة ذهاب وعودة لكل قيمة', () => {
    for (const raw of ['0', '1', '0.1', '99.99', '0.00012345', '10000']) {
      expect(formatQuantity(parseQuantity(raw))).toBe(String(Number(raw)))
    }
  })
})

describe('valueOfQuantity', () => {
  it('يحسب قيمة الذهب بالجرام', () => {
    // 12.5 جرام × 320.75 ريال = 4,009.375 ⇒ 4,009.38 بعد التقريب
    expect(valueOfQuantity(parseQuantity('12.5'), 32075)).toBe(400938)
  })

  it('لا يتجاوز الحد الآمن مع الأرقام الكبيرة', () => {
    // بتكوين واحد بسعر 1,500,000 ريال — الضرب الساذج بيكسر هنا
    const value = valueOfQuantity(parseQuantity('1'), 150_000_000)
    expect(value).toBe(150_000_000)
    expect(Number.isSafeInteger(value)).toBe(true)
  })

  it('كمية صفر قيمتها صفر', () => {
    expect(valueOfQuantity(0, 32075)).toBe(0)
  })
})

describe('shareOfAmount', () => {
  it('حصة نصف الكمية نصف التكلفة', () => {
    expect(shareOfAmount(100000, parseQuantity('5'), parseQuantity('10'))).toBe(50000)
  })

  it('الحصة الكاملة ترجع المبلغ كامل بلا تقريب', () => {
    const q = parseQuantity('3.33333333')
    expect(shareOfAmount(99999, q, q)).toBe(99999)
  })

  it('مجموع الحصة والمتبقي يساوي الأصل بالضبط', () => {
    const whole = parseQuantity('7')
    const part = parseQuantity('2')
    const cost = 100_000 // 1,000.00 ريال على 7 وحدات لا يقبل القسمة
    const share = shareOfAmount(cost, part, whole)
    expect(cost - share).toBe(71429)
    expect(share + (cost - share)).toBe(cost)
  })

  it('يرفض جزءًا أكبر من الكل', () => {
    expect(() => shareOfAmount(100, parseQuantity('11'), parseQuantity('10'))).toThrow(QuantityError)
  })
})

describe('unitPriceOf', () => {
  it('يستنتج سعر الوحدة', () => {
    expect(unitPriceOf(400938, parseQuantity('12.5'))).toBe(32075)
  })

  it('كمية صفر ترجّع null لا صفرًا', () => {
    expect(unitPriceOf(1000, 0)).toBeNull()
  })
})

describe('addQuantity', () => {
  it('الجمع دقيق حيث الجمع العشري بيغلط', () => {
    // 0.1 + 0.2 !== 0.3 بالعشري، وهنا صحيح
    expect(addQuantity(parseQuantity('0.1'), parseQuantity('0.2'))).toBe(parseQuantity('0.3'))
  })
})
