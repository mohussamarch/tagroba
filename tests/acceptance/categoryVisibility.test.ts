import { describe, it, expect } from 'vitest'
import tree from '../../src/infrastructure/import/categoryTree.json'
import { buildCategoryTree } from '../../src/infrastructure/import/categoryTreeLoader'
import { factsFromProfile, presentCategories, UNKNOWN_FACTS } from '../../src/domain/categoryVisibility'
import { groupCategoryOptions } from '../../src/domain/categoryOptions'
import { emptyProfile } from '../../src/domain/userProfile'

/** التصنيفات حسب معلومات الشخص — OVERRIDES §28.1. */

const { categories } = buildCategoryTree(tree)
const idOf = (name: string) => categories.find((c) => c.name === name)!.id
const pickerIds = (list: typeof categories, keepId?: string) =>
  groupCategoryOptions(list, keepId ? { keepId } : {}).flatMap((g) => g.options.map((o) => o.id))

describe('إظهار التصنيفات حسب ملف الشخص', () => {
  it('مجهول = مخفي، و«السيارة» اسمها «المواصلات» وفيها التاكسي والمواصلات العامة بس', () => {
    const shown = presentCategories(categories, UNKNOWN_FACTS)
    const car = shown.find((c) => c.id === idOf('السيارة'))!
    expect(car).toMatchObject({ name: 'المواصلات', iconKey: 'bus-front', active: true })
    const ids = pickerIds(shown)
    expect(ids).toContain(idOf('تاكسي وتطبيقات'))
    expect(ids).toContain(idOf('مواصلات عامة'))
    for (const hidden of ['وقود', 'مواقف وسايس', 'تأمين السيارة', 'الأسرة والأطفال', 'إيجار', 'عمالة منزلية', 'مصاريف الشغل']) {
      expect(ids).not.toContain(idOf(hidden))
    }
    expect(ids).toContain(idOf('كهرباء')) // غير المشروط ظاهر عادي
  })

  it('«أيوه» بتظهر المشروط، وصاحب العربية بيشوف «السيارة» باسمها', () => {
    const shown = presentCategories(categories, { hasCar: true, familyDependents: true, renter: true, domesticWorker: true, business: true })
    expect(shown.find((c) => c.id === idOf('السيارة'))).toMatchObject({ name: 'السيارة', iconKey: 'car' })
    expect(shown.every((c) => c.active)).toBe(true)
  })

  it('العرض ما بيغيّرش التصنيفات الأصلية، والتصنيف الحالي المخفي بيفضل في قايمة عمليته', () => {
    const before = JSON.stringify(categories)
    const shown = presentCategories(categories, UNKNOWN_FACTS)
    expect(JSON.stringify(categories)).toBe(before)
    expect(pickerIds(shown, idOf('وقود'))).toContain(idOf('وقود'))
  })

  it('معلومات الملف: «لأ» على بتعول حد بتلغي الأسرة، والأهل لوحدهم مش أسرة', () => {
    expect(factsFromProfile(null)).toEqual(UNKNOWN_FACTS)
    const base = emptyProfile()
    expect(factsFromProfile({ ...base, supportsDependents: false, dependentKinds: ['children'] }).familyDependents).toBe(false)
    expect(factsFromProfile({ ...base, supportsDependents: true, dependentKinds: ['parents'] }).familyDependents).toBe(false)
    expect(factsFromProfile({ ...base, supportsDependents: true, dependentKinds: ['spouse'] }).familyDependents).toBe(true)
    expect(factsFromProfile({ ...base, supportsDependents: true, dependentKinds: null }).familyDependents).toBeNull()
    expect(factsFromProfile({ ...base, hasCar: false, renter: true })).toMatchObject({ hasCar: false, renter: true, business: null })
  })
})
