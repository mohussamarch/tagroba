package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * حراس **حالة العملية** (اتنقلوا من `SmsGuards.kt` في الجولة الخامسة عشان حد الـ300 سطر): مرفوضة · مش حركة خلصت (حجز · طلب · لسه ·
 * معلومة · رصيد بس) · شيك رجع. الأنماط الأولانية = التطبيق الحالي (ملف المرجع `golden/sms.json`)، والإضافات مكتوب جنبها جولتها.
 * **الجولة الخامسة** (مراجعة الجولة الرابعة العدائية): صيغ تانية للرفض («لم تُقبل» · «غير مقبولة» · refused · «expired card» ·
 * «غير مكتملة» · «not sent» · «لم يُصرف» · unpaid · «not authorized» · «ماتمش») وللحالة اللي لسه ما خلصتش («قيد المعالجة» ·
 * «تحت الإجراء» · «بانتظار» · awaiting · initiated · submitted · «Auth hold» · «مبلغ مبدئي» · «مجدولة» · «future dated» · «القسط القادم» ·
 * «standing order created» · «تم إيقاف الأمر» · «كشف البطاقة» · e-statement · overdue · «يرجى إيداع» · «payment request» · «تحت التحصيل»).
 * ومع ده، الحماية الأساسية بقت **القايمة البيضا** للشكل (`SmsSaudiLines.kt` · `SmsKnownShapesEgypt.kt`): الحراس دول بيخلوا الرسالة
 * تظهر «مرفوضة/مش عملية» بدل «جاهزة» في الشاشة.
 */

private val GI = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"
private const val NA = "(?<![$AR])"
private const val NZ = "(?![$AR])"
private const val MONEY_VERB = "(?:ال)?(?:خصم|سحب|تحصيل|سداد|تسديد|تحويل|قيد|إيداع|ايداع|دفع|استقطاع|إضافة|اضافة|شحن|رد|استرداد|تنفيذ)"
private const val IF_NOT = "(?<!(?:إذا|اذا|إن|حال)[ \\t]{1,3})"
private const val NEG = "(?<!(?:\\bnot|\\bnever|\\bno|n't)[ \\t]{1,3})(?<![Uu]n)"

/** «If this transaction was not authorized by you, call …» = سطر تحذير، مش العملية نفسها. */
private const val IF_EN = "(?<!\\bif[ \\t]{1,3}(?:(?:this|the|your)[ \\t]{1,3})?)"

