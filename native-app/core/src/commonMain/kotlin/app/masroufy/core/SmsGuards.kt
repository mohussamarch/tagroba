package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * رسايل البنك اللي **مش عملية** — مشتركة بين قارئ السعودية وقارئ مصر وفلتر الجهاز (`SmsSafety` بيرميها قبل الحفظ).
 * الأنماط الأولانية في كل قايمة هي نفس أنماط التطبيق الحالي بالظبط (ملف المرجع `golden/sms.json` بيمسكها)، والباقي من أشكال
 * البحث (ملفات `sms-formats` في `research/banks`): رمز تحقق فيه مبلغ ومحل · حجز وتفويض · طلب لسه ما اتنفذش · مرفوض · رصيد مش كفاية · معلومة.
 * الجولة التانية من المراجعة (رسايل مخترعة عدائية، `SmsAdversarialTest`): صيغ تانية لنفس الأنواع — كل واحدة مكتوب جنبها.
 */

private val GI = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"

/**
 * عرض أو حركة جاية — نفس التطبيق الحالي. مراجعة جلسة 33: عروض إنجليزي من غير كلمة «offer» («Shop now … pay over 12 months» ·
 * «cashback when you pay with your debit card»). الجولة التانية: «احصل على» · «استمتع» · «يصل إلى» · «Earn SAR 25» ·
 * «next purchase» · «valid till» · «Enjoy 20% off» · «خصم 15%» · تحويل شراء لأقساط («Convert your purchase … into 12 installments» ·
 * «قسّط مشترياتك» · «Reply YES»).
 */
internal val SMS_OFFER_PATTERN = Regex(
    "عرض|سيتم|عرض خاص|offer|will be|scheduled" + "|shop$S+now|when$S+you$S+(?:pay|shop|spend|use)|pay$S+over$S+\\d" +
        "|احصل|استمتع|يصل$S*(?:إلى|الى)|${B}earn$S+(?:up$S+to$S+)?(?:SAR|SR|EGP|\\d)|next$S+purchase|valid$S+(?:till|until|thru)" +
        "|${B}enjoy$B[^\\n]{0,30}(?:\\d$S*[%٪]|${B}off$B|discount|cashback|خصم)" +
        "|\\d$S*[%٪]$S*(?:off|discount|cashback|خصم|كاش)|(?:خصم|discount|cashback)$S*(?:يصل$S*(?:إلى|الى)$S*)?\\d+(?:[.٫]\\d+)?$S*[%٪]" +
        "|convert$S+(?:your$S+)?(?:purchase|transaction|spend)|into$S+\\d+$S+(?:(?:easy|monthly)$S+)*installments?|easy$S+installments?" +
        "|reply$S+(?:yes|نعم)|قس[ّ]?ط$S*مشتريات|تقسيط$S*مشتريات|حو[ّ]?ل$S*مشتريات",
    GI,
)

/**
 * رمز تحقق أو كلمة سر. الإضافات: «كلمة مرور» من غير «ال» (الراجحي) · «رمز مؤقت» و«رمز:123456» (الراجحي) · «رمز شراء أونلاين»
 * (الإنماء — شكلها شراء بالظبط) · «الرقم السري» (الأهلي السعودي والتجاري الدولي وفودافون كاش — فيها مبلغ ومحل أحيانًا) · security code.
 * مراجعة جلسة 33: one-time PIN · verification PIN · passcode · «كود التحقق» · «code 482913»/«PIN 4829» (رقم جنب الكلمة).
 * الجولة التانية: «رمز التأكيد/الأمان/السري» · «الرمز السري» · confirmation/authentication code · «OTP482913» لازق في الرقم.
 * «كود العملية» (أورانج كاش — رقم العملية) مش رمز.
 */
internal val SMS_SENSITIVE_PATTERN = Regex(
    "(?<![A-Za-z])OTP(?![A-Za-z])|verification$S*code|one.time$S*(?:password|code)|رمز$S*(?:التحقق|التوثيق|التفعيل|الدخول)|كلمة$S*(?:المرور|السر)|" +
        "مشاركة${S}*الرمز|الرمز$S*[:：]?$S*\\d{4,8}" +
        "|كلمة$S*مرور|رمز$S*(?:مؤقت|شراء)|رمز$S*[:：]$S*\\d{4,8}|الرقم$S*السري|security$S*code" +
        "|one.?time$S*(?:PIN|passcode)|verification$S*PIN|passcode|كود$S*(?:ال)?(?:تحقق|تفعيل|تأكيد|أمان)" +
        "|$B(?:PIN|code)$B$S*(?:is$S*)?[:：]?$S*\\d{4,8}(?!\\d)" +
        "|رمز$S*(?:ال)?(?:تأكيد|أمان|امان|سري)|الرمز$S*السري|(?:confirmation|authentication|security)$S*(?:code|PIN)",
    GI,
)

