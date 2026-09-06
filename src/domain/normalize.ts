/**
 * الطبعنة (normalization) للبحث والمطابقة — spec/05.
 *
 * «طبعنة العربية واللاتينية للبحث **دون إتلاف النص الأصلي**.»
 * كل دوال هنا ترجع نسخة جديدة؛ النص الأصلي يُحفظ دائمًا في rawDescription.
 */

/** التشكيل والتطويل — تُزال للمطابقة فقط. */
const DIACRITICS = /[ؐ-ًؚ-ٰٟۖ-ۭـ]/g

/**
 * توحيد الحروف العربية المتغيّرة الشكل:
 * أإآا → ا · ى → ي · ة → ه · ؤ → و · ئ → ي
 * السبب: «عبد الفتاح» و«عبدالفتاح» و«عبد الفتّاح» اسم واحد في البحث.
 */
function unifyArabicLetters(text: string): string {
  return text
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/ؤ/g, 'و')
    .replace(/ئ/g, 'ي')
}

/** الأرقام العربية والفارسية إلى لاتينية. */
export function latinizeDigits(text: string): string {
  let out = ''
  for (const ch of text) {
    const code = ch.codePointAt(0)!
    if (code >= 0x0660 && code <= 0x0669) out += String.fromCharCode(code - 0x0660 + 48)
    else if (code >= 0x06f0 && code <= 0x06f9) out += String.fromCharCode(code - 0x06f0 + 48)
    else out += ch
  }
  return out
}

/**
 * الصيغة المُطبعَنة المستخدمة في مطابقة القواعد والتجار والبحث.
 * تُحفظ إلى جانب النص الأصلي، ولا تحل محله.
 */
export function normalizeText(text: string): string {
  if (!text) return ''
  return unifyArabicLetters(latinizeDigits(text))
    .replace(DIACRITICS, '')
    .toUpperCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ') // كل ما ليس حرفًا أو رقمًا يصير مسافة
    .trim()
    .replace(/\s+/g, ' ')
}

/** صيغة مضغوطة بلا مسافات — تلتقط «عبدالفتاح» مقابل «عبد الفتاح». */
export function normalizeCompact(text: string): string {
  return normalizeText(text).replace(/\s/g, '')
}

/**
 * هل يحتوي النص المُطبعَن على العبارة المُطبعَنة؟
 * يقارن بالصيغتين العادية والمضغوطة حتى لا تفوت فروق المسافات.
 */
export function normalizedContains(haystack: string, needle: string): boolean {
  const n = normalizeText(needle)
  if (!n) return false
  if (normalizeText(haystack).includes(n)) return true
  return normalizeCompact(haystack).includes(normalizeCompact(needle))
}