/**
 * مرفوضة أو رصيد مش كفاية. الإضافات: «لم يتم تنفيذ» · «تم رفض المعاملة» (التجاري الدولي) · «رصيد غير كافي» · «لا يكفي» (الإنماء) ·
 * «لا يوجد رصيد كاف» (فودافون كاش) · «عدم كفاية» · Insufficient balance (إس تي سي — كانت بتتسجل شراء).
 * مراجعة جلسة 33: «was rejected» · denied · «could not be completed». الجولة التانية: «تعذر» · «فشلت» · «رفضت» · «لا يسمح» ·
 * «not approved». الجولة التالتة: «لن يتم» · «لم تكتمل/تنجح» · «not completed» · «unable to» · رقم سري غلط · «is stopped» ·
 * blocked · «تجاوز الحد» · «not enough» · محاولة شراء («Purchase attempt» — مش «If you did not attempt»).
 * «Cancelled»/«إلغاء»/«ملغاة» في `isCancelled` (لو مفيش استرداد).
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
        "|${NA}محاولة$S*(?:شراء|سحب|دفع|تحويل|عملية|حوالة)" +
        // ── الجولة الخامسة ──
        "|${NA}${IF_NOT}(?:لم|لن)$S*(?:تقبل|يقبل|يتم$S*قبول|يصرف|تصرف|يتم$S*صرف|تسدد|يسدد|يتم$S*السداد)$NZ|$NA(?:غير|عدم)$S*(?:مقبول[ةه]?|قبول)$NZ" +
        "|${B}not$S+(?:been$S+)?(?:accepted|sent|dispensed|paid)$B|${B}refused$B|${B}expired$S+card$B|${B}card$S+(?:has$S+|is$S+)?expired$B" +
        "|منتهي[ةه]?$S*(?:ال)?صلاحي|انتهت$S*(?:ال)?صلاحي|$NA(?:غير|عدم)$S*(?:مكتمل[ةه]?|اكتمال)$NZ|${B}incomplete$B" +
        // «unpaid» — إلا الشيك اللي رجع («was returned unpaid» · «unpaid cheque» ⇒ «الاتجاه مش واضح» زي الجولة التالتة)
        "|$B(?<!returned$S)unpaid$B(?!$S+cheque)" +
        "|$IF_EN${B}(?:transaction|payment|purchase)$S+(?:was$S+|is$S+)?not$S+authori[sz]ed$B" +
        "|$NA(?:ماتمش|ماتمتش|مانجحتش)$NZ|${NA}ما$S*(?:تمتش|نجحتش)$NZ" +
        // ── الجولة السادسة: تحويل وصل البنك وما اتضافش («could not be credited» · «was not deposited» · «not yet credited») ──
        "|${B}(?:could$S+not|cannot|can'?t)$S+be$S+(?:credited|deposited|sent|transferred|executed)$B" +
        "|${B}not$S+(?:yet$S+)?(?:been$S+)?(?:deposited|credited)$B" +
        // ── الجولة التامنة: «couldn't be completed» · «didn't go through» · «wasn't successful» · «التحويل اتأخر وهيتراجع» (كانت بتستنى «جاهزة») ──
        "|${B}(?:couldn'?t|could$S+not|can'?t|cannot|won'?t)$S+be$S+(?:completed|processed|executed|done)$B|${B}did(?:n'?t|$S+not)$S+go$S+through$B" +
        "|${B}(?:was|is|has)(?:n'?t|$S+not)$S+(?:been$S+)?(?:successful|completed|processed|executed|done)$B" +
        "|$NA[وف]?(?:هيتراجع|هتتراجع|حيتراجع|اتراجع|اتراجعت|يتراجع)$NZ|$NA(?:ال)?(?:تحويل|عملية|العملية|دفع|سداد)$S*(?:ات[أا]خر|اتأخرت|اتاخرت)$NZ",
    GI,
)

/**
 * مش حركة فلوس خلصت: تفويض/حجز (الخصم الحقيقي بييجي بعدين في رسالة تانية) · طلب استرداد أو طلب سحب لسه مستني · تحويل شراء قديم
 * لأقساط (مش صرف جديد) · كشف حساب البطاقة · دخول للتطبيق · تفعيل بطاقة · رقم سري اتعمل · رسالة رصيد بس · جايزة لازم تتطلب.
 * مراجعة جلسة 33: pending · «معلقة» · under review · «قيد المراجعة» · «تم استلام طلبك/طلب سحب» · refund request.
 * الجولة التانية: pre-authorization · «تم حجز» · on hold · «طلب استرداد» · dispute · chargeback · ميعاد سداد.
 * الجولة التالتة: التفويض **بعبارته** («Authorization» مش منفية · «has been authorized» · pre-auth — مش «If you have not authorized») ·
 * «اعتراض على» و«تذكير» في أول الرسالة بس ([HEAD_ONLY]) · «محجوز» · «تجميد» · «بشكل مؤقت» · «لحين إتمام» · temporary hold ·
 * «amount held» · «جاري تنفيذ» (مش «حساب جاري») · being processed · in progress.
 * الجولة الخامسة: «معلقة» **كلمة لوحدها** (مطعم «المعلقة الذهبية» كان بيترمي) + الإضافات اللي فوق في أول الملف.
 */
