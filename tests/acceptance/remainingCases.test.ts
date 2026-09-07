import { describe, it, expect } from 'vitest'
import { parseCsv, CsvError, stripBom } from '../../src/infrastructure/import/csvReader'
import { parseRows, detectSchema, SchemaError } from '../../src/infrastructure/import/schemas'
import { reconcileBalance, type LedgerMovement } from '../../src/domain/reconcile'
import {
  buildDedupeIndex,
  classifyCandidate,
  hashContent,
  type ExistingRecord,
} from '../../src/domain/dedupe'
import { parseMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'

/** حالات spec/06 المتبقية التي لم تغطها بقية الملفات. */

describe('CSV: فاصلة داخل اسم مقتبس و BOM — spec/06', () => {
  it('الاسم صحيح وعدد الأعمدة صحيح', () => {
    const csv =
      '﻿date,name,amount,type,source,reference\n' +
      '2026-09-01,"مطعم البيك, الرياض",32.00,expense,حساب,REF-1\n'
    const doc = parseCsv(csv)

    expect(doc.header).toEqual(['date', 'name', 'amount', 'type', 'source', 'reference'])
    expect(doc.rows[0].cells).toHaveLength(6)
    expect(doc.rows[0].cells[1]).toBe('مطعم البيك, الرياض') // الفاصلة جزء من الاسم
    expect(doc.rows[0].cells[2]).toBe('32.00')
  })

  it('BOM لا يفسد أول عمود', () => {
    expect(stripBom('﻿date')).toBe('date')
    expect(stripBom('date')).toBe('date')
    const doc = parseCsv('﻿date,name\n2026-01-01,x\n')
    expect(doc.header[0]).toBe('date') // مش "﻿date"
  })

  it('اقتباس مزدوج داخل الحقل يُفك صح', () => {
    const doc = parseCsv('a,b\n"قال ""أهلاً"" وسكت",2\n')
    expect(doc.rows[0].cells[0]).toBe('قال "أهلاً" وسكت')
  })

  it('أسطر CRLF تُقرأ كأسطر عادية', () => {
    const doc = parseCsv('a,b\r\n1,2\r\n3,4\r\n')
    expect(doc.rows).toHaveLength(2)
    expect(doc.rows[1].cells).toEqual(['3', '4'])
  })

  it('اقتباس مفتوح لم يُغلق ⇒ خطأ صريح لا صمت', () => {
    expect(() => parseCsv('a,b\n"ناقص,2\n')).toThrow(CsvError)
  })

  it('ملف بأعمدة غير معروفة ⇒ رفض صريح، لا تخمين للأعمدة المالية', () => {
    const doc = parseCsv('foo,bar,baz\n1,2,3\n')
    expect(() => detectSchema(doc)).toThrow(SchemaError)
    try {
      detectSchema(doc)
    } catch (error) {
      expect((error as Error).message).toContain('مش هنخمّن أعمدة مالية')
    }
  })

  it('الملف الفاضي ⇒ رسالة واضحة', () => {
    expect(() => parseCsv('')).toThrow(CsvError)
  })
})

describe('مطابقة الرصيد: ترتيب يوم مجهول — spec/06', () => {
  const movement = (
    date: string,
    order: number,
    debit: string,
    stated: string,
  ): LedgerMovement => ({
    date,
    sourceOrder: order,
    debitMinor: parseMoney(debit),
    creditMinor: 0,
    statedBalanceMinor: parseMoney(stated),
  })

  it('يوم بحركة واحدة ⇒ الفرق منسوب لها يقينًا', () => {
    const result = reconcileBalance(parseMoney('1000.00'), '2026-01-01', [
      movement('2026-01-02', 0, '100.00', '900.00'), // صح
      movement('2026-01-03', 1, '50.00', '840.00'), // غلط: المفروض 850
    ])
    expect(result.mismatches).toHaveLength(1)
    expect(result.mismatches[0].sameDayCount).toBe(1)
    expect(result.ambiguityNote).toContain('حركة واحدة')
    expect(result.ambiguityNote).toContain('الفرق يخصها هي')
  })

  it('يوم متعدد الحركات ⇒ لا ادعاء تحديد أول فرق يقينًا', () => {
    const result = reconcileBalance(parseMoney('1000.00'), '2026-01-01', [
      movement('2026-01-02', 0, '100.00', '900.00'),
      movement('2026-01-02', 1, '50.00', '840.00'), // غلط
      movement('2026-01-02', 2, '10.00', '830.00'),
    ])
    expect(result.mismatches.length).toBeGreaterThan(0)
    expect(result.mismatches[0].sameDayCount).toBe(3)
    expect(result.ambiguityNote).toContain('مش أكيد')
    expect(result.ambiguityNote).toContain('3 حركة')
  })

  it('الحركة بلا رصيد معلن لا تُقارَن ولا تُعد مطابقة زورًا', () => {
    const result = reconcileBalance(parseMoney('1000.00'), '2026-01-01', [
      { date: '2026-01-02', sourceOrder: 0, debitMinor: parseMoney('100.00'), creditMinor: 0 },
      movement('2026-01-03', 1, '50.00', '850.00'),
    ])
    expect(result.checkedCount).toBe(1) // واحدة فقط كانت قابلة للمقارنة
    expect(result.movementCount).toBe(2)
    expect(result.mismatches).toHaveLength(0)
    expect(formatAmount(result.closingMinor)).toBe('850.00')
  })

  it('ترتيب المصدر يُحترم ولا يُعاد ترتيب اليوم', () => {
    // نفس الحركات بترتيب مختلف تعطي أرصدة وسيطة مختلفة
    const a = reconcileBalance(parseMoney('100.00'), '2026-01-01', [
      { date: '2026-01-02', sourceOrder: 0, debitMinor: parseMoney('30.00'), creditMinor: 0, statedBalanceMinor: parseMoney('70.00') },
      { date: '2026-01-02', sourceOrder: 1, debitMinor: 0, creditMinor: parseMoney('50.00'), statedBalanceMinor: parseMoney('120.00') },
    ])
    expect(a.mismatches).toHaveLength(0)

    // نفس الحركتين بترتيب مقلوب: الرصيد **النهائي** واحد (الجمع تبادلي)
    // لكن الرصيد **الوسيط** يختلف — وده ما يكشفه عمود الرصيد
    const b = reconcileBalance(parseMoney('100.00'), '2026-01-01', [
      { date: '2026-01-02', sourceOrder: 0, debitMinor: 0, creditMinor: parseMoney('50.00'), statedBalanceMinor: parseMoney('70.00') },
      { date: '2026-01-02', sourceOrder: 1, debitMinor: parseMoney('30.00'), creditMinor: 0, statedBalanceMinor: parseMoney('120.00') },
    ])
    expect(b.mismatches).toHaveLength(1)
    expect(formatAmount(b.mismatches[0].computedMinor)).toBe('150.00') // بدل 70
    expect(b.closingMinor).toBe(a.closingMinor) // النهائي واحد في الحالتين
    // ولهذا لا يجوز الادعاء بأن أول فرق هو موضع الخطأ في يوم متعدد الحركات
    expect(b.mismatches[0].sameDayCount).toBe(2)
    expect(b.ambiguityNote).toContain('مش أكيد')
  })
})

describe('مصدران لعملية واحدة — الدرجة ٥ من spec/05', () => {
  it('نفس المرجع من نفس الحساب ⇒ مكرر مؤكد، عملية واحدة ومصدران', () => {
    const existing: ExistingRecord[] = [
      {
        accountIdentity: 'الراجحي',
        sourceReference: 'REF-100',
        date: '2026-09-01',
        amountMinor: parseMoney('120.00'),
        direction: 'out',
        merchantName: 'البيك',
        rowIndex: 1,
        transactionId: 'txn-1',
      },
    ]
    const index = buildDedupeIndex(existing)

    // نفس العملية جاية من رسالة SMS بدل الكشف
    const verdict = classifyCandidate(
      {
        accountIdentity: 'الراجحي',
        sourceReference: 'REF-100',
        date: '2026-09-01',
        amountMinor: parseMoney('120.00'),
        direction: 'out',
        merchantName: 'البيك',
        rowIndex: 1,
      },
      index,
    )

    expect(verdict.state).toBe('duplicate')
    // العملية الاقتصادية واحدة، ويشار إليها ليُربط بها المصدر الثاني
    expect(verdict.matchedTransactionId).toBe('txn-1')
  })

  it('نفس المرجع من حساب مختلف ⇒ جديد، لا إسقاط تلقائي', () => {
    const index = buildDedupeIndex([
      {
        accountIdentity: 'الراجحي',
        sourceReference: 'REF-100',
        date: '2026-09-01',
        amountMinor: parseMoney('120.00'),
        direction: 'out',
        merchantName: 'البيك',
        rowIndex: 1,
        transactionId: 'txn-1',
      },
    ])

    const verdict = classifyCandidate(
      {
        accountIdentity: 'بنك تاني',
        sourceReference: 'REF-100',
        date: '2026-09-01',
        amountMinor: parseMoney('120.00'),
        direction: 'out',
        merchantName: 'البيك',
        rowIndex: 1,
      },
      index,
    )
    expect(verdict.state).toBe('new')
  })

  it('عمليتا مطعم بنفس المبلغ بلا مرجع ⇒ متشابه لا مكرر', () => {
    const index = buildDedupeIndex([
      {
        accountIdentity: 'الراجحي',
        sourceReference: null,
        date: '2026-09-01',
        amountMinor: parseMoney('13.00'),
        direction: 'out',
        merchantName: 'فلافل',
        rowIndex: 1,
        transactionId: 'txn-1',
      },
    ])

    const verdict = classifyCandidate(
      {
        accountIdentity: 'الراجحي',
        sourceReference: null,
        date: '2026-09-01',
        amountMinor: parseMoney('13.00'),
        direction: 'out',
        merchantName: 'فلافل',
        rowIndex: 2,
      },
      index,
    )
    // ممكن تكون زيارتين حقيقيتين في نفس اليوم — لا حذف تلقائي
    expect(verdict.state).toBe('similar')
    expect(verdict.reason).toContain('عملية تانية حقيقية')
  })
})

describe('بصمة الملف — الدرجة ١', () => {
  it('نفس المحتوى ⇒ نفس البصمة، واختلاف حرف واحد يغيّرها', () => {
    const a = 'date,name,amount\n2026-09-01,البيك,32.00\n'
    const b = 'date,name,amount\n2026-09-01,البيك,33.00\n'
    expect(hashContent(a)).toBe(hashContent(a))
    expect(hashContent(a)).not.toBe(hashContent(b))
    expect(hashContent('')).toBe(hashContent(''))
  })
})

describe('المخطط القديم: صفوف حدية', () => {
  const header = 'التاريخ,مدين,دائن,الرصيد,التاجر,التصنيف,نوع العملية,التفاصيل\n'

  it('مدين ودائن معًا ⇒ رفض باتجاه غير واضح', () => {
    const outcome = parseRows(parseCsv(header + '2026/09/01,10.00,5.00,100.00,x,y,z,w\n'))
    expect(outcome.rows).toHaveLength(0)
    expect(outcome.errors[0].message).toContain('مش واضح اتجاه الحركة')
  })

  it('صفر مدين وصفر دائن ⇒ رفض بلا أثر مالي', () => {
    const outcome = parseRows(parseCsv(header + '2026/09/01,0.0,0.0,100.00,x,y,z,w\n'))
    expect(outcome.rows).toHaveLength(0)
    expect(outcome.errors[0].message).toContain('مالهوش أثر مالي')
  })

  it('رصيد فاضي ⇒ الصف يُستورد لكن بلا مقارنة رصيد', () => {
    const outcome = parseRows(parseCsv(header + '2026/09/01,10.00,0.0,,x,y,z,w\n'))
    expect(outcome.rows).toHaveLength(1)
    expect(outcome.rows[0].statedBalanceMinor).toBeUndefined()
    expect(outcome.errors).toHaveLength(0)
  })

  it('تاريخ 31 فبراير ⇒ رفض برقم الصف', () => {
    const outcome = parseRows(parseCsv(header + '2026/02/31,10.00,0.0,100.00,x,y,z,w\n'))
    expect(outcome.rows).toHaveLength(0)
    expect(outcome.errors[0].lineNumber).toBe(2)
    expect(outcome.errors[0].field).toBe('التاريخ')
  })
})
