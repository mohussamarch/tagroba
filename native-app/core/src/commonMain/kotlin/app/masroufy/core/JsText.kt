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

    /** نفس `JSON.stringify(نص)` بالحرف — للبصمات اللي بتتحسب على نص JSON. */
    fun jsonString(text: String): String {
        val out = StringBuilder(text.length + 2).append('"')
        for ((i, c) in text.withIndex()) {
            when {
                c == '"' -> out.append('\\').append('"')
                c == '\\' -> out.append('\\').append('\\')
                c == '\b' -> out.append('\\').append('b')
                c == '\t' -> out.append('\\').append('t')
                c == '\n' -> out.append('\\').append('n')
                c.code == 0x0C -> out.append('\\').append('f')
                c == '\r' -> out.append('\\').append('r')
                c.code < 0x20 || isLoneSurrogate(text, i) -> out.append('\\').append('u').append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }

    private fun isLoneSurrogate(text: String, i: Int): Boolean {
        val c = text[i]
        if (c.isHighSurrogate()) return i + 1 >= text.length || !text[i + 1].isLowSurrogate()
        if (c.isLowSurrogate()) return i == 0 || !text[i - 1].isHighSurrogate()
        return false
    }

    /** الأرقام العربية الهندية (U+0660…) والفارسية (U+06F0…) ⇒ رقمها اللاتيني، وإلا null. */
    fun easternDigit(c: Char): Char? = when (c.code) {
        in 0x0660..0x0669 -> '0' + (c.code - 0x0660)
        in 0x06F0..0x06F9 -> '0' + (c.code - 0x06F0)
        else -> null
    }
}
