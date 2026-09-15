/**
 * ألوان التصنيفات — OVERRIDES §28: لكل أساسي لون مختلف، والفرعي «نفس لونه الرئيسي
 * ولكن بدرجات أخف أو أغمق حاجة بسيطة». الحساب هنا ألوان مش فلوس، فالكسور مسموحة.
 *
 * - الوضع الفاتح: الإضاءة بين 22 و52، و**الأصفر والأخضر الفاتح (35–110) سقفهم 40**
 *   عشان الرمز يفضل مقروء على الأبيض.
 * - الوضع الغامق: نفس الدرجة أفتح (+24، بين 58 و74).
 */

export const SUB_LIGHTNESS_OFFSETS: readonly number[] = [7, -6, 12, -10, 16, -3, 10]

export function hslToHex(h: number, s: number, l: number): string {
  const sat = s / 100
  const light = l / 100
  const k = (n: number) => (n + h / 30) % 12
  const a = sat * Math.min(light, 1 - light)
  const channel = (n: number) => light - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))
  return '#' + [0, 8, 4].map((n) => Math.round(channel(n) * 255).toString(16).padStart(2, '0')).join('').toUpperCase()
}

const clamp = (value: number, low: number, high: number) => Math.max(low, Math.min(high, value))
const lightCap = (h: number) => (h >= 35 && h <= 110 ? 40 : 52)

export function categoryColors(h: number, s: number, l: number): { lightColor: string; darkColor: string } {
  return {
    lightColor: hslToHex(h, s, clamp(l, 22, lightCap(h))),
    darkColor: hslToHex(h, Math.min(s + 5, 90), clamp(l + 24, 58, 74)),
  }
}

/** لون الفرعي رقم `index` تحت أساسي درجته (h, s, l). */
export function subCategoryColors(h: number, s: number, l: number, index: number) {
  const offset = SUB_LIGHTNESS_OFFSETS[index % SUB_LIGHTNESS_OFFSETS.length] ?? 0
  return categoryColors(h, s, l + offset)
}
