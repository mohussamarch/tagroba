/**
 * قفل التطبيق بالبصمة — متى يُطلب الفتح (OVERRIDES §21، قرار المالك 2026-09-13).
 *
 * - اختياري: مقفول لحد ما المستخدم يشغّله.
 * - عند فتح التطبيق من الأول ⇒ يتقفل.
 * - بعد **خمس دقايق** في الخلفية ⇒ يتقفل. الرجوع قبلها ما يسألش.
 *
 * دالة نقية: الوقت بييجي من برّه، فمفيش `Date.now()` هنا.
 */
export const LOCK_AFTER_BACKGROUND_MS = 5 * 60_000

export interface LockInput {
  enabled: boolean
  /** وقت دخول الخلفية بالمللي ثانية؛ null = التطبيق لسه متفتح من الأول. */
  hiddenAt: number | null
  now: number
}

export function shouldLock({ enabled, hiddenAt, now }: LockInput): boolean {
  if (!enabled) return false
  if (hiddenAt === null) return true
  // ساعة الجهاز رجعت لورا (تغيير وقت يدوي) ⇒ نقفل احتياطًا بدل ما نفتح من غير سؤال
  if (now < hiddenAt) return true
  return now - hiddenAt >= LOCK_AFTER_BACKGROUND_MS
}
