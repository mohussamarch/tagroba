package app.masroufy.core

/**
 * هل الرسالة دي نص من جداول النصوص باللغة/النسخة الشغالة (`uiText` بمتغيراته أو من غيرها)؟
 * أخطاء تحقق حالات الاستخدام بتتكتب بـ`uiText` ⇒ تتعرض زي ما هي. أي رسالة تانية (فايرستور · الشبكة · رسالة داخلية مكتوبة في الكود)
 * **ما تتعرضش** للمستخدم — الشاشة بتعرض بدلها نص ثابت من الجداول (`failureText` في `ui/text`).
 */
fun isUiText(message: String): Boolean = ALL_TEXT_KEYS.any { key -> templateMatches(Texts.of(key), message) }

/** القالب (`{0}` و`{1}` متغيرات) بيطابق الرسالة؟ قالب كله متغيرات (مفيش حرف ثابت) ممكن يطابق أي حاجة ⇒ ما بيتحسبش. */
internal fun templateMatches(template: String, message: String): Boolean {
    if (template == message) return true
    if (!PLACEHOLDER.containsMatchIn(template)) return false
    if (PLACEHOLDER.replace(template, "").none { it.isLetter() }) return false
    val pattern = PLACEHOLDER.split(template).joinToString("[\\s\\S]*") { Regex.escape(it) }
    return Regex(pattern).matches(message)
}

private val PLACEHOLDER = Regex("\\{\\d+\\}")
