package app.masroufy.core

/**
 * **حروف مخفية وأشكال العرض العربية** في رسايل البنك (الجولة السادسة — مراجعة عدائية تانية للجولة الخامسة).
 *
 * الجولة الخامسة كانت بتشيل 6 حروف مخفية بس (U+200B/C/D · U+2060 · U+00AD · U+FEFF)، فـ«decl⁣ined» (U+2063) و«مرف͏وضة» (U+034F)
 * و«ﻣﺮﻓﻮﺿﺔ» (أشكال العرض U+FE70…U+FEFC — نفس الحروف بشكلها جوه الكلمة) كانت بتعدّي الحراس وقايمة كلمات الحالة، والرسالة تتسجل.
 * دلوقتي:
 * - **كل** حرف تنسيق (فئة Unicode «Cf») + U+034F + محددات الشكل U+FE00…U+FE0F + حروف الوسوم (U+E0000…) بتتشال.
 * - أشكال العرض العربية (ب) بترجع للحرف الأصلي (من غير مكتبة: الجدول تحت بترتيب الكتلة نفسها).
 * - والرسالة اللي **كان فيها** حاجة من دول (غير علامات الاتجاه العادية) ما بتتسجلش لوحدها أبدًا ([hasHiddenOrPresentationChars]).
 */

/** حرف تنسيق مخفي (من غير علامات الاتجاه — دي بتتشال لوحدها في `normalizeSmsBody`). */
internal fun isHiddenFormat(c: Char): Boolean =
    c.category == CharCategory.FORMAT || c.code == 0x034F || c.code in 0xFE00..0xFE0F

private fun isTagPair(text: String, i: Int): Boolean =
    text[i].code == 0xDB40 && i + 1 < text.length && text[i + 1].code in 0xDC00..0xDC7F

/** أشكال العرض (ب) من U+FE80: عدد الأشكال لكل حرف + الحرف الأصلي (لام ألف = حرفين). */
private val FORMS_B: List<Pair<Int, String>> = listOf(
    1 to "ء", 2 to "آ", 2 to "أ", 2 to "ؤ", 2 to "إ", 4 to "ئ", 2 to "ا", 4 to "ب", 2 to "ة", 4 to "ت", 4 to "ث", 4 to "ج", 4 to "ح",
    4 to "خ", 2 to "د", 2 to "ذ", 2 to "ر", 2 to "ز", 4 to "س", 4 to "ش", 4 to "ص", 4 to "ض", 4 to "ط", 4 to "ظ", 4 to "ع", 4 to "غ",
    4 to "ف", 4 to "ق", 4 to "ك", 4 to "ل", 4 to "م", 4 to "ن", 4 to "ه", 2 to "و", 2 to "ى", 4 to "ي", 2 to "لآ", 2 to "لأ", 2 to "لإ", 2 to "لا",
)

private val FORMS_B_MAP: Map<Int, String> = buildMap {
    var code = 0xFE80
    for ((count, base) in FORMS_B) repeat(count) { put(code++, base) }
}

/** U+FE70…U+FE7F = التشكيل بشكل العرض ⇒ بيتشال زي التشكيل العادي. */
private fun formB(c: Char): String? = when (c.code) {
    in 0xFE70..0xFE7F -> ""
    else -> FORMS_B_MAP[c.code]
}

/** النص من غير الحروف المخفية، وأشكال العرض راجعة لحروفها. */
internal fun foldHiddenText(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            isTagPair(text, i) -> i++
            isHiddenFormat(c) -> Unit
            else -> out.append(formB(c) ?: c)
        }
        i++
    }
    return out.toString()
}

/**
 * النص من غير التشكيل (فتحة · ضمة · شدة …) — لتعرّف العملة بس («ريال عُماني» كانت بتتقري ريال سعودي، و«جُنيه» مش جنيه). القارئ بيكتب
 * النص الأصلي في الوصف والبصمة.
 */
internal fun withoutTashkeel(text: String): String = text.filterNot { it.code in 0x064B..0x065F || it.code == 0x0670 }

