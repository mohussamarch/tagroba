import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

/**
 * أرقام المالك المستهدفة (OVERRIDES §120-1): مش مكتوبة في الكود — المستودع عام.
 * بتتقري من `PRIVATE-FIGURES.md` (مستبعد من Git) بالدوَران لفوق من المستودع (زي worktree جوه `.claude/`).
 * سطور `مفتاح=قيمة` بس. مش موجود ⇒ `null`، والاختبار الحقيقي يفشل برسالة صريحة.
 */
export function readPrivateFigures(): Record<string, string> | null {
  let dir = resolve(__dirname, '..')
  for (;;) {
    const file = resolve(dir, 'PRIVATE-FIGURES.md')
    if (existsSync(file)) {
      const out: Record<string, string> = {}
      for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
        const m = /^([A-Za-z][A-Za-z0-9]*)=(.+)$/.exec(line.trim())
        if (m) out[m[1]] = m[2].trim()
      }
      return out
    }
    const up = dirname(dir)
    if (up === dir) return null
    dir = up
  }
}

/** قيمة مفتاح لازم يكون موجود — وإلا خطأ واضح. */
export function figure(figures: Record<string, string> | null, key: string): string {
  const v = figures?.[key]
  if (v === undefined) throw new Error(`«${key}» مش موجود — PRIVATE-FIGURES.md ناقص أو مش موجود (OVERRIDES §120-1)`)
  return v
}
