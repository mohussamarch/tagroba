package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * رسايل البنك اللي **مش عملية** — مشتركة بين قارئ السعودية وقارئ مصر وفلتر الجهاز (`SmsSafety` بيرميها قبل الحفظ).
 * الأنماط الأولانية في كل قايمة هي نفس أنماط التطبيق الحالي بالظبط (ملف المرجع `golden/sms.json` بيمسكها)، والباقي من أشكال
 * البحث (ملفات `sms-formats` في `research/banks`): رمز تحقق فيه مبلغ ومحل · حجز وتفويض · طلب لسه ما اتنفذش · مرفوض · رصيد مش كفاية · معلومة.
 */

private val GI = setOf(RegexOption.IGNORE_CASE)

/** عرض أو حركة جاية — نفس التطبيق الحالي. */
internal val SMS_OFFER_PATTERN = Regex("عرض|سيتم|عرض خاص|offer|will be|scheduled", GI)

/**
 * رمز تحقق أو كلمة سر. الإضافات: «كلمة مرور» من غير «ال» (الراجحي) · «رمز مؤقت» و«رمز:123456» (الراجحي) · «رمز شراء أونلاين»
 * (الإنماء — شكلها شراء بالظبط) · «الرقم السري» (الأهلي السعودي والتجاري الدولي وفودافون كاش — فيها مبلغ ومحل أحيانًا) · security code.
 */
internal val SMS_SENSITIVE_PATTERN = Regex(
    "${B}OTP$B|verification$S*code|one.time$S*(?:password|code)|رمز$S*(?:التحقق|التوثيق|التفعيل|الدخول)|كلمة$S*(?:المرور|السر)|" +
        "مشاركة${S}*الرمز|الرمز$S*[:：]?$S*\\d{4,8}" +
        "|كلمة$S*مرور|رمز$S*(?:مؤقت|شراء)|رمز$S*[:：]$S*\\d{4,8}|الرقم$S*السري|security$S*code",
    GI,
)

/**
 * مرفوضة أو رصيد مش كفاية. الإضافات: «لم يتم تنفيذ» · «تم رفض المعاملة» (التجاري الدولي) · «رصيد غير كافي» · «لا يكفي» (الإنماء) ·
 * «لا يوجد رصيد كاف» (فودافون كاش) · «عدم كفاية» · Insufficient balance (إس تي سي — كانت بتتسجل شراء).
 */
internal val SMS_DECLINED_PATTERN = Regex(
    "مرفوض|رفض العملية|لم تتم|غير ناجح|declined|failed|unsuccessful" +
        "|لم$S*يتم|رفض$S*المعاملة|تم$S*رفض|insufficient|رصيد$S*غير$S*كا[فٍ]|لا$S*يكفي|لا$S*يوجد$S*رصيد|عدم$S*(?:وجود$S*رصيد|كفاية)",
    GI,
)

/**
 * مش حركة فلوس خلصت: تفويض/حجز (الخصم الحقيقي بييجي بعدين في رسالة تانية) · طلب استرداد أو طلب سحب لسه مستني · تحويل شراء قديم
 * لأقساط (مش صرف جديد) · كشف حساب البطاقة · دخول للتطبيق · تفعيل بطاقة · رقم سري اتعمل · رسالة رصيد بس · جايزة لازم تتطلب.
 */
private val NOT_TRANSACTION_ANYWHERE = Regex(
    "حجز$S*مبلغ|Cash$S*Re(?:serve|lease)|تم$S*استلام$S*الطلب|تم$S*طلب|تم$S*تقسيط|كشف$S*حساب|الحد$S*الأدنى$S*للسداد|" +
        "تسجيل$S*الدخول|logged$S*in|تم$S*تفعيل|${B}PIN$B[^\\n]*${B}SET$B|مبروك$S*كسبت",
    GI,
)

/** سطر بيبدأ بـ«تفويض» = حجز مبلغ لشراء إنترنت. «خصم من التفويض» (الخصم الحقيقي) ما بيتلمسش. */
private val HOLD_TITLE = Regex("(?:^|\\n)$S*تفويض")

