package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * رسايل البنك اللي **مش عملية** — مشتركة بين قارئ السعودية وقارئ مصر وفلتر الجهاز (`SmsSafety` بيرميها قبل الحفظ).
 * الأنماط الأولانية في كل قايمة هي نفس أنماط التطبيق الحالي بالظبط (ملف المرجع `golden/sms.json` بيمسكها)، والباقي من أشكال
 * البحث (ملفات `sms-formats` في `research/banks`): رمز تحقق فيه مبلغ ومحل · حجز وتفويض · طلب لسه ما اتنفذش · مرفوض · رصيد مش كفاية · معلومة.
 * الجولة التانية من المراجعة (رسايل مخترعة عدائية، `SmsAdversarialTest`): صيغ تانية لنفس الأنواع — كل واحدة مكتوب جنبها.
 * **الجولة التالتة:** صيغ تانية (رمز بـ«ال» أو «ك» أو تطويل · حجز · خصم جاي أو منفي · محاولة · عروض «اربح/win/spend») — **ومن غير ما
 * تمسك سطر تحذير في آخر رسالة حقيقية** («للاعتراض … اتصل» · «If you have not authorized …» · «الغاء الخدمة» · «استمتع بخدماتنا»):
 * الكلام العام زي «تذكير» و«اعتراض» والإلغاء بقى بيتفحص في **أول الرسالة** بس ([headOf])، والتفويض والعرض بعبارتهم مش بالكلمة لوحدها
 * (جدول `SmsAdversarialCases.mustBook` بيمسك ده).
 */

private val GI = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"

/** أول كلمة عربية (مش جوه كلمة تانية: «للاعتراض» مش «اعتراض») وآخرها. */
private const val NA = "(?<![$AR])"
private const val NZ = "(?![$AR])"

/** فعل حركة فلوس بعد «سوف يتم» / «هيتم» / «لن يتم» (من غيره الجملة ممكن تبقى سطر تحذير: «سوف يتم التواصل معك»). */
private const val MONEY_VERB = "(?:ال)?(?:خصم|سحب|تحصيل|سداد|تسديد|تحويل|قيد|إيداع|ايداع|دفع|استقطاع|إضافة|اضافة|شحن|رد|استرداد|تنفيذ)"

/** «إذا لم تتم العملية بواسطتك …» = سطر تحذير في آخر رسالة حقيقية، مش عملية ما تمتش. */
private const val IF_NOT = "(?<!(?:إذا|اذا|إن|حال)[ \\t]{1,3})"

/** مش منفية: «Not attempted purchase?» · «No authorization …» · «never» · «didn't» · «unauthorized» = جملة تحذير، مش العملية نفسها. */
private const val NEG = "(?<!(?:\\bnot|\\bnever|\\bno|n't)[ \\t]{1,3})(?<![Uu]n)"

/**
 * عرض أو حركة جاية — نفس التطبيق الحالي («عرض» بقت كلمة لوحدها: «معرض» محل مش عرض). مراجعة جلسة 33: عروض إنجليزي من غير كلمة
 * «offer». الجولة التانية: «احصل على» · «استمتع» · «يصل إلى» · «Earn SAR 25» · «next purchase» · «valid till» · «Enjoy 20% off» ·
 * «خصم 15%» · تحويل شراء لأقساط. الجولة التالتة: «احصل»/«استمتع» **جنب عرض** بس («استمتع بخدماتنا» تحت رسالة حقيقية مش عرض) ·
 * «اربح/اكسب … جايزة» · «ابتداءً من» · «win/spend SAR» · «Get SAR 50 back» · campaign · نقاط مضاعفة · خصم جاي («سوف يتم» · «هيتم» ·
 * «سيخصم» · «to be debited» · upcoming).
 */
