import { normalizeText } from '../../domain/normalize'
import type {
  Category,
  ClassificationRule,
  Id,
  Merchant,
  RuleMatchMode,
} from '../../domain/entities/types'
import type {
  CategoryRepository,
  IdGenerator,
  MerchantRepository,
  RuleRepository,
} from '../ports/repositories'

/**
 * ManageRules — تحرير قواعد التصنيف والتجار.
 *
 * `spec/05` بيقول إن المراجع الأولية **قابلة للتحرير**، وكانت محفوظة في
 * حساب المستخدم من المرحلة الأولى — بس مفيش شاشة تعدّلها. ده اللي بيتبني هنا.
 *
 * ترتيب الأولويات في التصنيف ثابت ومكتوب في `domain/categorize.ts`:
 * تأكيد المستخدم ← تاجر موثّق ← قواعد مرتّبة ← اقتراح.
 * تعديل القواعد **مش بيغيّر الترتيب ده**، بيغيّر محتوى الخطوة التالتة بس.
 *
 * ⚠️ تعديل قاعدة **ما بيعيدش تصنيف العمليات القديمة تلقائيًا**.
 * `spec/05`: «المؤكد لا يُكتب فوقه» — وإعادة التصنيف الشاملة كانت هتدهس
 * قرارات المستخدم اليدوية. القواعد الجديدة بتنطبق على اللي جاي.
 */

export class RulesError extends Error {}

export const MAX_MATCH_TEXT = 60

export interface RuleRow {
  rule: ClassificationRule
  categoryName: string
  /** التصنيف اتشال؟ القاعدة بتفضل ظاهرة بسببها لا بتختفي بصمت. */
  categoryMissing: boolean
}

export interface MerchantRow {
  merchant: Merchant
  /** التصنيف الموثّق — أقوى من كل القواعد. */
  verifiedCategoryName: string | null
}

export interface ManageRulesDeps {
  rules: RuleRepository
  merchants: MerchantRepository
  categories: CategoryRepository
  ids: IdGenerator
}

