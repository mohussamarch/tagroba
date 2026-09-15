import { describe, it, expect } from 'vitest'
import { planCategorySave } from '../../src/domain/categoryEdit'
import { categoryPlaceChoices, manageCategoryView } from '../../src/domain/categoryManageView'
import { childColors, firstFreeSwatch, hexToHsl, swatchColors, swatchKeyOf } from '../../src/domain/categoryPalette'
import { hslToHex } from '../../src/domain/categoryColors'
import { makeManageCategories } from '../../src/application/useCases/manageCategories'
import type { Category } from '../../src/domain/entities/types'

/** شاشة التصنيفات: مكان التصنيف ولونه ورمزه — OVERRIDES §33.1. */

const blue = swatchColors('blue')!
const red = swatchColors('red')!
const cat = (id: string, order: number, extra: Partial<Category> = {}): Category => ({
  id, parentId: null, name: id, iconKey: 'tag', ...blue, active: true, order, ...extra,
})
const food = cat('food', 1, { groupKey: 'food' })
const cafe = cat('cafe', 2, { parentId: 'food', ...childColors(blue, 0) })
const bakery = cat('bakery', 3, { parentId: 'food', ...childColors(blue, 1) })
const car = cat('car', 4, { groupKey: 'transport', ...red, requires: 'hasCar', noCarName: 'المواصلات' })
const all = [food, cafe, bakery, car]
const newId = () => 'new'

describe('حفظ تصنيف', () => {
  it('الفرعي الجديد: من غير مجموعة، ولونه درجة من لون أبوه بعد إخواته، وآخر الترتيب', () => {
    const { item, recolored } = planCategorySave(all, { name: 'عصاير', active: true, parentId: 'food', iconKey: 'coffee' }, () => 'juice')
    expect(item).toMatchObject({ id: 'juice', parentId: 'food', iconKey: 'coffee', order: 5, groupKey: undefined, ...childColors(blue, 2) })
    expect(childColors(blue, 2)).not.toEqual(childColors(blue, 1))
    expect(recolored).toEqual([])
  })

  it('مستوى واحد بس: مفيش فرعي تحت فرعي، والتصنيف اللي تحته فرعيات ما يتنقلش', () => {
    expect(() => planCategorySave(all, { name: 'x', active: true, parentId: 'cafe' }, newId)).toThrow('مش فرعي')
    expect(() => planCategorySave(all, { id: 'food', name: 'food', active: true, parentId: 'car' }, newId)).toThrow('تحته فرعيات')
    expect(() => planCategorySave(all, { id: 'car', name: 'car', active: true, parentId: 'car' }, newId)).toThrow('تحت نفسه')
    expect(() => planCategorySave(all, { name: 'x', active: true, parentId: 'missing' }, newId)).toThrow('مش موجود')
    expect(() => planCategorySave(all, { id: 'missing', name: 'x', active: true }, newId)).toThrow('مش موجود')
  })

  it('تغيير لون الأساسي بيغيّر درجات فرعياته بترتيبهم، واللي الشاشة ما بتعدلهوش بيفضل', () => {
    const { item, recolored } = planCategorySave(all, { id: 'food', name: 'الأكل', active: true, swatchKey: 'red' }, newId)
    expect(item).toMatchObject({ id: 'food', name: 'الأكل', groupKey: 'food', order: 1, ...red })
    expect(recolored.map((c) => [c.id, c.lightColor, c.darkColor])).toEqual([
      ['cafe', childColors(red, 0).lightColor, childColors(red, 0).darkColor],
      ['bakery', childColors(red, 1).lightColor, childColors(red, 1).darkColor],
    ])
    const renamed = planCategorySave(all, { id: 'car', name: 'عربيتي', active: false }, newId)
    expect(renamed.item).toMatchObject({ requires: 'hasCar', noCarName: 'المواصلات', ...red, active: false, groupKey: 'transport', iconKey: 'tag' })
    expect(renamed.recolored).toEqual([])
  })

  it('الفرعي لما يتغير اسمه لونه ثابت، ولما يتنقل تحت أساسي تاني بياخد درجة من لونه', () => {
    expect(planCategorySave(all, { id: 'bakery', name: 'مخبز', active: true }, newId).item)
      .toMatchObject({ parentId: 'food', name: 'مخبز', ...childColors(blue, 1) })
    expect(planCategorySave(all, { id: 'bakery', name: 'bakery', active: true, parentId: 'car' }, newId).item)
      .toMatchObject({ parentId: 'car', ...childColors(red, 0) })
  })

  it('الفرعي اللي بقى أساسي بيفضل في مجموعة أبوه القديم وبلونه، إلا لو اختار غيرهم', () => {
    expect(planCategorySave(all, { id: 'cafe', name: 'cafe', active: true, parentId: null }, newId).item)
      .toMatchObject({ parentId: null, groupKey: 'food', ...childColors(blue, 0) })
    expect(planCategorySave(all, { id: 'cafe', name: 'cafe', active: true, parentId: null, groupKey: 'personal', swatchKey: 'red' }, newId).item)
      .toMatchObject({ parentId: null, groupKey: 'personal', ...red })
    expect(planCategorySave(all, { id: 'food', name: 'food', active: true, groupKey: null }, newId).item.groupKey).toBeUndefined()
  })

  it('اللون والرمز والمجموعة لازم يكونوا من القوايم، والاسم المكرر مرفوض', () => {
    expect(() => planCategorySave(all, { name: 'x', active: true, swatchKey: 'nope' }, newId)).toThrow('لون')
    expect(() => planCategorySave(all, { name: 'x', active: true, iconKey: 'Bad Icon' }, newId)).toThrow('رمز')
    // @ts-expect-error — مجموعة مش معروفة جاية من بيانات قديمة
    expect(() => planCategorySave(all, { name: 'x', active: true, groupKey: 'nope' }, newId)).toThrow('المجموعة')
    expect(() => planCategorySave(all, { name: 'cafe', active: true }, newId)).toThrow('نفس الاسم')
    expect(() => planCategorySave(all, { name: '   ', active: true }, newId)).toThrow('اسم التصنيف')
  })

  it('الأساسي الجديد من غير لون بياخد أول لون مش مستخدم في الأساسيات', () => {
    expect(firstFreeSwatch(all)).toBe('orange')
    const { item } = planCategorySave(all, { name: 'هدايا', active: true, groupKey: 'personal' }, () => 'gifts')
    expect(swatchKeyOf(item.lightColor)).toBe('orange')
    expect(item).toMatchObject({ id: 'gifts', parentId: null, groupKey: 'personal', iconKey: 'tag' })
  })

  it('تحويل اللون لدرجة ورجوعه بيطلع نفس اللون تقريبًا، واللون المش مفهوم بيتنسخ زي ما هو', () => {
    const hsl = hexToHsl(hslToHex(210, 70, 42))!
    expect(Math.abs(hsl.h - 210)).toBeLessThan(1)
    expect(Math.abs(hsl.s - 70)).toBeLessThan(1)
    expect(Math.abs(hsl.l - 42)).toBeLessThan(1)
    expect(hexToHsl('blue')).toBeNull()
    expect(childColors({ lightColor: 'green', darkColor: 'lime' }, 3)).toEqual({ lightColor: 'green', darkColor: 'lime' })
  })
})