internal val SMS_OFFER_PATTERN = Regex(
    // الجولة الخامسة: «لعرض تفاصيل العملية» مش عرض · «scheduled transfer … executed successfully» خلص · «هدية … صالحة لمدة» عرض
    "$NA(?:[وف])?(?:بال|لل|ال|ب|ل)?عرض(?!$S*(?:ال)?(?:تفاصيل|رصيد|كشف|حساب))|سيتم|عرض خاص|offer|will be" +
        "|scheduled(?![^\\n]{0,100}?${B}(?:executed|completed|processed)$S+successfully$B)|${NA}هدي[ةه][^\\n]{0,40}صالح" +
        "|shop$S+now|when$S+you$S+(?:pay|shop|spend|use)|pay$S+over$S+\\d" +
        "|${NA}و?احصل$S*(?:على|علي)?$S*(?:\\d|خصم|كاش|استرداد|نقاط|هدي|مكافأ|مكافا|جائز|عرض|ضعف|مضاعف|قسيم|كوبون)" +
        "|${NA}استمتع$S*(?:ب)?(?:ال)?(?:\\d|خصم|عرض|كاش|استرداد|نقاط|تقسيط|هدي)" +
        "|يصل$S*(?:إلى|الى)|${B}earn$S+(?:up$S+to$S+)?(?:SAR|SR|EGP|\\d)|next$S+purchase|valid$S+(?:till|until|thru)" +
        "|${B}enjoy$B[^\\n]{0,30}(?:\\d$S*[%٪]|${B}off$B|discount|cashback|خصم)" +
        "|\\d$S*[%٪]$S*(?:off|discount|cashback|خصم|كاش)|(?:خصم|discount|cashback)$S*(?:يصل$S*(?:إلى|الى)$S*)?\\d+(?:[.٫]\\d+)?$S*[%٪]" +
        "|convert$S+(?:your$S+)?(?:purchase|transaction|spend)|into$S+\\d+$S+(?:(?:easy|monthly)$S+)*installments?|easy$S+installments?" +
        "|reply$S+(?:yes|نعم)|قس[ّ]?ط$S*مشتريات|تقسيط$S*مشتريات|حو[ّ]?ل$S*مشتريات" +
        "|$NA(?:اربح|إربح|اكسب|إكسب)$NZ[^\\n]{0,30}(?:\\d|جائز|جوائز|نقاط|هدي|كاش|استرداد|ضعف|مضاعف)|${NA}ابتداء|خلال$S*الفترة" +
        "|${B}win$S+(?:SAR|SR|EGP|up$S+to|an?$S|double|\\d)|${B}spend$S+(?:SAR|SR|EGP|over|more|\\d)" +
        "|${B}get$S+(?:SAR|SR|EGP|\\d|up$S+to)[^\\n]{0,25}?${B}back$B|${B}campaign$B|(?:double|triple|bonus|extra)$S+points" +
        "|${NA}سوف$S*(?:يتم|تتم)$S*$MONEY_VERB|$NA(?:هيتم|هتتم|حيتم)$S*$MONEY_VERB|$NA(?:سوف$S*)?[سه][يت]ت?(?:خصم|سحب|حول|قيد|سدد|دفع|حصل)$NZ" +
        "|${B}to$S+be$S+(?:debited|charged|deducted|paid|collected|withdrawn)$B|${B}upcoming$S+(?:payment|debit|deduction|installment|bill|charge)",
    GI,
)

/**
 * رمز تحقق أو كلمة سر. الإضافات: «كلمة مرور» من غير «ال» (الراجحي) · «رمز مؤقت» و«رمز:123456» (الراجحي) · «رمز شراء أونلاين»
 * (الإنماء — شكلها شراء بالظبط) · «الرقم السري» (الأهلي السعودي والتجاري الدولي وفودافون كاش — فيها مبلغ ومحل أحيانًا) · security code.
 * مراجعة جلسة 33: one-time PIN · verification PIN · passcode · «كود التحقق» · «code 482913»/«PIN 4829» (رقم جنب الكلمة).
 * الجولة التانية: «رمز التأكيد/الأمان/السري» · «الرمز السري» · confirmation/authentication code · «OTP482913» لازق في الرقم.
 * الجولة التالتة: «ال» و«ك» على الرمز والكود والرقم في كل الصيغ («الرمز المؤقت» · «رمزك هو 482913» · «الكود 482913» · «رقم التحقق»
 * · «رمز الشراء») · التطويل («رمـز» — [guardText]) · «3D Secure» · «password for/is» · «Token 482913» · «Your code» · authenticate ·
 * «by entering 482913» · «أدخل 482913». «Auth code: 123456» و«Approval code» (رقم موافقة في رسالة شراء حقيقية) **مش** رمز.
 * «كود العملية» (أورانج كاش — رقم العملية) مش رمز.
 */