export function makeManageRules(deps: ManageRulesDeps) {
  async function categoryIndex(): Promise<Map<Id, Category>> {
    return new Map((await deps.categories.listAll()).map((c) => [c.id, c]))
  }

  /** القواعد بترتيب تطبيقها — الأصغر أولوية بيتطبق أولًا. */
  async function listRules(): Promise<RuleRow[]> {
    const [rules, categories] = await Promise.all([deps.rules.listAll(), categoryIndex()])
    return rules
      .slice()
      .sort((a, b) => a.priority - b.priority || a.matchText.localeCompare(b.matchText, 'ar'))
      .map((rule) => {
        const category = categories.get(rule.categoryId)
        return {
          rule,
          categoryName: category?.name ?? 'تصنيف محذوف',
          categoryMissing: !category,
        }
      })
  }

  async function listMerchants(): Promise<MerchantRow[]> {
    const [merchants, categories] = await Promise.all([
      deps.merchants.listAll(),
      categoryIndex(),
    ])
    return merchants
      .slice()
      .sort((a, b) => a.displayName.localeCompare(b.displayName, 'ar'))
      .map((merchant) => ({
        merchant,
        verifiedCategoryName: merchant.verifiedCategoryId
          ? (categories.get(merchant.verifiedCategoryId)?.name ?? 'تصنيف محذوف')
          : null,
      }))
  }

  async function requireCategory(categoryId: Id): Promise<void> {
    const categories = await deps.categories.listAll()
    if (!categories.some((c) => c.id === categoryId)) {
      throw new RulesError('التصنيف ده مش موجود')
    }
  }

  async function addRule(input: {
    matchText: string
    matchMode: RuleMatchMode
    categoryId: Id
    priority?: number
  }): Promise<ClassificationRule> {
    const matchText = input.matchText.trim()
    if (!matchText) throw new RulesError('اكتب النص اللي القاعدة تدوّر عليه')
    if (matchText.length > MAX_MATCH_TEXT) {
      throw new RulesError(`النص أطول من ${MAX_MATCH_TEXT} حرف`)
    }
    await requireCategory(input.categoryId)

    const existing = await deps.rules.listAll()
    const normalized = normalizeText(matchText)
    if (
      existing.some(
        (r) => normalizeText(r.matchText) === normalized && r.matchMode === input.matchMode,
      )
    ) {
      throw new RulesError(`فيه قاعدة بنفس النص «${matchText}» ونفس طريقة المطابقة`)
    }

    // الجديدة بتيجي في الآخر فما تسبقش قاعدة موجودة بلا ما المستخدم يطلب
    const priority =
      input.priority ?? (existing.length === 0 ? 10 : Math.max(...existing.map((r) => r.priority)) + 10)

    const rule: ClassificationRule = {
      id: deps.ids.next('rule'),
      priority,
      matchText,
      matchMode: input.matchMode,
      categoryId: input.categoryId,
      enabled: true,
    }
    await deps.rules.saveMany([rule])
    return rule
  }

  async function updateRule(id: Id, patch: Partial<Omit<ClassificationRule, 'id'>>): Promise<void> {
    const rules = await deps.rules.listAll()
    const rule = rules.find((r) => r.id === id)
    if (!rule) throw new RulesError('القاعدة دي مش موجودة')

    if (patch.categoryId) await requireCategory(patch.categoryId)
    if (patch.matchText !== undefined) {
      const text = patch.matchText.trim()
      if (!text) throw new RulesError('نص القاعدة مايبقاش فاضي')
      if (text.length > MAX_MATCH_TEXT) throw new RulesError(`النص أطول من ${MAX_MATCH_TEXT} حرف`)
      patch = { ...patch, matchText: text }
    }

    await deps.rules.saveMany([{ ...rule, ...patch }])
  }

  /**
   * يقفل قاعدة أو يفتحها. **مفيش حذف**: القاعدة المقفولة بتفضل ظاهرة
   * فالمستخدم يفتكر إنها موجودة ومقفولة، بدل ما يدوّر على سبب اختفاء التصنيف.
   */
  async function setRuleEnabled(id: Id, enabled: boolean): Promise<void> {
    await updateRule(id, { enabled })
  }

  /**
   * يثبّت تصنيف تاجر — **أقوى من كل القواعد** (`spec/05`).
   * `null` بيشيل التثبيت فيرجع للقواعد.
   */
  async function setMerchantCategory(merchantId: Id, categoryId: Id | null): Promise<void> {
    const merchants = await deps.merchants.listAll()
    const merchant = merchants.find((m) => m.id === merchantId)
    if (!merchant) throw new RulesError('التاجر ده مش موجود')
    if (categoryId !== null) await requireCategory(categoryId)

    const next: Merchant = { ...merchant }
    if (categoryId) next.verifiedCategoryId = categoryId
    else delete next.verifiedCategoryId
    await deps.merchants.saveMany([next])
  }

  async function renameMerchant(merchantId: Id, displayName: string): Promise<void> {
    const name = displayName.trim()
    if (!name) throw new RulesError('اكتب اسم التاجر')

    const merchants = await deps.merchants.listAll()
    const merchant = merchants.find((m) => m.id === merchantId)
    if (!merchant) throw new RulesError('التاجر ده مش موجود')

    /*
     * الاسم المطبّع **ما بيتغيّرش** مع إعادة التسمية: هو مفتاح المطابقة
     * مع الكشوف الجاية، وتغييره كان هيفصل التاجر عن عملياته القديمة.
     * المعروض بس هو اللي بيتغيّر.
     */
    await deps.merchants.saveMany([{ ...merchant, displayName: name }])
  }

  return {
    listRules,
    listMerchants,
    addRule,
    updateRule,
    setRuleEnabled,
    setMerchantCategory,
    renameMerchant,
  }
}
