/**
 * أدوات ملفات المرجع (golden files) — KOTLIN_PLAN §3.
 * كل حالة: المدخل + الناتج (`out`) أو رسالة الخطأ (`error`) من كود التطبيق الحالي نفسه.
 * اختبارات كوتلن بتقرا نفس الملفات ولازم تطلع نفس النتيجة بالحرف والهللة.
 * ⚠️ المستودع عام: بيانات وهمية وfixtures بس — ممنوع أي رقم أو اسم من حساب المالك.
 */

export type GoldenCase = { in: unknown; out?: unknown; error?: string }

/** يشغّل الدالة ويسجّل ناتجها أو رسالة خطأها. `-0` بيتسجل `0` (JSON أصلًا). */
export function record(input: unknown, run: () => unknown): GoldenCase {
  try {
    const out = run()
    return { in: input, out: out === undefined ? null : out }
  } catch (cause) {
    return { in: input, error: cause instanceof Error ? cause.message : String(cause) }
  }
}

/** زي `record` بس لحالات الاستخدام (async). */
export async function recordAsync(input: unknown, run: () => Promise<unknown>): Promise<GoldenCase> {
  try {
    const out = await run()
    return { in: input, out: out === undefined ? null : out }
  } catch (cause) {
    return { in: input, error: cause instanceof Error ? cause.message : String(cause) }
  }
}

/** مولّد أرقام شبه عشوائية ثابت البذرة (mulberry32) — نفس الحالات في كل مرة. */
export function seeded(seed: number) {
  let a = seed >>> 0
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
  /** عدد صحيح في [min, max] */
  const int = (min: number, max: number) => min + Math.floor(next() * (max - min + 1))
  return { next, int, pick: <T>(list: readonly T[]) => list[int(0, list.length - 1)]! }
}
