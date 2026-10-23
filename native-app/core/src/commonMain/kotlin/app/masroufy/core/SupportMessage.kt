package app.masroufy.core

/**
 * «ابعتلنا» (`SupportForm.dc.html` — فرع التصميم 9170906d): نوع الرسالة. السعودي بيسمي المشكلة «شكوى».
 * ⚠️ **الوجهة لسه مش متوصّلة** (سؤال مفتوح للمالك — OVERRIDES ❓ «ابعتلنا»): الرسايل بتتحفظ على الجهاز بس ومبتتبعتش لأي مكان.
 */
enum class SupportKind(val egLabel: String, val saLabel: String) {
    QUESTION("سؤال", "سؤال"),
    IDEA("اقتراح", "اقتراح"),
    PROBLEM("مشكلة", "شكوى"),
    ;

    fun label(egypt: Boolean): String = if (egypt) egLabel else saLabel

    /** نص خانة الكتابة (من النموذج). */
    fun hint(egypt: Boolean): String = when (this) {
        QUESTION -> "اكتب سؤالك"
        IDEA -> "اكتب اقتراحك"
        PROBLEM -> if (egypt) "إيه اللي حصل؟" else "ماذا حدث؟"
    }
}

/** رسالة محفوظة على الجهاز: [day] تاريخ اليوم (`yyyy-MM-dd`) · [countryCode] بلد الشاشة وقت الكتابة (null لو مجهول). */
data class SupportMessage(val kind: SupportKind, val text: String, val day: String, val countryCode: String?)

/** نصوص النموذج نفسه بالمصري والسعودي. */
class SupportChrome(egypt: Boolean) {
    val title = if (egypt) "ابعتلنا" else "أرسل لنا"
    val kindLabel = if (egypt) "نوعه" else "النوع"
    val send = if (egypt) "ابعت" else "أرسل"
    val note = if (egypt) "بنقرا كل رسالة." else "نقرأ كل رسالة."
    val sent = if (egypt) "اتبعت" else "تم الإرسال"
    val pageTitle = "الدعم"
    val faqFirst = if (egypt) "شوف الأسئلة الشائعة الأول" else "اطّلع على الأسئلة الشائعة أولًا"
}