/**
 * مرفوضة أو رصيد مش كفاية. الإضافات: «لم يتم تنفيذ» · «تم رفض المعاملة» (التجاري الدولي) · «رصيد غير كافي» · «لا يكفي» (الإنماء) ·
 * «لا يوجد رصيد كاف» (فودافون كاش) · «عدم كفاية» · Insufficient balance (إس تي سي — كانت بتتسجل شراء).
 * مراجعة جلسة 33: «was rejected» · denied · «could not be completed». الجولة التانية: «تعذر» · «فشلت» · «رفضت» · «لا يسمح» ·
 * «not approved». «Cancelled»/«إلغاء»/«ملغاة» في [CANCELLED] (لو مفيش استرداد).
 */
internal val SMS_DECLINED_PATTERN = Regex(
    "مرفوض|رفض العملية|لم تتم|غير ناجح|declined|failed|unsuccessful" +
        "|لم$S*يتم|رفض$S*المعاملة|تم$S*رفض|insufficient|رصيد$S*غير$S*كا[فٍ]|لا$S*يكفي|لا$S*يوجد$S*رصيد|عدم$S*(?:وجود$S*رصيد|كفاية)" +
        "|${B}rejected$B|${B}denied$B|could$S*not$S*be$S*(?:completed|processed)" +
        "|تعذر|فشل|رفضت|لا$S*يسمح|${B}not$S+(?:been$S+)?approved$B",
    GI,
)

/** عملية اتلغت: مرفوضة **لو** الرسالة مفيهاش استرداد (استرداد طلب اتلغى = فلوس راجعة حقيقية — بتعدّي للقارئ). */
private val CANCELLED = Regex("${B}cancel(?:l?ed|lation)$B|${B}void(?:ed)?$B|(?<![$AR])(?:إلغاء|الغاء|ملغا[ةه]|ملغي[ةه]?|ملغى)(?![$AR])", GI)
private val REFUND_WORD = Regex("استرداد|استرجاع|مرتجع|${B}refund", GI)

/**
 * مش حركة فلوس خلصت: تفويض/حجز (الخصم الحقيقي بييجي بعدين في رسالة تانية) · طلب استرداد أو طلب سحب لسه مستني · تحويل شراء قديم
 * لأقساط (مش صرف جديد) · كشف حساب البطاقة · دخول للتطبيق · تفعيل بطاقة · رقم سري اتعمل · رسالة رصيد بس · جايزة لازم تتطلب.
 * مراجعة جلسة 33: pending · «معلقة» · under review · «قيد المراجعة» · «تم استلام طلبك/طلب سحب» · refund request.
 * الجولة التانية: authorization/pre-authorization · «تم حجز» · on hold · «طلب استرداد» · «اعتراض» · dispute · chargeback ·
 * تذكير وميعاد سداد («المستحق للسداد» · «جاهزة للسداد» · «is due» · «due on/by» · statement balance · minimum payment).
 */
private val NOT_TRANSACTION_ANYWHERE = Regex(
    "حجز$S*مبلغ|Cash$S*Re(?:serve|lease)|تم$S*استلام$S*الطلب|تم$S*طلب|تم$S*تقسيط|كشف$S*حساب|الحد$S*الأدنى$S*للسداد|" +
        "تسجيل$S*الدخول|logged$S*in|تم$S*تفعيل|${B}PIN$B[^\\n]*${B}SET$B|مبروك$S*كسبت" +
        "|${B}pending$B|معلق(?:ة|ه)?(?![$AR])|under$S*review|قيد$S*(?:المراجعة|الانتظار|التنفيذ)|تم$S*استلام$S*طلب" +
        "|authori[sz]|تم$S*حجز|حجز$S*مؤقت|${B}on$S+hold$B|${B}hold$S+(?:of|on|amount)$B" +
        "|طلب$S*(?:استرداد|استرجاع|اعتراض)|اعتراض|${B}disputed?$B|chargeback" +
        "|${B}refund$S+request$B|request(?:ed)?$S+(?:a$S+|for$S+(?:a$S+)?)?(?:refund|chargeback)|(?:withdrawal|cash.?out)$S+request" +
        "|تذكير|المستحق$S*للسداد|مستحق[ةه]?$S*(?:السداد|الدفع)|جاهز[ةه]?$S*للسداد|${B}(?:is|are)$S+due$B|${B}due$S+(?:on|by|date)$B" +
        "|payment$S+due|${B}due$B$S*[:：]?$S*\\d|statement$S+(?:balance|amount)|minimum$S+(?:payment|due|amount)",
    GI,
)