describe('شاشة إدارة التصنيفات', () => {
  it('الأساسي المخفي ليه قسم لوحده، واللي من غير مجموعة الأول، والفرعي المخفي بيفضل تحت أبوه', () => {
    const view = manageCategoryView([
      ...all,
      cat('old', 0),
      cat('lost', 9, { parentId: 'gone' }),
      cat('hiddenSub', 8, { parentId: 'car', active: false }),
      cat('merged', 7, { active: false }),
      cat('mergedKid', 10, { parentId: 'merged' }),
    ])
    expect(view.groups.map((g) => g.key)).toEqual(['ungrouped', 'food', 'transport'])
    expect(view.groups[0]!.mains.map((m) => m.category.id)).toEqual(['old', 'lost'])
    expect(view.groups[1]!.mains[0]!.subs.map((c) => c.id)).toEqual(['cafe', 'bakery'])
    expect(view.groups[2]!).toMatchObject({ label: 'التنقل', iconKey: 'route' })
    expect(view.groups[2]!.mains[0]!.subs.map((c) => c.id)).toEqual(['hiddenSub'])
    expect(view.hidden.map((m) => [m.category.id, m.subs.map((c) => c.id)])).toEqual([['merged', ['mergedKid']]])
  })

  it('حساب المالك: القديم المدموج مخفي ومن غير مجموعة ⇒ الشاشة بتبدأ بالمجموعات', () => {
    const view = manageCategoryView([...all, cat('merged', 0, { active: false })])
    expect(view.groups.map((g) => g.key)).toEqual(['food', 'transport'])
    expect(view.hidden.map((m) => m.category.id)).toEqual(['merged'])
  })

  it('اختيارات المكان: «بلا مجموعة» لو محتاجينها بس، والمخفية آخر القايمة، واللي تحته فرعيات ما يبقاش فرعي', () => {
    const fresh = categoryPlaceChoices(all, 'car')
    expect(fresh.groups.map((g) => g.key)).toEqual(['food', 'home', 'transport', 'personal', 'saving', 'movement'])
    expect(fresh.parentGroups).toEqual([{ key: 'food', label: 'الأكل والشرب', options: [{ id: 'food', label: 'food' }] }])
    expect(fresh.hasSubs).toBe(false)

    const withHidden = [...all, cat('merged', 0, { active: false })]
    expect(categoryPlaceChoices(withHidden, 'car').groups[0]!.key).toBe('food')
    expect(categoryPlaceChoices(withHidden, 'merged').groups[0]).toEqual({ key: null, label: 'بلا مجموعة' })
    const parents = categoryPlaceChoices(withHidden, 'car').parentGroups
    expect(parents[parents.length - 1]).toEqual({ key: 'hidden', label: 'المخفية', options: [{ id: 'merged', label: 'merged' }] })

    const legacy = [...all, cat('old', 0)]
    expect(categoryPlaceChoices(legacy, 'car').groups[0]).toEqual({ key: null, label: 'بلا مجموعة' })
    const withSubs = categoryPlaceChoices(legacy, 'food')
    expect(withSubs.hasSubs).toBe(true)
    expect(withSubs.parentGroups).toEqual([])
  })
})

describe('حفظ التصنيف في المستودع', () => {
  it('الفرعيات بتتحفظ قبل أبوها، عشان الحفظ تاني يكمّل لو وقف في النص', async () => {
    const saved: string[] = []
    const use = makeManageCategories({
      categories: { listAll: async () => all, save: async (c) => { saved.push(c.id) } },
      ids: { next: () => 'unused' },
    })
    await use.save({ id: 'food', name: 'food', active: true, swatchKey: 'red' })
    expect(saved).toEqual(['cafe', 'bakery', 'food'])
  })
})
