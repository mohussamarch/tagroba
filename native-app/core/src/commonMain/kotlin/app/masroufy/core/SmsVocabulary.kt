package app.masroufy.core

import app.masroufy.core.JsText.S

/**
 * فلتر الجهاز (`SmsSafety`). **الجولة الخامسة (قاعدة المالك §72: الانتظار مقبول، ضياع عملية حقيقية مش مقبول):** الرسالة بتترمي قبل
 * الحفظ **بسبب صريح بس** — رمز تحقق (دايمًا) · عرض/مرفوضة/«مش عملية» **إلا** لو أولها عنوان أو قالب بنك معروف (ساعتها بتتحفظ والقارئ
 * بيرفضها بسببها فتستنى في «المرفوضة» — «لعرض تفاصيل العملية» أو «See latest offers» تحت شراء حقيقي كانوا بيرموه) · مفيهاش مبلغ جنبه
 * عملة. **كلمة الحركة ما بقتش شرط** («تمت إضافة» · «تم استقطاع» · «استلمت» · «You paid» · «withdrawn» · «وصلك» كانوا بيضيعوا في صمت):
 * أي رسالة من بنك مفعّل فيها مبلغ بتتحفظ، والقارئ يرفض اللي مش فاهمه فيستنى المالك.
 * **الجولة السادسة:** الحارس بيتفحص على **أول** الرسالة بس (سطر إعلان أو تنبيه في آخر عملية حقيقية بشكل مش معروف ما بيرميهاش)،
 * والرمز بيترمي لو فيه رمز فعلًا (جملة «لا تشارك الرقم السري» من غير رقم مش رمز) — [ignoreBeforeStorage].
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
            "ج\\.م\\.?|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.])|(?<![A-Za-z])L\\.?E(?![A-Za-z])" +
            // الجولة السادسة: عملات اسمها أو اختصارها ما كانش هنا (الشراء كان بيترمي «مفيهاش مبلغ» بدل ما يستنى §75-12)
            "|(?<![\\u0600-\\u06FF])(?:بات|وون|بيزو|كرون[ةه]|كرونا|شيكل|دونغ|دونج)(?![\\u0600-\\u06FF])|(?<![A-Za-z])(?:Ft|Kč|L\\.L)(?![A-Za-z])|ل\\.ل|ل\\.س)"

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
    fun hasAmount(text: String): Boolean =
        (hasMoney(text) && text.any { it in '0'..'9' }) || (AMOUNT_LABEL_NUMBER.containsMatchIn(text) && hasKnownHead(normalizeSmsBody(text)))

    /**
     * الجولة السادسة: تحت عنوان أو قالب بنك معروف، رقم بعد «مبلغ/Amount» ومعاه كلمة عملة ما نعرفهاش لسه («مبلغ: 1,500.00 بات») =
     * عملية بعملة مش في القايمة — بتتحفظ وتستنى (القارئ بيرفضها) بدل ما تترمي «مفيهاش مبلغ».
     */
    private val AMOUNT_LABEL_NUMBER = Regex("(?:بمبلغ|المبلغ|مبلغ|${JsText.B}amount)$S*[:：]?$S*\\d", GI)

    /**
     * الجولة السادسة: «خلصت» في أول الرسالة — عرض في نفس الجملة («تمت عملية شراء … ، سيتم إضافة النقاط») ما بيرميهاش.
     * «سيتم/هيتم» مش «تم»، و«will be credited» مش «credited».
     */
    private val COMPLETED = Regex(
        "(?<![\\u0600-\\u06FF])و?تمت?(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])(?:اتخصم|اتحول|اتسحب|استلمت|وصلك|أضيف|اضيف)(?![\\u0600-\\u06FF])" +
            "|${JsText.B}(?:has|have)$S+been${JsText.B}|${JsText.B}was$S+(?:successful|completed)${JsText.B}" +
            "|(?<!(?:will|to|shall|would)$S{1,3}be$S{1,3})${JsText.B}(?:credited|debited|charged|spent|withdrawn|received|sent|paid|deducted|deposited|transferred|refunded)${JsText.B}",
        GI,
    )

    /**
     * أماكن المبالغ بكود أو رمز أو اسم عملة أجنبية — فلتر الجهاز ما بيحجبهاش («JPY 45000» · «$12500» مش رقم حساب). الكود جنب رقم
     * صحيح بيتساب **حتى من غير كسور** (الجولة التالتة): «JPY 45000 (SAR 112.50)» كانت بتتحفظ «JPY ••••5000» فالقارئ ما يشوفش
     * المبلغ الأجنبي ويسجّلها بالمقابل المحلي.
     */
    fun foreignMoneyRanges(text: String): List<IntRange> = (isoMoneyIn(text, relaxed = true) + namedMoneyIn(text)).map { it.range }

    /** سبب الحارس (رمز · عرض · مرفوضة · مش عملية)، أو null — القارئ بيرفض بيه. */
    fun ignoreReason(text: String): TextKey? = smsIgnoreReason(normalizeSmsBody(text))

    /**
     * الرسالة دي **ما تتحفظش**: رمز تحقق **فعلًا** (جملة «لا تشارك الرمز/Never share your OTP» من غير رقم مش رمز — الجولة السادسة) ·
     * أو حارس تاني (عرض · مرفوضة · مش عملية) **في أولها** ([headOf]) وأولها مش عنوان أو قالب بنك معروف.
     * الجولة السادسة: الحارس اللي مسك **آخر** الرسالة بس (إعلان أو تنبيه تحت عملية حقيقية بشكل مش معروف — «You will earn 23 points» ·
     * «سيتم خصم قسط التمويل بتاريخ …» · «Next bill due on …») ما بيرميهاش: بتتحفظ والقارئ يرفضها فتستنى (§72: الضياع مش مقبول).
     * والعرض في أولها مع «تم/has been/credited…» في نفس الجملة («تمت عملية شراء … ، سيتم إضافة النقاط») ما بيرميهاش برضه.
     */
    fun ignoreBeforeStorage(text: String): Boolean {
        val body = normalizeSmsBody(text)
        if (isSensitiveText(guardText(body))) return true
        if (smsIgnoreReason(body) == null || hasKnownHead(body)) return false
        val head = headOf(body)
        val headReason = smsIgnoreReason(head) ?: return false
        return !(headReason == TextKey.SMS_OFFER && guardReasonBesidesOffer(head) == null && COMPLETED.containsMatchIn(guardText(head)))
    }

    /** أول الرسالة عنوان سعودي معروف (موحّد أو قالب بنك) أو أول جملة قالب مصري معروف. */
    fun hasKnownHead(text: String): Boolean = hasSaudiKnownTitle(text) || hasEgyptianKnownHead(text)
}
