import { stripBom } from './csvReader'
import type { ImportSourceType } from '../../domain/entities/types'

/** علامات مميِّزة لكل مخطط، من spec/05. */
const PREVIEW_HEADER_START = 'date,name,amount'
const LEGACY_DATE = 'التاريخ'
const LEGACY_DEBIT = 'مدين'

/**
 * نوع المصدر للتسجيل في الدفعة. المخطط نفسه يُكتشف من الترويسة داخل
 * parseRows؛ ده وسم للدفعة عشان نعرف بعدين الملف جه منين.
 */
export function guessSourceType(content: string): ImportSourceType {
  const header = stripBom(content).slice(0, 200)
  if (header.startsWith(PREVIEW_HEADER_START)) return 'csv_preview'
  if (header.includes(LEGACY_DATE) && header.includes(LEGACY_DEBIT)) return 'csv_legacy'
  return 'csv_preview'
}

