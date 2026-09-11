import { normalizeText } from './normalize'
import type { Halalas } from './money'
import type { IsoDate, MatchingState } from './entities/types'

/**
 * منع التكرار — الدرجات الخمس في spec/05.
 *
 *   ١. بصمة ملف مطابق سبق استيراده  ← تُعالَج في حالة الاستخدام قبل التحليل
 *   ٢. مرجع + هوية حساب متطابقان وتفاصيل متطابقة  ← مكرر مؤكد
 *   ٣. مرجع مطابق وتفاصيل مختلفة                  ← تعارض، لا استبدال صامت
 *   ٤. تشابه بلا مرجع موثوق                       ← مرشح مراجعة، لا حذف تلقائي
 *   ٥. مصدران لعملية واحدة (رسالة + كشف)          ← SourceRecords متعددة
 *
 * ملاحظة حاسمة على الدرجة ٤: «عمليتا مطعم بالمبلغ نفسه قد تكونان حقيقتين» —
 * لذلك التشابه **لا يحذف أبدًا**، يعرض للقرار فقط.
 *
 * بلا مرجع، **سطر الكشف نفسه** يُعرف بالحساب + اليوم + المبلغ + الاتجاه + **الرصيد
 * المعلن بعد الحركة**: عمليتان حقيقيتان متتاليتان لا يتساوى رصيدهما بعدهما. ده بيمسك
 * إعادة استيراد نفس الكشف لما اسم التاجر يتقرا مختلف (HANDOVER §29)، ومن غير رصيد
 * معلن لا يُحكم بالتكرار من اليوم والمبلغ وحدهما.
 */

/** بصمة سطر واردة من الاستيراد. */
export interface DedupeCandidate {
  /** نطاق تفرّد المرجع: مصدر + حساب. مرجع من حسابين مختلفين ليس تكرارًا. */
  smsSource?: boolean
  accountIdentity: string
  sourceReference: string | null
  date: IsoDate
  amountMinor: Halalas
  direction: 'in' | 'out'
  merchantName: string
  rowIndex: number
  /** الرصيد المعلن بعد الحركة في الكشف، إن وُجد. */
  statedBalanceMinor?: Halalas
}

/** سجل موجود مسبقًا للمقارنة. */
export interface ExistingRecord extends DedupeCandidate {
  transactionId: string
}

export interface DedupeVerdict {
  state: MatchingState
  /** سبب القرار بلغة المستخدم. لا حالة بلا تفسير. */
  reason: string
  /** العملية الموجودة المرتبطة، عند التكرار أو التعارض أو التشابه. */
  matchedTransactionId?: string
  /** تفاصيل الاختلاف عند التعارض. */
  conflictFields?: string[]
}

/**
 * مفتاح التفاصيل: تاريخ + مبلغ + اتجاه + اسم تاجر مُطبعَن.
 * يستخدم للتمييز بين «مكرر مؤكد» و«تعارض» عند تطابق المرجع.
 */
export function detailKey(c: DedupeCandidate): string {
  return [c.date, String(c.amountMinor), c.direction, normalizeText(c.merchantName)].join('|')
}

/** مفتاح المرجع مقيّد بهوية الحساب — spec/03: «المرجع الفريد مقيد بالمصدر والحساب». */
export function referenceKey(c: DedupeCandidate): string | null {
  if (!c.sourceReference || !c.sourceReference.trim()) return null
  return `${c.accountIdentity}|${c.sourceReference.trim()}`
}

/** مفتاح سطر الكشف بالرصيد المعلن — null لو مفيش رصيد (رسالة، أو مخطط بلا عمود رصيد). */
export function balanceKey(c: DedupeCandidate): string | null {
  if (c.smsSource || c.statedBalanceMinor === undefined) return null
  return [c.accountIdentity, c.date, c.amountMinor, c.direction, c.statedBalanceMinor].join('|')
}

function diffFields(a: DedupeCandidate, b: DedupeCandidate): string[] {
  const out: string[] = []
  if (a.date !== b.date) out.push('التاريخ')
  if (a.amountMinor !== b.amountMinor) out.push('المبلغ')
  if (a.direction !== b.direction) out.push('اتجاه الحركة')
  if (normalizeText(a.merchantName) !== normalizeText(b.merchantName)) out.push('اسم التاجر')
  return out
}

/** فهرس للمقارنة السريعة، يُبنى مرة قبل فحص الدفعة. */
export interface DedupeIndex {
  byAmountDay: Map<string, ExistingRecord[]>
  byReference: Map<string, ExistingRecord>
  byDetail: Map<string, ExistingRecord[]>
  byBalance: Map<string, ExistingRecord>
}

export function buildDedupeIndex(existing: readonly ExistingRecord[]): DedupeIndex {
  const byAmountDay = new Map<string, ExistingRecord[]>()
  const byReference = new Map<string, ExistingRecord>()
  const byDetail = new Map<string, ExistingRecord[]>()
  const byBalance = new Map<string, ExistingRecord>()
  for (const record of existing) {
    const dayKey = amountDayKey(record)
    byAmountDay.set(dayKey, [...(byAmountDay.get(dayKey) ?? []), record])
    const ref = referenceKey(record)
    if (ref) byReference.set(ref, record)
    const balance = balanceKey(record)
    if (balance && !byBalance.has(balance)) byBalance.set(balance, record)
    const key = detailKey(record)
    const list = byDetail.get(key)
    if (list) list.push(record)
    else byDetail.set(key, [record])
  }
  return { byReference, byDetail, byAmountDay, byBalance }
}

