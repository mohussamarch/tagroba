import { merchantIndex } from '../../domain/merchantIndex'
import { normalizeText } from '../../domain/normalize'
import type { ReferenceSeedPort, SeedSource } from '../ports/ReferenceSeedPort'
export type { SeedSource } from '../ports/ReferenceSeedPort'
import type {
  CategoryRepository,
  MerchantRepository,
  RuleRepository,
} from '../ports/repositories'

/**
 * SeedUserReferences — زرع المراجع الأولية في تخزين المستخدم عند أول دخول.
 *
 * يحل قيد ARCHITECTURE.md §10.6.
 *
 * `spec/05`: «ملفات القواعد مرجع أولي **قابل للتحرير**، وليست سياسة لا تتغير.»
 * القابلية للتحرير تستلزم أن تُخزَّن، وإلا ضاع التعديل عند إعادة الفتح.
 *
 * الزرع له علامة دائمة: pending قبل أي كتابة، complete بعد اكتمالها.
 * الاستئناف يضيف الناقص فقط. الحساب القديم بلا علامة ومعه تصنيفات لا يُغيَّر.
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
  progress: ReferenceSeedPort
}

export function makeSeedUserReferences(deps: SeedUserReferencesDeps) {
  return async function seed(source: SeedSource): Promise<SeedOutcome> {
    const existing = await deps.categories.listAll()

    if (await deps.progress.begin(existing.length > 0) === 'complete') {
      return {
        seeded: false,
        categories: existing.length,
        rules: (await deps.rules.listAll()).length,
        merchants: 0, // لا تُعدّ: قائمة التجار كبيرة والعدّ قراءة بلا داعٍ
        reason: 'المراجع متزروعة قبل كده. مش هنكتب فوق تعديلاتك.',
      }
    }

    await deps.progress.insertMissing(source)
    await deps.progress.complete()

    return {
      seeded: true,
      categories: source.categories.length,
      rules: source.rules.length,
      merchants: source.merchants.length,
      reason: 'اكتمل تجهيز التصنيفات والقواعد والتجار كمرجع أولي تقدر تعدّله.',
    }
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
    merchantsByNormalizedName: merchantIndex(merchants),
    categoryIdByName: new Map(categories.map((c) => [normalizeText(c.name), c.id])),
    rules,
  }
}
