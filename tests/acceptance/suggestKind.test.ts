import { describe, it, expect } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { suggestEconomicKind, isBulkConfirmable } from '../../src/domain/suggestEconomicKind'
import { parseCsv } from '../../src/infrastructure/import/csvReader'
import { parseRows } from '../../src/infrastructure/import/schemas'
import { countsAsIncome, countsAsPersonalExpense } from '../../src/domain/entities/economicKind'

/**
 * محرك اقتراح النوع الاقتصادي — spec/02.
 *
 * القاعدة التي يقيس عليها كل اختبار هنا:
 * **يقترح ولا يقرر، والغامض يبقى غامضًا.**
 */

describe('الوارد لا يُقترح كدخل أبدًا — spec/02', () => {
  it('«لا تصنف تحويلًا غامضًا كراتب افتراضيًا»', () => {
    const s = suggestEconomicKind({ direction: 'in', sourceCategory: 'تحويلات' })
    expect(s.kind).toBeNull()
    expect(s.confidence).toBe('ambiguous')
    expect(s.alternatives).toContain('salary')
    expect(s.alternatives).toContain('loan_received')
    expect(s.alternatives).toContain('debt_collected')
  })

  it('الإيداع كذلك — إيداع ليس دليلًا على راتب', () => {
    expect(suggestEconomicKind({ direction: 'in', sourceCategory: 'إيداع' }).kind).toBeNull()
  })

  it('الوارد بلا تصنيف غامض أيضًا', () => {
    expect(suggestEconomicKind({ direction: 'in' }).kind).toBeNull()
  })

  it('**لا يوجد أي مدخل يُنتج اقتراح دخل تلقائيًا**', () => {
    const categories = [
      undefined, '', 'تحويلات', 'إيداع', 'محافظ رقمية', 'استرداد',
      'مطاعم وقهوة', 'راتب', 'دخل', 'غير مصنّف',
    ]
    for (const cat of categories) {
      for (const merchant of ['', 'شركة', 'SALARY', 'راتب شهري']) {
        const s = suggestEconomicKind({
          direction: 'in',
          ...(cat !== undefined ? { sourceCategory: cat } : {}),
          merchantName: merchant,
        })
        if (s.kind !== null) {
          // الاستثناء الوحيد المسموح: بيع أصل استثماري
          expect(countsAsIncome(s.kind)).toBe(false)
        }
      }
    }
  })

  it('الاسترداد ليس دخلًا جديدًا', () => {
    const s = suggestEconomicKind({ direction: 'in', sourceCategory: 'استرداد' })
    expect(s.kind).toBeNull()
    expect(s.reason).toContain('مش دخل جديد')
  })
})

describe('الصادر: ما ليس مصروفًا يُميَّز — spec/02', () => {
  it('سحب نقدي ⇒ تحويل داخلي بثقة عالية', () => {
    const s = suggestEconomicKind({ direction: 'out', sourceCategory: 'سحب نقدي' })
    expect(s.kind).toBe('internal_transfer')
    expect(s.confidence).toBe('high')
    expect(countsAsPersonalExpense(s.kind!)).toBe(false)
  })

  it('استثمار وذهب ⇒ شراء أصل، لا مصروف', () => {
    for (const cat of ['استثمار', 'ذهب']) {
      const s = suggestEconomicKind({ direction: 'out', sourceCategory: cat })
      expect(s.kind).toBe('asset_buy')
      expect(countsAsPersonalExpense(s.kind!)).toBe(false)
    }
  })

  it('رسوم بنكية ⇒ رسوم، وهي مصروف مستقل', () => {
    const s = suggestEconomicKind({ direction: 'out', sourceCategory: 'رسوم بنكية' })
    expect(s.kind).toBe('fee')
    expect(countsAsPersonalExpense(s.kind!)).toBe(true)
  })

  it('تقسيط ⇒ سداد التزام، بثقة متوسطة لا عالية', () => {
    const s = suggestEconomicKind({ direction: 'out', sourceCategory: 'تقسيط' })
    expect(s.kind).toBe('debt_repaid')
    expect(s.confidence).toBe('medium')
    expect(isBulkConfirmable(s)).toBe(false) // لا يدخل التأكيد الجماعي
    expect(s.reason).toContain('عدّ مزدوج')
  })

  it('تصنيف إنفاق معروف ⇒ شراء بثقة عالية', () => {
    for (const cat of ['مطاعم وقهوة', 'بقالة وسوبرماركت', 'اتصالات', 'سفر']) {
      const s = suggestEconomicKind({ direction: 'out', sourceCategory: cat })
      expect(s.kind).toBe('purchase')
      expect(s.confidence).toBe('high')
      expect(isBulkConfirmable(s)).toBe(true)
    }
  })
})

