import { tryParseMoney, type Halalas } from '../../../domain/money'
import { latinizeDigits } from '../../../domain/normalize'
import { isValidIsoDate } from '../../../domain/period'
import { joinLine, tidy, type PositionedWord } from './arabicText'
import { extractMerchantName } from './merchantName'
import type { ParsedRow, RowError } from '../schemas'
import type { IsoDate } from '../../../domain/entities/types'

/**
 * مخطط كشف حساب الراجحي (PDF) — **دالة نقية**.
 *
 * بتاخد كلمات بإحداثياتها لا ملفًا، فبتتختبر من fixtures نصية بلا PDF
 * أصلًا. القرار ده مقصود: الكشف الحقيقي ممنوع رفعه لـGit
 * (`ARCHITECTURE §9.5`)، ولو التحليل كان جوه غلاف المكتبة كان اختباره
 * هيحتاج ملفًا مستحيل رفعه.
 *
 * قياسات الأعمدة محققة على كشف حقيقي من ٦٨ صفحة — `ARCHITECTURE §19.4`.
 */

/** حدود الأعمدة بالنقاط على صفحة A4 عرضها 595، والمستند من اليمين لليسار. */
export const COLUMNS = {
  balanceMax: 110,
  creditMax: 200,
  debitMax: 310,
  detailsMin: 305,
  detailsMax: 500,
  dateMin: 450,
} as const

/** «839.30 SAR» — المبلغ بعملته. العملة جزء من النمط فلا يُلتقط رقم عابر. */
const AMOUNT = /^([\d,]+\.\d{2})\s*SAR$/
/** «2025/01/05» — سنة/شهر/يوم. */
const DATE = /^(\d{4})\/(\d{2})\/(\d{2})$/

export interface PdfPage {
  pageNumber: number
  words: PositionedWord[]
}

/** سطر واحد: كلمات على نفس الارتفاع. */
interface Line {
  y: number
  words: PositionedWord[]
}

/** المبالغ والتاريخ اللي بتعرّف سطر عملية. */
interface AmountLine {
  balanceMinor: Halalas
  creditMinor: Halalas
  debitMinor: Halalas
  date: IsoDate
}

/** يجمع كلمات الصفحة في أسطر بالارتفاع، من أعلى لأسفل. */
function toLines(words: readonly PositionedWord[]): Line[] {
  const buckets = new Map<number, PositionedWord[]>()
  for (const word of words) {
    if (!word.text.trim()) continue
    const y = Math.round(word.y)
    const bucket = buckets.get(y)
    if (bucket) bucket.push(word)
    else buckets.set(y, [word])
  }
  return [...buckets.entries()]
    .sort((a, b) => b[0] - a[0])
    .map(([y, ws]) => ({ y, words: ws }))
}

/**
 * يقرأ المبالغ والتاريخ من سطر، أو `null` لو مش سطر عملية.
 *
 * السطر بيتقبل بس لما **التلاتة مبالغ والتاريخ** يكونوا موجودين معًا.
 * الشرط الصارم ده بيمنع ترويسة أو سطر مجموع إنه يتقرا كعملية.
 */
function readAmountLine(line: Line): AmountLine | null {
  let balance: Halalas | null = null
  let credit: Halalas | null = null
  let debit: Halalas | null = null
  let date: IsoDate | null = null

  for (const word of line.words) {
    const text = latinizeDigits(word.text.trim())

    const amount = AMOUNT.exec(text)
    if (amount) {
      // نصًا لا بـparseFloat — «0.29 × 100» بتدي 28.999… (CLAUDE.md #1)
      const minor = tryParseMoney(amount[1])
      if (minor === null) continue
      if (word.x < COLUMNS.balanceMax) balance = minor
      else if (word.x < COLUMNS.creditMax) credit = minor
      else if (word.x < COLUMNS.debitMax) debit = minor
      continue
    }

    const parts = DATE.exec(text)
    if (parts && word.x >= COLUMNS.dateMin) {
      const iso = `${parts[1]}-${parts[2]}-${parts[3]}`
      if (isValidIsoDate(iso)) date = iso
    }
  }

  if (balance === null || credit === null || debit === null || date === null) return null
  return { balanceMinor: balance, creditMinor: credit, debitMinor: debit, date }
}

/**
 * نص عمود التفاصيل في سطر.
 *
 * عمود التاريخ ملاصق لعمود التفاصيل، فبيتشال بالنمط لا بالإحداثي —
 * الاعتماد على الحد وحده كان بيسرّب التاريخ جوه وصف كل عملية.
 */
