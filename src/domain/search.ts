import { normalizeText, normalizeCompact, latinizeDigits } from './normalize'
import { tryParseMoney, withinRelativeTolerance, type Halalas } from './money'
import type { Transaction } from './entities/types'

/**
 * البحث — spec/01 و spec/06.
 *
 * «يشمل الملاحظات والوسوم والكاش والأسماء البديلة بالعربية واللاتينية.
 *  المبلغ ±5% خيار **معلن**، مع دعم الأرقام العربية.
 *  **لا تُرسل بيانات البحث للخارج.**»
 *
 * كل الحساب هنا محلي في domain/ — لا شبكة ولا خدمة تصنيف خارجية.
 */

/** الهامش المعلن: ٥٪ = 50 بالألف. */
export const AMOUNT_TOLERANCE_PER_THOUSAND = 50

export type SearchMatchField = 'merchant' | 'description' | 'note' | 'category' | 'tag' | 'cash' | 'amount'

export interface SearchableTransaction {
  transaction: Transaction
  categoryName?: string
  tagNames?: readonly string[]
  merchantNames?: readonly string[]
}

export interface SearchHit {
  transaction: Transaction
  /** أي الحقول طابقت — يُعرض للمستخدم فلا يحتار لماذا ظهرت النتيجة. */
  matchedFields: SearchMatchField[]
}

export interface AmountQuery {
  targetMinor: Halalas
  tolerancePerThousand: number
}

export interface ParsedQuery {
  text: string
  /** استعلام مبلغ، إن كان النص رقمًا. */
  amount: AmountQuery | null
}

/**
 * يحلّل نص البحث.
 * الرقم يُعامَل **كنص وكمبلغ معًا**، لأن «85.99» قد يكون مبلغًا
 * وقد يكون جزءًا من اسم. spec/06 يطلب أن يكون بحث المبلغ
 * «مستقلًا عن البحث باسم رمز أصل».
 */
export function parseQuery(raw: string, tolerancePerThousand = AMOUNT_TOLERANCE_PER_THOUSAND): ParsedQuery {
  const text = raw.trim()
  const latin = latinizeDigits(text).replace(/٫/g, '.')
  // رقم صافٍ فقط (مع فواصل عشرية أو آلاف) يُعد استعلام مبلغ
  const looksNumeric = /^[\d.,\s]+$/.test(latin) && /\d/.test(latin)
  const amountMinor = looksNumeric ? tryParseMoney(latin) : null

  return {
    text,
    amount:
      amountMinor !== null && amountMinor > 0
        ? { targetMinor: amountMinor, tolerancePerThousand }
        : null,
  }
}

/** كلمة «كاش» تبحث في شارة الكاش لا في النص فقط. */
const CASH_WORDS = new Set([normalizeText('كاش'), normalizeText('نقدي'), 'CASH'])

function textMatches(haystack: string | undefined, needleNormalized: string, needleCompact: string): boolean {
  if (!haystack) return false
  return (
    normalizeText(haystack).includes(needleNormalized) ||
    normalizeCompact(haystack).includes(needleCompact)
  )
}

/**
 * يبحث في مجموعة عمليات. دالة نقية.
 * المطابقة النصية والمطابقة بالمبلغ **مستقلتان**: نتيجة تظهر لو طابقت أيهما.
 */
export function searchTransactions(
  items: readonly SearchableTransaction[],
  query: ParsedQuery,
): SearchHit[] {
  const needleNormalized = normalizeText(query.text)
  const needleCompact = normalizeCompact(query.text)
  const isCashQuery = CASH_WORDS.has(needleNormalized)

  if (!needleNormalized && !query.amount) return []

  const hits: SearchHit[] = []

  for (const item of items) {
    const t = item.transaction
    const matched: SearchMatchField[] = []

    if (needleNormalized) {
      if (textMatches(t.rawMerchantName, needleNormalized, needleCompact) || item.merchantNames?.some(n=>textMatches(n,needleNormalized,needleCompact))) matched.push('merchant')
      if (textMatches(t.rawDescription, needleNormalized, needleCompact)) matched.push('description')
      if (textMatches(t.note, needleNormalized, needleCompact)) matched.push('note')
      if (textMatches(item.categoryName, needleNormalized, needleCompact)) matched.push('category')
      if (item.tagNames?.some((tag) => textMatches(tag, needleNormalized, needleCompact))) {
        matched.push('tag')
      }
      if (isCashQuery && t.isCashTagged) matched.push('cash')
    }

    if (
      query.amount &&
      withinRelativeTolerance(t.amountMinor, query.amount.targetMinor, query.amount.tolerancePerThousand)
    ) {
      matched.push('amount')
    }

    if (matched.length > 0) hits.push({ transaction: t, matchedFields: matched })
  }

  return hits
}