internal val SMS_SENSITIVE_PATTERN = Regex(
    "(?<![A-Za-z])OTP(?![A-Za-z])|verification$S*code|one.time$S*(?:password|code)|رمز$S*(?:التحقق|التوثيق|التفعيل|الدخول)|كلمة$S*(?:المرور|السر)|" +
        "مشاركة${S}*الرمز|الرمز$S*[:：]?$S*\\d{4,8}" +
        "|كلمة$S*مرور|رمز$S*(?:مؤقت|شراء)|رمز$S*[:：]$S*\\d{4,8}|الرقم$S*السري|security$S*code" +
        "|one.?time$S*(?:PIN|passcode)|verification$S*PIN|passcode|كود$S*(?:ال)?(?:تحقق|تفعيل|تأكيد|أمان)" +
        // الجولة السابعة: «Auth. Code» · «Appr Code» · «Ref. Code» · «Txn Code» · «Approval No.» رقم موافقة/مرجع في شراء حقيقي، مش رمز
        "|(?<!(?:auth|appr|approval|authori[sz]ation|merchant|branch|terminal|promo|transaction|txn|trx|ref|reference)\\.?[ \\t:#.]{1,3})${B}code$B$S*(?:is$S*)?[:：]?$S*\\d{4,8}(?!\\d)" +
        // الجولة السادسة: «Approval PIN: 553901» رمز (رقم الموافقة «Approval code» بس هو اللي مش رمز)
        "|(?<!(?:merchant|branch|terminal|promo|transaction|trx|ref|reference)[ \\t:]{1,2})${B}PIN$B$S*(?:is$S*)?[:：]?$S*\\d{4,8}(?!\\d)" +
        "|رمز$S*(?:ال)?(?:تأكيد|أمان|امان|سري)|الرمز$S*السري|(?:confirmation|authentication|security)$S*(?:code|PIN)" +
        "|$NA(?:ال)?(?:رمز|كود)ك?$S*(?:ال)?(?:تحقق|توثيق|تفعيل|دخول|تأكيد|تاكيد|أمان|امان|سري|مؤقت|شراء|مرور)$NZ" +
        "|$NA(?:ال)?رقم$S*(?:ال)?(?:تحقق|سري)$NZ|$NA(?:ال)?(?:رمز|كود)ك?$S*(?:هو$S*)?[:：]?$S*\\d{4,8}(?!\\d)" +
        "|3D$S*-?$S*Secure$S+(?:code|password|PIN|OTP)|${B}password$S+(?:for|is)$B|${B}token$B$S*[:：]?$S*(?:is$S*)?\\d{4,8}(?!\\d)" +
        "|${B}to$S+authenticate$B|${B}(?:your|the)$S+code$S+(?:for|is|to)$B|${B}(?:enter|entering)$S+(?:the$S+)?(?:code$S+|OTP$S+)?\\d{4,8}(?!\\d)" +
        "|$NA(?:أدخل|ادخل|بإدخال|إدخال|ادخال)$S*(?:ال)?(?:رمز|كود)?$S*\\d{4,8}(?!\\d)" +
        // ── الجولة الخامسة: «رمز لمرة واحدة 731905» · «الكود بتاعك 4829» · «أدخل الرمز المرسل 731905» · «رقمك السري المؤقت» ·
        // «Verification No.» · «one-time 731905» · «Use 731905 to confirm» · «731905 is your code» («كود العملية» لسه رقم العملية)
        // الجولة السابعة: «رمز/كود الموافقة 553120» · «رمز التفويض» = رقم موافقة في شراء حقيقي (زي «كود العملية»)، مش رمز
        "|(?:رمز|كود|كلمة$S*(?:ال)?(?:مرور|سر))[^\\n]{0,15}لمرة$S*واحدة" +
        "|$NA(?:ال)?(?:رمز|كود)ك?[ \\t]+(?!(?:ال)?(?:عملية|معاملة|موافقة|موافقه|تفويض|مرجع))(?:[^\\s\\d]+[ \\t]+){1,2}\\d{4,8}(?!\\d)" +
        "|$NA(?:أدخل|ادخل|بإدخال|إدخال|ادخال)$S*(?:ال)?(?:رمز|كود)[^\\n\\d]{0,20}\\d{4,8}(?!\\d)|$NA(?:ال)?رقمك$S*(?:ال)?(?:سري|تحقق)" +
        "|${B}verification$S*(?:no\\.?|number|num)(?![A-Za-z])|${B}one.?time(?:[ \\t]+[^\\s\\d]+){0,2}[ \\t]*[:：]?[ \\t]*\\d{4,8}(?!\\d)" +
        "|${B}use$S+\\d{4,8}$S+to$B|\\d{4,8}$S+is$S+your$S+(?:[A-Za-z]+$S+)?(?:code|OTP|PIN|password|passcode)$B" +
        // ── الجولة السادسة: «رقم التعريف المؤقت 662190» · «activation number 662190» · «Secure code for … : 662190» ──
        "|$NA(?:ال)?رقم$S*(?:ال)?تعريف$S*(?:ال)?(?:مؤقت|شخصي)|${B}activation$S+(?:number|code|no\\.?)(?![A-Za-z])|${B}secure$S+code$B" +
        // ── الجولة السابعة: «Verification: 551204» · «Password: 551204» · «أدخل: 551204» · «registration code for card *7739 is 551204» ·
        // «transaction PIN for InstaPay transfer … is 553320» ──
        "|${B}verification$S*[:：]$S*\\d{4,8}(?!\\d)|${B}password$S*[:：]|$NA(?:أدخل|ادخل)$S*[:：]$S*\\d{4,8}(?!\\d)" +
        "|${B}(?:code|PIN)$S+for$B[^\\n]{0,80}?${B}is$S*[:：]?$S*\\d{4,8}(?!\\d)",
    GI,
)

