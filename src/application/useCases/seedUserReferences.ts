import { normalizeText } from '../../domain/normalize'
import type { Category, ClassificationRule, Merchant } from '../../domain/entities/types'
import type {
  CategoryRepository,
  MerchantRepository,
  RuleRepository,
  UnitOfWork,
} from '../ports/repositories'

/**
 * SeedUserReferences — زرع المراجع الأولية في تخزين المستخدم عند أول دخول.
 *
 * يحل قيد ARCHITECTURE.md §10.6.
 *
 * `spec/05`: «ملفات القواعد مرجع أولي **قابل للتحرير**، وليست سياسة لا تتغير.»
 * القابلية للتحرير تستلزم أن تُخزَّن، وإلا ضاع التعديل عند إعادة الفتح.
 *
 * القاعدة الحاكمة: **الزرع مرة واحدة فقط.**
 * لو وُجدت تصنيفات محفوظة، فالمستخدم عدّلها أو حذف منها، ولا يجوز
 * أن يُعاد المرجع الأصلي فوقها فيُلغى تعديله بلا علمه.
 */

export interface SeedOutcome {
  /** هل زُرعت المراجع الآن؟ false = كانت موجودة فلم يُلمس شيء. */
  seeded: boolean
  categories: number
  rules: number
  merchants: number
  reason: string
}

export interface SeedUserReferencesDeps {
  categories: CategoryRepository
  rules: RuleRepository
  merchants: MerchantRepository
  uow: UnitOfWork
}

export interface SeedSource {
  categories: readonly Category[]
  rules: readonly ClassificationRule[]
  merchants: readonly Merchant[]
}

export function makeSeedUserReferences(deps: SeedUserReferencesDeps) {
  return async function seed(source: SeedSource): Promise<SeedOutcome> {
    const existing = await deps.categories.listAll()

    if (existing.length > 0) {
      return {
        seeded: false,
        categories: existing.length,
        rules: (await deps.rules.listAll()).length,
        merchants: 0, // لا تُعدّ: قائمة التجار كبيرة والعدّ قراءة بلا داعٍ
        reason: 'المراجع متزروعة قبل كده. مش هنكتب فوق تعديلاتك.',
      }
    }

    return deps.uow.run(async () => {
      for (const category of source.categories) await deps.categories.save(category)
      await deps.rules.saveMany(source.rules)
      await deps.merchants.saveMany(source.merchants)

      return {
        seeded: true,
        categories: source.categories.length,
        rules: source.rules.length,
        merchants: source.merchants.length,
        reason: 'أول دخول: زرعنا التصنيفات والقواعد والتجار كمرجع أولي تقدر تعدّله.',
      }
    })
  }
}

/**
 * يبني خرائط التصنيف من مستودعات محفوظة.
 * موجودة هنا لا في `categorize.ts` لأن الأخير في `domain/` ولا يعرف المستودعات.
 */
export async function loadCategorizeMaps(deps: {
  merchants: MerchantRepository
  categories: CategoryRepository
  rules: RuleRepository
}) {
  const [merchants, categories, rules] = await Promise.all([
    deps.merchants.listAll(),
    deps.categories.listAll(),
    deps.rules.listAll(),
  ])
  return {
    merchantsByNormalizedName: new Map(merchants.map((m) => [m.normalizedName, m])),
    categoryIdByName: new Map(categories.map((c) => [normalizeText(c.name), c.id])),
    rules,
  }
}
