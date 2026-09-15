import { describe, it, expect } from 'vitest'
import { groupDistribution } from '../../src/domain/groupDistribution'
import { parseMoney } from '../../src/domain/money'
import type { CategorySlice } from '../../src/domain/analytics'
import type { Category } from '../../src/domain/entities/types'

/** التوزيع «من برا» بالمجموعات — OVERRIDES §28.1. */

const cat = (id: string, extra: Partial<Category> = {}): Category => ({
  id, parentId: null, name: id, iconKey: 'tag', lightColor: '#111111', darkColor: '#EEEEEE', active: true, order: 0, ...extra,
})
const slice = (categoryId: string | null, amount: string, count = 1): CategorySlice => ({
  categoryId, amountMinor: parseMoney(amount), count, shareTenthPercent: 0,
})

const categories = [
  cat('food', { groupKey: 'food' }),
  cat('coffee', { parentId: 'food' }),
  cat('grocery', { groupKey: 'food' }),
  cat('car', { groupKey: 'transport' }),
  cat('fuel', { parentId: 'car' }),
  cat('old'), // حساب قديم من غير مجموعة
]

describe('التوزيع بالمجموعات', () => {
  it('الفرعي بيتجمع في أساسيه ومجموعته، والمجموع = الإجمالي بالظبط', () => {
    const result = groupDistribution([
      slice('coffee', '32.00', 2), slice('food', '18.00'), slice('grocery', '150.50', 3), slice('fuel', '120.00'),
    ], categories)
    expect(result.grouped).toBe(true)
    expect(result.totalMinor).toBe(parseMoney('320.50'))
    expect(result.groups.map((g) => [g.key, g.amountMinor, g.count])).toEqual([
      ['food', parseMoney('200.50'), 6],
      ['transport', parseMoney('120.00'), 1],
    ])
    expect(result.groups[0].mains.map((m) => [m.categoryId, m.amountMinor, m.count])).toEqual([
      ['grocery', parseMoney('150.50'), 3],
      ['food', parseMoney('50.00'), 3],
    ])
    const sum = result.groups.reduce((total, g) => total + g.amountMinor, 0)
    expect(sum).toBe(result.totalMinor)
  })

  it('النسبة بالعُشر من المئة وعدد صحيح', () => {
    const result = groupDistribution([slice('coffee', '75.00'), slice('fuel', '25.00')], categories)
    expect(result.groups.map((g) => g.shareTenthPercent)).toEqual([750, 250])
    expect(result.groups[0].mains[0].shareTenthPercent).toBe(750)
    for (const g of result.groups) expect(Number.isInteger(g.shareTenthPercent)).toBe(true)
  })

  it('بلا تصنيف أو تصنيف اتمسح ⇒ صف «بلا تصنيف» من غير تصنيفات جواه', () => {
    const result = groupDistribution([slice(null, '10.00'), slice('gone', '5.00'), slice('coffee', '1.00')], categories)
    const none = result.groups.find((g) => g.key === 'uncategorized')!
    expect(none).toMatchObject({ amountMinor: parseMoney('15.00'), count: 2, mains: [] })
  })

  it('حساب قديم من غير مجموعات ⇒ grouped: false والتصنيفات تتعرض زي الأول', () => {
    const result = groupDistribution([slice('old', '40.00'), slice(null, '10.00')], categories)
    expect(result.grouped).toBe(false)
    expect(result.groups.map((g) => g.key)).toEqual(['ungrouped', 'uncategorized'])
  })

  it('مفيش مصروف ⇒ مفيش مجموعات والنسبة ما بتقسمش على صفر', () => {
    expect(groupDistribution([], categories)).toEqual({ grouped: false, totalMinor: 0, groups: [] })
  })

  it('المبالغ المتساوية بتترتب بترتيب المجموعات الثابت', () => {
    const result = groupDistribution([slice('fuel', '10.00'), slice('coffee', '10.00')], categories)
    expect(result.groups.map((g) => g.key)).toEqual(['food', 'transport'])
  })
})
