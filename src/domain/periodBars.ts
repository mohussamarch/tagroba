import { rateOfMoney, type Halalas } from './money'

/**
 * ارتفاع كل عمود في رسم «آخر ست فترات» بالعُشر في المية من أكبر فترة معروفة — OVERRIDES §31.
 * للرسم بس: بالهللة وقسمة صحيحة (قاعدة 1). `null` = الفترة غير متاحة، وتفضل `null` (قاعدة 10).
 */
export function relativeTenths(values: readonly (Halalas | null)[]): (number | null)[] {
  const max = values.reduce<number>((highest, value) => (value !== null && value > highest ? value : highest), 0)
  return values.map((value) => {
    if (value === null) return null
    if (max <= 0 || value <= 0) return 0
    return rateOfMoney(value, 1000, max)
  })
}
