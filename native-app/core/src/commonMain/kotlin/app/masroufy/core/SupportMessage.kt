package app.masroufy.core

/**
 * «ابعتلنا» (`SupportForm.dc.html` — فرع التصميم 9170906d): نوع الرسالة. السعودي بيسمي المشكلة «شكوى».
 * الوجهة (r06-telemetry · OVERRIDES §114 · PLAN-0.6 §16): نسخة على الجهاز + مستند في `inbox/{id}` على فايربيز ([FeedbackDoc]) — مهمة آلية في
 * المستودع الخاص بتقراه وتفتح مشكلة وتمسحه. [wire] = قيمة `kind` في المستند.
 */
enum class SupportKind(val egLabel: String, val saLabel: String, val wire: String) {
    QUESTION("سؤال", "سؤال", "question"),
    IDEA("اقتراح", "اقتراح", "suggestion"),
    PROBLEM("مشكلة", "شكوى", "problem"),
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

/** أقصى طول لنص «ابعتلنا» — نفس حد `firestore.rules` على `inbox`. */
const val FEEDBACK_TEXT_MAX = 2000

/**
 * مستند `inbox/{id}` بالظبط (القواعد بتقبل المفاتيح دي بس): [kind] question|suggestion|problem · [country] eg|sa · [text] من 1 لـ2000 ·
 * [appVersion] رقم النسخة. `createdAt` = وقت السيرفر (بيتحط وقت الكتابة). **مفيش uid ولا إيميل**.
 */
data class FeedbackDoc(val kind: String, val country: String, val text: String, val appVersion: String) {
    companion object {
        /** بلد مش مصر ⇒ «sa» (الافتراضي في التطبيق كله). النص بيتقص على [FEEDBACK_TEXT_MAX]. */
        fun of(m: SupportMessage, appVersion: String): FeedbackDoc = FeedbackDoc(
            m.kind.wire, if (m.countryCode.equals("EG", ignoreCase = true)) "eg" else "sa", m.text.take(FEEDBACK_TEXT_MAX), appVersion.take(40),
        )
    }
}

/** نصوص النموذج نفسه بالمصري والسعودي. */
class SupportChrome(egypt: Boolean) {
    val title = if (egypt) "ابعتلنا" else "أرسل لنا"
    val kindLabel = if (egypt) "نوعه" else "النوع"
    val send = if (egypt) "ابعت" else "أرسل"
    val note = if (egypt) "بنقرا كل رسالة." else "نقرأ كل رسالة."
    val sent = if (egypt) "اتبعت" else "تم الإرسال"

    /** الكتابة على فايربيز ما نجحتش (غالبًا النت) — النسخة على الجهاز وبتتبعت في دورة الخلفية الجاية. */
    val queued = if (egypt) "محفوظ وهيتبعت لما النت يرجع" else "محفوظة وستُرسل عند عودة الاتصال"
    val pageTitle = "الدعم"
    val faqFirst = if (egypt) "شوف الأسئلة الشائعة الأول" else "اطّلع على الأسئلة الشائعة أولًا"
}
