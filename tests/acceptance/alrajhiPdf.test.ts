import { describe, expect, it } from 'vitest'
import { parseAlrajhiPdf, type PdfPage } from '../../src/infrastructure/import/pdf/alrajhiPdfSchema'
import { extractMerchantName } from '../../src/infrastructure/import/pdf/merchantName'
import { joinLine, normalizeArabic } from '../../src/infrastructure/import/pdf/arabicText'
import { formatAmount } from '../../src/domain/formatMoney'
import type { PositionedWord } from '../../src/infrastructure/import/pdf/arabicText'

/**
 * مخطط كشف الراجحي — مختبَر على **كلمات بإحداثيات** لا على ملف PDF.
 *
 * الكشف الحقيقي ممنوع رفعه لـGit (`ARCHITECTURE §9.5`)، فالنصوص هنا
 * منسوخة حرفيًا من الكشف الحقيقي بإحداثياتها الحقيقية، والملف نفسه
 * ما بيتحطش. اختبار الملف الكامل في `alrajhiPdfReal.test.ts`.
 */

const w = (x: number, y: number, text: string): PositionedWord => ({ x, y, text })

/** صفحة حقيقية مصغّرة: عمليتان بنفس تخطيط الكشف. */
function samplePage(): PdfPage {
  return {
    pageNumber: 3,
    words: [
      // ترويسة الأعمدة
      w(71, 803, 'ﺍﻟﺮﺻﻴﺪ'),
      w(167, 803, 'ﺩﺍﺋﻦ'),
      w(255, 803, 'ﻣﺪﻳﻦ'),
      w(367, 803, 'ﺗﻔﺎﺻﻴﻞ ﺍﻟﻌﻤﻠﻴﺔ'),
      w(508, 803, 'ﺍﻟﺘﺎﺭﻳﺦ'),

      // عملية ١ — مدين ١٥٫٠٠
      w(341, 784, 'ﻋﺒﺮ ﺍﻟﺒﻄﺎﻗﺔ ﺍﻟﻤﺴﺠﻠﺔ )ﻣﺤﻠﻲ('),
      w(443, 784, 'ﺷﺮﺍﺀ ﺍﻧﺘﺮﻧﺖ'),
      w(63, 778, '839.30 SAR'),
      w(159, 778, '0.00 SAR'),
      w(247, 778, '15.00 SAR'),
      w(497, 778, '2025/01/05'),
      w(342, 776, 'ARBS2I02 -500511065904) Agmt):ﺍﻟﻮﻗﺖ:14:47:50, ﻣﻼﺣﻈﺔ'),
      w(315, 770, 'ag_8e7cbb5c5624462d890e5c4018174df5, Shawarmer, Riyadh, SA'),

      // عملية ٢ — دائن ٧٬٠٠٠٫٠٠
      w(464, 758, 'ﺗﺤﻮﻳﻞ'),
      w(63, 752, '7,839.30 SAR'),
      w(159, 752, '7,000.00 SAR'),
      w(247, 752, '0.00 SAR'),
      w(497, 752, '2025/01/06'),
      w(332, 750, 'ﺍﻟﻮﻗﺖ:11:49:51, ﻣﻼﺣﻈﺔ'),
    ],
  }
}

describe('قراءة سطر العملية', () => {
  const { rows, errors } = parseAlrajhiPdf([samplePage()])

  it('بيلاقي العمليتين بس — الترويسة مش عملية', () => {
    expect(rows).toHaveLength(2)
    expect(errors).toHaveLength(0)
  })

  it('المبلغ والاتجاه من العمود لا من إشارة', () => {
    expect(formatAmount(rows[0].amountMinor)).toBe('15.00')
    expect(rows[0].direction).toBe('out')
    expect(formatAmount(rows[1].amountMinor)).toBe('7,000.00')
    expect(rows[1].direction).toBe('in')
  })

  it('الرصيد المعلن محفوظ مع كل عملية', () => {
    expect(formatAmount(rows[0].statedBalanceMinor!)).toBe('839.30')
    expect(formatAmount(rows[1].statedBalanceMinor!)).toBe('7,839.30')
  })

  it('التاريخ بيتحول لصيغة قياسية', () => {
    expect(rows[0].date).toBe('2025-01-05')
    expect(rows[1].date).toBe('2025-01-06')
  })

  it('التاريخ ما بيتسربش جوه وصف العملية', () => {
    // عمود التاريخ ملاصق لعمود التفاصيل — الحد وحده مكانش بيكفي
    expect(rows[0].description).not.toContain('2025/01/05')
    expect(rows[0].description).not.toContain('2025-01-05')
  })

  it('نوع العملية بيتقرا من السطر اللي فوق، بترتيب عربي صحيح', () => {
    expect(rows[0].sourceOperationType).toBe('شراء انترنت عبر البطاقة المسجلة (محلي)')
    expect(rows[1].sourceOperationType).toBe('تحويل')
  })

  it('اسم التاجر بيتستخرج من التفاصيل', () => {
    expect(rows[0].merchantName).toBe('SHAWARMER')
  })

  it('المصدر مكتوب على كل صف', () => {
    expect(rows[0].sourceName).toBe('كشف الراجحي')
    expect(rows[0].raw).toContain('صفحة 3')
  })
})

