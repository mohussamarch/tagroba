import { categoryColors, subCategoryColors } from './categoryColors'
import type { Category } from './entities/types'

/**
 * الألوان اللي بيختار منها التصنيف الأساسي في شاشة التصنيفات — OVERRIDES §33.1.
 * الدرجات مأخوذة من ألوان الأساسيات في `categoryTree.json`، فالتصنيف الجديد بيبان من نفس
 * عيلة ألوان التطبيق. **الفرعي ما بيختارش لون**: لونه درجة من لون أبوه (OVERRIDES §28).
 * الحساب هنا ألوان مش فلوس، فالكسور مسموحة.
 */

export interface CategorySwatch {
  key: string
  /** اسم اللون بالعربي — للقارئ الصوتي. */
  name: string
  h: number
  s: number
  l: number
}

export const CATEGORY_SWATCHES: readonly CategorySwatch[] = [
  { key: 'red', name: 'أحمر', h: 2, s: 70, l: 44 },
  { key: 'orange', name: 'برتقالي', h: 22, s: 78, l: 46 },
  { key: 'amber', name: 'كهرماني', h: 40, s: 90, l: 38 },
  { key: 'olive', name: 'زيتوني', h: 60, s: 80, l: 28 },
  { key: 'lime', name: 'ليموني', h: 85, s: 65, l: 30 },
  { key: 'green', name: 'أخضر', h: 135, s: 55, l: 34 },
  { key: 'mint', name: 'نعناعي', h: 158, s: 65, l: 30 },
  { key: 'teal', name: 'فيروزي', h: 174, s: 70, l: 29 },
  { key: 'cyan', name: 'سماوي', h: 190, s: 80, l: 33 },
  { key: 'blue', name: 'أزرق', h: 210, s: 70, l: 42 },
  { key: 'indigo', name: 'نيلي', h: 250, s: 55, l: 50 },
  { key: 'purple', name: 'بنفسجي', h: 268, s: 40, l: 40 },
  { key: 'lilac', name: 'ليلكي', h: 285, s: 50, l: 48 },
  { key: 'magenta', name: 'أرجواني', h: 305, s: 50, l: 42 },
  { key: 'fuchsia', name: 'فوشيا', h: 322, s: 62, l: 40 },
  { key: 'rose', name: 'وردي', h: 340, s: 65, l: 48 },
  { key: 'slate', name: 'رمادي مزرق', h: 200, s: 22, l: 32 },
  { key: 'gray', name: 'رمادي', h: 210, s: 8, l: 45 },
]

export interface CategoryColorPair {
  lightColor: string
  darkColor: string
}

export function swatchColors(key: string): CategoryColorPair | null {
  const swatch = CATEGORY_SWATCHES.find((s) => s.key === key)
  return swatch ? categoryColors(swatch.h, swatch.s, swatch.l) : null
}

/** الدرجة اللي لونها هو لون التصنيف ده بالظبط — عشان شاشة التعديل تعلّم اللون الحالي. */
export function swatchKeyOf(lightColor: string): string | null {
  const wanted = lightColor.toUpperCase()
  return CATEGORY_SWATCHES.find((s) => categoryColors(s.h, s.s, s.l).lightColor === wanted)?.key ?? null
}

/** أول درجة مش مستخدمة في أي تصنيف أساسي — اللون المبدئي للتصنيف الجديد. */
export function firstFreeSwatch(categories: readonly Category[]): string {
  const used = new Set(categories.filter((c) => c.parentId === null).map((c) => c.lightColor.toUpperCase()))
  const free = CATEGORY_SWATCHES.find((s) => !used.has(categoryColors(s.h, s.s, s.l).lightColor))
  return (free ?? CATEGORY_SWATCHES[0]!).key
}

export function hexToHsl(hex: string): { h: number; s: number; l: number } | null {
  const match = /^#?([0-9a-f]{6})$/i.exec(hex.trim())
  if (!match) return null
  const value = parseInt(match[1]!, 16)
  const r = ((value >> 16) & 255) / 255
  const g = ((value >> 8) & 255) / 255
  const b = (value & 255) / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const l = (max + min) / 2
  const d = max - min
  if (d === 0) return { h: 0, s: 0, l: l * 100 }
  const s = d / (1 - Math.abs(2 * l - 1))
  let h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4
  h *= 60
  if (h < 0) h += 360
  return { h, s: s * 100, l: l * 100 }
}

/**
 * لون الفرعي رقم `index` تحت أساسي لونه `parent`: نفس الدرجة أفتح أو أغمق شوية.
 * لون أبوه مش مكتوب بصيغة معروفة ⇒ بياخد لون أبوه زي ما هو.
 */
export function childColors(parent: CategoryColorPair, index: number): CategoryColorPair {
  const hsl = hexToHsl(parent.lightColor)
  if (!hsl) return { lightColor: parent.lightColor, darkColor: parent.darkColor }
  return subCategoryColors(hsl.h, hsl.s, hsl.l, index)
}
