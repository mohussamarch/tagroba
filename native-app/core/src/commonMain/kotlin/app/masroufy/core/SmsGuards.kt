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
    "$NA(?:[وف])?(?:بال|لل|ال|ب|ل)?عرض|سيتم|عرض خاص|offer|will be|scheduled" +
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
        "|(?<!(?:auth|approval|authori[sz]ation|merchant|branch|terminal|promo)[ \\t:]{1,2})$B(?:PIN|code)$B$S*(?:is$S*)?[:：]?$S*\\d{4,8}(?!\\d)" +
        "|رمز$S*(?:ال)?(?:تأكيد|أمان|امان|سري)|الرمز$S*السري|(?:confirmation|authentication|security)$S*(?:code|PIN)" +
        "|$NA(?:ال)?(?:رمز|كود)ك?$S*(?:ال)?(?:تحقق|توثيق|تفعيل|دخول|تأكيد|تاكيد|أمان|امان|سري|مؤقت|شراء|مرور)$NZ" +
        "|$NA(?:ال)?رقم$S*(?:ال)?(?:تحقق|سري)$NZ|$NA(?:ال)?(?:رمز|كود)ك?$S*(?:هو$S*)?[:：]?$S*\\d{4,8}(?!\\d)" +
        "|3D$S*-?$S*Secure$S+(?:code|password|PIN|OTP)|${B}password$S+(?:for|is)$B|${B}token$B$S*[:：]?$S*(?:is$S*)?\\d{4,8}(?!\\d)" +
        "|${B}to$S+authenticate$B|${B}(?:your|the)$S+code$S+(?:for|is|to)$B|${B}(?:enter|entering)$S+(?:the$S+)?(?:code$S+|OTP$S+)?\\d{4,8}(?!\\d)" +
        "|$NA(?:أدخل|ادخل|بإدخال|إدخال|ادخال)$S*(?:ال)?(?:رمز|كود)?$S*\\d{4,8}(?!\\d)",
    GI,
)

/**
 * مرفوضة أو رصيد مش كفاية. الإضافات: «لم يتم تنفيذ» · «تم رفض المعاملة» (التجاري الدولي) · «رصيد غير كافي» · «لا يكفي» (الإنماء) ·
 * «لا يوجد رصيد كاف» (فودافون كاش) · «عدم كفاية» · Insufficient balance (إس تي سي — كانت بتتسجل شراء).
 * مراجعة جلسة 33: «was rejected» · denied · «could not be completed». الجولة التانية: «تعذر» · «فشلت» · «رفضت» · «لا يسمح» ·
 * «not approved». الجولة التالتة: «لن يتم» · «لم تكتمل/تنجح» · «not completed» · «unable to» · رقم سري غلط · «is stopped» ·
 * blocked · «تجاوز الحد» · «not enough» · محاولة شراء («Purchase attempt» — مش «If you did not attempt»).
 * «Cancelled»/«إلغاء»/«ملغاة» في [isCancelled] (لو مفيش استرداد).
 */