/** «تفويض» في أي مكان = حجز مبلغ لشراء إنترنت. «خصم من التفويض» (الراجحي — الخصم الحقيقي) ما بيتلمسش. */
private val HOLD_WORD = Regex("تفويض")
private val DEBIT_FROM_HOLD = Regex("خصم$S*من$S*(?:ال)?تفويض")

/**
 * الرسالة كلها رصيد: «رصيد حسابك فى فودافون كاش الحالي…» من غير حركة قبلها. الجولة التانية: بتبدأ بـ«رصيدك» · «الرصيد» لوحده
 * في أول سطر · «Your … balance» («Your available balance on account …» · «Your Vodafone Cash balance is …»).
 */
private val BALANCE_ONLY = Regex(
    "^$S*(?:رصيد$S*حسابك|Your$S*current$S*Vodafone$S*Cash$S*balance|رصيدك|(?:ال)?رصيد(?:$S*(?:ال)?(?:متاح|حالي))?[ \\t]*(?:[:：]|\\n|$)" +
        "|Your$S+(?:[A-Za-z]+$S+){0,3}balance$B)",
    GI,
)

internal fun isNotATransaction(body: String): Boolean =
    NOT_TRANSACTION_ANYWHERE.containsMatchIn(body) || BALANCE_ONLY.containsMatchIn(body) ||
        (HOLD_WORD.containsMatchIn(body) && !DEBIT_FROM_HOLD.containsMatchIn(body))

private fun cancelled(body: String) = CANCELLED.containsMatchIn(body) && !REFUND_WORD.containsMatchIn(body)

/**
 * سبب تجاهل الرسالة أو null — الترتيب نفس التطبيق الحالي (عرض ⇒ رمز ⇒ مرفوض) وبعدهم «مش عملية» ⇒ اتلغت
 * («الغاء حجز مبلغ» = رجوع حجز، يعني «مش عملية» مش «مرفوضة»).
 */
internal fun smsIgnoreReason(body: String): TextKey? = when {
    SMS_OFFER_PATTERN.containsMatchIn(body) -> TextKey.SMS_OFFER
    SMS_SENSITIVE_PATTERN.containsMatchIn(body) -> TextKey.SMS_SENSITIVE
    SMS_DECLINED_PATTERN.containsMatchIn(body) -> TextKey.SMS_DECLINED
    isNotATransaction(body) -> TextKey.SMS_NOT_TRANSACTION
    cancelled(body) -> TextKey.SMS_DECLINED
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

    /** العملات: ريال سعودي وجنيه مصري بكل كتاباتهم + العملات الأجنبية (عشان الرسالة تظهر بسبب رفضها بدل ما تختفي) + دينار · درهم · ليرة. */
    const val CURRENCY: String =
        "(?:(?<![A-Za-z])(?:SAR|SR|USD|EUR|GBP|AED|EGP|KWD|BHD|QAR|OMR|JOD)(?![A-Za-z])|ريال|ر\\.?س\\.?|دولار|يورو|جنيه|جنية|دينار|درهم|ليرة|" +
            "ج\\.م\\.?|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.])|(?<![A-Za-z])L\\.?E(?![A-Za-z]))"

    private val MONEY = Regex(CURRENCY, GI)

    fun hasMovement(text: String): Boolean = MOVEMENT.containsMatchIn(text)

    /** عملة معروفة، أو أي كود عملة أجنبية جنب مبلغ («TRY 450.00» — كانت بتترمي في صمت قبل ما تتسأل عن مبلغها المحلي §75-12). */
    fun hasMoney(text: String): Boolean = MONEY.containsMatchIn(text) || isoMoneyIn(text).isNotEmpty()

    /** أماكن المبالغ بكود عملة أجنبية — فلتر الجهاز ما بيحجبهاش («JPY 45000» مش رقم حساب). */
    fun foreignMoneyRanges(text: String): List<IntRange> = isoMoneyIn(text).map { it.range }

    /** ليه الرسالة دي **ما تتحفظش** (رمز · عرض · مرفوضة · مش عملية)، أو null. */
    fun ignoreReason(text: String): TextKey? = smsIgnoreReason(normalizeSmsBody(text))

    fun ignoreBeforeStorage(text: String): Boolean = ignoreReason(text) != null
}
