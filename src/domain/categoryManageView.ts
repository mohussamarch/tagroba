import { CATEGORY_GROUPS, type CategoryGroupKey } from './categoryTree'
import type { Category, Id } from './entities/types'

/**
 * شاشة إدارة التصنيفات — OVERRIDES §33.1. عكس `groupCategoryOptions`: **المخفي بيظهر كمان**
 * (عشان يترجع). نفس الترتيب: «التصنيفات» اللي من غير مجموعة الأول، بعدين المجموعات بترتيبها،
 * وكل فرعي تحت أبوه. فرعي أبوه مش موجود بيتعامل كأساسي.
 */

export interface ManagedMain {
  category: Category
  subs: Category[]
}

export interface ManagedGroup {
  key: CategoryGroupKey | 'ungrouped'
  label: string
  iconKey: string
  mains: ManagedMain[]
}

const byOrder = (a: Category, b: Category) => a.order - b.order || a.name.localeCompare(b.name, 'ar')
const KNOWN = new Set<string>(CATEGORY_GROUPS.map((g) => g.key))

export function manageCategoryView(categories: readonly Category[]): ManagedGroup[] {
  const ids = new Set(categories.map((c) => c.id))
  const mains = categories.filter((c) => !c.parentId || !ids.has(c.parentId)).sort(byOrder)
  const toMain = (category: Category): ManagedMain => ({
    category,
    subs: categories.filter((c) => c.parentId === category.id).sort(byOrder),
  })
  const groups: ManagedGroup[] = []
  const ungrouped = mains.filter((m) => !m.groupKey || !KNOWN.has(m.groupKey))
  if (ungrouped.length) {
    groups.push({ key: 'ungrouped', label: 'التصنيفات', iconKey: 'tag', mains: ungrouped.map(toMain) })
  }
  for (const group of CATEGORY_GROUPS) {
    const list = mains.filter((m) => m.groupKey === group.key)
    if (list.length) groups.push({ key: group.key, label: group.name, iconKey: group.iconKey, mains: list.map(toMain) })
  }
  return groups
}

export interface CategoryPlaceChoices {
  /** مجموعات الأساسي. «بلا مجموعة» بتظهر بس لو الحساب فيه أساسي من غير مجموعة (حساب قديم). */
  groups: { key: CategoryGroupKey | null; label: string }[]
  /** الأساسيات اللي ينفع يبقى فرعي تحتها، بمجموعاتها. فاضية لو هو نفسه تحته فرعيات. */
  parentGroups: { key: string; label: string; options: { id: Id; label: string }[] }[]
  /** التصنيف ده تحته فرعيات، فمينفعش يبقى فرعي. */
  hasSubs: boolean
}

export function categoryPlaceChoices(categories: readonly Category[], editingId: Id | null): CategoryPlaceChoices {
  const view = manageCategoryView(categories)
  const hasSubs = editingId !== null && categories.some((c) => c.parentId === editingId)
  const groups: CategoryPlaceChoices['groups'] = [
    ...(view.some((g) => g.key === 'ungrouped') ? [{ key: null, label: 'بلا مجموعة' }] : []),
    ...CATEGORY_GROUPS.map((g) => ({ key: g.key, label: g.name })),
  ]
  const parentGroups = hasSubs
    ? []
    : view
      .map((g) => ({
        key: g.key,
        label: g.label,
        options: g.mains
          .filter((m) => m.category.id !== editingId)
          .map((m) => ({ id: m.category.id, label: m.category.active ? m.category.name : `${m.category.name} (مخفي)` })),
      }))
      .filter((g) => g.options.length > 0)
  return { groups, parentGroups, hasSubs }
}
