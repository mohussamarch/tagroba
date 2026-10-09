package app.masroufy.core

/**
 * **قوايم مقفولة** لسطور رسالة البنك السعودي (الجولة السادسة — المراجعة العدائية التانية للجولة الخامسة). قبل كده:
 * - سطر التحذير في الآخر كان بيتقبل **بأول كلمة** («للاستفسار/للمزيد/في حال/for details/If you …») وأي كلام بعدها — «للاستفسار: الحوالة
 *   معادة من بنك المستفيد» · «for details: refund will reflect on your card within 14 days» · «If you … wish to stop it before
 *   execution …» اتسجلوا عمليات خلصت.
 * - سطر اسم البنك كان «بنك + أي كلام» («بنك المرسل استرد الحوالة» · «Bank transfer recalled»).
 * - ذيل الكارت والحساب كانوا كلام حر («Card: *4417 - blocked» · «بطاقة: 8063* - حجز ضمان» · «من حساب: 5519 - مجمد»).
 * دلوقتي كل واحد فيهم **جملة/اسم/كلمة من قايمة معروفة** (الخانة الوحيدة الحرة = رقم التليفون). غير كده ⇒ السطر مش معروف ⇒ الرسالة
 * تستنى تأكيد المالك (§72 — الانتظار مقبول). الأنماط على [shapeKey] بالنقطتين: حروف صغيرة · «ا» مكان «أ/إ/آ» · «ي» مكان «ى».
 */

/**
 * رقم تليفون — أو **بعد الحجب** في فلتر الجهاز («اتصل ••••0000»): الجولة الخامسة كانت بتقرا الرقم المحجوب «رمز» فالسطر المعروف
 * بيبقى مش معروف والرسالة الحقيقية تستنى، وأي جملة من غير رقم كانت بتعدّي (العكس بالظبط).
 */
private const val PHONE = "(?:\\+?\\d[\\d -]{3,15}\\d|[•*]{2,}\\d{2,4})"
private const val CALL = "(?:please )?call(?: us)?(?: on| at)?:? ?$PHONE"
private const val AR_CALL = "(?:اتصل|اتصال|يرجي الاتصال|الاتصال)(?: ب| علي| علي الرقم| بالرقم)? ?:? ?$PHONE"
private const val SECRET = "(?:card details|otp|pin|password|cvv|card details or password|otp or pin|password or otp|one-time password)"

/** جمل التحذير المعروفة في آخر الرسالة — كل واحدة **بالكامل**. */
private val FOOTERS: List<Regex> = listOf(
    "للاعتراض(?: علي (?:ال)?عملية)?،? $AR_CALL",
    "(?:لايقاف|لالغاء) (?:ال)?(?:بطاقة|خدمة)(?: او (?:الغاء|ايقاف) (?:ال)?(?:خدمة|بطاقة))?،? $AR_CALL",
    "(?:اذا|في حال) لم تتم العملية بواسطتك،? $AR_CALL",
    "(?:للاستفسار|للمزيد)(?: ?:)? ?(?:$AR_CALL|$PHONE)",
    "if (?:you|this)(?: have not| haven't| did not| didn't| are not| was not)? ?(?:authori[sz]ed?|attempt(?:ed)?|made|make|perform(?:ed)?|initiate(?:d)?|recogni[sz]e)" +
        " (?:this|the|it)(?: (?:transaction|purchase|payment|transfer|withdrawal))?(?: by you)?,? $CALL",
    "to dispute (?:this|the) (?:transaction|purchase|payment),? $CALL",
    "not attempted(?: this)? (?:purchase|transaction)\\?? $CALL",
    "(?:no authori[sz]ation from you|not authori[sz]ed by you)\\?? $CALL",
    "lost card\\?? $CALL(?: to (?:get it )?block(?:ed)?(?: it)?)?",
    "for (?:more )?(?:info|information|details),? $CALL",
    "call us(?: on| at)?:? ?$PHONE",
    "(?:never|do not|don't) share your $SECRET(?: with anyone)?",
    "if you are unable to recogni[sz]e it,? never share the code with anyone",
    "\\*?(?:تذكير: ?)?لا تشارك (?:بيانات بطاقتك|بيانات حسابك|الرمز|رمز التحقق|الرقم السري|كلمة المرور)(?: مع (?:احد|اي شخص|اي احد))?",
    "استمتع بخدماتنا",
).map { Regex(it) }

internal fun isKnownFooter(key: String): Boolean = FOOTERS.any { it.matches(key) }

