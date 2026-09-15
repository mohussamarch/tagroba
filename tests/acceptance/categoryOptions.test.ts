import { describe, it, expect } from 'vitest'
import { groupCategoryOptions } from '../../src/domain/categoryOptions'
import type { Category } from '../../src/domain/entities/types'

/** قايمة اختيار التصنيف بالمجموعات — OVERRIDES §28.1. */

const cat = (id: string, order: number, extra: Partial<Category> = {}): Category => ({
  id, parentId: null, name: id, iconKey: 'tag', lightColor: '#111111', darkColor: '#EEEEEE', active: true, order, ...extra,
})

describe('قايمة اختيار التصنيف', () => {
  it('المجموعات بترتيبها، والفرعي تحت أبوه، والمجموعة الفاضية ما بتظهرش', () => {
    const groups = groupCategoryOptions([
      cat('car', 5, { groupKey: 'transport' }),
      cat('food', 2, { groupKey: 'food' }),
      cat('coffee', 4, { parentId: 'food' }),
      cat('restaurants', 3, { parentId: 'food' }),
    ])
    expect(groups.map((g) => g.key)).toEqual(['food', 'transport'])
    expect(groups[0]).toMatchObject({ label: 'الأكل والشرب' })
    expect(groups[0].options).toEqual([
      { id: 'food', label: 'food', depth: 0 },
      { id: 'restaurants', label: 'restaurants', depth: 1 },
      { id: 'coffee', label: 'coffee', depth: 1 },
    ])
  })

  it('الحساب القديم من غير مجموعات بيفضل قايمة واحدة زي الأول', () => {
    const groups = groupCategoryOptions([cat('b', 2), cat('a', 1), cat('hidden', 3, { active: false })])
    expect(groups).toEqual([{ key: 'ungrouped', label: 'التصنيفات', options: [
      { id: 'a', label: 'a', depth: 0 }, { id: 'b', label: 'b', depth: 0 },
    ] }])
  })

  it('الأساسي المخفي بيخفي فروعه، والتصنيف الحالي بيفضل ظاهر باسم أبوه', () => {
    const all = [
      cat('home', 1, { groupKey: 'home', active: false }),
      cat('rent', 2, { parentId: 'home' }),
      cat('water', 3, { parentId: 'home' }),
      cat('fuel', 4, { groupKey: 'transport' }),
      cat('parking', 5, { parentId: 'fuel', active: false }),
    ]
    expect(groupCategoryOptions(all).map((g) => g.options.map((o) => o.id))).toEqual([['fuel']])
    expect(groupCategoryOptions(all, { keepId: 'rent' })[0]).toEqual(
      { key: 'home', label: 'البيت والالتزامات', options: [{ id: 'rent', label: 'home › rent', depth: 0 }] })
    expect(groupCategoryOptions(all, { keepId: 'parking' })[0].options).toEqual([
      { id: 'fuel', label: 'fuel', depth: 0 }, { id: 'parking', label: 'parking', depth: 1 },
    ])
  })

  it('فرعي أبوه مش موجود بيتعامل كأساسي', () => {
    const groups = groupCategoryOptions([cat('orphan', 1, { parentId: 'gone' })])
    expect(groups[0].options).toEqual([{ id: 'orphan', label: 'orphan', depth: 0 }])
  })
})