private val NOT_TRANSACTION_ANYWHERE = Regex(
    "حجز$S*مبلغ|Cash$S*Re(?:serve|lease)|تم$S*استلام$S*الطلب|تم$S*طلب|تم$S*تقسيط|كشف$S*حساب|الحد$S*الأدنى$S*للسداد|" +
        "تسجيل$S*الدخول|logged$S*in|تم$S*تفعيل|${B}PIN$B[^\\n]*${B}SET$B|مبروك$S*كسبت" +
        "|${B}pending$B(?!$S*[:：]?$S*(?:EGP|SAR|SR|LE)?$S*0+(?:[.,]0+)?(?![\\d.,]))|${NA}معلق(?:ة|ه)?$NZ|under$S*review|قيد$S*(?:المراجعة|الانتظار|التنفيذ)|تم$S*استلام$S*طلب" +
        // الجولة التامنة: «Authorization code 482211» · «authorization no.» = رقم موافقة في شراء حقيقي (زي «Approval code»)، مش حجز
        "|$NEG(?<![A-Za-z])authori[sz]ation(?![A-Za-z])(?!$S*(?:code|no\\.?|number|num|id|#|ref)$B)" +
        "|${B}(?:has|have|was|were|is|been)$S+(?:been$S+)?authori[sz]ed$B|${B}pre.?auth" +
        "|تم$S*حجز(?!$S*(?:ال)?(?:تذكر|موعد|رحل|طاول|غرف|مقعد))|حجز$S*مؤقت|${B}on$S+hold$B|${B}hold$S+(?:of|on|amount)$B" +
        "|طلب$S*(?:ال)?(?:استرداد|استرجاع|اعتراض)|تم$S*(?:تسجيل|استلام|رفع)$S*(?:ال)?اعتراض|${B}(?:your|the)$S+dispute$B" +
        "|${B}dispute$S+(?:for|on|of|has|was|is|request|case|ref)$B|${B}disputed$B|chargeback" +
        "|${B}refund$S+request$B|request(?:ed)?$S+(?:a$S+|for$S+(?:a$S+)?)?(?:refund|chargeback)|(?:withdrawal|cash.?out)$S+request" +
        "|المستحق$S*للسداد|مستحق[ةه]?$S*(?:السداد|الدفع)|جاهز[ةه]?$S*للسداد|${B}(?:is|are)$S+due$B|${B}due$S+(?:on|by|date)$B" +
        "|payment$S+due|${B}due$B$S*[:：]?$S*\\d|statement$S+(?:balance|amount)|minimum$S+(?:payment|due|amount)" +
        "|$NA(?:ال)?مبلغ$S*(?:ال)?محجوز|${NA}تجميد$S*(?:ال)?مبلغ|تم$S*تجميد|بشكل$S*مؤقت|${NA}مؤقت(?:ا|ًا|اً)$NZ|لحين$S*(?:إتمام|اتمام|تسوية|التسوية|اكتمال)" +
        "|${B}temporar(?:y|ily)$S+(?:hold|held|blocked|reserved|debit)|${B}(?:amount|funds?)$S+(?:is$S+|are$S+|has$S+been$S+|have$S+been$S+)?held$B" +
        "|${B}held$S+(?:on|against)$S+your$B|جار[يى]$S*(?:ال)?(?:تنفيذ|معالجة|عمل|تحويل|إيداع|ايداع|سداد|خصم|تأكيد|تاكيد)" +
        "|${B}being$S+processed$B|${B}in$S+progress$B|${B}(?:is|are)$S+processing$B" +
        // ── الجولة الخامسة ──
        "|قيد$S*(?:ال)?(?:معالجة|تسوية|إجراء|اجراء|تحقق)|تحت$S*(?:ال)?(?:إجراء|اجراء|مراجعة|تحصيل|معالجة|تسوية|تحقق)" +
        "|$NA(?:ب|في$S*)انتظار$NZ|${B}awaiting$B|${B}in$S+process$B|${B}(?:initiated|submitted|queued)$B" +
        "|${B}(?:status|state|result)$S*[:：]?$S*(?:processing|pending|in$S+progress|on$S+hold)$B" +
        "|${B}under$S+(?:clearing|collection|processing)$B|${B}auth(?:ori[sz]ation)?$S+hold$B|${B}awaiting$S+settlement$B" +
        "|$NA(?:ال)?مبلغ$S*(?:ال)?مبدئي$NZ|${NA}مبدئي[ةه]?$NZ|$NA(?:ال)?(?:مجدول[ةه]?|جدولة)$NZ|${B}future.?dated$B" +
        "|${B}next$S+instal?l?ments?$B|$NA(?:ال)?قسط$S*(?:ال)?قادم$NZ" +
        "|${B}standing$S+order$S+(?:has$S+been$S+|was$S+|is$S+)?(?:created|set$S+up|stopped|cancell?ed|amended|modified)$B" +
        "|$NA(?:تم$S*)?(?:إنشاء|انشاء|إيقاف|ايقاف|تعديل)$S*(?:ال)?(?:أمر|امر)$NZ|كشف$S*(?:ال)?بطاق" +
        "|${B}(?:e-?)?statement$S+(?:is$S+)?(?:ready|available|generated|issued)$B|${B}e-?statement$B|${B}overdue$B" +
        "|(?:يرجى|يرجي|برجاء|الرجاء|نرجو)$S*(?:إيداع|ايداع|سداد|دفع|تحويل)|${B}(?:payment|money)$S+request$B" +
        "|${B}reserved$B|$NA(?:تم$S*)?تعليق$S*(?:ال)?مبلغ|${NA}مؤجل[ةه]?$NZ" +
        // ── الجولة السادسة: لسه في السكة أو مستني تأكيد («subject to bank verification» · «on its way» · «provisional credit») ──
        "|${B}subject$S+to$S+(?:bank$S+|further$S+)?(?:verification|approval|review|confirmation)$B|${B}on$S+(?:its|the)$S+way$B" +
        "|${B}in$S+transit$B|${B}to$S+be$S+confirmed$B|${B}provisional(?:ly)?$S+credit" +
        // ── الجولة السابعة: فلوس لسه هتتضاف أو هتتنفذ («To be credited/cleared/executed/released/posted/sent/reflected …» · «من المتوقع إيداع» ·
        // «من المقرر تنفيذ» · «عند تحصيل الشيك» · «الى حين استكمال التحقق» · «حدّث بياناتك» · «update your ID» · «compliance check») ──
        "|${B}to$S+be$S+(?:credited|cleared|executed|released|posted|sent|reflected|added|deposited|transferred|processed|completed|refunded|returned)$B" +
        "|$NA(?:من$S*)?(?:ال)?(?:متوقع|مقرر)$S*(?:ان$S*)?(?:إيداع|ايداع|إضافة|اضافة|إضافت|اضافت|ظهور|وصول|تحويل|خصم|تنفيذ|صرف)" +
        "|${NA}عند$S*(?:ال)?(?:تحصيل|اكتمال|استكمال|اتمام|إتمام)|$NA(?:الى|إلى|الي)$S*حين$NZ|(?:فضلك|يرجى|يرجي|برجاء|الرجاء)$S*(?:حد[ّ]?ث|تحديث)" +
        "|${B}update$S+your$S+(?:id|iqama|kyc|details|data|information|info)$B|${B}compliance$S+(?:check|review)$B",
    GI,
)

