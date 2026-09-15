import { CATEGORY_GROUPS } from './categoryTree'
import type { Category, Id } from './entities/types'

export interface CategoryOption {
  id: Id
  label: string
  /** 0 = أساسي، 1 = فرعي تحت الأساسي اللي قبله. */
  depth: 0 | 1
}

export interface CategoryOptionGroup {
  key: string
  label: string
  options: CategoryOption[]
}

/** التصنيفات اللي مالهاش مجموعة (الحسابات القديمة قبل OVERRIDES §28.1). */
const UNGROUPED = { key: 'ungrouped', label: 'التصنيفات' }

const byOrder = (a: Category, b: Category) => a.order - b.order || a.name.localeCompare(b.name, 'ar')

/**
 * قايمة اختيار التصنيف مقسّمة بالمجموعات، وكل فرعي تحت أبوه (OVERRIDES §28.1).
 *
 * - بيظهر الظاهر بس (`active`)، و**الأساسي المخفي بيخفي فروعه**.
 * - `keepId`: تصنيف لازم يظهر حتى لو مخفي (التصنيف الحالي للعملية)، عشان القايمة ما تمسحهوش.
 *   لو هو فرعي وأبوه مخفي بيظهر لوحده باسم «الأساسي › الفرعي».
 * - فرعي أبوه مش موجود بيتعامل كأساسي. مجموعة فاضية ما بتظهرش.
 */
export function groupCategoryOptions(
  categories: readonly Category[],
  options: { keepId?: Id | null } = {},
): CategoryOptionGroup[] {
  const ids = new Set(categories.map((c) => c.id))
  const children = new Map<Id, Category[]>()
  const mains: Category[] = []
  for (const category of categories) {
    if (category.parentId && ids.has(category.parentId)) {
      const list = children.get(category.parentId) ?? []
      list.push(category)
      children.set(category.parentId, list)
    } else {
      mains.push(category)
    }
  }
  const shown = (category: Category) => category.active || category.id === options.keepId

  const optionsFor = (list: Category[]): CategoryOption[] => {
    const out: CategoryOption[] = []
    for (const main of [...list].sort(byOrder)) {
      const kids = [...(children.get(main.id) ?? [])].sort(byOrder)
      if (shown(main)) {
        out.push({ id: main.id, label: main.name, depth: 0 })
        for (const kid of kids) if (shown(kid)) out.push({ id: kid.id, label: kid.name, depth: 1 })
      } else {
        const kept = kids.find((kid) => kid.id === options.keepId)
        if (kept) out.push({ id: kept.id, label: `${main.name} › ${kept.name}`, depth: 0 })
      }
    }
    return out
  }

  const groups: CategoryOptionGroup[] = []
  const known = new Set<string>(CATEGORY_GROUPS.map((g) => g.key))
  const ungrouped = optionsFor(mains.filter((m) => !m.groupKey || !known.has(m.groupKey)))
  if (ungrouped.length) groups.push({ ...UNGROUPED, options: ungrouped })
  for (const group of CATEGORY_GROUPS) {
    const list = optionsFor(mains.filter((m) => m.groupKey === group.key))
    if (list.length) groups.push({ key: group.key, label: group.name, options: list })
  }
  return groups
}
