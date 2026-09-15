import { describe, it, expect } from 'vitest'
import { relativeTenths } from '../../src/domain/periodBars'
import { buildCategoryLines } from '../../src/domain/budget'
import { parseMoney } from '../../src/domain/money'

/** رسومات الشكل الجديد — OVERRIDES §31. */
describe('أعمدة آخر ست فترات', () => {
  it('أكبر فترة = 100%، والباقي نسبة منها بالعُشر، وغير المتاح يفضل غير متاح', () => {
    expect(relativeTenths([parseMoney('2000.00'), parseMoney('1000.00'), null, 0])).toEqual([1000, 500, null, 0])
  })
  it('كل الفترات صفر أو غير متاحة ⇒ مفيش قسمة على صفر', () => {
    expect(relativeTenths([0, null])).toEqual([0, null])
    expect(relativeTenths([])).toEqual([])
  })
})

describe('نصيب التصنيف من مصروف التصنيفات في الميزانية', () => {
  it('النسبة من مجموع التصنيفات بالعُشر، ومجموعها بيقرب من 100%', () => {
    const lines = buildCategoryLines(
      new Map([['a', parseMoney('75.00')], ['b', parseMoney('25.00')]]),
      new Map(), new Map(), new Map(),
    )
    expect(lines.map((l) => [l.categoryId, l.shareTenthPercent])).toEqual([['a', 750], ['b', 250]])
  })
  it('تصنيف ليه سقف ومفيش مصروف خالص ⇒ 0% مش قسمة على صفر', () => {
    const lines = buildCategoryLines(new Map(), new Map([['a', { limitMinor: parseMoney('100.00'), thresholdPercent: null }]]), new Map(), new Map())
    expect(lines[0].shareTenthPercent).toBe(0)
  })
})
