import type { Halalas } from '../money'
import type { Id, IsoDate } from './types'

/**
 * كيانات الميزانية — اتفصلت من `types.ts` عشان حد الـ300 سطر (قاعدة 7)؛
 * `types.ts` بيعيد تصديرها فمفيش استيراد اتغير.
 */

/**
 * ميزانية فترة — spec/03: «حدود صريحة».
 * لا تُنشأ تلقائيًا من متوسط؛ المستخدم يحددها (spec/01).
 */
export interface Budget {
  /** مفتاح الفترة نفسه: "2026-09". فترة واحدة = ميزانية واحدة. */
  id: Id
  periodKey: string
  periodStart: IsoDate
  periodEnd: IsoDate
  /** السقف الإجمالي، أو null لو المستخدم حدد سقوف تصنيفات فقط. */
  totalLimitMinor: Halalas | null
  /** عتبة التنبيه بالمئة (80 = ٨٠٪)، أو null فلا تنبيه (spec/06). */
  thresholdPercent: number | null
  createdAt: string
  updatedAt: string
}

export interface CategoryBudget {
  id: Id
  budgetId: Id
  categoryId: Id
  limitMinor: Halalas
  notifyEnabled: boolean
  thresholdPercent: number | null
}
