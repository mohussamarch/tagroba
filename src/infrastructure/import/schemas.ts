import { tryParseMoney, type Halalas } from '../../domain/money'
import { isValidIsoDate, parseIsoDate, formatIsoDate } from '../../domain/period'
import { latinizeDigits } from '../../domain/normalize'
import type { CsvDocument, CsvRow } from './csvReader'
import type { IsoDate } from '../../domain/entities/types'

/**
 * مخططا CSV — spec/05.
 *
 * «المخططان غير متطابقين؛ مطلوب adapter صريح.
 *  لا تستورد ناتج السكربت كأنه CSV المعاينة دون تحويل.»
 *
 * ولذلك: كل مخطط له كاشف ومحوّل مستقل، والناتج نوع موحّد واحد
 * (ParsedRow) تعمل عليه بقية المنظومة.
 */

export type SchemaId = 'preview' | 'legacy'

/** الصف بعد التحويل — الشكل الموحّد الذي لا يعرف من أي مخطط جاء. */
export interface ParsedRow {
  lineNumber: number
  date: IsoDate
  /** المبلغ موجب دائمًا؛ الاتجاه منفصل (spec/03). */
  amountMinor: Halalas
  direction: 'in' | 'out'
  merchantName: string
  reference: string | null
  /** اسم المصدر/الحساب كما ورد. */
  sourceName: string
  /** التصنيف من عمود الملف — دليل لا حكم. */
  sourceCategory?: string
  /** نوع العملية من الملف — تحذير موثق: قد ينزلق لترويسة مجاورة. */
  sourceOperationType?: string
  /** الرصيد المعلن بعد الحركة، إن وُجد في المخطط. */
  statedBalanceMinor?: Halalas
  /** نص الوصف الكامل — يُحفظ ولا يُقص. */
  description: string
  raw: string
}

export interface RowError {
  lineNumber: number
  field: string
  message: string
  /** النص الأصلي محفوظ — «لا تفقد المدخلات» (spec/04). */
  raw: string
}

export interface ParseOutcome {
  schema: SchemaId
  rows: ParsedRow[]
  errors: RowError[]
}

export class SchemaError extends Error {}

/* ─────────────────────────── الكشف عن المخطط ─────────────────────────── */

const PREVIEW_HEADER = ['date', 'name', 'amount', 'type', 'source', 'reference']
const LEGACY_HEADER = [
  'التاريخ',
  'مدين',
  'دائن',
  'الرصيد',
  'التاجر',
  'التصنيف',
  'نوع العملية',
  'التفاصيل',
]

function headerMatches(header: readonly string[], expected: readonly string[]): boolean {
  if (header.length < expected.length) return false
  return expected.every((name, i) => header[i]?.trim().toLowerCase() === name.toLowerCase())
}

/**
 * يكشف المخطط من الترويسة.
 * spec/05: «لا تخمن الأعمدة المالية بصمت» — لذلك الفشل صريح ويطلب تعيين أعمدة.
 */
export function detectSchema(doc: CsvDocument): SchemaId {
  if (headerMatches(doc.header, PREVIEW_HEADER)) return 'preview'
  if (headerMatches(doc.header, LEGACY_HEADER)) return 'legacy'
  throw new SchemaError(
    `الأعمدة مش متطابقة مع أي مخطط معروف.\n` +
      `الموجود: ${doc.header.join(' , ')}\n` +
      `المتوقع إما: ${PREVIEW_HEADER.join(' , ')}\n` +
      `أو: ${LEGACY_HEADER.join(' , ')}\n` +
      `لو الملف بأعمدة مختلفة، محتاج تعيين أعمدة يدوي — مش هنخمّن أعمدة مالية.`,
  )
}

/* ─────────────────────────── التواريخ ─────────────────────────── */

/** يحوّل YYYY/MM/DD (المخطط القديم) إلى ISO، ويرفض المستحيل. */
export function legacyDateToIso(value: string): IsoDate | null {
  const m = /^(\d{4})[/-](\d{1,2})[/-](\d{1,2})$/.exec(latinizeDigits(value).trim())
  if (!m) return null
  const iso = formatIsoDate({ year: Number(m[1]), month: Number(m[2]), day: Number(m[3]) })
  return isValidIsoDate(iso) ? iso : null
}

/* ─────────────────────────── مخطط المعاينة ─────────────────────────── */

