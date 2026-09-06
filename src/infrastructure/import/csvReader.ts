/**
 * قارئ CSV بمعيار RFC 4180 + متطلبات spec/05:
 * BOM · UTF-8 · فواصل داخل الاقتباس · أسطر CRLF · اقتباس مزدوج داخل الحقل.
 *
 * لا مكتبة خارجية: القارئ 60 سطرًا، وإضافة مكتبة تخالف قاعدة «لا مكتبة بلا سبب».
 */

export interface CsvRow {
  /** رقم الصف في الملف كما يراه المستخدم (الترويسة = 1). */
  lineNumber: number
  cells: string[]
  /** النص الأصلي للصف — يُحفظ للمراجعة (spec/05). */
  raw: string
}

export interface CsvDocument {
  header: string[]
  rows: CsvRow[]
}

export class CsvError extends Error {}

/** حدود معلنة — spec/05: «ضع حدودًا للملف وعدد الصفوف والوقت مع رسالة واضحة». */
export const CSV_LIMITS = {
  maxBytes: 12 * 1024 * 1024, // 12 ميجابايت
  maxRows: 100_000,
  maxCellLength: 20_000,
} as const

export function stripBom(text: string): string {
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text
}

export function parseCsv(input: string): CsvDocument {
  if (input.length > CSV_LIMITS.maxBytes) {
    throw new CsvError(
      `الملف أكبر من الحد المسموح (${Math.round(CSV_LIMITS.maxBytes / 1024 / 1024)} ميجابايت)`,
    )
  }

  const text = stripBom(input)
  const rows: CsvRow[] = []

  let cells: string[] = []
  let field = ''
  let inQuotes = false
  let lineNumber = 1
  let rawStart = 0

  const pushRow = (endIndex: number) => {
    cells.push(field)
    // تجاهل الأسطر الفارغة تمامًا
    if (!(cells.length === 1 && cells[0] === '')) {
      rows.push({ lineNumber, cells, raw: text.slice(rawStart, endIndex) })
      if (rows.length > CSV_LIMITS.maxRows) {
        throw new CsvError(`الملف فيه أكتر من ${CSV_LIMITS.maxRows} صف`)
      }
    }
    cells = []
    field = ''
    lineNumber++
    rawStart = endIndex + 1
  }

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]

    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"' // اقتباس مهروب داخل حقل
          i++
        } else {
          inQuotes = false
        }
      } else {
        field += ch
      }
      continue
    }

    if (ch === '"') {
      inQuotes = true
    } else if (ch === ',') {
      cells.push(field)
      field = ''
    } else if (ch === '\r') {
      // CRLF: تُعالَج عند \n التالي
      if (text[i + 1] === '\n') continue
      pushRow(i)
    } else if (ch === '\n') {
      const end = text[i - 1] === '\r' ? i - 1 : i
      pushRow(end)
      rawStart = i + 1
    } else {
      field += ch
      if (field.length > CSV_LIMITS.maxCellLength) {
        throw new CsvError(`خانة في السطر ${lineNumber} أطول من الحد المسموح`)
      }
    }
  }

  if (inQuotes) {
    throw new CsvError(`اقتباس مفتوح ولم يُغلق — الملف ناقص أو تالف عند السطر ${lineNumber}`)
  }
  if (field !== '' || cells.length > 0) pushRow(text.length)

  if (rows.length === 0) throw new CsvError('الملف فاضي')

  const header = rows[0].cells.map((h) => h.trim())
  return { header, rows: rows.slice(1) }
}
