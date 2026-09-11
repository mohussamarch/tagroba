import type { EconomicKind } from './entities/economicKind'
import type { Transaction } from './entities/types'
import { isBulkConfirmable, suggestEconomicKind } from './suggestEconomicKind'

/**
 * الأرقام دايمًا ظاهرة — OVERRIDES §18 (تعديل المالك 2026-09-11):
 * «لازم تظهر أرقام سواء البرنامج متأكد من كل العمليات أو لا… يكتبها (تقريبي) أو
 *  يكتب تحتها ملاحظة إن فيه مثلًا 40 عملية محتاجة تأكيد… لازم كل المتوسطات وكل
 *  حاجة تكون شغالة، وبعدين نعدلها».
 *
 * كل عملية نوعها «غير محدد» تتحسب **في المجاميع فقط** بنوع مفترض — لا يُكتب شيء في
 * التخزين، ولا يُلمس نوع أكّده المستخدم:
 *   ١. اقتراح عالي الثقة  ⇒ نوعه (واضح، لا يحتاج تأكيد)
 *   ٢. اقتراح مرجّح أو بدائل ⇒ النوع المقترح (محتاج تأكيد)
 *   ٣. لا اقتراح           ⇒ الصادر مصروف والوارد دخل (محتاج تأكيد)
 * النتيجة تُعرض «تقريبي» مع عدد العمليات المحتاجة تأكيد.
 */

/** الافتراض عند غياب أي دليل: حركة السيولة كما هي. */
const FALLBACK: Record<'in' | 'out', EconomicKind> = { out: 'purchase', in: 'salary' }

export interface EstimatedView {
  /** نفس العمليات؛ غير المحدد منها نسخة بنوعه المفترض. */
  transactions: Transaction[]
  /** كام عملية اتحسبت بنوع مش متأكد منه المستخدم (واضحة أو مفترضة). */
  estimatedCount: number
  /** منها: كام عملية التطبيق مش متأكد منها — دي اللي محتاجة تأكيد. */
  needsReviewCount: number
}

export function withEstimatedKinds(
  transactions: readonly Transaction[],
  categoryNameById: ReadonlyMap<string, string>,
): EstimatedView {
  let estimatedCount = 0
  let needsReviewCount = 0
  const view = transactions.map((t) => {
    if (t.economicKind !== 'unclassified' || t.economicKindConfirmed) return t
    const categoryName = t.categoryId ? categoryNameById.get(t.categoryId) : undefined
    const suggestion = suggestEconomicKind({
      direction: t.observedDirection,
      ...(categoryName ? { categoryName } : {}),
      merchantName: t.rawMerchantName ?? '',
      description: t.rawDescription ?? '',
    })
    estimatedCount += 1
    if (isBulkConfirmable(suggestion) && suggestion.kind !== null) {
      return { ...t, economicKind: suggestion.kind }
    }
    needsReviewCount += 1
    return { ...t, economicKind: suggestion.kind ?? FALLBACK[t.observedDirection] }
  })
  return { transactions: view, estimatedCount, needsReviewCount }
}
