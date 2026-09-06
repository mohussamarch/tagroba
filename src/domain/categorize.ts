import { normalizeText, normalizedContains } from './normalize'
import type { ClassificationRule, Id, Merchant, ReviewState } from './entities/types'

/**
 * محرك التصنيف — ترتيب الأولوية في spec/05:
 *
 *   ١. تأكيد المستخدم على العملية      ← لا يُكتب فوقه أبدًا
 *   ٢. تعيين التاجر المؤكد
 *   ٣. القواعد المرتبة حسب priority
 *   ٤. اقتراح للمراجعة                  ← وإلا: بلا تصنيف
 *
 * «لا تستبدل المؤكد عند إعادة الاستيراد» — أهم شرط هنا.
 * «ملفات القواعد مرجع أولي قابل للتحرير، وليست سياسة لا تتغير.»
 */

export type CategorizationSource =
  | 'user_confirmed' // ١
  | 'verified_merchant' // ٢
  | 'rule' // ٣
  | 'source_category' // اقتراح من عمود التصنيف في الملف
  | 'none'

export interface CategorizationInput {
  /** التصنيف الحالي على العملية، إن وُجد. */
  currentCategoryId?: Id
  /** هل أكّده المستخدم بنفسه؟ */
  currentConfirmed: boolean
  /** اسم التاجر كما ورد في المصدر. */
  merchantName?: string
  /** وصف العملية الكامل — تُطبَّق عليه القواعد أيضًا. */
  description?: string
  /** عمود التصنيف في ملف المصدر — دليل لا حكم. */
  sourceCategory?: string
}

export interface CategorizationResult {
  categoryId: Id | undefined
  source: CategorizationSource
  reviewState: ReviewState
  /** سبب القرار بلغة المستخدم — spec/04: «شارة مراجعة وتفسير السبب». */
  reason: string
  /** القاعدة أو التاجر الذي طابق، للعرض والتحرير. */
  matchedBy?: string
}

export interface CategorizeDeps {
  /** التجار بأسمائهم المُطبعَنة. */
  merchantsByNormalizedName: ReadonlyMap<string, Merchant>
  /** القواعد مرتّبة تصاعديًا بـ priority. */
  rules: readonly ClassificationRule[]
  /** أسماء التصنيفات إلى معرّفاتها — لترجمة عمود التصنيف النصي. */
  categoryIdByName: ReadonlyMap<string, Id>
}

/** يرتّب القواعد ويستبعد المعطّلة. يُستدعى مرة عند التحميل لا لكل عملية. */
export function prepareRules(rules: readonly ClassificationRule[]): ClassificationRule[] {
  return rules.filter((r) => r.enabled).sort((a, b) => a.priority - b.priority)
}

function ruleMatches(rule: ClassificationRule, haystack: string): boolean {
  const target = normalizeText(rule.matchText)
  if (!target) return false
  const text = normalizeText(haystack)
  switch (rule.matchMode) {
    case 'exact':
      return text === target
    case 'startsWith':
      return text.startsWith(target)
    case 'contains':
      return normalizedContains(haystack, rule.matchText)
  }
}

export function categorize(
  input: CategorizationInput,
  deps: CategorizeDeps,
): CategorizationResult {
  // ─── ١. تأكيد المستخدم — يعلو على كل شيء ولا يُكتب فوقه ───
  if (input.currentConfirmed && input.currentCategoryId) {
    return {
      categoryId: input.currentCategoryId,
      source: 'user_confirmed',
      reviewState: 'confirmed',
      reason: 'أنت أكّدت التصنيف ده بنفسك',
    }
  }

  // ─── ٢. تعيين التاجر المؤكد ───
  if (input.merchantName) {
    const merchant = deps.merchantsByNormalizedName.get(normalizeText(input.merchantName))
    if (merchant?.verifiedCategoryId) {
      return {
        categoryId: merchant.verifiedCategoryId,
        source: 'verified_merchant',
        reviewState: 'confirmed',
        reason: `التاجر «${merchant.displayName}» له تصنيف مؤكد`,
        matchedBy: merchant.displayName,
      }
    }
  }

  // ─── ٣. القواعد المرتّبة ───
  // تُطبَّق على اسم التاجر أولًا ثم الوصف، لأن التاجر أدل من نص العملية الطويل.
  const haystacks = [input.merchantName, input.description].filter(
    (v): v is string => typeof v === 'string' && v.length > 0,
  )
  for (const rule of deps.rules) {
    for (const haystack of haystacks) {
      if (ruleMatches(rule, haystack)) {
        return {
          categoryId: rule.categoryId,
          source: 'rule',
          reviewState: 'suggested',
          reason: `قاعدة «${rule.matchText}» طابقت`,
          matchedBy: rule.matchText,
        }
      }
    }
  }

  // ─── ٤. عمود التصنيف في الملف — اقتراح يحتاج مراجعة ───
  if (input.sourceCategory) {
    const id = deps.categoryIdByName.get(normalizeText(input.sourceCategory))
    if (id) {
      return {
        categoryId: id,
        source: 'source_category',
        reviewState: 'suggested',
        reason: `التصنيف جه من عمود التصنيف في الملف: «${input.sourceCategory}»`,
        matchedBy: input.sourceCategory,
      }
    }
  }

  // ─── بلا تصنيف: يُعرض للمراجعة، ولا يُخترع له تصنيف ───
  return {
    categoryId: undefined,
    source: 'none',
    reviewState: 'needs_review',
    reason: 'مفيش قاعدة ولا تاجر مؤكد طابق العملية دي',
  }
}