/**
 * كلام عام بيتفحص في **أول الرسالة** بس: تذكير · اعتراض · reminder · dispute (في آخر رسالة حقيقية = سطر تحذير). الجولة الخامسة:
 * أول الرسالة في السعودية = العنوان **والسطر اللي بعده** (`headOf`) — «سداد فاتورة\nتذكير بسداد …» كانت بتعدّي.
 */
private val HEAD_ONLY = Regex("$NA(?:ال)?(?:تذكير|اعتراض|محجوز(?:ة|ه)?)$NZ|${B}reminder$B|${B}dispute$B|${B}blocked$B", GI)

/**
 * «تفويض» في أي مكان = حجز مبلغ لشراء إنترنت. «خصم من التفويض» (الراجحي — الخصم الحقيقي) ما بيتلمسش. الجولة التامنة: «رقم/كود/رمز التفويض»
 * = رقم موافقة في شراء أو سحب حقيقي («عملية شراء ناجحة … رقم التفويض 553120» · «سحبت 1,000 جنيه … رقم التفويض 552310» كانوا بيترموا).
 */
private val HOLD_WORD = Regex("(?<!(?:رقم|كود|رمز)[ \\t]{0,3}(?:ال)?)تفويض")
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

/**
 * الجولة الخامسة: الرصيد **وبعده حركة بمبلغ** («رصيدك الحالي 2,300 جنيه بعد تحويل 700 جنيه لرقم …» · «… balance is EGP 2,300.00 after
 * receiving EGP 700.00 from …») = عملية، مش رسالة رصيد — «بعد عملية الشراء» (من غير مبلغ للحركة) لسه رصيد بس.
 */
