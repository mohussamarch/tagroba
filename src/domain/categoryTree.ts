/**
 * مجموعات التصنيفات والتصنيفات اللي بتظهر حسب معلومات الشخص — OVERRIDES §28.1.
 *
 * الترتيب: مجموعة ← تصنيف أساسي ← فرعي. **مكان التصنيف في مجموعة للعرض بس**:
 * هل العملية مصروف ولا لأ بيتحدد من نوعها الاقتصادي (spec/02)، مش من مجموعتها.
 */

export type CategoryGroupKey = 'food' | 'home' | 'transport' | 'personal' | 'saving' | 'movement'

/** معلومة في ملف المستخدم لازم تتأكد قبل ما التصنيف يظهر. */
export type CategoryRequirement = 'hasCar' | 'dependents' | 'renter' | 'domesticWorker' | 'business'

export interface CategoryGroup {
  key: CategoryGroupKey
  name: string
  iconKey: string
  /** `saving` و`movement` مش مصروف — الشاشة لازم تقول ده. */
  kind: 'spend' | 'saving' | 'movement'
}

/** رد المالك «الخمسة اللي اقترحتهم»؛ التحويلات والسحب برا الخمسة. */
export const CATEGORY_GROUPS: readonly CategoryGroup[] = [
  { key: 'food', name: 'الأكل والشرب', iconKey: 'chef-hat', kind: 'spend' },
  { key: 'home', name: 'البيت والالتزامات', iconKey: 'house-heart', kind: 'spend' },
  { key: 'transport', name: 'التنقل', iconKey: 'route', kind: 'spend' },
  { key: 'personal', name: 'الحياة الشخصية', iconKey: 'user-round', kind: 'spend' },
  { key: 'saving', name: 'الادخار والاستثمار', iconKey: 'piggy-bank', kind: 'saving' },
  { key: 'movement', name: 'حركة فلوس مش مصروف', iconKey: 'arrow-left-right', kind: 'movement' },
]

export const REQUIREMENT_LABELS: Record<CategoryRequirement, string> = {
  hasCar: 'لو عنده سيارة',
  dependents: 'لو بيعول زوجة أو أولاد',
  renter: 'لو ساكن بإيجار',
  domesticWorker: 'لو عنده عمالة منزلية',
  business: 'لو عنده شغل خاص',
}

export function isCategoryGroupKey(value: unknown): value is CategoryGroupKey {
  return CATEGORY_GROUPS.some((group) => group.key === value)
}

export function isCategoryRequirement(value: unknown): value is CategoryRequirement {
  return typeof value === 'string' && Object.prototype.hasOwnProperty.call(REQUIREMENT_LABELS, value)
}
