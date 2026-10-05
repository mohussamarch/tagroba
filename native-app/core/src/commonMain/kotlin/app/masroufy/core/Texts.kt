package app.masroufy.core

/**
 * لغة العرض — OVERRIDES §40. العربي له **نسختين** (§66) بتتحدد من بلد الحساب الشغال ([ArabicVariant]).
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
 * نسخة العربي (OVERRIDES §66 — قرار المالك 2026-10-05):
 * - [MSA] **فصحى مختصرة** — للسعودية، و**الافتراضي لأي بلد تانية**.
 * - [EGYPTIAN] **النص المصري الحالي بالحرف** — لمصر. ده نفس نص التطبيق الحالي، فملفات المرجع (golden) بتشتغل عليه.
 * النسخة بتيجي من حزمة البلد ([CountryPack.arabicVariant]) — مش اختيار في الشاشة.
 */
enum class ArabicVariant {
    MSA,
    EGYPTIAN,
    ;

    companion object {
        /** بلد الحساب الشغال ⇒ النسخة. بلد مش معروفة (أو مفيش) ⇒ الحزمة الافتراضية (السعودية) ⇒ فصحى. */
        fun forCountry(countryCode: String?): ArabicVariant = countryPack(countryCode).arabicVariant
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
 * `language` بتتظبط **مرة واحدة** عند بداية التطبيق من إعدادات المستخدم. `arabicVariant` بتتبع **بلد الحساب الشغال**
 * ([Texts.followCountry] — من الجلسة وإدارة البلاد)، فحالات الاستخدام والشاشات ما تعرفش عنها حاجة.
 */
// كل نسخة مقسومة على كذا ملف عشان حد الـ300 سطر (CLAUDE.md #7). المصري = الجداول القديمة **بالاسم الجديد بس** (من غير ولا حرف اتغير).
internal val EGYPTIAN_TEXTS: Map<TextKey, String> = EGYPTIAN_SCREEN_TEXTS + EGYPTIAN_DATA_TEXTS + EGYPTIAN_DUES_TEXTS + EGYPTIAN_STORAGE_TEXTS + EGYPTIAN_AUTH_TEXTS + EGYPTIAN_ZAKAT_TEXTS + EGYPTIAN_ALERT_TEXTS +
    EGYPTIAN_EVENT_TEXTS + EGYPTIAN_INCOME_TEXTS + EGYPTIAN_SPACE_TEXTS + EGYPTIAN_CALENDAR_TEXTS + EGYPTIAN_USECASE_TEXTS + EGYPTIAN_PEOPLE_TEXTS +
    EGYPTIAN_FEED_ALERT_TEXTS + EGYPTIAN_ADVISOR_TEXTS + EGYPTIAN_ADVISOR_MORE_TEXTS + EGYPTIAN_INHERITANCE_TEXTS + EGYPTIAN_CALC_TEXTS
internal val MSA_TEXTS: Map<TextKey, String> = MSA_SCREEN_TEXTS + MSA_DATA_TEXTS + MSA_DUES_TEXTS + MSA_STORAGE_TEXTS + MSA_AUTH_TEXTS + MSA_ZAKAT_TEXTS + MSA_ALERT_TEXTS +
    MSA_EVENT_TEXTS + MSA_INCOME_TEXTS + MSA_SPACE_TEXTS + MSA_CALENDAR_TEXTS + MSA_USECASE_TEXTS + MSA_PEOPLE_TEXTS + MSA_FEED_ALERT_TEXTS + MSA_ADVISOR_TEXTS +
    MSA_ADVISOR_MORE_TEXTS + MSA_INHERITANCE_TEXTS + MSA_CALC_TEXTS
internal val ENGLISH_TEXTS: Map<TextKey, String> = ENGLISH_SCREEN_TEXTS + ENGLISH_DATA_TEXTS + ENGLISH_DUES_TEXTS + ENGLISH_STORAGE_TEXTS + ENGLISH_AUTH_TEXTS + ENGLISH_ZAKAT_TEXTS + ENGLISH_ALERT_TEXTS +
    ENGLISH_EVENT_TEXTS + ENGLISH_INCOME_TEXTS + ENGLISH_SPACE_TEXTS + ENGLISH_CALENDAR_TEXTS + ENGLISH_USECASE_TEXTS + ENGLISH_PEOPLE_TEXTS +
    ENGLISH_FEED_ALERT_TEXTS + ENGLISH_ADVISOR_TEXTS + ENGLISH_ADVISOR_MORE_TEXTS + ENGLISH_INHERITANCE_TEXTS + ENGLISH_CALC_TEXTS

/** جدول نسخة العربي. */
internal fun arabicTable(variant: ArabicVariant): Map<TextKey, String> = when (variant) {
    ArabicVariant.MSA -> MSA_TEXTS
    ArabicVariant.EGYPTIAN -> EGYPTIAN_TEXTS
}

object Texts {
    var language: Language = Language.AR

    /** الافتراضي فصحى (السعودية والافتراضي لأي بلد — §66) لحد ما الجلسة تعرف بلد الحساب الشغال. */
    var arabicVariant: ArabicVariant = ArabicVariant.MSA

    /** نسخة العربي من بلد الحساب الشغال (مصر ⇒ المصري، أي بلد تانية أو مفيش ⇒ الفصحى). الإنجليزي ما بيتأثرش. */
    fun followCountry(countryCode: String?) {
        arabicVariant = ArabicVariant.forCountry(countryCode)
    }

    fun of(key: TextKey, vararg args: String): String {
        // لو مفتاح لسه ماتترجمش للإنجليزي، بيرجع بالعربي بدل ما يختفي من الشاشة
        val pattern = when (language) {
            Language.AR -> arabic(key)
            Language.EN -> ENGLISH_TEXTS[key] ?: arabic(key)
        }
        return fill(pattern, args)
    }

    private fun arabic(key: TextKey): String {
        val other = if (arabicVariant == ArabicVariant.MSA) ArabicVariant.EGYPTIAN else ArabicVariant.MSA
        return resolveArabic(key, arabicTable(arabicVariant), arabicTable(other))
    }
}

/** النسخة الشغالة ⇒ النسخة التانية ⇒ اسم المفتاح (ما بيوقعش أبدًا — واختبار الاكتمال بيمنع إن ده يحصل أصلًا). */
internal fun resolveArabic(key: TextKey, primary: Map<TextKey, String>, other: Map<TextKey, String>): String =
    primary[key] ?: other[key] ?: key.name

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
