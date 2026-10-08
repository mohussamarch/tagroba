package app.masroufy.core

import app.masroufy.core.JsText.S

/**
 * فلتر الجهاز (`SmsSafety`): الرسالة تتحفظ بس لو فيها كلمة حركة فلوس وعملة — وما تبقاش رمز ولا عرض ولا مرفوضة ولا «مش عملية».
 * كلمات الحركة: كلمات التطبيق الحالي + كلمات أشكال البحث (استرجاع · عكس عملية · كاش باك · مشتريات · عملية انترنت · قيد · استلام ·
 * إضافة · رد · شحن · charged · credited · received · deducted · recharged · reversal · fees · Trx …).
 * (كان جوه `SmsGuards.kt` — اتنقل لملف لوحده في الجولة التالتة عشان حد الـ300 سطر.)
 */
object SmsVocabulary {
    private val GI = setOf(RegexOption.IGNORE_CASE)

    private val MOVEMENT = Regex(
        "شراء|سحب|خصم|سداد|مدفوعات|دفع|حوالة|تحويل|إيداع|ايداع|راتب|استرداد|مرتجع|purchase|withdrawal|transfer|deposit|refund|salary|payment|transaction" +
            "|استرجاع|إرجاع|ارجاع|عكس|كاش$S*باك|مشتريات|انترنت|إنترنت|شيك|تسوية|تسديد|تم$S*(?:قيد|استلام|إضافة|اضافة|رد|شحن)|" +
            "charged|credited|debited|received|deducted|recharged|refunded|returned|reversal|cashback|cheque|settlement|fees?|Trx|adding$S*money|top.?up",
        GI,
    )

    /** العملات: ريال سعودي وجنيه مصري بكل كتاباتهم + العملات الأجنبية (عشان الرسالة تظهر بسبب رفضها بدل ما تختفي) + دينار · درهم · ليرة. */
    const val CURRENCY: String =
        "(?:(?<![A-Za-z])(?:SAR|SR|USD|EUR|GBP|AED|EGP|KWD|BHD|QAR|OMR|JOD)(?![A-Za-z])|ريال|ر\\.?س\\.?|دولار|يورو|جنيه|جنية|دينار|درهم|ليرة|" +
            "ج\\.م\\.?|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.])|(?<![A-Za-z])L\\.?E(?![A-Za-z]))"

    private val MONEY = Regex(CURRENCY, GI)

    fun hasMovement(text: String): Boolean = MOVEMENT.containsMatchIn(text)

    /**
     * عملة معروفة، أو أي كود عملة أجنبية جنب مبلغ («TRY 450.00» — كانت بتترمي في صمت قبل ما تتسأل عن مبلغها المحلي §75-12)،
     * أو رمز/اسم عملة جنب مبلغ («$23.40» · «4500 ين» · «12.50 Swiss Francs» — الجولة التالتة).
     */
    fun hasMoney(text: String): Boolean = MONEY.containsMatchIn(text) || isoMoneyIn(text).isNotEmpty() || namedMoneyIn(text).isNotEmpty()

    /**
     * أماكن المبالغ بكود أو رمز أو اسم عملة أجنبية — فلتر الجهاز ما بيحجبهاش («JPY 45000» · «$12500» مش رقم حساب). الكود جنب رقم
     * صحيح بيتساب **حتى من غير كسور** (الجولة التالتة): «JPY 45000 (SAR 112.50)» كانت بتتحفظ «JPY ••••5000» فالقارئ ما يشوفش
     * المبلغ الأجنبي ويسجّلها بالمقابل المحلي.
     */
    fun foreignMoneyRanges(text: String): List<IntRange> = (isoMoneyIn(text, relaxed = true) + namedMoneyIn(text)).map { it.range }

    /** ليه الرسالة دي **ما تتحفظش** (رمز · عرض · مرفوضة · مش عملية)، أو null. */
    fun ignoreReason(text: String): TextKey? = smsIgnoreReason(normalizeSmsBody(text))

    fun ignoreBeforeStorage(text: String): Boolean = ignoreReason(text) != null
}
