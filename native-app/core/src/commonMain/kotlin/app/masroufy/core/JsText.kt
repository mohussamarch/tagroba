package app.masroufy.core

/**
 * أدوات نص بنفس سلوك جافاسكربت بالظبط — عشان النتايج تطابق التطبيق الحالي حرف بحرف (KOTLIN_PLAN §3).
 * مثال: `trim()` في كوتلن ما بيشيلش U+FEFF، و`\s` في جافاسكربت بيشيله.
 * (الحروف مكتوبة بأرقامها عشان المسافات الخفية تبان وما تتلخبطش.)
 */
internal object JsText {
    private val SPACE_CODES = setOf(
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x20, 0xA0, 0x1680,
        0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF,
    )

    /** نفس `\s` في جافاسكربت: WhiteSpace + LineTerminator. */
    fun isWhitespace(c: Char): Boolean = c.code in SPACE_CODES || c.code in 0x2000..0x200A

    /** نفس `String.prototype.trim`. */
    fun trim(text: String): String {
        var start = 0
        var end = text.length
        while (start < end && isWhitespace(text[start])) start++
        while (end > start && isWhitespace(text[end - 1])) end--
        return text.substring(start, end)
    }

    /** نفس `Math.round`: أقرب عدد صحيح، والنص بالظبط بيروح لفوق (مش تقريب البنوك بتاع `kotlin.math.round`). */
    fun round(x: Double): Double {
        val floor = kotlin.math.floor(x)
        return if (x - floor >= 0.5) floor + 1.0 else floor
    }

    fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

    /** الأرقام العربية الهندية (U+0660…) والفارسية (U+06F0…) ⇒ رقمها اللاتيني، وإلا null. */
    fun easternDigit(c: Char): Char? = when (c.code) {
        in 0x0660..0x0669 -> '0' + (c.code - 0x0660)
        in 0x06F0..0x06F9 -> '0' + (c.code - 0x06F0)
        else -> null
    }
}