/** البنوك السعودية بالاسم (عربي وإنجليزي — `research/banks/saudi-banks.json`). سطر اسم بنك = واحد منهم بالظبط. */
internal const val SAUDI_BANK_NAMES =
    "(?:(?:ال)?بنك )?(?:ال)?اهلي(?: السعودي)?|(?:مصرف |بنك )?الراجحي|بنك الرياض|ساب|البنك السعودي (?:الاول|البريطاني)" +
        "|البنك العربي(?: الوطني)?|(?:مصرف |بنك )?الانماء|(?:البنك )?السعودي الفرنسي|الفرنسي|البنك السعودي للاستثمار|(?:بنك )?البلاد" +
        "|(?:بنك )?الجزيرة|بنك الخليج الدولي|بنك (?:اس تي سي|stc)|(?:بنك )?(?:دي ?360|d ?360)" +
        "|(?:the )?saudi national bank|snb|ncb|al ?rajhi(?: bank)?|riyad ?bank|sab|saudi (?:awwal|british) bank|arab national bank|anb" +
        "|alinma(?: bank)?|banque saudi fransi|bsf|(?:the )?saudi investment bank|saib|bank ?albilad|albilad(?: bank)?|bank ?aljazira" +
        "|aljazira(?: bank)?|gulf international bank|gib|stc ?bank|d ?360(?: bank)?|vision bank|ez ?bank"

/** كلمات ذيل الكارت المعروفة: الشبكة · نوع الكارت · المحفظة (Apple Pay · أثير …). */
private const val CARD_WORD =
    "(?:mada|مدي|visa|فيزا|mastercard|master|ماستركارد|ماستر|credit|debit|card|prepaid|(?:ال)?ائتماني[ةه]?|(?:ال)?ائتمان|مسبق[ةه]|(?:ال)?دفع" +
        "|apple|ابل|pay|باي|google|جوجل|samsung|سامسونج|stc|atheer|اثير|huawei|هواوي|garmin|fitbit|e-?commerce|online|انترنت|platinum" +
        "|بلاتينيوم|signature|gold|ذهبي[ةه]|classic|كلاسيك|infinite|world|amex|wallet|محفظ[ةه]|mobile|contactless|virtual|افتراضي[ةه])"
private const val CARD_SEP = "[ ;,()\\-.]"

/** ذيل الكارت: فواصل وكلمات من [CARD_WORD] بس. */
internal const val SA_CARD_TAIL = "(?:$CARD_SEP*$CARD_WORD(?![a-z\\u0600-\\u06FF]))*$CARD_SEP*"

/** قبل رقم الكارت («مدى:MADA PAY 4821*»). */
internal const val SA_CARD_HEAD = "(?:$CARD_WORD$CARD_SEP*)*"

/** ذيل رقم الحساب بعد « - »: نوع الحساب أو اسم بنك («IBAN: ****7719 - Riyad Bank»). */
internal const val ACCOUNT_TAIL =
    "(?:(?:حساب )?(?:ال)?(?:جاري|توفير|ادخار|استثماري|راتب)|(?:current|savings?|investment|payroll)(?: account)?|$SAUDI_BANK_NAMES)"

/**
 * كلمة **مش اسم** في تحية «هلا <اسم>» (حرف جر · فعل «تم/لم/لن» · اسم حاجة في العملية): «هلا سامر الحوالة تحت التدقيق» مش تحية،
 * حتى لو ولا كلمة فيها من قايمة الحالة.
 */
internal val GREETING_NOT_A_NAME = Regex(
    "(?:^| )(?:في|من|الي|عن|تحت|قيد|تم|تمت|لم|لن|غير|عدم|بعد|قبل|لحين|حتي|و?ال?حوال[ةه]|و?ال?عملي[ةه]|و?ال?مبلغ|و?ال?راتب|و?ال?بنك" +
        "|و?ال?حساب\\S*|و?ال?بطاق[ةه]\\S*|و?ال?رصيد)(?= |$)",
)

/** الدولة السعودية بأي كتابة — خانة الدولة بأي قيمة تانية = عملية برّه البلد (§75-12). */
internal val SAUDI_COUNTRY = Regex(
    "^(?:sa|ksa|sau|saudi arabia|(?:the )?kingdom of saudi arabia|السعودي[ةه]|(?:ال)?مملك[ةه] (?:ال)?عربي[ةه] (?:ال)?سعودي[ةه]|المملك[ةه])$",
)
