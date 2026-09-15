import { addMoney, rateOfMoney, type Halalas } from './money'
import { CATEGORY_GROUPS, isCategoryGroupKey, type CategoryGroupKey } from './categoryTree'
import type { CategorySlice } from './analytics'
import type { Category, Id } from './entities/types'

/**
 * توزيع المصروف «من برا» بالمجموعات — طلب المالك (OVERRIDES §28.1):
 * «من برا قبل التحليل العميق للمصاريف يظهر بالكتير 4 أو 5 أنواع للصرف عامة».
 *
 * - بياخد توزيع التصنيفات الجاهز (`categoryDistribution`) ويجمّعه: فرعي ← أساسي ← مجموعة.
 *   **مفيش عملية بتتحسب مرتين**: كل شريحة ليها تصنيف نهائي واحد، فمجموع المجموعات = الإجمالي بالظبط.
 * - العمليات بلا تصنيف (أو تصنيفها اتمسح) ⇒ صف «بلا تصنيف»، ولا تتلحق بمجموعة مخترعة.
 * - تصنيف مالوش مجموعة (الحسابات القديمة) ⇒ صف «من غير مجموعة». لو **ولا تصنيف** في التوزيع ليه مجموعة
 *   ⇒ `grouped: false` والشاشة تفضل تعرض التصنيفات زي الأول.
 * - المبالغ أعداد صحيحة بالهللة، والنسبة بالعُشر من المئة (قاعدة 1).
 */

export type GroupSliceKey = CategoryGroupKey | 'ungrouped' | 'uncategorized'

export interface MainSlice {
  categoryId: Id
  amountMinor: Halalas
  count: number
  shareTenthPercent: number
}

export interface GroupSlice {
  key: GroupSliceKey
  amountMinor: Halalas
  count: number
  shareTenthPercent: number
  /** التصنيفات الأساسية جوه المجموعة، الأكبر الأول. فاضية لصف «بلا تصنيف». */
  mains: MainSlice[]
}

export interface GroupDistribution {
  grouped: boolean
  totalMinor: Halalas
  groups: GroupSlice[]
}

const ORDER = new Map<string, number>(CATEGORY_GROUPS.map((g, i) => [g.key, i]))
const rank = (key: GroupSliceKey) => ORDER.get(key) ?? (key === 'ungrouped' ? 100 : 101)

export function groupDistribution(slices: readonly CategorySlice[], categories: readonly Category[]): GroupDistribution {
  const byId = new Map(categories.map((c) => [c.id, c]))
  const mainOf = (id: Id): Category | undefined => {
    let current = byId.get(id)
    const seen = new Set<Id>()
    while (current?.parentId && byId.has(current.parentId) && !seen.has(current.id)) {
      seen.add(current.id)
      current = byId.get(current.parentId)
    }
    return current
  }

  let totalMinor: Halalas = 0
  let grouped = false
  const groups = new Map<GroupSliceKey, { amount: Halalas; count: number; mains: Map<Id, { amount: Halalas; count: number }> }>()

  for (const slice of slices) {
    totalMinor = addMoney(totalMinor, slice.amountMinor)
    const main = slice.categoryId ? mainOf(slice.categoryId) : undefined
    const key: GroupSliceKey = !main ? 'uncategorized' : isCategoryGroupKey(main.groupKey) ? main.groupKey : 'ungrouped'
    if (key !== 'uncategorized' && key !== 'ungrouped') grouped = true

    const group = groups.get(key) ?? { amount: 0, count: 0, mains: new Map() }
    group.amount = addMoney(group.amount, slice.amountMinor)
    group.count += slice.count
    if (main) {
      const entry = group.mains.get(main.id) ?? { amount: 0, count: 0 }
      group.mains.set(main.id, { amount: addMoney(entry.amount, slice.amountMinor), count: entry.count + slice.count })
    }
    groups.set(key, group)
  }

  const share = (amount: Halalas) => (totalMinor === 0 ? 0 : rateOfMoney(amount, 1000, totalMinor))
  const result: GroupSlice[] = [...groups.entries()]
    .map(([key, g]) => ({
      key,
      amountMinor: g.amount,
      count: g.count,
      shareTenthPercent: share(g.amount),
      mains: [...g.mains.entries()]
        .map(([categoryId, m]) => ({ categoryId, amountMinor: m.amount, count: m.count, shareTenthPercent: share(m.amount) }))
        .sort((a, b) => b.amountMinor - a.amountMinor),
    }))
    .sort((a, b) => b.amountMinor - a.amountMinor || rank(a.key) - rank(b.key))

  return { grouped, totalMinor, groups: result }
}
