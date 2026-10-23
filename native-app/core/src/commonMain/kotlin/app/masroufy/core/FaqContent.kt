package app.masroufy.core

/**
 * «الأسئلة الشائعة» (`Faq.dc.html` — فرع التصميم 9170906d): النموذج النقي + اختيار الأسئلة حسب البلد + البحث. **مفيش واجهة هنا.**
 * النصوص كلها في `FaqContentBasics.kt` و`FaqContentZakat.kt` و`FaqContentMore.kt` (قائمة typed — مش مفاتيح `TextKey`، عشان مئات
 * النصوص ما تزوّدش حجم `TextKey` — HANDOVER §4-4). كل سؤال بنسختين: المصري (مصر) والسعودي (السعودية — الفصحى).
 */
enum class FaqCountry { BOTH, EG, SA }

/** الأقسام بترتيب الشرايح. [saLabel] = اسم القسم بالسعودي لو مختلف. */
enum class FaqSection(val egLabel: String, private val saLabelOrNull: String? = null) {
    BASICS("الأساسيات"),
    BALANCE("الرصيد والكاش", "الرصيد والنقد"),
    SMS("رسايل البنك", "رسائل البنك"),
    OPS("العمليات"),
    PEOPLE("الديون والأشخاص"),
    BNPL("التقسيط"),
    ZAKAT("الزكاة"),
    INVEST("الاستثمار"),
    PRIVACY("الخصوصية"),
    ;

    val saLabel: String get() = saLabelOrNull ?: egLabel

    fun label(egypt: Boolean): String = if (egypt) egLabel else saLabel
}

/** سؤال وإجابته سطور (الإجابة سطر أو كذا سطر). */
class FaqText(val question: String, vararg answer: String) {
    val lines: List<String> = answer.toList()
}

/** سؤال: [eg] أو [sa] = `null` لو السؤال مش للبلد دي (تابي وتمارا للسعودية بس · الزكاة لكل بلد مرجعها). */
class FaqEntry(val section: FaqSection, val country: FaqCountry, val eg: FaqText?, val sa: FaqText?) {
    fun shownIn(egypt: Boolean): Boolean = country == FaqCountry.BOTH || country == if (egypt) FaqCountry.EG else FaqCountry.SA

    fun textFor(egypt: Boolean): FaqText? = if (egypt) eg else sa
}

/** سؤال جاهز للعرض في بلد. [id] ثابت لكل سؤال (الترتيب في القائمة) — بيتحفظ مفتوح/مقفول بيه. */
data class FaqItem(val id: Int, val section: FaqSection, val question: String, val lines: List<String>)

/** كل الأسئلة (39 لمصر · 41 للسعودية: الجديدة 38 و40 + سؤال الاستثمار القديم). */
val FAQ_ENTRIES: List<FaqEntry> = FAQ_CONTENT_BASICS + FAQ_CONTENT_ZAKAT + FAQ_CONTENT_MORE

private fun isEgypt(countryCode: String?): Boolean = countryCode.equals("EG", ignoreCase = true)

/** أسئلة البلد الشغالة بترتيب الأقسام ثم ترتيب الكتابة. بلد مش مصر ⇒ نسخة السعودية (زي باقي الشاشات). */
fun faqFor(countryCode: String?): List<FaqItem> {
    val egypt = isEgypt(countryCode)
    val shown = FAQ_ENTRIES.mapIndexedNotNull { i, e -> if (e.shownIn(egypt)) e.textFor(egypt)?.let { FaqItem(i, e.section, it.question, it.lines) } else null }
    return FaqSection.entries.flatMap { s -> shown.filter { it.section == s } }
}

/** الأقسام اللي فيها أسئلة للبلد دي (للشرايح). */
fun faqSections(items: List<FaqItem>): List<FaqSection> = FaqSection.entries.filter { s -> items.any { it.section == s } }

/** توحيد الحروف للبحث: الألف بأنواعها · التاء المربوطة · الألف المقصورة · قص الفراغات (نفس `norm` في النموذج). */
fun faqNormalize(text: String): String =
    text.map { c -> when (c) { 'إ', 'أ', 'آ' -> 'ا'; 'ة' -> 'ه'; 'ى' -> 'ي'; else -> c } }.joinToString("").trim()

/** البحث في السؤال والإجابة معًا. بحث فاضي ⇒ الكل. الحروف بعد التوحيد؛ مفيش تقطيع كلمات (زي النموذج: `includes`). */
fun faqSearch(items: List<FaqItem>, query: String): List<FaqItem> {
    val q = faqNormalize(query)
    if (q.isEmpty()) return items
    return items.filter { faqNormalize(it.question + " " + it.lines.joinToString(" ")).contains(q) }
}

/** نصوص الصفحة نفسها (مش الأسئلة) بالمصري والسعودي. */
class FaqChrome(egypt: Boolean) {
    val title = "الأسئلة الشائعة"
    val tip = if (egypt) "دوّر بكلمة، أو اختار قسم." else "ابحث بكلمة، أو اختر قسمًا."
    val searchHint = if (egypt) "دوّر في الأسئلة" else "ابحث في الأسئلة"
    val all = "الكل"
    val empty = if (egypt) "مفيش سؤال بالكلمة دي." else "لا يوجد سؤال بهذه الكلمة."
    val ask = if (egypt) "مالقيتش إجابتك؟ اسأل مصروفي" else "لم تجد إجابتك؟ اسأل مصروفي"
}
