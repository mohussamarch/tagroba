import { CATEGORY_GROUPS, type CategoryGroupKey } from './categoryTree'
import type { Category, Id } from './entities/types'

/**
 * شاشة إدارة التصنيفات — OVERRIDES §33.1. عكس `groupCategoryOptions`: **المخفي بيظهر كمان** (عشان يترجع)،
 * بس الأساسي المخفي ليه **قسم لوحده تحت** — على حساب المالك التصنيفات القديمة اللي اتدمجت في الشجرة
 * (مخفية ومن غير مجموعة) كانت بتطلع أول الشاشة فوق اللي شغال (اتشاف على المحاكي 2026-09-16).
 * الترتيب: «التصنيفات» اللي من غير مجموعة الأول، بعدين المجموعات بترتيبها، وكل فرعي تحت أبوه
 * (الفرعي المخفي تحت أساسي ظاهر بيفضل مكانه بعلامة). فرعي أبوه مش موجود بيتعامل كأساسي.
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

export interface ManagedCategoryView {
  /** الأساسيات الظاهرة بمجموعاتها، وتحت كل واحد كل فرعياته. */
  groups: ManagedGroup[]
  /** الأساسيات المخفية بفرعياتها، بالترتيب. */
  hidden: ManagedMain[]
}

const byOrder = (a: Category, b: Category) => a.order - b.order || a.name.localeCompare(b.name, 'ar')
const KNOWN = new Set<string>(CATEGORY_GROUPS.map((g) => g.key))
const isMainIn = (ids: ReadonlySet<Id>) => (c: Category) => !c.parentId || !ids.has(c.parentId)
const ungrouped = (c: Category) => !c.groupKey || !KNOWN.has(c.groupKey)

export function manageCategoryView(categories: readonly Category[]): ManagedCategoryView {
  const ids = new Set(categories.map((c) => c.id))
  const mains = categories.filter(isMainIn(ids)).sort(byOrder)
  const toMain = (category: Category): ManagedMain => ({
    category,
    subs: categories.filter((c) => c.parentId === category.id).sort(byOrder),
  })
  const shown = mains.filter((m) => m.active)
  const groups: ManagedGroup[] = []
  const loose = shown.filter(ungrouped)
  if (loose.length) groups.push({ key: 'ungrouped', label: 'التصنيفات', iconKey: 'tag', mains: loose.map(toMain) })
  for (const group of CATEGORY_GROUPS) {
    const list = shown.filter((m) => m.groupKey === group.key)
    if (list.length) groups.push({ key: group.key, label: group.name, iconKey: group.iconKey, mains: list.map(toMain) })
  }
  return { groups, hidden: mains.filter((m) => !m.active).map(toMain) }
}

export interface CategoryPlaceChoices {
  /** مجموعات الأساسي. «بلا مجموعة» بتظهر لو فيه أساسي ظاهر من غير مجموعة، أو التصنيف اللي بيتعدل نفسه كده. */
  groups: { key: CategoryGroupKey | null; label: string }[]
  /** الأساسيات اللي ينفع يبقى فرعي تحتها، بمجموعاتها، والمخفية آخر حاجة. فاضية لو هو نفسه تحته فرعيات. */
  parentGroups: { key: string; label: string; options: { id: Id; label: string }[] }[]
  /** التصنيف ده تحته فرعيات، فمينفعش يبقى فرعي. */
  hasSubs: boolean
}

export function categoryPlaceChoices(categories: readonly Category[], editingId: Id | null): CategoryPlaceChoices {
  const view = manageCategoryView(categories)
  const ids = new Set(categories.map((c) => c.id))
  const editing = editingId === null ? undefined : categories.find((c) => c.id === editingId)
  const hasSubs = editingId !== null && categories.some((c) => c.parentId === editingId)
  const editingLoose = editing !== undefined && isMainIn(ids)(editing) && ungrouped(editing)
  const groups: CategoryPlaceChoices['groups'] = [
    ...(view.groups.some((g) => g.key === 'ungrouped') || editingLoose ? [{ key: null, label: 'بلا مجموعة' }] : []),
    ...CATEGORY_GROUPS.map((g) => ({ key: g.key, label: g.name })),
  ]
  const options = (mains: readonly ManagedMain[]) =>
    mains.filter((m) => m.category.id !== editingId).map((m) => ({ id: m.category.id, label: m.category.name }))
  const parentGroups = hasSubs
    ? []
    : [
      ...view.groups.map((g) => ({ key: g.key as string, label: g.label, options: options(g.mains) })),
      { key: 'hidden', label: 'المخفية', options: options(view.hidden) },
    ].filter((g) => g.options.length > 0)
  return { groups, parentGroups, hasSubs }
}
