import { categorize, prepareRules } from './categorize'
import { merchantIndex } from './merchantIndex'
import { normalizeText } from './normalize'
import type { Category, ClassificationRule, Id, Merchant, Transaction } from './entities/types'

/**
 * رجوع القواعد والتجار الافتراضيين على الشجرة الجديدة — قرار المالك (OVERRIDES §28.1): «رجّعهم بمعاينة».
 *
 * **دالة قراءة بس.** بترجع:
 * - `rules` / `merchants`: الافتراضيين **الناقصين بس** — قاعدة بنفس النص والطريقة أو نفس المعرّف موجودة ⇒ ما تتضافش؛
 *   تاجر بنفس الاسم المطبعن أو نفس المعرّف موجود ⇒ ما يتضافش. **ولا حاجة موجودة بتتكتب فوقها.**
 *   قاعدة تصنيفها مش موجود في الحساب بتتشال، وتاجر تصنيفه المؤكد مش موجود بيتضاف من غير تصنيف مؤكد.
 * - `suggestions`: العمليات **اللي مالهاش تصنيف ومش مؤكدة** وهتاخد تصنيف مقترح بالمرجع بعد الإضافة.
 *   العملية المصنفة أو المؤكدة ما بتتلمسش (spec/05: «لا تستبدل المؤكد»).
 * - `byCategory`: عدد الاقتراحات لكل تصنيف، الأكبر الأول — للعرض على المالك.
 */

export interface ReferenceSuggestion {
  transactionId: Id
  categoryId: Id
  reason: string
}

export interface ReferenceRestorePlan {
  rules: ClassificationRule[]
  merchants: Merchant[]
  suggestions: ReferenceSuggestion[]
  byCategory: { categoryId: Id; count: number }[]
  /** العمليات اللي من غير تصنيف ومش مؤكدة (المرشحة للاقتراح). */
  uncategorized: number
}

const ruleKey = (rule: Pick<ClassificationRule, 'matchText' | 'matchMode'>) => `${rule.matchMode}|${normalizeText(rule.matchText)}`

export function planReferenceRestore(
  account: {
    rules: readonly ClassificationRule[]
    merchants: readonly Merchant[]
    categories: readonly Category[]
    transactions: readonly Transaction[]
  },
  defaults: { rules: readonly ClassificationRule[]; merchants: readonly Merchant[] },
): ReferenceRestorePlan {
  const categoryIds = new Set(account.categories.map((c) => c.id))

  const ruleIds = new Set(account.rules.map((r) => r.id))
  const ruleKeys = new Set(account.rules.map(ruleKey))
  const rules = defaults.rules.filter((r) => !ruleIds.has(r.id) && !ruleKeys.has(ruleKey(r)) && categoryIds.has(r.categoryId))

  const merchantIds = new Set(account.merchants.map((m) => m.id))
  const merchantNames = new Set(account.merchants.map((m) => normalizeText(m.normalizedName)))
  const merchants = defaults.merchants
    .filter((m) => !merchantIds.has(m.id) && !merchantNames.has(normalizeText(m.normalizedName)))
    .map((m) => {
      if (m.verifiedCategoryId === undefined || categoryIds.has(m.verifiedCategoryId)) return m
      const { verifiedCategoryId: _dropped, ...rest } = m
      return rest
    })

  const deps = {
    merchantsByNormalizedName: merchantIndex([...account.merchants, ...merchants]),
    categoryIdByName: new Map(account.categories.map((c) => [normalizeText(c.name), c.id])),
    rules: prepareRules([...account.rules, ...rules]),
  }

  const suggestions: ReferenceSuggestion[] = []
  let uncategorized = 0
  for (const txn of account.transactions) {
    if (txn.categoryConfirmed || txn.categoryId) continue
    uncategorized++
    const input: Parameters<typeof categorize>[0] = { currentConfirmed: false }
    if (txn.rawMerchantName) input.merchantName = txn.rawMerchantName
    if (txn.rawDescription) input.description = txn.rawDescription
    const result = categorize(input, deps)
    if (result.categoryId && categoryIds.has(result.categoryId)) {
      suggestions.push({ transactionId: txn.id, categoryId: result.categoryId, reason: result.reason })
    }
  }

  const counts = new Map<Id, number>()
  for (const s of suggestions) counts.set(s.categoryId, (counts.get(s.categoryId) ?? 0) + 1)
  const byCategory = [...counts.entries()]
    .map(([categoryId, count]) => ({ categoryId, count }))
    .sort((a, b) => b.count - a.count || a.categoryId.localeCompare(b.categoryId))

  return { rules, merchants, suggestions, byCategory, uncategorized }
}