internal val SMS_DECLINED_PATTERN = Regex(
    "مرفوض|رفض العملية|${IF_NOT}لم تتم|غير ناجح|declined|failed|unsuccessful" +
        "|${IF_NOT}لم$S*يتم|رفض$S*المعاملة|تم$S*رفض|insufficient|رصيد$S*غير$S*كا[فٍ]|لا$S*يكفي|لا$S*يوجد$S*رصيد|عدم$S*(?:وجود$S*رصيد|كفاية)" +
        "|${B}rejected$B|${B}denied$B|could$S*not$S*be$S*(?:completed|processed)" +
        "|تعذر|فشل|رفضت|لا$S*يسمح|${B}not$S+(?:been$S+)?approved$B" +
        "|${NA}لن$S*(?:يتم|تتم)$S*$MONEY_VERB|${NA}${IF_NOT}لم$S*(?:تكتمل|يكتمل|تنجح|ينجح|تنفذ|ينفذ)|${B}not$S+(?:been$S+)?(?:completed|processed|successful)$B" +
        "|${B}unable$S+to$S+(?:process|complete|authori[sz]e|approve|execute|perform|debit|charge)$B|${B}(?:incorrect|wrong|invalid)$S+(?:PIN|password|CVV|OTP)$B|${B}PIN$S+(?:is$S+)?(?:incorrect|wrong|invalid)$B" +
        "|${NA}(?:ال)?رقم$S*(?:ال)?سري$S*(?:خاطئ|خطأ|غير$S*صحيح)|${B}(?:is|was|has$S+been)$S+(?:stopped|suspended|frozen)$B" +
        "|${B}(?:purchase|transaction|payment|amount)$S+(?:is$S+|was$S+|has$S+been$S+)?blocked$B|\\d$S*blocked$B|${B}blocked$S+(?:on|against)$S+your$B|${NA}تجاوز$S*(?:ال)?حد|${B}not$S+enough$B" +
        "|${B}exceed(?:s|ed)?$S+(?:the$S+|your$S+)?(?:daily$S+|monthly$S+)?(?:limit|balance)" +
        "|${B}(?:purchase|transaction|payment|withdrawal|transfer)$S+attempt|$NEG${B}attempted$S+(?:purchase|transaction|payment|withdrawal)" +
        "|${NA}محاولة$S*(?:شراء|سحب|دفع|تحويل|عملية|حوالة)",
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
private val REFUND_WORD = Regex("استرداد|استرجاع|مرتجع|${B}refund", GI)

/**
 * مش حركة فلوس خلصت: تفويض/حجز (الخصم الحقيقي بييجي بعدين في رسالة تانية) · طلب استرداد أو طلب سحب لسه مستني · تحويل شراء قديم
 * لأقساط (مش صرف جديد) · كشف حساب البطاقة · دخول للتطبيق · تفعيل بطاقة · رقم سري اتعمل · رسالة رصيد بس · جايزة لازم تتطلب.
 * مراجعة جلسة 33: pending · «معلقة» · under review · «قيد المراجعة» · «تم استلام طلبك/طلب سحب» · refund request.
 * الجولة التانية: pre-authorization · «تم حجز» · on hold · «طلب استرداد» · dispute · chargeback · ميعاد سداد.
 * الجولة التالتة: التفويض **بعبارته** («Authorization» مش منفية · «has been authorized» · pre-auth — مش «If you have not authorized») ·
 * «اعتراض على» و«تذكير» في أول الرسالة بس ([HEAD_ONLY]) · «محجوز» · «تجميد» · «بشكل مؤقت» · «لحين إتمام» · temporary hold ·
 * «amount held» · «جاري تنفيذ» (مش «حساب جاري») · being processed · in progress.
 */
private val NOT_TRANSACTION_ANYWHERE = Regex(
    "حجز$S*مبلغ|Cash$S*Re(?:serve|lease)|تم$S*استلام$S*الطلب|تم$S*طلب|تم$S*تقسيط|كشف$S*حساب|الحد$S*الأدنى$S*للسداد|" +
        "تسجيل$S*الدخول|logged$S*in|تم$S*تفعيل|${B}PIN$B[^\\n]*${B}SET$B|مبروك$S*كسبت" +
        "|${B}pending$B|معلق(?:ة|ه)?(?![$AR])|under$S*review|قيد$S*(?:المراجعة|الانتظار|التنفيذ)|تم$S*استلام$S*طلب" +
        "|$NEG(?<![A-Za-z])authori[sz]ation(?![A-Za-z])|${B}(?:has|have|was|were|is|been)$S+(?:been$S+)?authori[sz]ed$B|${B}pre.?auth" +
        "|تم$S*حجز|حجز$S*مؤقت|${B}on$S+hold$B|${B}hold$S+(?:of|on|amount)$B" +
        "|طلب$S*(?:استرداد|استرجاع|اعتراض)|تم$S*(?:تسجيل|استلام|رفع)$S*(?:ال)?اعتراض|${B}(?:your|the)$S+dispute$B" +
        "|${B}dispute$S+(?:for|on|of|has|was|is|request|case|ref)$B|${B}disputed$B|chargeback" +
        "|${B}refund$S+request$B|request(?:ed)?$S+(?:a$S+|for$S+(?:a$S+)?)?(?:refund|chargeback)|(?:withdrawal|cash.?out)$S+request" +
        "|المستحق$S*للسداد|مستحق[ةه]?$S*(?:السداد|الدفع)|جاهز[ةه]?$S*للسداد|${B}(?:is|are)$S+due$B|${B}due$S+(?:on|by|date)$B" +
        "|payment$S+due|${B}due$B$S*[:：]?$S*\\d|statement$S+(?:balance|amount)|minimum$S+(?:payment|due|amount)" +
        "|$NA(?:ال)?مبلغ$S*(?:ال)?محجوز|${NA}تجميد$S*(?:ال)?مبلغ|تم$S*تجميد|بشكل$S*مؤقت|${NA}مؤقت(?:ا|ًا|اً)$NZ|لحين$S*(?:إتمام|اتمام|تسوية|التسوية|اكتمال)" +
        "|${B}temporar(?:y|ily)$S+(?:hold|held|blocked|reserved|debit)|${B}(?:amount|funds?)$S+(?:is$S+|are$S+|has$S+been$S+|have$S+been$S+)?held$B" +
        "|${B}held$S+(?:on|against)$S+your$B|جار[يى]$S*(?:ال)?(?:تنفيذ|معالجة|عمل|تحويل|إيداع|ايداع|سداد|خصم)" +
        "|${B}being$S+processed$B|${B}in$S+progress$B|${B}(?:is|are)$S+processing$B",
    GI,
)

/** كلام عام بيتفحص في **أول الرسالة** بس: تذكير · اعتراض · reminder · dispute (في آخر رسالة حقيقية = سطر تحذير). */
private val HEAD_ONLY = Regex("$NA(?:ال)?(?:تذكير|اعتراض|محجوز(?:ة|ه)?)$NZ|${B}reminder$B|${B}dispute$B|${B}blocked$B", GI)

/** «تفويض» في أي مكان = حجز مبلغ لشراء إنترنت. «خصم من التفويض» (الراجحي — الخصم الحقيقي) ما بيتلمسش. */
private val HOLD_WORD = Regex("تفويض")
private val DEBIT_FROM_HOLD = Regex("خصم$S*من$S*(?:ال)?تفويض")

/**
 * الرسالة كلها رصيد: «رصيد حسابك فى فودافون كاش الحالي…» من غير حركة قبلها. الجولة التانية: بتبدأ بـ«رصيدك» · «الرصيد» لوحده
 * · «Your … balance». الجولة التالتة: **أول سطر بيبدأ بالرصيد** مهما كان بعده («الرصيد المتاح بعد عملية الشراء» · «Available Balance
 * after Purchase») — «Balance transfer» (تحويل رصيد بطاقة) مش هنا.
 */
private val BALANCE_ONLY = Regex(
    "^$S*(?:رصيد$S*حسابك|Your$S*current$S*Vodafone$S*Cash$S*balance|رصيدك|(?:ال)?رصيد$NZ" +
        "|(?:(?:available|current|ledger|account|card|new|remaining)$S+)+balance$B(?!$S*transfer)|balance$B(?!$S*transfer)" +
        "|Your$S+(?:[A-Za-z]+$S+){0,3}balance$B)",
    GI,
)

private val GREETING = Regex("^(?:عزيزي|عميلنا|Dear)", GI)
private val SENTENCE_END = Regex("[.!؟?](?:$S|$)")

/**
 * أول الرسالة: أول سطر فيه كلام (والسطر اللي بعده لو الأولاني تحية «عزيزي العميل»)، ولو الرسالة سطر واحد (مصر) أول جملة —
 * «تم خصم … يوم 05/03/2026. تذكير: لا تشارك …» أولها «تم خصم …».
 */
internal fun headOf(body: String): String {
    val lines = body.split('\n').map(JsText::trim).filter { it.isNotEmpty() }
    if (lines.isEmpty()) return ""
    if (lines.size > 1) return if (GREETING.containsMatchIn(lines[0])) lines[0] + " " + lines[1] else lines[0]
    val end = SENTENCE_END.find(lines[0])?.range?.first ?: return lines[0]
    return lines[0].substring(0, end)
}

internal fun isNotATransaction(body: String): Boolean =
    NOT_TRANSACTION_ANYWHERE.containsMatchIn(body) || BALANCE_ONLY.containsMatchIn(body) || HEAD_ONLY.containsMatchIn(headOf(body)) ||
        (HOLD_WORD.containsMatchIn(body) && !DEBIT_FROM_HOLD.containsMatchIn(body))

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
    body.filterNot { it.code == 0x0640 || it.code in 0x064B..0x065F || it.code == 0x0670 || it.code in 0x0610..0x061A }

/**
 * سبب تجاهل الرسالة أو null — الترتيب نفس التطبيق الحالي (عرض ⇒ رمز ⇒ مرفوض) وبعدهم «مش عملية» ⇒ اتلغت
 * («الغاء حجز مبلغ» = رجوع حجز، يعني «مش عملية» مش «مرفوضة»).
 */
internal fun smsIgnoreReason(body: String): TextKey? {
    val text = guardText(body)
    return when {
        SMS_OFFER_PATTERN.containsMatchIn(text) -> TextKey.SMS_OFFER
        SMS_SENSITIVE_PATTERN.containsMatchIn(text) -> TextKey.SMS_SENSITIVE
        SMS_DECLINED_PATTERN.containsMatchIn(text) -> TextKey.SMS_DECLINED
        isNotATransaction(text) -> TextKey.SMS_NOT_TRANSACTION
        isCancelled(text) -> TextKey.SMS_DECLINED
        else -> null
    }
}

/**
 * شيك رجع (اترفض) — في القارئين (الجولة التالتة: كان في السعودية بس، ومصر سجلت «was returned unpaid» استرداد داخل):
 * ممكن رصيد اتخصم تاني أو ما حصلش حاجة — مش استرداد داخل ⇒ «الاتجاه مش واضح».
 */
private val RETURNED_CHEQUE = Regex(
    "شيك$S*(?:مرتجع|مرفوض|راجع)|$NA(?:ارتجاع|إرجاع|ارجاع|رفض|رد)$S*(?:ال)?شيك|(?:returned|bounced|dishonou?red|unpaid)$S+cheque" +
        "|cheque[^\\n]{0,80}?$B(?:returned|bounced|dishonou?red|unpaid)$B",
    GI,
)

internal fun isReturnedCheque(body: String): Boolean = RETURNED_CHEQUE.containsMatchIn(body)

/** نص الرسالة زي ما القارئ بيشوفه: أرقام لاتيني، من غير `\r` ولا علامات الاتجاه المخفية. */
internal fun normalizeSmsBody(body: String): String = latinizeDigits(body).filterNot { it == '\r' || it.code in BIDI_CODES }