function detailsOf(line: Line): string {
  const inColumn = line.words.filter(
    (w) =>
      w.x >= COLUMNS.detailsMin &&
      w.x <= COLUMNS.detailsMax &&
      !DATE.test(latinizeDigits(w.text.trim())),
  )
  return joinLine(inColumn)
}

/**
 * هل النص ده صالح كاسم بديل لما استخراج التاجر يفشل؟
 *
 * نوع العملية بينزلق أحيانًا لسطر مجاور فيه مرجع بنكي، زي
 * «Agmt )200209596939- 30483052(:الوقت» — ده مش اسم يتعرض للمستخدم.
 * الرفض هنا بيخلي الاسم فاضي بدل ما يبقى ضوضاء: «لا رقم بلا مصدر»
 * بينطبق على الاسم زي ما بينطبق على المبلغ.
 */
function usableAsName(text: string): boolean {
  if (text.length < 3) return false
  // مرجع بنكي طويل مش اسم
  if (/\d{8,}/.test(text)) return false
  if (/الوقت|ملاحظة|Agmt|FRACCT|TOACCT/i.test(text)) return false
  return true
}

export interface AlrajhiPdfOutcome {
  rows: ParsedRow[]
  errors: RowError[]
  /** الرصيد المعلن قبل أول عملية، لو الكشف ذكره. */
  pagesRead: number
}

/**
 * اسم العرض: التاجر أولًا، وإلا نوع العملية لو صالح، وإلا فاضي.
 * الفاضي مقبول — الاسم المخترع مش مقبول.
 */
function merchantNameFor(description: string, typeLine: string): string {
  const merchant = extractMerchantName(description)
  if (merchant) return merchant
  const fallback = tidy(typeLine, 60)
  return usableAsName(fallback) ? fallback : ''
}

/**
 * يحوّل صفحات الكشف لصفوف موحّدة.
 *
 * العملية = سطر فيه المبالغ التلاتة والتاريخ. نوعها في السطر **فوقه**،
 * وتفاصيلها في الأسطر **تحته** لحد العملية اللي بعدها.
 */
export function parseAlrajhiPdf(pages: readonly PdfPage[]): AlrajhiPdfOutcome {
  const rows: ParsedRow[] = []
  const errors: RowError[] = []
  let lineNumber = 0

  for (const page of pages) {
    const lines = toLines(page.words)
    // فهارس الأسطر اللي فيها عملية، بترتيب الصفحة
    const hits: { index: number; amounts: AmountLine }[] = []
    lines.forEach((line, index) => {
      const amounts = readAmountLine(line)
      if (amounts) hits.push({ index, amounts })
    })

    hits.forEach((hit, order) => {
      lineNumber += 1
      const { amounts } = hit
      const raw = `صفحة ${page.pageNumber} · ${amounts.date}`

      // الاتجاه من العمود اللي فيه رقم، مش من إشارة
      const isDebit = amounts.debitMinor > 0
      const isCredit = amounts.creditMinor > 0

      if (isDebit === isCredit) {
        // صفر في الاتنين أو رقم في الاتنين — غموض يُرفض بتفسير لا يُخمَّن
        errors.push({
          lineNumber,
          field: 'المبلغ',
          message: isDebit
            ? 'العملية فيها مدين ودائن مع بعض — مش واضح اتجاهها'
            : 'العملية مبلغها صفر في المدين والدائن',
          raw,
        })
        return
      }

      // نوع العملية: السطر اللي فوق مباشرة
      const typeLine = hit.index > 0 ? detailsOf(lines[hit.index - 1]) : ''

      // التفاصيل: من سطر العملية لحد اللي قبل العملية اللي بعدها
      const stop = order + 1 < hits.length ? hits[order + 1].index - 1 : lines.length
      const details: string[] = []
      for (let i = hit.index; i < stop; i += 1) {
        const text = detailsOf(lines[i])
        if (text) details.push(text)
      }
      const description = details.join(' | ')

      rows.push({
        lineNumber,
        date: amounts.date,
        amountMinor: isDebit ? amounts.debitMinor : amounts.creditMinor,
        direction: isDebit ? 'out' : 'in',
        merchantName: merchantNameFor(description, typeLine),
        reference: null,
        sourceName: 'كشف الراجحي',
        // نوع العملية **دليل لا حكم** — موثّق إنه ممكن ينزلق لسطر مجاور
        sourceOperationType: tidy(typeLine, 80) || undefined,
        statedBalanceMinor: amounts.balanceMinor,
        description: tidy(description, 400),
        raw,
      })
    })
  }

  return { rows, errors, pagesRead: pages.length }
}