private val MOVEMENT_AFTER_BALANCE = Regex(
    "(?:بعد|${B}after)$S+(?:ال)?(?:تحويل|استلام|إيداع|ايداع|سحب|إضافة|اضافة|receiving|sending|transferring|withdrawing|depositing|receipt$S+of|transfer$S+of)" +
        "$S+(?:مبلغ$S+|of$S+)?(?:[A-Za-z]{3}$S*)?\\d" +
        // الجولة السابعة: «Available balance SAR 3,912.40 after PoS purchase of SAR 48.50 at …» — «of» ومبلغ **في نفس السطر** («Available Balance after
        // Purchase\nSAR 4,100.00» لسه رصيد بس)
        "|${B}after[ \\t]+(?:(?:pos|online|card)[ \\t]+)?purchase[ \\t]+of[ \\t]+(?:[A-Za-z]{3}[ \\t]*)?\\d" +
        // الجولة التامنة: «<الرصيد> <رقم> بعد/after/following <فعل حركة> … <مبلغ>» **في نفس السطر** («balance is SAR 3,212.00 after a debit of SAR 48.50» ·
        // «following a purchase of …» · «3,163.50 ر.س بعد عملية شراء بمبلغ 48.50» · «بعد سداد فاتورة … بمبلغ 450» · «650.00 جنيه بعد خصم 350.00 جنيه» ·
        // «after paying EGP 450.00») — الرصيد **قبل** «بعد» والحركة بمبلغها بعده («الرصيد المتاح بعد عملية الشراء 4,100.00» لسه رصيد بس)
        "|\\d[^\\n\\d]{0,60}?(?:(?<![$AR])بعد|${B}after|${B}following)[ \\t]+(?:(?:a|an|the|your)[ \\t]+)?(?:(?:ال)?عملي[ةه][ \\t]*)?" +
        "(?:(?:ال)?(?:خصم|دفع|شراء|سداد|تسديد|تحويل|سحب|إيداع|ايداع|استلام|إضافة|اضافة)" +
        "|(?:debit|credit|purchase|payment|paying|transfer|withdrawal|deposit|charge|spend|spending)$B)" +
        "[^\\n\\d]{0,40}?\\d",
    GI,
)

/**
 * الجولة السابعة: **حركة فلوس خلصت** بفعلها («تم خصم/تحويل/استلام/رد/تنفيذ/تسوية …» · «وخصم … من حسابك» · «اتحولك» · «has been credited» ·
 * «was executed» · «credited to/returned to» · «completed successfully» · «You received»). فلتر الجهاز (`SmsVocabulary`)
 * ما بيرميش رسالة فيها كده ومبلغ (§72: الضياع مش مقبول — القارئ يرفضها لو مش عملية فتستنى)، والرصيد في أولها ما بيخليهاش «رصيد بس».
 * المستقبل والمنفي مش منها («سيتم/لم يتم» — «تم» لازم كلمة لوحدها · «will be/to be credited»).
 */