/** الرسالة كلها رصيد: «رصيد حسابك فى فودافون كاش الحالي…» من غير حركة قبلها. */
private val BALANCE_ONLY = Regex("^$S*(?:رصيد$S*حسابك|Your$S*current$S*Vodafone$S*Cash$S*balance)", GI)

internal fun isNotATransaction(body: String): Boolean =
    NOT_TRANSACTION_ANYWHERE.containsMatchIn(body) || HOLD_TITLE.containsMatchIn(body) || BALANCE_ONLY.containsMatchIn(body)

/** سبب تجاهل الرسالة أو null — الترتيب نفس التطبيق الحالي (عرض ⇒ رمز ⇒ مرفوض) وبعدهم «مش عملية». */
internal fun smsIgnoreReason(body: String): TextKey? = when {
    SMS_OFFER_PATTERN.containsMatchIn(body) -> TextKey.SMS_OFFER
    SMS_SENSITIVE_PATTERN.containsMatchIn(body) -> TextKey.SMS_SENSITIVE
    SMS_DECLINED_PATTERN.containsMatchIn(body) -> TextKey.SMS_DECLINED
    isNotATransaction(body) -> TextKey.SMS_NOT_TRANSACTION
    else -> null
}

/** نص الرسالة زي ما القارئ بيشوفه: أرقام لاتيني، من غير `\r` ولا علامات الاتجاه المخفية. */
internal fun normalizeSmsBody(body: String): String = latinizeDigits(body).filterNot { it == '\r' || it.code in BIDI_CODES }

/**
 * فلتر الجهاز (`SmsSafety`): الرسالة تتحفظ بس لو فيها كلمة حركة فلوس وعملة — وما تبقاش رمز ولا عرض ولا مرفوضة ولا «مش عملية».
 * كلمات الحركة: كلمات التطبيق الحالي + كلمات أشكال البحث (استرجاع · عكس عملية · كاش باك · مشتريات · عملية انترنت · قيد · استلام ·
 * إضافة · رد · شحن · charged · credited · received · deducted · recharged · reversal · fees · Trx …).
 */
object SmsVocabulary {
    private val MOVEMENT = Regex(
        "شراء|سحب|خصم|سداد|مدفوعات|دفع|حوالة|تحويل|إيداع|ايداع|راتب|استرداد|مرتجع|purchase|withdrawal|transfer|deposit|refund|salary|payment|transaction" +
            "|استرجاع|إرجاع|ارجاع|عكس|كاش$S*باك|مشتريات|انترنت|إنترنت|شيك|تسوية|تسديد|تم$S*(?:قيد|استلام|إضافة|اضافة|رد|شحن)|" +
            "charged|credited|debited|received|deducted|recharged|refunded|returned|reversal|cashback|cheque|settlement|fees?|Trx|adding$S*money|top.?up",
        GI,
    )

    /** العملات: ريال سعودي وجنيه مصري بكل كتاباتهم + العملات الأجنبية (عشان الرسالة تظهر بسبب رفضها بدل ما تختفي). */
    const val CURRENCY: String =
        "(?:(?<![A-Za-z])(?:SAR|SR|USD|EUR|GBP|AED|EGP|KWD|BHD|QAR|OMR|JOD)(?![A-Za-z])|ريال|ر\\.?س\\.?|دولار|يورو|جنيه|جنية|" +
            "ج\\.م\\.?|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.])|(?<![A-Za-z])L\\.?E(?![A-Za-z]))"

    private val MONEY = Regex(CURRENCY, GI)

    fun hasMovement(text: String): Boolean = MOVEMENT.containsMatchIn(text)

    fun hasMoney(text: String): Boolean = MONEY.containsMatchIn(text)

    /** ليه الرسالة دي **ما تتحفظش** (رمز · عرض · مرفوضة · مش عملية)، أو null. */
    fun ignoreReason(text: String): TextKey? = smsIgnoreReason(normalizeSmsBody(text))

    fun ignoreBeforeStorage(text: String): Boolean = ignoreReason(text) != null
}