/** إلغاء في **أول الرسالة** ([headOf]) — وفي أي مكان بعبارته بس («Purchase Cancelled» · «إلغاء عملية» · «عملية ملغاة»). */
private val CANCEL_WORD = Regex("${B}cancel(?:l?ed|lation)$B|${B}void(?:ed)?$B|$NA(?:إلغاء|الغاء|ملغا[ةه]|ملغي[ةه]?|ملغى)$NZ", GI)
private val CANCEL_PHRASE = Regex(
    "${B}(?:purchase|transaction|payment|order|withdrawal|transfer)$S+(?:has$S+been$S+|was$S+|is$S+)?cancel(?:l?ed)$B" +
        "|${B}cancel(?:l?ed|lation)$S+(?:of$S+)?(?:your$S+|the$S+)?(?:purchase|transaction|payment|order)$B|${B}void(?:ed)?$B" +
        "|$NA(?:تم$S*)?(?:إلغاء|الغاء)$S*(?:ال)?(?:عملية|معاملة|شراء|طلب|دفع|تحويل|حوالة|سحب)" +
        "|$NA(?:ال)?(?:عملية|معاملة|شراء|حوالة|طلب)$S*(?:ال)?(?:ملغا[ةه]|ملغي[ةه]?|ملغى)$NZ",
    GI,
)
/**
 * فلوس راجعة مع الإلغاء. الجولة السابعة: «وإعادة مبلغ 212.00 ر.س إلى بطاقتك» · «وإرجاع 450.00 ر.س» · «تم رد مبلغ 89 جنيه» · «returned to
 * your wallet» · «credited back» — كانت بتترمي «مرفوضة» قبل الحفظ والاسترداد الحقيقي يضيع.
 */
private val REFUND_WORD = Regex(
    "استرداد|استرجاع|مرتجع|${B}refund|$NA[وف]?(?:إعادة|اعادة|إرجاع|ارجاع)$S*(?:ال)?(?:مبلغ|\\d)|$NA[وف]?(?:تم$S*)?رد$S*(?:ال)?مبلغ" +
        "|${NA}تم$S*رد$NZ|${B}credited$S+back$B|${B}(?:returned|reversed|credited)$S+to$S+your$B",
    GI,
)

private val GREETING = Regex("^(?:عزيزي|عميلنا|Dear)", GI)
private val SENTENCE_END = Regex("[.!؟?](?:$S|$)")

/**
 * أول الرسالة: أول سطر فيه كلام **والسطر اللي بعده** (الجولة الخامسة: في رسايل السعودية أول سطر = العنوان، و«سداد فاتورة\nتذكير
 * بسداد …» · «Credit Card Payment\nYour card … has been blocked» كانت بتعدّي؛ ولو الأولاني تحية «عزيزي العميل» ⇒ التلات سطور)،
 * ولو الرسالة سطر واحد (مصر) أول جملة — «تم خصم … يوم 05/03/2026. تذكير: لا تشارك …» أولها «تم خصم …».
 */
