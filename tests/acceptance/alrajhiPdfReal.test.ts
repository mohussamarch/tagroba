import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { globSync } from 'node:fs'
import { parseAlrajhiPdf, type PdfPage } from '../../src/infrastructure/import/pdf/alrajhiPdfSchema'
import { addMoney, parseMoney, subtractMoney } from '../../src/domain/money'
import { formatAmount } from '../../src/domain/formatMoney'
import type { PositionedWord } from '../../src/infrastructure/import/pdf/arabicText'

/**
 * قارئ الـPDF على الكشف الحقيقي — ٦٨ صفحة.
 *
 * الملف مستبعد من Git (كشف بنكي حقيقي — `ARCHITECTURE §9.5`).
 * عند غيابه الاختبار **يفشل برسالة صريحة** بدل ما يمر زورًا:
 * «لا ادعاء نجاح لم يحدث» (CLAUDE.md #14).
 *
 * ده الاختبار اللي بيثبت إن القارئ صح: نفس المجاميع المرجعية المعتمدة،
 * وسلسلة رصيد مطابقة لكل سطر بالهللة — كلها مقروءة من الـPDF مباشرة
 * بلا أي CSV.
 */

const TARGETS = {
  debit: parseMoney('302171.45'),
  credit: parseMoney('300040.87'),
  opening: parseMoney('4837.83'),
  closing: parseMoney('2707.25'),
  count: 1912,
  closingAt: '2026-09-04',
}

function findStatementPdf(): string | null {
  const root = resolve(__dirname, '../..')
  for (const name of globSync('document_*.pdf', { cwd: root })) {
    return resolve(root, name)
  }
  return null
}

/** يقرأ الـPDF لكلمات بإحداثيات — نفس اللي بيعمله الغلاف في المتصفح. */
async function readPages(path: string): Promise<PdfPage[]> {
  const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs')
  const data = new Uint8Array(readFileSync(path))
  const doc = await pdfjs.getDocument({ data, useSystemFonts: true }).promise

  const pages: PdfPage[] = []
  for (let n = 1; n <= doc.numPages; n += 1) {
    const content = await (await doc.getPage(n)).getTextContent()
    const words: PositionedWord[] = []
    for (const item of content.items) {
      if (!('str' in item) || !item.str.trim()) continue
      words.push({ x: item.transform[4], y: item.transform[5], text: item.str })
    }
    pages.push({ pageNumber: n, words })
  }
  return pages
}

const pdfPath = findStatementPdf()

describe('كشف الراجحي الحقيقي — PDF', () => {
  it('الملف موجود', () => {
    expect(
      pdfPath !== null && existsSync(pdfPath),
      'كشف الـPDF مش موجود في جذر المشروع (document_*.pdf). ' +
        'الاختبار ده بيثبت المجاميع المرجعية، ومن غير الملف مينفعش يمر.',
    ).toBe(true)
  })

  if (!pdfPath) return

  it('بيستخرج كل العمليات بلا خطأ واحد', async () => {
    const { rows, errors, pagesRead } = parseAlrajhiPdf(await readPages(pdfPath))
    expect(pagesRead).toBeGreaterThan(60)
    expect(errors).toEqual([])
    expect(rows).toHaveLength(TARGETS.count)
  }, 120_000)

  it('المجاميع المرجعية مطابقة بالهللة', async () => {
    const { rows } = parseAlrajhiPdf(await readPages(pdfPath))

    const debit = addMoney(...rows.filter((r) => r.direction === 'out').map((r) => r.amountMinor))
    const credit = addMoney(...rows.filter((r) => r.direction === 'in').map((r) => r.amountMinor))

    expect(formatAmount(debit)).toBe(formatAmount(TARGETS.debit))
    expect(formatAmount(credit)).toBe(formatAmount(TARGETS.credit))
  }, 120_000)

  it('سلسلة الرصيد مطابقة في كل سطر — صفر اختلاف', async () => {
    const { rows } = parseAlrajhiPdf(await readPages(pdfPath))

    let balance = TARGETS.opening
    const mismatches: string[] = []

    for (const row of rows) {
      balance =
        row.direction === 'in'
          ? addMoney(balance, row.amountMinor)
          : subtractMoney(balance, row.amountMinor)

      if (row.statedBalanceMinor !== balance) {
        mismatches.push(
          `سطر ${row.lineNumber} (${row.date}): محسوب ${formatAmount(balance)} ` +
            `والكشف قايل ${formatAmount(row.statedBalanceMinor!)}`,
        )
        // نكمّل من رقم الكشف عشان نشوف كل الاختلافات مش أول واحد بس
        balance = row.statedBalanceMinor!
      }
    }

    expect(mismatches).toEqual([])
    expect(formatAmount(balance)).toBe(formatAmount(TARGETS.closing))
    expect(rows[rows.length - 1].date).toBe(TARGETS.closingAt)
  }, 120_000)

  it('كل عملية ليها تاريخ ومبلغ موجب واتجاه', async () => {
    const { rows } = parseAlrajhiPdf(await readPages(pdfPath))
    for (const row of rows) {
      expect(row.amountMinor).toBeGreaterThan(0)
      expect(Number.isInteger(row.amountMinor)).toBe(true)
      expect(row.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
      expect(['in', 'out']).toContain(row.direction)
    }
  }, 120_000)

  it('أغلب العمليات ليها اسم تاجر — والباقي فاضي مش مخترع', async () => {
    const { rows } = parseAlrajhiPdf(await readPages(pdfPath))
    const named = rows.filter((r) => r.merchantName.trim().length > 0)

    // نسبة معلنة مقيسة، مش هدف مخترع
    expect(named.length / rows.length).toBeGreaterThan(0.85)

    // اللي بلا اسم اسمه فاضي — مفيش «غير معروف» ولا تخمين
    for (const row of rows) expect(row.merchantName).not.toMatch(/غير معروف|unknown/i)
  }, 120_000)
})
