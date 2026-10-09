package app.masroufy.core

import app.masroufy.core.JsText.S

/**
 * فلتر الجهاز (`SmsSafety`). **الجولة الخامسة (قاعدة المالك §72: الانتظار مقبول، ضياع عملية حقيقية مش مقبول):** الرسالة بتترمي قبل
 * الحفظ **بسبب صريح بس** — رمز تحقق (دايمًا) · عرض/مرفوضة/«مش عملية» **إلا** لو أولها عنوان أو قالب بنك معروف (ساعتها بتتحفظ والقارئ
 * بيرفضها بسببها فتستنى في «المرفوضة» — «لعرض تفاصيل العملية» أو «See latest offers» تحت شراء حقيقي كانوا بيرموه) · مفيهاش مبلغ جنبه
 * عملة. **كلمة الحركة ما بقتش شرط** («تمت إضافة» · «تم استقطاع» · «استلمت» · «You paid» · «withdrawn» · «وصلك» كانوا بيضيعوا في صمت):
 * أي رسالة من بنك مفعّل فيها مبلغ بتتحفظ، والقارئ يرفض اللي مش فاهمه فيستنى المالك.
 * (كان جوه `SmsGuards.kt` — اتنقل لملف لوحده في الجولة التالتة عشان حد الـ300 سطر.)
 */
object SmsVocabulary {
    private val GI = setOf(RegexOption.IGNORE_CASE)

    /** كلمات حركة الفلوس — للتشخيص بس من الجولة الخامسة (`Evaluate` في أداة القياس)، مش شرط للحفظ. */
    private val MOVEMENT = Regex(
        "شراء|سحب|خصم|سداد|مدفوعات|دفع|حوالة|تحويل|إيداع|ايداع|راتب|استرداد|مرتجع|purchase|withdrawal|transfer|deposit|refund|salary|payment|transaction" +
            "|استرجاع|إرجاع|ارجاع|عكس|كاش$S*باك|مشتريات|انترنت|إنترنت|شيك|تسوية|تسديد|تمت?$S*(?:قيد|استلام|إضافة|اضافة|رد|شحن|استقطاع|تحصيل|إرسال|ارسال)|" +
            "charged|credited|debited|received|deducted|recharged|refunded|returned|reversal|cashback|cheque|settlement|fees?|Trx|adding$S*money|top.?up" +
            "|استلمت|أضيف|اضيف|وصلك|اتحول|مرتب|إشعار$S*(?:دائن|مدين)|اشعار$S*(?:دائن|مدين)|${JsText.B}(?:paid|withdrawn|spent|sent|receiving|POS)${JsText.B}",
        GI,
    )

    /**
     * العملات: ريال سعودي وجنيه مصري بكل كتاباتهم («ر. س» بمسافة — الجولة الخامسة) + العملات الأجنبية (عشان الرسالة تظهر بسبب رفضها
     * بدل ما تختفي) + دينار · درهم · ليرة · إسترليني.
     */
    const val CURRENCY: String =
        "(?:(?<![A-Za-z])(?:SAR|SR|USD|EUR|GBP|AED|EGP|KWD|BHD|QAR|OMR|JOD)(?![A-Za-z])|ريال|ر\\.?س\\.?|ر\\.[ \\t]س\\.?|دولار|يورو|جنيه|جنية|دينار|درهم|ليرة|[إا]سترليني|" +
            "ج\\.م\\.?|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.])|(?<![A-Za-z])L\\.?E(?![A-Za-z]))"

    private val MONEY = Regex(CURRENCY, GI)

    fun hasMovement(text: String): Boolean = MOVEMENT.containsMatchIn(text)

    /**
     * عملة معروفة، أو أي كود عملة أجنبية جنب مبلغ («TRY 450.00» — كانت بتترمي في صمت قبل ما تتسأل عن مبلغها المحلي §75-12)،
     * أو رمز/اسم/اختصار عملة جنب مبلغ («$23.40» · «4500 ين» · «12.50 Swiss Francs» — الجولة التالتة؛ «KD 12.500» · «45.00 د.إ» ·
     * «₩45,000» — الجولة الخامسة).
     */
    fun hasMoney(text: String): Boolean = MONEY.containsMatchIn(text) || isoMoneyIn(text).isNotEmpty() || namedMoneyIn(text).isNotEmpty()

    /**
     * فيها **فلوس ورقم** — شرط الحفظ (الجولة الخامسة، مكان «كلمة حركة + عملة»): عملة (محلية أو أجنبية) ورقم في أي مكان («تم شحن رصيد
     * موبايلك ب 50 بنجاح وخصم 57 من محفظتك شاملة الضريبة جنيه» — الرقم مش لازق في العملة).
     */
    fun hasAmount(text: String): Boolean = hasMoney(text) && text.any { it in '0'..'9' }

    /**
     * أماكن المبالغ بكود أو رمز أو اسم عملة أجنبية — فلتر الجهاز ما بيحجبهاش («JPY 45000» · «$12500» مش رقم حساب). الكود جنب رقم
     * صحيح بيتساب **حتى من غير كسور** (الجولة التالتة): «JPY 45000 (SAR 112.50)» كانت بتتحفظ «JPY ••••5000» فالقارئ ما يشوفش
     * المبلغ الأجنبي ويسجّلها بالمقابل المحلي.
     */
    fun foreignMoneyRanges(text: String): List<IntRange> = (isoMoneyIn(text, relaxed = true) + namedMoneyIn(text)).map { it.range }

    /** سبب الحارس (رمز · عرض · مرفوضة · مش عملية)، أو null — القارئ بيرفض بيه. */
    fun ignoreReason(text: String): TextKey? = smsIgnoreReason(normalizeSmsBody(text))

    /**
     * الرسالة دي **ما تتحفظش**: رمز تحقق دايمًا، وأي حارس تاني **لو أولها مش عنوان أو قالب بنك معروف** (الجولة الخامسة — العنوان المعروف
     * بيتحفظ ويستنى في «المرفوضة» بسببه بدل ما يضيع لو الحارس مسك سطر إعلان أو تحذير).
     */
    fun ignoreBeforeStorage(text: String): Boolean {
        val body = normalizeSmsBody(text)
        val reason = smsIgnoreReason(body) ?: return false
        return reason == TextKey.SMS_SENSITIVE || !hasKnownHead(body)
    }

    /** أول الرسالة عنوان سعودي معروف (موحّد أو قالب بنك) أو أول جملة قالب مصري معروف. */
    fun hasKnownHead(text: String): Boolean = hasSaudiKnownTitle(text) || hasEgyptianKnownHead(text)
}
