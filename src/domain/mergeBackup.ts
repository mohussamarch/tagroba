import { detailKey } from './dedupe'
import type { Transaction } from './entities/types'

/**
 * قواعد دمج نسخة احتياطية — **دوال نقية** بلا أي تخزين.
 *
 * القرار (المالك، 2026-09-07): **الدمج** لا الاستبدال ولا الرفض.
 * مسجَّل في `OVERRIDES §12`.
 *
 * قاعدتان تحكمان الدمج، والاتنين اتجاههما واحد: **الموجود ما يتدهسش**.
 *
 * ١. **نفس المعرّف ⇒ الموجود يفضل.** النسخة صورة قديمة؛ الموجود ممكن
 *    يكون المستخدم عدّله بعدها (أكّد نوعه، غيّر تصنيفه). الكتابة فوقه
 *    كانت هترجّع قرارات قديمة بلا ما حد يسأله.
 *
 * ٢. **نفس المحتوى بمعرّف مختلف ⇒ يتخطى برضه.** `spec/03`:
 *    «الاستعادة **لا تكرر العمليات** أو تخلط العملات». العملية الواحدة
 *    ممكن تكون اتخزنت بمعرّفين لو الكشف اتستورد على جهازين.
 *    الدمج بالمعرّف وحده كان هيخليها عمليتين، والمصروف يتضاعف بصمت.
 */

export type MergeAction = 'added' | 'skipped_same_id' | 'skipped_same_content'

export interface MergeDecision<T> {
  item: T
  action: MergeAction
  /** سبب التخطي بلغة المستخدم. `null` للمضاف. */
  reason: string | null
}

export interface MergeReport<T> {
  /** العناصر اللي هتتكتب فعلًا. */
  toAdd: T[]
  addedCount: number
  skippedSameId: number
  skippedSameContent: number
  decisions: MergeDecision<T>[]
}

/**
 * دمج عام بالمعرّف — للتصنيفات والقواعد والتجار والمحافظ.
 * ما بيبصّش للمحتوى: عنصر بنفس المعرّف موجود ⇒ يتخطى.
 */
export function mergeById<T extends { id: string }>(
  incoming: readonly T[],
  existing: readonly T[],
): MergeReport<T> {
  const existingIds = new Set(existing.map((e) => e.id))
  const seen = new Set<string>()
  const decisions: MergeDecision<T>[] = []
  const toAdd: T[] = []

  for (const item of incoming) {
    // التكرار جوه الملف نفسه بيتعامل زي التكرار مع الموجود
    if (existingIds.has(item.id) || seen.has(item.id)) {
      decisions.push({
        item,
        action: 'skipped_same_id',
        reason: 'موجود عندك بنفس المعرّف — الموجود ما اتغيّرش',
      })
      continue
    }
    seen.add(item.id)
    toAdd.push(item)
    decisions.push({ item, action: 'added', reason: null })
  }

  return {
    toAdd,
    addedCount: toAdd.length,
    skippedSameId: decisions.filter((d) => d.action === 'skipped_same_id').length,
    skippedSameContent: 0,
    decisions,
  }
}

/**
 * مفتاح محتوى العملية: تاريخ + مبلغ + اتجاه + تاجر + محفظة.
 *
 * بيستعمل `detailKey` بتاع منع التكرار نفسه، فقاعدة «إيه اللي يعتبر
 * نفس العملية» متكتبة **مرة واحدة** في المشروع كله. المحفظة بتتضاف
 * لأن نفس المبلغ في نفس اليوم من محفظتين مختلفتين عمليتان لا واحدة.
 */
export function transactionContentKey(txn: Transaction): string {
  return [
    txn.currency,
    detailKey({
      accountIdentity: '',
      sourceReference: null,
      date: txn.occurredAt,
      amountMinor: txn.amountMinor,
      direction: txn.observedDirection,
      merchantName: txn.merchantId ?? txn.rawMerchantName ?? '',
      rowIndex: 0,
    }),
    txn.walletId ?? '',
  ].join('|')
}

/** دمج العمليات: بالمعرّف **وبالمحتوى**. */
export function mergeTransactions(
  incoming: readonly Transaction[],
  existing: readonly Transaction[],
): MergeReport<Transaction> {
  const existingIds = new Set(existing.map((t) => t.id))
  const existingContent = new Set(existing.map(transactionContentKey))

  const seenIds = new Set<string>()
  const seenContent = new Set<string>()
  const decisions: MergeDecision<Transaction>[] = []
  const toAdd: Transaction[] = []

  for (const txn of incoming) {
    if (existingIds.has(txn.id) || seenIds.has(txn.id)) {
      decisions.push({
        item: txn,
        action: 'skipped_same_id',
        reason: 'العملية دي موجودة عندك — الموجود ما اتغيّرش',
      })
      continue
    }

    const key = transactionContentKey(txn)
    if (existingContent.has(key) || seenContent.has(key)) {
      decisions.push({
        item: txn,
        action: 'skipped_same_content',
        reason: 'فيه عملية بنفس التاريخ والمبلغ والاتجاه والمحفظة — متضافتش تاني',
      })
      continue
    }

    seenIds.add(txn.id)
    seenContent.add(key)
    toAdd.push(txn)
    decisions.push({ item: txn, action: 'added', reason: null })
  }

  return {
    toAdd,
    addedCount: toAdd.length,
    skippedSameId: decisions.filter((d) => d.action === 'skipped_same_id').length,
    skippedSameContent: decisions.filter((d) => d.action === 'skipped_same_content').length,
    decisions,
  }
}

/**
 * العملات المختلفة `spec/03`: «الاستعادة لا تكرر العمليات
 * **أو تخلط العملات**». بترجّع العملات اللي في النسخة ومش في الموجود.
 */
export function foreignCurrencies(
  incoming: readonly Transaction[],
  existing: readonly Transaction[],
): string[] {
  const known = new Set(existing.map((t) => t.currency))
  // أول استعادة على حساب فاضي: أي عملة في النسخة مقبولة
  if (known.size === 0) return []
  const found = new Set<string>()
  for (const t of incoming) if (!known.has(t.currency)) found.add(t.currency)
  return [...found]
}