/**
 * يصنّف سطرًا واحدًا مقابل ما هو موجود.
 * الدالة نقية ولا تعدّل الفهرس — الإضافة مسؤولية المستدعي بعد القرار.
 */
export function classifyCandidate(
  candidate: DedupeCandidate,
  index: DedupeIndex,
): DedupeVerdict {
  const ref = referenceKey(candidate)

  if (ref) {
    const existing = index.byReference.get(ref)
    if (existing) {
      const differences = diffFields(candidate, existing)
      // ─── الدرجة ٢: مرجع + حساب + تفاصيل متطابقة ───
      if (differences.length === 0) {
        return {
          state: 'duplicate',
          reason: `نفس المرجع «${candidate.sourceReference}» ونفس التفاصيل — العملية دي متسجلة قبل كده`,
          matchedTransactionId: existing.transactionId,
        }
      }
      // ─── الدرجة ٣: مرجع مطابق وتفاصيل مختلفة ← تعارض ───
      return {
        state: 'conflict',
        reason:
          `نفس المرجع «${candidate.sourceReference}» بس ${differences.join(' و')} مختلف. ` +
          `مش هنكتب فوق القديم من غير قرارك.`,
        matchedTransactionId: existing.transactionId,
        conflictFields: differences,
      }
    }
  }

  // ─── نفس سطر الكشف بالرصيد المعلن، حتى لو اسم التاجر اتقرا مختلف ───
  const sameLine = balanceKey(candidate)
  const lineMatch = sameLine ? index.byBalance.get(sameLine) : undefined
  if (lineMatch) {
    return {
      state: 'duplicate',
      reason:
        'نفس سطر الكشف: نفس اليوم والمبلغ والاتجاه والرصيد بعد العملية في نفس الحساب — ' +
        'متسجلة قبل كده حتى لو الاسم مقروء بشكل مختلف',
      matchedTransactionId: lineMatch.transactionId,
    }
  }

  if (ref) {
    const related = smsSimilarity(candidate, index)
    if (related) return related
    // مرجع جديد بالكامل — لا يُفحص التشابه لأن المرجع دليل موثوق
    return { state: 'new', reason: 'مرجع جديد مش موجود قبل كده' }
  }

  const related = smsSimilarity(candidate, index)
  if (related) return related
  // ─── الدرجة ٤: بلا مرجع موثوق — تشابه يحتاج قرارًا، لا حذفًا ───
  const similar = index.byDetail.get(detailKey(candidate))
  if (similar && similar.length > 0) {
    return {
      state: 'similar',
      reason:
        'نفس التاجر والمبلغ والتاريخ والاتجاه، ومفيش مرجع بنكي يأكد. ' +
        'ممكن تكون نفس العملية وممكن تكون عملية تانية حقيقية — محتاجة قرارك.',
      matchedTransactionId: similar[0].transactionId,
    }
  }

  return { state: 'new', reason: 'عملية جديدة، مفيش ما يشبهها' }
}

/**
 * بصمة الملف — الدرجة ١. تجزئة FNV-1a 64-bit بلا مكتبة.
 * الغرض تمييز «نفس الملف بالضبط»، لا الحماية من العبث.
 */
export function hashContent(content: string): string {
  let hi = 0xcbf29ce4 >>> 0
  let lo = 0x84222325 >>> 0
  for (let i = 0; i < content.length; i++) {
    const code = content.charCodeAt(i)
    lo ^= code & 0xff
    hi ^= (code >>> 8) & 0xff
    // الضرب في 16777619 بأجزاء 16-بت لتفادي فقد الدقة
    const l = Math.imul(lo, 16777619) >>> 0
    const h = (Math.imul(hi, 16777619) + Math.imul(lo, 1099511628211 % 4294967296)) >>> 0
    lo = l
    hi = h
  }
  return (hi.toString(16).padStart(8, '0') + lo.toString(16).padStart(8, '0')).toUpperCase()
}

function amountDayKey(c:DedupeCandidate):string {
 return [c.accountIdentity,c.date,c.amountMinor,c.direction].join('|')
}
function smsSimilarity(c:DedupeCandidate,index:DedupeIndex):DedupeVerdict|null {
 const matches=index.byAmountDay.get(amountDayKey(c))??[]
 const match=matches.find(r=>c.smsSource||r.smsSource)
 if(!match)return null
 return {state:'similar',matchedTransactionId:match.transactionId,reason:'فيه عملية بنفس اليوم والمبلغ والاتجاه في نفس المحفظة، وأحد المصدرين رسالة بنك. قد تكون نفس العملية باسم مختلف؛ غير مختارة للإضافة حتى تراجعها.'}
}

export function importFingerprint(content:string,accountIdentity:string):string {
 return hashContent(JSON.stringify([accountIdentity,content]))
}