function parsePreviewRow(row: CsvRow, errors: RowError[]): ParsedRow | null {
  const [dateRaw, name, amountRaw, typeRaw, source, referenceRaw] = row.cells
  const fail = (field: string, message: string) => {
    errors.push({ lineNumber: row.lineNumber, field, message, raw: row.raw })
    return null
  }

  const date = (dateRaw ?? '').trim()
  if (!isValidIsoDate(date)) {
    return fail('date', `التاريخ «${dateRaw}» مش صالح. المتوقع YYYY-MM-DD بتاريخ موجود فعلًا`)
  }

  const amountMinor = tryParseMoney(amountRaw ?? '')
  if (amountMinor === null) return fail('amount', `المبلغ «${amountRaw}» مش رقم صالح`)
  if (amountMinor < 0) return fail('amount', `المبلغ «${amountRaw}» سالب. الاتجاه بيتحدد من عمود type`)
  if (amountMinor === 0) return fail('amount', 'المبلغ صفر — العملية دي مالهاش أثر مالي')

  const type = (typeRaw ?? '').trim().toLowerCase()
  if (!['expense', 'income', 'transfer'].includes(type)) {
    return fail('type', `نوع العملية «${typeRaw}» مش معروف. المتوقع expense أو income أو transfer`)
  }

  // تنبيه spec/README: «لا تفترض أن transfer يعني تلقائيًا تحويلًا داخليًا».
  // العمود ده يحدد **اتجاه السيولة** فقط؛ النوع الاقتصادي يتأكد في المراجعة.
  const direction: 'in' | 'out' = type === 'income' ? 'in' : 'out'

  const reference = (referenceRaw ?? '').trim()
  return {
    lineNumber: row.lineNumber,
    date,
    amountMinor,
    direction,
    merchantName: (name ?? '').trim(),
    reference: reference === '' ? null : reference,
    sourceName: (source ?? '').trim(),
    description: (name ?? '').trim(),
    raw: row.raw,
  }
}

/* ─────────────────────────── مخطط السكربت القديم ─────────────────────────── */

function parseLegacyRow(row: CsvRow, errors: RowError[]): ParsedRow | null {
  const [dateRaw, debitRaw, creditRaw, balanceRaw, merchant, category, opType, details] = row.cells
  const fail = (field: string, message: string) => {
    errors.push({ lineNumber: row.lineNumber, field, message, raw: row.raw })
    return null
  }

  const date = legacyDateToIso(dateRaw ?? '')
  if (!date) return fail('التاريخ', `التاريخ «${dateRaw}» مش صالح. المتوقع YYYY/MM/DD بتاريخ موجود`)

  const debit = tryParseMoney(debitRaw ?? '0')
  const credit = tryParseMoney(creditRaw ?? '0')
  if (debit === null) return fail('مدين', `قيمة المدين «${debitRaw}» مش رقم صالح`)
  if (credit === null) return fail('دائن', `قيمة الدائن «${creditRaw}» مش رقم صالح`)
  if (debit < 0 || credit < 0) return fail('المبلغ', 'المدين والدائن لازم يكونوا موجبين')

  if (debit > 0 && credit > 0) {
    return fail('المبلغ', 'الصف فيه مدين ودائن في نفس الوقت — مش واضح اتجاه الحركة')
  }
  if (debit === 0 && credit === 0) {
    return fail('المبلغ', 'الصف مفيهوش مدين ولا دائن — مالهوش أثر مالي')
  }

  // المدين/الدائن يحددان **اتجاه السيولة، لا النوع الاقتصادي** (spec/05)
  const direction: 'in' | 'out' = credit > 0 ? 'in' : 'out'
  const amountMinor = credit > 0 ? credit : debit

  // الرصيد اختياري: غيابه لا يمنع الاستيراد، لكنه يمنع المقارنة لهذا السطر
  const balance = balanceRaw === undefined || balanceRaw.trim() === ''
    ? undefined
    : (tryParseMoney(balanceRaw) ?? undefined)

  const result: ParsedRow = {
    lineNumber: row.lineNumber,
    date,
    amountMinor,
    direction,
    merchantName: (merchant ?? '').trim(),
    // المخطط القديم بلا عمود مرجع مستقل — المرجع يُستخرج من التفاصيل لاحقًا إن وُجد
    reference: null,
    sourceName: '',
    sourceCategory: (category ?? '').trim() || undefined,
    sourceOperationType: (opType ?? '').trim() || undefined,
    description: (details ?? '').trim(),
    raw: row.raw,
  }
  if (balance !== undefined) result.statedBalanceMinor = balance
  return result
}

/* ─────────────────────────── الواجهة الموحّدة ─────────────────────────── */

/**
 * يحوّل مستند CSV إلى صفوف موحّدة.
 * الصفوف الفاسدة تدخل قائمة الأخطاء برقم الصف ونصها الأصلي،
 * ولا تُسقط بصمت ولا توقف بقية الملف (spec/05).
 */
export function parseRows(doc: CsvDocument, schema?: SchemaId): ParseOutcome {
  const detected = schema ?? detectSchema(doc)
  const errors: RowError[] = []
  const rows: ParsedRow[] = []

  for (const row of doc.rows) {
    const parsed =
      detected === 'preview' ? parsePreviewRow(row, errors) : parseLegacyRow(row, errors)
    if (parsed) rows.push(parsed)
  }

  return { schema: detected, rows, errors }
}

export { parseIsoDate }