internal fun headOf(body: String): String {
    val lines = body.split('\n').map(JsText::trim).filter { it.isNotEmpty() }
    if (lines.isEmpty()) return ""
    if (lines.size > 1) return lines.take(if (GREETING.containsMatchIn(lines[0])) 3 else 2).joinToString(" ")
    val end = SENTENCE_END.find(lines[0])?.range?.first ?: return lines[0]
    return lines[0].substring(0, end)
}

/** فيها إلغاء (في أولها أو بعبارة إلغاء عملية). */
private fun mentionsCancel(body: String) = CANCEL_WORD.containsMatchIn(headOf(body)) || CANCEL_PHRASE.containsMatchIn(body)

private fun isCancelled(body: String) = mentionsCancel(body) && !REFUND_WORD.containsMatchIn(body)

/**
 * إلغاء **ومعاه** استرداد (الجولة التالتة): فلوس راجعة — يعني داخل. لو العنوان قال صرف («Purchase Cancelled … Refund») الرسالة
 * متناقضة ⇒ القارئ بيرفضها «الاتجاه مش واضح» بدل ما يسجلها صرف جديد (§75-6: الاسترداد بيستنى التأكيد).
 */
internal fun cancelledWithRefund(body: String): Boolean = mentionsCancel(body) && REFUND_WORD.containsMatchIn(body)

/**
 * النص اللي الحراس بتتفحص عليه: من غير التطويل ولا التشكيل («رمـز التحقق» = «رمز التحقق»). القارئ نفسه بيقرا النص زي ما هو
 * (بصمة الرسالة ووصفها في ملف المرجع فيهم «بـ» و«لـ»).
 */
internal fun guardText(body: String): String =
    foldHiddenText(body).filterNot { it.code == 0x0640 || it.code in 0x064B..0x065F || it.code == 0x0670 || it.code in 0x0610..0x061A }

/**
 * سبب تجاهل الرسالة أو null — الترتيب نفس التطبيق الحالي (عرض ⇒ رمز ⇒ مرفوض) وبعدهم «مش عملية» ⇒ اتلغت
 * («الغاء حجز مبلغ» = رجوع حجز، يعني «مش عملية» مش «مرفوضة»).
 */
internal fun smsIgnoreReason(body: String): TextKey? {
    val text = guardText(body)
    return when {
        SMS_OFFER_PATTERN.containsMatchIn(text) -> TextKey.SMS_OFFER
        isSensitiveText(text) -> TextKey.SMS_SENSITIVE
        SMS_DECLINED_PATTERN.containsMatchIn(text) -> TextKey.SMS_DECLINED
        isNotATransaction(text) -> TextKey.SMS_NOT_TRANSACTION
        isCancelled(text) -> TextKey.SMS_DECLINED
        else -> null
    }
}

/** سبب الحارس **من غير العرض** (فلتر الجهاز — الجولة السادسة: «تمت عملية شراء … ، سيتم إضافة النقاط» عرض بس في نفس الجملة). */
internal fun guardReasonBesidesOffer(body: String): TextKey? {
    val text = guardText(body)
    return when {
        isSensitiveText(text) -> TextKey.SMS_SENSITIVE
        SMS_DECLINED_PATTERN.containsMatchIn(text) || isCancelled(text) -> TextKey.SMS_DECLINED
        isNotATransaction(text) -> TextKey.SMS_NOT_TRANSACTION
        else -> null
    }
}

/**
 * الجولة السادسة: جملة **تحذير** من غير رقم («Never share your OTP or PIN» · «لا تشارك الرقم السري مع أحد» · «اوعى تدي كود التحقق
 * لأي حد» · «فودافون كاش عمرها ما هتطلب منك الرقم السري») مش رسالة رمز — كانت بتخلي عملية حقيقية تترمي قبل الحفظ وتضيع في صمت.
 * التحذير بيتشال لحد آخر جملته **لو مفيهوش رقم 4–8 أرقام** («Do not share the code 731905» لسه رمز)، والباقي بيتفحص.
 */
