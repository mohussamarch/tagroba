import { describe, it, expect } from 'vitest'
import { buildPeriod, periodForDate, periodIndex, shiftPeriodWithin } from '../../src/domain/period'

/** منتقي الشهر ما يروحش للمستقبل — OVERRIDES §31. */
describe('حد منتقي الشهر', () => {
  const latest = periodForDate('2026-09-15', 28) // فترة أغسطس (28 أغسطس – 27 سبتمبر)

  it('الرجوع للشهور اللي فاتت عادي', () => {
    expect(shiftPeriodWithin(latest, -1, latest, 28).key).toBe('2026-07')
    expect(shiftPeriodWithin(latest, -12, latest, 28).key).toBe('2025-08')
  })

  it('الشهر الجاي والسنة الجاية ما يعدّوش الفترة الحالية', () => {
    expect(shiftPeriodWithin(latest, 1, latest, 28).key).toBe(latest.key)
    expect(shiftPeriodWithin(buildPeriod(2026, 3, 28), 12, latest, 28).key).toBe(latest.key)
    expect(shiftPeriodWithin(buildPeriod(2026, 3, 28), 2, latest, 28).key).toBe('2026-05')
  })

  it('ترتيب الفترات بيعدّي السنين صح', () => {
    expect(periodIndex(buildPeriod(2026, 1, 28))).toBe(periodIndex(buildPeriod(2025, 12, 28)) + 1)
  })
})
