import { normalizeText } from '../../domain/normalize'
import type { Category, ClassificationRule, Merchant } from '../../domain/entities/types'

/**
 * تحميل المراجع الأولية — spec/05:
 * «ملفات القواعد مرجع أولي **قابل للتحرير**، وليست سياسة لا تتغير.»
 *
 * لذلك ما يُحمَّل هنا يُكتب في المستودعات كبيانات عادية يعدّلها المستخدم،
 * ولا يُقرأ من الملف في كل تشغيل.
 *
 * وspec/05 أيضًا: «تجنب تحويل اسم تاجر غير واضح إلى تأكيد آلي» —
 * لذلك التجار بثقة «يحتاج تأكيد» **لا يُمنحون verifiedCategoryId**.
 */

export interface RawRule {
  word: string
  cat: string
}

export interface RawMerchant {
  name: string
  cat: string
  confidence: string
}

/** القيمة التي تعني «لا تصنيف مؤكد» في ملف التجار. */
const NEEDS_CONFIRMATION = 'يحتاج تأكيد'
const CONFIDENCE_VERIFIED = 'مؤكد'

export interface LoadedReferences {
  rules: ClassificationRule[]
  merchants: Merchant[]
  /** أسماء تصنيفات ظهرت في المراجع ولا تقابلها فئة معرّفة — تُبلَّغ ولا تُخترع. */
  unknownCategoryNames: string[]
  /** تجار تُركوا بلا تصنيف مؤكد عمدًا. */
  unverifiedMerchantCount: number
}

/**
 * يحوّل المراجع الخام إلى كيانات.
 *
 * ترتيب الأولوية: القواعد تأخذ `priority` بترتيب ورودها في الملف،
 * لأن الملف مرتَّب من الأخص للأعم (BARQ قبل STC مثلًا).
 */
export function loadReferences(
  rawRules: readonly RawRule[],
  rawMerchants: readonly RawMerchant[],
  categories: readonly Category[],
): LoadedReferences {
  const categoryIdByName = new Map(categories.map((c) => [normalizeText(c.name), c.id]))
  const unknown = new Set<string>()

  const rules: ClassificationRule[] = []
  rawRules.forEach((raw, index) => {
    const word = raw.word?.trim()
    const cat = raw.cat?.trim()
    if (!word || !cat) return
    const categoryId = categoryIdByName.get(normalizeText(cat))
    if (!categoryId) {
      unknown.add(cat)
      return // لا يُخترع تصنيف غير معرّف
    }
    rules.push({
      id: `rule-${String(index + 1).padStart(4, '0')}`,
      priority: index + 1,
      matchText: word,
      matchMode: 'contains',
      categoryId,
      enabled: true,
    })
  })

  const merchants: Merchant[] = []
  let unverified = 0
  const seen = new Set<string>()

  rawMerchants.forEach((raw, index) => {
    const name = raw.name?.trim()
    if (!name) return
    const normalized = normalizeText(name)
    if (!normalized || seen.has(normalized)) return
    seen.add(normalized)

    const merchant: Merchant = {
      id: `merch-${String(index + 1).padStart(5, '0')}`,
      displayName: name,
      normalizedName: normalized,
    }

    const cat = raw.cat?.trim()
    const confident = raw.confidence?.trim() === CONFIDENCE_VERIFIED
    // ⚠️ التأكيد الآلي ممنوع على الغامض (spec/05)
    if (cat && cat !== NEEDS_CONFIRMATION && confident) {
      const categoryId = categoryIdByName.get(normalizeText(cat))
      if (categoryId) merchant.verifiedCategoryId = categoryId
      else unknown.add(cat)
    }
    if (!merchant.verifiedCategoryId) unverified++

    merchants.push(merchant)
  })

  return {
    rules,
    merchants,
    unknownCategoryNames: [...unknown].sort(),
    unverifiedMerchantCount: unverified,
  }
}

/** يبني فئات من قائمة أسماء + ألوان tokens.json. */
export interface RawCategory {
  name: string
  baseColor: string
  icon: string
}

export function buildCategories(raw: readonly RawCategory[]): Category[] {
  return raw.map((c, i) => ({
    id: `cat-${normalizeText(c.name).replace(/\s+/g, '-').toLowerCase() || `n${i}`}`,
    parentId: null,
    name: c.name,
    iconKey: c.icon,
    // لون واحد في المرجع؛ اشتقاق نسخة مقروءة لكل وضع يتم في طبقة العرض
    lightColor: c.baseColor,
    darkColor: c.baseColor,
    active: true,
    order: i,
  }))
}