private val WARNING = Regex(
    "(?:${B}never$S+(?:share|disclose|give|reveal|tell)|${B}(?:do$S+not|don'?t)$S+(?:share|disclose|give|reveal|tell)" +
        "|${B}(?:will$S+)?never$S+ask|$NA(?:لا|ولا)$S*(?:تشارك|تعطي|تعط|تفصح|تخبر|تبلغ|تدي)|$NA(?:ب|يجب$S*)?عدم$S*(?:مشاركة|اعطاء|إعطاء|الإفصاح|الافصاح)" +
        "|$NA(?:[اإ]وع[ىي])$NZ|$NA(?:ما$S*)?م?ا?تدي(?:ش|هوش|هاش)$NZ|$NA(?:ما$S*)?م?ا?تقول(?:ش|هوش|هاش)$NZ" +
        "|${NA}عمر(?:ها|نا|ه|هم)?$S*ما$S*(?:ه|ح)?[تين]?طلب|${NA}(?:لن|لا)$S*(?:نطلب|يطلب|تطلب))[^.!؟?\\n]*",
    GI,
)
private val CODE_DIGITS = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

/** فيها رمز تحقق أو كلمة سر **برّه** جمل التحذير اللي مفيهاش رقم. */
internal fun isSensitiveText(text: String): Boolean =
    SMS_SENSITIVE_PATTERN.containsMatchIn(WARNING.replace(text) { if (CODE_DIGITS.containsMatchIn(it.value)) it.value else " " })

/** رقم 4–8 أرقام لوحده: مش جوه رقم أطول ولا تاريخ ولا ساعة ولا مبلغ بكسور، ومش بعد نجمة أو «•» (كارت أو حساب متقص). */
private val CODE_CANDIDATE = Regex("(?<![\\d.,٫٬*•xX#/:\\\\-])\\d{4,8}(?!\\d|[.,٫٬/:\\\\-]\\d)")
private val MONEY_BEFORE = Regex("(?:${SmsVocabulary.CURRENCY}|بمبلغ|المبلغ|مبلغ|${B}amount|${B}of)$S*[:：]?$S*$", GI)
private val MONEY_AFTER = Regex("^$S*(?:${SmsVocabulary.CURRENCY})", GI)
private val ID_BEFORE = Regex(
    // «Verification No. 731905» رمز — «No.» لوحدها مش رقم حساب
    "(?:${B}ending(?:$S+(?:in|with))?|المنتهي[ةه]?$S*بـ?|${B}(?:ref(?:erence)?|id|trx|txn|transaction|card|account|acct|ac)(?:$S*(?:no|number|id)\\.?)?" +
        "|بطاقة|البطاقة|بطاقتك|بطاقتكم|حساب|الحساب|حسابك|حسابكم|مرجع[يى]?|(?:ال)?عملية|رقم)$S*[:：#.]?$S*$",
    GI,
)
private val MONTH_DAY_BEFORE = Regex("(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?$S+\\d{1,2},?$S*$", GI)

/**
 * الجولة السابعة: الرسالة فيها **رقم رمز فعلًا** — 4 لـ8 أرقام لوحدهم مش مبلغ (جنبه عملة أو «مبلغ») ولا سنة تاريخ ولا آخر 4 من كارت أو
 * حساب ولا رقم مرجع. فلتر الجهاز بيرمي رسالة الرمز **بس لو فيها رمز**: «Keep your IPN PIN and OTP private» · «متشاركش الرقم السري
 * مع حد» · «Verified with OTP» · «لو حد طلب منك كود التحقق اقفل السكة» تحت تحويل حقيقي **مش رمز** (كانت بتترمي والعملية تضيع).
 */
internal fun hasFreeCode(text: String): Boolean = CODE_CANDIDATE.findAll(text).any { m ->
    val lineStart = text.lastIndexOf('\n', m.range.first - 1) + 1
    val before = text.substring(maxOf(lineStart, m.range.first - 40), m.range.first)
    val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 12))
    !MONEY_BEFORE.containsMatchIn(before) && !MONEY_AFTER.containsMatchIn(after) && !ID_BEFORE.containsMatchIn(before) &&
        !MONTH_DAY_BEFORE.containsMatchIn(before)
}

/**
 * نص الرسالة زي ما القارئ بيشوفه: أرقام لاتيني، من غير `\r` ولا علامات الاتجاه ولا **أي** حرف تنسيق مخفي، وأشكال العرض العربية
 * راجعة لحروفها (`SmsHiddenText.kt` — الجولة السادسة).
 */
internal fun normalizeSmsBody(body: String): String =
    foldHiddenText(latinizeDigits(body).filterNot { it == '\r' || it.code in BIDI_CODES })