/**
 * الجولة السابعة — **علامات قلب الاتجاه** (bidi override): U+202D (LRO) و U+202E (RLO) بيعرضوا الكلام **بالمقلوب**، فالنص اللي المالك
 * شايفه غير النص اللي القارئ بيقراه: «SAR ‮06.84‬» بتتعرض 48.60 واتسجلت 6.84 · «QUOLL BAKERY ‮DENILCED‬» بتتعرض DECLINED واتسجلت
 * شراء. القارئ **بيرفض** الرسالة دي ([hasBidiOverride] — مش هيقرا رقم أو كلمة المالك شايفها بشكل تاني). علامات التضمين U+202A/U+202B
 * ما بتقلبش الحروف بس البنوك ما بتبعتهاش ⇒ الرسالة **تستنى** ([BIDI_EMBEDDING]). العلامات العادية (LRM · RLM · ALM · العزل U+2066…2069 ·
 * PDF U+202C) زي ما هي.
 */
private val BIDI_OVERRIDE = setOf(0x202D, 0x202E)
private val BIDI_EMBEDDING = setOf(0x202A, 0x202B, 0x202D, 0x202E)

/** فيها علامة قلب اتجاه (LRO/RLO) ⇒ القارئ بيرفضها (الكلام المعروض غير المقروء). */
internal fun hasBidiOverride(raw: String): Boolean = raw.any { it.code in BIDI_OVERRIDE }

private fun isNumberChar(c: Char?): Boolean = c != null && (c.isDigit() || c in ".,٫٬")

/**
 * الجولة التامنة — **علامة اتجاه جوه الرقم** (أي علامة من `BIDI_CODES`: RLM/LRM/ALM · العزل U+2066…2069 · التضمين): «SAR 1⁧,234.5⁩0» بتتعرض
 * في برنامج الرسايل «1234.5,0» والقارئ بيقرا 1,234.50 — الرقم اللي المالك شايفه غير اللي بيتسجل. البنوك بتحط العلامات دي **حوالين**
 * الرقم مش جواه، فالرسالة دي **بتترفض** زي علامات القلب (`SMS_HIDDEN_TEXT`). علامة قبل الرقم أو بعده («‏48.60 ر.س») عادي.
 */
internal fun hasBidiInsideNumber(raw: String): Boolean {
    /** من [from] في اتجاه [step]: حروف رقم (وعلامات اتجاه) من غير مسافة لحد رقم فعلًا. */
    fun digitReachable(from: Int, step: Int): Boolean {
        var j = from
        while (j in raw.indices && (raw[j].code in BIDI_CODES || isNumberChar(raw[j]))) {
            if (raw[j].isDigit()) return true
            j += step
        }
        return false
    }
    return raw.indices.any { i -> raw[i].code in BIDI_CODES && digitReachable(i - 1, -1) && digitReachable(i + 1, 1) }
}

/** رمز الريال (U+FDFC) جوه كتلة أشكال العرض (أ) — مش حرف متشكل. */
private const val RIAL_SIGN = 0xFDFC

/**
 * الرسالة الأصلية فيها حرف مخفي (غير علامات الاتجاه العادية · BOM في أولها) أو شكل عرض عربي ⇒ **ما بتتسجلش لوحدها** (حتى لو الحراس
 * بقت تشوف الكلمة): البنوك ما بتبعتش كده، فده تلاعب أو نص منقول — يستنى المالك.
 */
internal fun hasHiddenOrPresentationChars(raw: String): Boolean {
    for ((i, c) in raw.withIndex()) {
        if (c.code in BIDI_EMBEDDING) return true
        if (c.code in BIDI_CODES || (i == 0 && c.code == 0xFEFF)) continue
        if (isHiddenFormat(c) || isTagPair(raw, i)) return true
        if ((c.code in 0xFB50..0xFDFF && c.code != RIAL_SIGN) || c.code in 0xFE70..0xFEFC) return true
    }
    return false
}
