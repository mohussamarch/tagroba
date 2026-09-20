package app.masroufy.core

/**
 * لغة العرض — OVERRIDES §40. **العربي هو الأصل**: قيمة أي مفتاح بالعربي هي نفس النص القديم
 * بالحرف، عشان ملفات المرجع (golden) تفضل مطابقة وما يتغيرش أي رقم ولا أي نص عند المالك.
 */
enum class Language(val wire: String) {
    AR("ar"),
    EN("en"),
    ;

    companion object {
        fun fromWire(wire: String): Language = entries.firstOrNull { it.wire == wire } ?: AR
    }
}

/**
 * جدول النصوص المعروضة للمستخدم.
 *
 * **اللي بيتحط هنا:** أي نص هيقرأه المستخدم (أسماء الأنواع الاقتصادية، أسباب الاقتراح،
 * رسايل الأخطاء، رموز العملات).
 *
 * **اللي ما بيتحطش هنا:** النصوص اللي التطبيق **بيطابق** بيها — زي أسماء التصنيفات اللي
 * بيتقارن بيها في `SuggestEconomicKind`، وكلمات رسايل البنك في `BankSmsParser`، وعناوين
 * أعمدة الملفات في `ImportSchemas`. دي لغة البيانات مش لغة الواجهة، وترجمتها بتكسّر القراءة.
 * وكمان **بذور** الحساب الجديد (أسماء التصنيفات والمحافظ) — دي بتتخزن في بيانات المستخدم
 * وقت الإنشاء، فمكانها حزمة البلد في `CountryPack`.
 *
 * `language` بتتظبط **مرة واحدة** عند بداية التطبيق من إعدادات المستخدم، وبعدها ما تتغيرش
 * جوه حساب واحد وسط الشغل. الافتراضي عربي.
 */
// الجداول مقسومة على ملفين لكل لغة عشان حد الـ300 سطر (CLAUDE.md #7)
internal val ARABIC_TEXTS: Map<TextKey, String> = ARABIC_SCREEN_TEXTS + ARABIC_DATA_TEXTS
internal val ENGLISH_TEXTS: Map<TextKey, String> = ENGLISH_SCREEN_TEXTS + ENGLISH_DATA_TEXTS

object Texts {
    var language: Language = Language.AR

    fun of(key: TextKey, vararg args: String): String {
        val table = when (language) {
            Language.AR -> ARABIC_TEXTS
            Language.EN -> ENGLISH_TEXTS
        }
        // لو مفتاح لسه ماتترجمش، بيرجع بالعربي بدل ما يختفي من الشاشة
        val pattern = table[key] ?: ARABIC_TEXTS.getValue(key)
        return fill(pattern, args)
    }
}

/** النص المعروض للمفتاح باللغة الحالية. `{0}` و`{1}` بيتبدلوا بالمتغيرات بالترتيب. */
fun uiText(key: TextKey, vararg args: String): String = Texts.of(key, *args)

private fun fill(pattern: String, args: Array<out String>): String {
    if (args.isEmpty() || !pattern.contains('{')) return pattern
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '{') {
            val close = pattern.indexOf('}', i + 1)
            val index = if (close > i + 1) pattern.substring(i + 1, close).toIntOrNull() else null
            if (index != null && index in args.indices) {
                out.append(args[index])
                i = close + 1
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}