describe('الرفض بتفسير لا بتخمين', () => {
  it('مدين ودائن مع بعض ⇒ رفض، والسبب مكتوب', () => {
    const page: PdfPage = {
      pageNumber: 1,
      words: [
        w(63, 700, '100.00 SAR'),
        w(159, 700, '50.00 SAR'),
        w(247, 700, '50.00 SAR'),
        w(497, 700, '2025/01/05'),
      ],
    }
    const { rows, errors } = parseAlrajhiPdf([page])
    expect(rows).toHaveLength(0)
    expect(errors[0].message).toContain('مش واضح اتجاهها')
  })

  it('صفر في الاتنين ⇒ رفض', () => {
    const page: PdfPage = {
      pageNumber: 1,
      words: [
        w(63, 700, '100.00 SAR'),
        w(159, 700, '0.00 SAR'),
        w(247, 700, '0.00 SAR'),
        w(497, 700, '2025/01/05'),
      ],
    }
    expect(parseAlrajhiPdf([page]).errors[0].message).toContain('صفر')
  })

  it('سطر ناقص مبلغ مش عملية أصلًا', () => {
    const page: PdfPage = {
      pageNumber: 1,
      words: [w(63, 700, '100.00 SAR'), w(497, 700, '2025/01/05')],
    }
    const { rows, errors } = parseAlrajhiPdf([page])
    expect(rows).toHaveLength(0)
    expect(errors).toHaveLength(0)
  })

  it('تاريخ مستحيل بيمنع قراءة السطر كعملية', () => {
    const page: PdfPage = {
      pageNumber: 1,
      words: [
        w(63, 700, '100.00 SAR'),
        w(159, 700, '0.00 SAR'),
        w(247, 700, '10.00 SAR'),
        w(497, 700, '2025/02/30'),
      ],
    }
    expect(parseAlrajhiPdf([page]).rows).toHaveLength(0)
  })
})

describe('النص العربي', () => {
  it('صيغة العرض بتتحول لحروف عادية', () => {
    expect(normalizeArabic('ﺍﻟﺮﺻﻴﺪ')).toBe('الرصيد')
    expect(normalizeArabic('ﺗﻔﺎﺻﻴﻞ ﺍﻟﻌﻤﻠﻴﺔ')).toBe('تفاصيل العملية')
  })

  it('⚠️ العكس بيفسد النص — السلوك اللي في السكربت القديم', () => {
    // السكربت القديم بيعكس لأن pdfplumber بيسلّم معكوسًا. pdfjs لأ.
    const reversed = [...normalizeArabic('ﺍﻟﺮﺻﻴﺪ')].reverse().join('')
    expect(reversed).toBe('ديصرلا')
    expect(reversed).not.toBe('الرصيد')
  })

  it('ترتيب الكلمات العربي من اليمين للشمال', () => {
    const line = [w(341, 0, 'ﻋﺒﺮ ﺍﻟﺒﻄﺎﻗﺔ'), w(443, 0, 'ﺷﺮﺍﺀ ﺍﻧﺘﺮﻧﺖ')]
    // الكلمة ذات x الأكبر أولًا
    expect(joinLine(line)).toBe('شراء انترنت عبر البطاقة')
  })

  it('السطر اللاتيني الخالص بيفضل من الشمال لليمين', () => {
    const line = [w(400, 0, 'Riyadh,'), w(315, 0, 'Shawarmer,'), w(450, 0, 'SA')]
    expect(joinLine(line)).toBe('Shawarmer, Riyadh, SA')
  })

  it('الأقواس بتترد لاتجاهها الصحيح', () => {
    expect(joinLine([w(0, 0, 'ﺷﺮﺍﺀ )ﻣﺤﻠﻲ(')])).toBe('شراء (محلي)')
  })
})

describe('اسم التاجر — استدلال بيرجّع null لما يفشل', () => {
  const cases: [string, string | null][] = [
    ['Online Purchase from Mokab, JEDDAH', 'MOKAB'],
    ['ag_8e7cbb5c5624462d890e5c4018174df5, Shawarmer, Riyadh, SA', 'SHAWARMER'],
    ['payment_agreement_oWIh15259110nNh2aa0z425, Trendyol, Riyadh, SA', 'TRENDYOL'],
    ['ALKANDEI EST, RIYADH, SA', 'ALKANDEI EST'],
    ['Temu.com Dublin 4 IE : 5924******484783', 'TEMU.COM DUBLIN 4'],
  ]

  for (const [details, expected] of cases) {
    it(`«${details.slice(0, 40)}…» ⇒ ${expected}`, () => {
      expect(extractMerchantName(details)).toBe(expected)
    })
  }

  it('بادئة البنك بتتشال من الاسم', () => {
    expect(extractMerchantName('TYB FALAFEL, RIYADH, SA')).toBe('FALAFEL')
  })

  it('مفيش نمط طابق ⇒ null مش تخمين', () => {
    expect(extractMerchantName('W-/TOACCT/53900608017592353TO')).toBeNull()
    expect(extractMerchantName('')).toBeNull()
  })

  it('رقم صافي مش اسم تاجر', () => {
    expect(extractMerchantName('123456, 789, SA')).toBeNull()
  })
})

describe('الاسم البديل ما بيبقاش ضوضاء', () => {
  it('نوع عملية انزلق لمرجع بنكي ⇒ الاسم يفضل فاضي', () => {
    const page: PdfPage = {
      pageNumber: 1,
      words: [
        w(340, 706, 'Agmt )200209596939- 30483052(:ﺍﻟﻮﻗﺖ:12:11:46, ﻣﻼﺣﻈﺔ'),
        w(63, 700, '100.00 SAR'),
        w(159, 700, '0.00 SAR'),
        w(247, 700, '10.00 SAR'),
        w(497, 700, '2025/01/05'),
      ],
    }
    const { rows } = parseAlrajhiPdf([page])
    expect(rows[0].merchantName).toBe('')
  })
})
