import { parseAlrajhiPdf, type PdfPage } from '../../src/infrastructure/import/pdf/alrajhiPdfSchema'
import { hasArabic, joinLine, tidy, type PositionedWord } from '../../src/infrastructure/import/pdf/arabicText'
import { extractMerchantName } from '../../src/infrastructure/import/pdf/merchantName'
import { record, seeded } from './goldenKit'

/** كشف الراجحي PDF بعد قراية النص — صفحات **مصنوعة** بنفس مقاسات الأعمدة (الكشف الحقيقي ممنوع يترفع). */
export function pdfGolden() {
  const rnd = seeded(1905)
  const TYPES = ['شراء عبر نقاط البيع', 'حوالة واردة', 'ﺷﺮﺍء ﺍﻧﺘﺮﻧﺖ', 'سداد فاتورة (كهرباء)', 'Online Purchase', 'Agmt )200209596939- 30483052(:الوقت', 'رسوم']
  const DETAILS = [
    'Online Purchase from TEST SHOP.COM', 'payment_agreement_ab12, TEST WALLET, RIYADH,', 'ag_1a2b3c4d, TEST AGENT, JEDDAH, SA', 'TYB TEST MART 123-45, RIYADH, SA',
    'NETFLIX.COM : 4321****1234', 'PG123 TEST CAFE, DAMMAM, SA', 'تفاصيل عربية فقط', 'ARBS12 X, RIYADH, SA', '1234567 SHOP NAME US : 1111****22',
  ]
  const money = (minor: number) => `${Math.floor(minor / 100).toLocaleString('en-US')}.${String(minor % 100).padStart(2, '0')} SAR`
  const makePage = (pageNumber: number): PdfPage => {
    const words: PositionedWord[] = [{ x: 250, y: 800, text: 'كشف حساب' }, { x: 60, y: 780, text: 'الرصيد' }, { x: 470, y: 780, text: 'التاريخ' }]
    let y = 760
    let balance = rnd.int(10_000, 900_000)
    for (let i = 0; i < rnd.int(0, 7); i++) {
      const type = rnd.pick(TYPES)
      for (const [k, part] of type.split(' ').entries()) words.push({ x: 480 - k * 30, y, text: part })
      y -= 12
      const out = rnd.next() < 0.7
      const amount = rnd.int(1, 90_000)
      balance += out ? -amount : amount
      const odd = rnd.next()
      words.push({ x: 60 + rnd.next() * 20, y: y + rnd.next() * 0.4, text: money(Math.abs(balance)) })
      words.push({ x: 150, y, text: money(odd < 0.05 ? 0 : out ? 0 : amount) })
      words.push({ x: 250, y, text: money(odd < 0.05 ? 0 : odd < 0.1 ? amount : out ? amount : 0) })
      words.push({ x: 470, y, text: rnd.pick(['2025/01/05', '2025/02/28', '٢٠٢٥/٠٣/١٠', '2025/02/30']) })
      if (rnd.next() < 0.5) words.push({ x: 330, y, text: 'مرجع' })
      for (let d = 0; d < rnd.int(0, 3); d++) {
        y -= 12
        for (const [k, part] of rnd.pick(DETAILS).split(' ').entries()) words.push({ x: 310 + k * 25, y, text: part })
      }
      y -= 14
    }
    words.push({ x: 300, y: 20, text: `صفحة ${pageNumber}` }, { x: 100, y: 20, text: '   ' })
    return { pageNumber, words }
  }
  const documents = Array.from({ length: 25 }, () => Array.from({ length: rnd.int(1, 3) }, (_, i) => makePage(i + 1)))
  const lines: PositionedWord[][] = [[], [{ x: 10, y: 0, text: 'ABC' }, { x: 5, y: 0, text: 'xyz' }], [{ x: 10, y: 0, text: 'ﺍﻟﺮﺻﻴﺪ' }, { x: 50, y: 0, text: '(مبلغ)' }, { x: 30, y: 0, text: 'SAR' }], [{ x: 1, y: 0, text: '  a   b ' }]]

  return {
    parseAlrajhiPdf: documents.map((pages) => record(pages, () => parseAlrajhiPdf(pages))),
    joinLine: lines.map((words) => record(words, () => joinLine(words))),
    tidy: [['  ﺍﻟﺮﺻﻴﺪ   الحالي ', 160], ['x'.repeat(50), 10], ['short', 5], ['abcdef', 5]].map(([t, n]) => record({ text: t, max: n }, () => tidy(t as string, n as number))),
    hasArabic: ['abc', 'مرحبا', 'ﷺ', 'ﻻ', '123'].map((t) => record(t, () => hasArabic(t))),
    extractMerchantName: [...DETAILS, '', 'nothing here', 'Online Purchase from ab', 'X, Y, SA', 'payment_agreement_x, NAME WITH SPACE ,', 'ag_12345, TOO SHORT, SA', 'Online Purchase from test-shop.sa - '].map((d) => record(d, () => extractMerchantName(d))),
  }
}
