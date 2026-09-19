package app.masroufy.core

/**
 * الطبعنة للبحث والمطابقة — نقل `src/domain/normalize.ts` (spec/05).
 * بترجّع نسخة جديدة دايمًا؛ النص الأصلي بيتحفظ زي ما هو.
 */

/** NFKC — أشكال العرض العربية (ﺔﻈﺣﻼﻣ من قارئ PDF قديم) ترجع حروفها. خاصة بكل جهاز. */
internal expect fun nfkc(text: String): String

/** التشكيل والتطويل — بيتشالوا للمطابقة بس. */
private fun isDiacritic(c: Char): Boolean =
    c.code in 0x0610..0x061A || c.code in 0x064B..0x065F || c.code == 0x0670 || c.code in 0x06D6..0x06ED || c.code == 0x0640

/** أإآٱ ⇒ ا · ى ⇒ ي · ة ⇒ ه · ؤ ⇒ و · ئ ⇒ ي («عبد الفتاح» و«عبدالفتّاح» اسم واحد في البحث). */
private fun unifyArabicLetter(c: Char): Char = when (c) {
    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
    'ى' -> 'ي'
    'ة' -> 'ه'
    'ؤ' -> 'و'
    'ئ' -> 'ي'
    else -> c
}

/** الأرقام العربية والفارسية للاتينية. */
fun latinizeDigits(text: String): String {
    val out = StringBuilder(text.length)
    for (c in text) out.append(JsText.easternDigit(c) ?: c)
    return out.toString()
}

private val NOT_LETTER_OR_NUMBER = Regex("[^\\p{L}\\p{N}]+")
private val SPACES = Regex(" +")

/** الصيغة المطبعنة للمطابقة (القواعد والتجار والبحث) — بتتحفظ جنب الأصل مش مكانه. */
fun normalizeText(text: String): String {
    if (text.isEmpty()) return ""
    val unified = StringBuilder()
    for (c in latinizeDigits(nfkc(text))) {
        val u = unifyArabicLetter(c)
        if (!isDiacritic(u)) unified.append(u)
    }
    return unified.toString()
        .uppercase()
        .replace(NOT_LETTER_OR_NUMBER, " ")
        .trim()
        .replace(SPACES, " ")
}

/** من غير مسافات — «عبدالفتاح» قصاد «عبد الفتاح». */
fun normalizeCompact(text: String): String = normalizeText(text).replace(" ", "")

/** النص المطبعن فيه العبارة المطبعنة؟ بالصيغتين العادية والمضغوطة. */
fun normalizedContains(haystack: String, needle: String): Boolean {
    val n = normalizeText(needle)
    if (n.isEmpty()) return false
    if (normalizeText(haystack).contains(n)) return true
    return normalizeCompact(haystack).contains(normalizeCompact(needle))
}
