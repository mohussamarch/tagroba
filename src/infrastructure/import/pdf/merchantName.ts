/**
 * استخراج اسم التاجر من تفاصيل عملية الراجحي — **دالة نقية**.
 *
 * ⚠️ **دي أنماط استدلالية (heuristics) مش قاعدة مثبتة.** الكشف بيكتب
 * اسم التاجر بصيغ مختلفة، والأنماط دي متقاسة على كشف حقيقي — لكنها
 * بتفشل على صيغ ما شفناهاش. الفشل بيرجّع `null`، **مش تخمين**:
 * `spec/05` بيمنع اختراع بيانات، والاسم الغلط أسوأ من غياب الاسم
 * لأنه بيغذّي التصنيف التلقائي بمعلومة كاذبة.
 *
 * حد معروف موثّق: الاسم اللي بيتقطع على سطرين في الـPDF بيتقرا ناقصًا
 * («AL-» + «OTHAIM MARKETS COMP» بتطلع «OTHAIM MARKETS COMP»).
 * السكربت القديم عنده نفس الحد.
 */

/** شراء إنترنت محلي: «Online Purchase from NAME» */
const ONLINE = /Online Purchase from ([A-Za-z0-9&.'\- ]+)/

/** اتفاقية دفع: «payment_agreement_XXX, NAME,» */
const AGREEMENT = /payment_agreement_\w+,\s*([A-Za-z0-9&.'\- ]{2,45}?)\s*,/

/** معرّف اتفاقية: «ag_XXXX, NAME, CITY, SA» */
const AGENT = /\bag_[0-9a-f]{6,},\s*([A-Za-z0-9&.'\- ]{2,45}?)\s*,/

/** محلي: «NAME, CITY, SA» */
const LOCAL = /([A-Za-z0-9&.'\- ]{3,}?),\s*([A-Za-z\- ]+),\s*SA\b/

/** دولي: «NAME : 1234****5678» */
const INTERNATIONAL = /([A-Za-z0-9*.'&\- ]{3,45}?)\s*:\s*\d{4}\*+\d+/

/** بادئات بنكية بتلزق في أول الاسم ومالهاش معنى للمستخدم. */
const BANK_PREFIX = /^(TYB|Agmt\)?|ARBS\w*|SABS\w*|PG\d+|\d{6,})[\s-]*/i

/** لواحق دولة في آخر الاسم الدولي. */
const COUNTRY_SUFFIX = /\s+(US|IE|GB|AE|NL|LU|CZ|SA|TR|EG)$/i

function clean(name: string): string | null {
  let out = name.replace(/\s+/g, ' ').trim()
  out = out.replace(BANK_PREFIX, '')
  out = out.replace(/\s*\d{3}-\d+\s*/g, ' ')
  out = out.replace(COUNTRY_SUFFIX, '')
  out = out.replace(/[\s,.\-]+$/, '').trim()

  // اسم من حرف أو اتنين مش اسم — والرقم الصافي مرجع مش تاجر
  if (out.length < 3) return null
  if (!/[A-Za-z]/.test(out)) return null
  return out.toUpperCase().slice(0, 40)
}

/**
 * يرجّع اسم التاجر، أو `null` لو مفيش نمط طابق.
 * الترتيب مقصود: الأنماط الأدق الأول.
 */
export function extractMerchantName(details: string): string | null {
  for (const pattern of [ONLINE, AGREEMENT, AGENT, LOCAL, INTERNATIONAL]) {
    const match = pattern.exec(details)
    if (match) {
      const name = clean(match[1])
      if (name) return name
    }
  }
  return null
}