internal val COMPLETED_MOVEMENT = Regex(
    // «تم استلام طلبك لاسترداد …» = طلب اتسجل مش فلوس (حارس «طلب استرداد» بيرميه)
    "${NA}و?تمت?$S*(?:عملية$S*)?(?:ال)?(?:خصم|سحب|تحويل|استلام(?!$S*(?:ال)?طلب)|إيداع|ايداع|إضافة|اضافة|قيد|سداد|تسديد|دفع|شحن|رد|استرداد|استرجاع" +
        "|إرجاع|ارجاع|إعادة|اعادة|تنفيذ|تسوية|شراء)" +
        "|$NA[وف]?(?:اتخصم|اتخصملك|اتسحب|اتحول|اتحولك|اتحولّك|اترجع|اترجعلك|اتضاف|اتضافلك|استلمت|وصلك|وصلتك|وصلتلك|دفعت)$NZ" +
        "|${NA}و(?:خصم|دفع)$S*[^\\n]{0,40}?من$S*(?:حساب|محفظت|بطاقت)" +
        "|${B}(?:has|have|had|was|were|is|are)$S+(?:been$S+)?(?:successfully$S+)?(?:credited|debited|charged|deducted|withdrawn|transferred|received" +
        "|paid|refunded|returned|executed|completed|processed|sent|deposited)$B" +
        "|(?<!(?:will|to|shall|would|may|can)$S{1,3}be$S{1,3})${B}(?:credited|debited|deducted|withdrawn|refunded|returned|transferred)$S+(?:to|from|back|into)$B" +
        "|${B}was$S+successful$B|${B}completed$S+successfully$B|${B}you$S+(?:have$S+)?(?:received|sent|paid|spent|withdrew|transferred)$B" +
        "|${B}received$S+(?:towards|from|into)$B" +
        // ── الجولة التامنة (المراجعة العدائية الرابعة): أفعال خلصت كانت ناقصة فالرسالة بتترمي لما حارس يمسك كلمة تانية فيها ──
        // «has been made» · «was used for» · «You've received» · «Payment successful» · «Transfer done» · «Scheduled Transfer Executed» ·
        // «Approved purchase SAR 48.50» · «Purchase of EGP 850.00» · «شراء بمبلغ 48.50»
        "|${B}(?:has|have|had|was|were)$S+(?:been$S+)?(?:successfully$S+)?(?:made|used$S+(?:for|at))$B|${B}you'?ve$S+(?:received|sent|paid|spent|withdrawn|transferred)$B" +
        "|${B}(?:payment|transfer|transaction|purchase|withdrawal|deposit|money)$S*(?:was$S+|is$S+)?(?:successful|done|complete[d]?|executed|sent|received)$B" +
        "|(?<!(?:will|to|shall|would|may|can)$S{1,3}be$S{1,3})${B}executed$B|${B}approved$S+(?:purchase|transaction|payment|withdrawal)$B" +
        "|(?:^|[.!؟?\\n]$S*)(?:${B}purchase$S+of|${B}purchase$B|$NA(?:عملية$S*)?شراء(?:$S*ناجح[ةه]?)?$S*(?:بمبلغ|بقيمة|مبلغ)?)$S*[:：]?$S*" +
        "(?:SAR|SR|EGP|LE|ر\\.?$S?س\\.?|جنيه|جم)?$S*\\d" +
        // «عملية شراء ناجحة» · «تنفيذ دفعة» (عنوان من غير «تم»، ومش «سيتم/يتم/لم يتم/جاري تنفيذ») · «وتحصيل/واستيفاء رسوم» · «وخصم … من رصيد محفظتك» ·
        // «وتمت إضافتها لمحفظتك» · المصري الماضي («حولت · بعتّ · سحبت · شحنت · قبضت · جالك · اتدفع · اتشحن»)
        "|$NA(?:عملية$S*)?شراء$S*ناجح[ةه]?$NZ|^$S*تنفيذ$S+(?:دفع[ةه]?|حوال[ةه]|تحويل|سداد|امر|أمر)" +
        "|${NA}و(?:خصم|دفع|تحصيل|استيفاء|سحب)[^\\n\\d]{0,30}\\d[\\d,.]*$S*(?:ر\\.?$S?س|ريال|SAR|SR|جنيه|جم|ج\\.م|EGP|LE)" +
        "|${NA}و(?:خصم|دفع)$S*[^\\n]{0,40}?من$S*رصيد$S*(?:حساب|محفظت|بطاقت)" +
        "|${NA}و?تمت?$S*(?:ال)?(?:إضافت|اضافت)(?:ه|ها|هم)" +
        "|$NA(?:حولت|بعت|بعتّ|بعتت|سحبت|شحنت|قبضت|جالك|جالكم|جالكو|اتدفع|اتدفعت|اتشحن|اتشحنت)$NZ",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)

internal fun isNotATransaction(body: String): Boolean =
    NOT_TRANSACTION_ANYWHERE.containsMatchIn(body) ||
        (BALANCE_ONLY.containsMatchIn(body) && !MOVEMENT_AFTER_BALANCE.containsMatchIn(body) && !COMPLETED_MOVEMENT.containsMatchIn(body)) ||
        HEAD_ONLY.containsMatchIn(headOf(body)) || (HOLD_WORD.containsMatchIn(body) && !DEBIT_FROM_HOLD.containsMatchIn(body))

/**
 * شيك رجع (اترفض) — في القارئين (الجولة التالتة: كان في السعودية بس، ومصر سجلت «was returned unpaid» استرداد داخل):
 * ممكن رصيد اتخصم تاني أو ما حصلش حاجة — مش استرداد داخل ⇒ «الاتجاه مش واضح». الجولة الخامسة: «شيك معاد» · «إعادة الشيك».
 */
private val RETURNED_CHEQUE = Regex(
    "شيك$S*(?:مرتجع|مرفوض|راجع)|$NA(?:ارتجاع|إرجاع|ارجاع|رفض|رد)$S*(?:ال)?شيك|(?:returned|bounced|dishonou?red|unpaid)$S+cheque" +
        "|cheque[^\\n]{0,80}?$B(?:returned|bounced|dishonou?red|unpaid)$B" +
        "|(?:ال)?شيك$S*(?:ال)?(?:معاد|معادة|معاده)$NZ|$NA(?:إعادة|اعادة)$S*(?:ال)?شيك",
    GI,
)

internal fun isReturnedCheque(body: String): Boolean = RETURNED_CHEQUE.containsMatchIn(body)