describe('الغامض يبقى غامضًا', () => {
  it('محفظة رقمية: ثغرة برق معروفة ولا تُحسم آليًا', () => {
    const s = suggestEconomicKind({ direction: 'out', sourceCategory: 'محافظ رقمية' })
    expect(s.kind).toBeNull()
    expect(s.alternatives).toEqual(
      expect.arrayContaining(['internal_transfer', 'support_gift', 'loan_granted']),
    )
  })

  it('برق خدمة تحويل لا نوع مصروف — spec/02', () => {
    const s = suggestEconomicKind({
      direction: 'out',
      sourceCategory: 'غير مصنّف',
      merchantName: 'BARQ',
    })
    expect(s.kind).toBeNull()
    expect(s.reason).toContain('وسيلة تحويل')
  })

  it('سداد: التعارض الموثّق لا يُحسم آليًا — ARCHITECTURE.md §9.6', () => {
    const s = suggestEconomicKind({ direction: 'out', sourceCategory: 'سداد' })
    expect(s.kind).toBeNull()
    expect(s.alternatives).toContain('debt_repaid')
    expect(s.alternatives).toContain('purchase')
  })

  it('كل غامض له بدائل معروضة وسبب مكتوب — لا حالة بلا تفسير', () => {
    const inputs = [
      { direction: 'out' as const, sourceCategory: 'تحويلات' },
      { direction: 'out' as const, sourceCategory: 'محافظ رقمية' },
      { direction: 'in' as const, sourceCategory: 'تحويلات' },
      { direction: 'in' as const },
    ]
    for (const input of inputs) {
      const s = suggestEconomicKind(input)
      expect(s.kind).toBeNull()
      expect(s.alternatives.length).toBeGreaterThan(1)
      expect(s.reason.length).toBeGreaterThan(20)
    }
  })

  it('الغامض لا يدخل التأكيد الجماعي أبدًا', () => {
    const s = suggestEconomicKind({ direction: 'in', sourceCategory: 'تحويلات' })
    expect(isBulkConfirmable(s)).toBe(false)
  })
})

const CSV_PATH = resolve(__dirname, '../../files/transactions_full.csv')

describe.skipIf(!existsSync(CSV_PATH))('تغطية على الكشف الحقيقي', () => {
  const rows = parseRows(parseCsv(readFileSync(CSV_PATH, 'utf8'))).rows

  it('يغطي أغلبية العمليات ويترك «عالم انتقال الفلوس» للقرار', () => {
    let high = 0
    let ambiguousCount = 0
    let ambiguousMinor = 0

    for (const r of rows) {
      const s = suggestEconomicKind({
        direction: r.direction,
        ...(r.sourceCategory !== undefined ? { sourceCategory: r.sourceCategory } : {}),
        merchantName: r.merchantName,
        description: r.description,
      })
      if (s.confidence === 'high') high++
      if (s.confidence === 'ambiguous') {
        ambiguousCount++
        ambiguousMinor += r.amountMinor
      }
    }

    // ثلثا العمليات لها اقتراح قاطع، فالتأكيد الجماعي ذو معنى
    expect(high / rows.length).toBeGreaterThan(0.6)

    // والغامض ليس قليلًا: هو «عالم انتقال الفلوس» الذي يحتاج قرار المالك
    expect(ambiguousCount).toBeGreaterThan(400)
    expect(ambiguousMinor).toBeGreaterThan(40_000_000) // أكثر من 400 ألف ريال
  })

  it('ولا عملية وارد واحدة تُقترح كدخل في الكشف كله', () => {
    const incoming = rows.filter((r) => r.direction === 'in')
    expect(incoming.length).toBeGreaterThan(0)

    for (const r of incoming) {
      const s = suggestEconomicKind({
        direction: 'in',
        ...(r.sourceCategory !== undefined ? { sourceCategory: r.sourceCategory } : {}),
        merchantName: r.merchantName,
      })
      if (s.kind !== null) expect(countsAsIncome(s.kind)).toBe(false)
    }
  })
})
