package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * **بوابة «واضحة» على الرسالة كلها** (الجولة الخامسة — مراجعة الجولة الرابعة العدائية، OVERRIDES §72.1): قبل كده الشكل المعروف كان
 * بيتفحص على **أول سطر** (السعودية) أو **أول الجملة** (مصر) بس، وباقي الرسالة كان محمي بقايمة سودا من الكلمات (`SmsGuards.kt`) —
 * فأي سطر حالة القايمة ما تعرفهوش («Status: Processing» · «الحالة: تحت الإجراء» · «حوالة مجدولة» · «Transaction reversed») كان بيتسجل
 * عملية خلصت. دلوقتي الشكل المعروف **قايمة بيضا للرسالة كلها** (`SmsSaudiLines.kt` · `SmsKnownShapesEgypt.kt`)، وفوقها البوابة دي:
 * - [SHAPE_DOUBT]: كلمة حالة أو عكس أو طلب أو جاي في **خانة حرة** (اسم المحل/الطرف) أو في أي مكان في رسالة مصر ⇒ تستنى.
 * - **أكتر من تاريخ مختلف** في الرسالة (ميعاد استحقاق · تاريخ طلب · تاريخ كشف قبل تاريخ العملية) ⇒ تستنى (القراية نفسها زي ما هي —
 *   ملف المرجع `golden/sms.json`).
 * - **تاريخ العملية بعد يوم الوصول** (حوالة مجدولة لبكرة) ⇒ تستنى — القراية لسه بتقبل يوم بعد الوصول (ملف المرجع).
 * القراية ما بتتغيرش هنا: البوابة بتحوّل [SmsShape] لـ[SmsShape.KeywordFallback] بس (الرسالة بتفضل في الصندوق مستنية تأكيد المالك).
 */

private val GI = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"
private const val NA = "(?<![$AR])"
private const val NZ = "(?![$AR])"

/** ضماير متصلة بعد الكلمة («استرجاعها» · «تعليقها» · «ارجاعه» · «إلغاؤها») — الجولة السادسة (كان الحد بيمنعها). */
private const val SUF = "(?:ه|ها|هم|هما|كم|ك|ي)?"

/**
 * كلام بيقول إن الحركة لسه ما خلصتش · اتعكست أو رجعت · اترفضت · طلب أو حاجة جاية — في **مكان مش من القالب**. بيتفحص على النص بعد
 * توحيد الألف والياء ([hasShapeDoubt]). **الجولة السادسة:** ده خط دفاع تاني بس — الأساس إن الخانة الحرة نفسها بقت محصورة
 * (`SmsSaudiLines.kt` · `SmsKnownShapesEgypt.kt`)؛ الإضافات من رسايل المراجع (جذور «عكس/رد/استرد/حجز/وقف/جمد/علّق» بصيغها ·
 * المصري «متمتش/مطلعتش/هيتضاف/مستني/في الطريق» · recall · revert · timed out · retained · not yet · provisional · verification …).
 */
internal val SHAPE_DOUBT = Regex(
    // الجولة التامنة: «Scheduled transfer completed/executed» خلص (مش «to be executed»)
    "$B(?:revers(?:e|ed|al)|chargeback|pending|processing|in$S+process|awaiting|initiated|submitted" +
        "|scheduled(?![^\\n]{0,60}?(?<!(?:to|will|shall)$S{1,3}be$S{1,3})${B}(?:executed|completed)$B)|queued|on$S+hold" +
        "|auth(?:ori[sz]ation)?$S+hold|reserved|held|declined|rejected|refused|failed|unsuccessful|cancel(?:l?ed|lation)|void(?:ed)?" +
        "|expired|incomplete|unpaid|overdue|reminder|blocked|suspended|frozen|e-?statement|statement|refund$S+(?:request|initiated)" +
        "|returned$S+(?:to|by)|request(?:ed)?|future.?dated|next$S+instal?l?ments?|standing$S+order" +
        "|recall(?:ed)?|revert(?:ed)?|roll(?:ed)?$S*back|timed?$S*out|retain(?:ed)?|withh(?:e|o)ld|bounced?|unsettled|unposted" +
        "|provisional(?:ly)?|temporar(?:y|ily)|pre-?approv(?:al|ed)|pre-?auth(?:ori[sz](?:ation|ed))?|auth(?:ori[sz]ation)?$S+only" +
        "|verification|verify(?:ing)?|counting|tomorrow|yesterday|later|due|in$S+transit|on$S+(?:its|the)$S+way|subject$S+to" +
        "|not$S+yet|yet$S+to|could$S+not|cannot|can'?t|guarantee|security$S+deposit|before$S+execution|will$S+reflect|mistaken?|erroneous(?:ly)?)$B" +
        "|${B}to$S+be$S+[A-Za-z]+ed$B" +
        "|${B}not$S+(?:yet$S+)?(?:been$S+)?(?:accepted|sent|dispensed|paid|completed|authori[sz]ed|approved|successful|processed|executed|received" +
        "|credited|done|posted|deposited|settled|charged|reflected|confirmed|cleared|delivered|transferred)$B" +
        "|${B}under$S+(?:review|clearing|collection|process(?:ing)?)$B" +
        // «معلقة» كلمة لوحدها بس («مطعم المعلقة الذهبية» اسم محل)
        "|$NA(?:معلق[ةه]?)$NZ" +
        "|$NA[وف]?(?:بال|لل|ال|ب|ل)?(?:عكس|إرجاع|ارجاع|مرتجع[ةه]?|استرداد|استرجاع|معاد[ةه]?|انتظار|مجدول[ةه]?|جدولة|مؤجل[ةه]?" +
        "|تعليق|مرفوض[ةه]?|فشلت?|تعذر|ملغا[ةه]|ملغي[ةه]?|ملغى|إلغاء|الغاء|الغا[ؤئ]|اتلغت|اتلغ[ىي]|تذكير|محجوز[ةه]?|حجز|قادم[ةه]?" +
        // ── الجولة السادسة ──
        "|معكوس[ةه]?|انعكاس|مسترد[ةه]?|مسترجع[ةه]?|مرتد[ةه]?|مردود[ةه]?|محتجز[ةه]?|احتجاز|مجمد[ةه]?|تجميد|متعلق[ةه]?|موقوف[ةه]?|ايقاف" +
        "|متوقف[ةه]?|مؤقت[ةه]?|مؤقتا|مستني[ةه]?|منتظر[ةه]?|مسوا[ةه]|مسوي[ةه]|لحين|غلط|امبارح|البارح[ةه]|بكر[ةها]|غدا|لاحقا" +
        "|[تي]ستحق|[تي]ودع)$SUF$NZ" +
        // تأمين/ضمان **كمبلغ محجوز** بس («مبلغ تأمين» · «حجز ضمان») — مش «شركة التأمين» ولا «الضمان الاجتماعي» (دخل حقيقي)
        "|$NA(?:مبلغ|حجز|خطاب|وديع[ةه])$S*(?:ال)?(?:تأمين|تامين|ضمان)" +
        "|$NA[وف]?(?:اتعكس|انعكس|اتوقف|اوقف|توقف|اترفض|الغيت|الغي|اتحجز|اتجمد|اتعلق|اترجع|رجع|استرد|استرجع)(?:ت|تلك|لك|وا)?$SUF$NZ" +
        "|$NA[وف]?[هح](?:يت|تت|ي|ت)(?:ضاف|نفذ|سحب|رجع|فك|حول|خصم|رد|صرف|حط|نزل|سجل|اكد|فعل|ظهر|وصل|ودع|حجز|علق)|$NA(?:ما$S*|م)ا?(?:تمت|تم|نجحت|نجح|كملت|كمل|طلعت|طلع" +
        "|اتنفذت|اتنفذ|وصلت|وصل|اتحولت|اتحول)ش$NZ|${NA}في$S*(?:ال)?طريق|${NA}لحد$S+ما$NZ" +
        "|قيد$S*(?:ال)?(?:معالجة|تسوية|إجراء|اجراء|مراجعة|تحقق|انتظار|تنفيذ)|تحت$S*(?:ال)?(?:إجراء|اجراء|مراجعة|تحصيل|معالجة|تسوية|تحقق)" +
        "|جار[يى]$S*(?:ال)?(?:تنفيذ|معالجة|تأكيد|تاكيد|عمل|تحويل|إيداع|ايداع|سداد|خصم|مراجعة|تحقق)" +
        "|$NA(?:غير|عدم)$S*(?:مقبول|مكتمل|ناجح|منفذ|مسدد|مسوا[ةه]|مسوي[ةه]|مرحل[ةه]?|مقيد[ةه]?|نهائي[ةه]?)" +
        "|$NA(?:لم|لن|ماتمش|مانجحتش)$NZ|${NA}ما$S*(?:تمتش|نجحتش)$NZ" +
        "|منتهي[ةه]?$S*الصلاحية|طلب$S*(?:ال)?(?:استرداد|استرجاع|سحب|دفع|تحويل)|(?:يرجى|يرجي|برجاء|الرجاء)$S*(?:إيداع|ايداع|سداد|دفع|تحويل)" +
        // ── الجولة السابعة: «To be <أي فعل>» (sent · cleared · released …) · المتوقع/المقرر · «عند تحصيل الشيك» · «الى حين» · «حدّث بياناتك»
        // · «يضاف/سيضاف» · الموافقة والقبول المصري · approval/kyc/aml/uncleared/unconfirmed/estimated/incremental/validation ──
        "|${B}to$S+be$B|$NA(?:من$S*)?(?:ال)?(?:متوقع|مقرر)$NZ|${NA}عند$S*(?:ال)?(?:تحصيل|اكتمال|استكمال|اتمام|إتمام)" +
        "|$NA(?:الي|الى|إلى)$S*حين$NZ|(?:فضلك|يرجي|يرجى|برجاء|الرجاء)$S*(?:حدث|تحديث)|$NA[وف]?(?:س)?[يت]ضاف$SUF$NZ" +
        "|$NA(?:محتاج|محتاجة|محتاجه|موافقت|اقبل|ارفض)[$AR]*|$NA(?:لسه|لسة)$NZ" +
        "|$B(?:approval|acceptance|kyc|aml|uncleared|unconfirmed|unverified|estimated|incremental|validation|awaits?|awaited|draft)$B",
    GI,
)

/** «أ/إ/آ» ⇒ «ا» · «ى» ⇒ «ي» (زي `shapeKey`) — «أُلغيت» و«إلغاؤها» و«الى» بنفس الأنماط. */
private fun unifyLetters(text: String): String =
    text.map { c -> if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') 'ا' else if (c == 'ى') 'ي' else c }.joinToString("")

/** الجولة السابعة: كلمة الحالة اللازقة في رقم («778812REVERSED») بتتفصل قبل الفحص (`\b` بتاع جافاسكربت بيعتبر الرقم والحرف كلمة واحدة). */
internal fun hasShapeDoubt(text: String): Boolean = SHAPE_DOUBT.containsMatchIn(splitDigitLetter(unifyLetters(guardText(text))))

/** أي تاريخ: بسنة كاملة أو سنتين («26-03-05» · «05/03/2026») · يوم/شهر في مكانه («يوم 03-05» · «on 05/03») · الشهر بالاسم. */
private val DATE_TOKEN = Regex(
    "(?<![\\d.,])\\d{1,4}[-/\\\\]\\d{1,2}[-/\\\\]\\d{1,4}(?![\\d,])|(?<![\\d.,])\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?![\\d.,])" +
        "|(?:(?<![A-Za-z])(?:on|dated)|(?<![$AR])(?:يوم|في|فى|بتاريخ))[ \\t]*[:：]?[ \\t]*\\d{1,2}[-/\\\\]\\d{1,2}(?![\\d,/\\\\-])" +
        "|(?<![A-Za-z])(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?[ \\t]+\\d{1,2},?[ \\t]+\\d{4}" +
        // الجولة السادسة: خانة «تاريخ العملية» (فودافون كاش) من غير سنة والساعة قبله («23:50 7/10» · «21:05 07.10» · «6/10»)
        "|$VF_DATE_SLOT",
    GI,
)
private val DIGITS = Regex("\\d+")

private fun dateKey(value: String): String = DIGITS.findAll(value).joinToString("-") { it.value.trimStart('0') }

/** عدد التواريخ **المختلفة** في الرسالة (نفس التاريخ مكتوب مرتين = واحد). */
internal fun distinctDateCount(body: String): Int = DATE_TOKEN.findAll(body).map { dateKey(it.value) }.toSet().size

/**
 * الجولة التامنة — مصر: نفس العدّ + «يوم/شهر» من غير سنة في أي مكان (`egyptPartialDateMatches` — «08.10 23:58: …» · «… 900000553 06/10 …»)
 * اللي مش جوه تاريخ اتعدّ خلاص.
 */
internal fun egyptDistinctDateCount(body: String): Int {
    val tokens = DATE_TOKEN.findAll(body).toList()
    val keys = tokens.map { dateKey(it.value) }.toMutableSet()
    for (m in egyptPartialDateMatches(body)) {
        if (tokens.none { it.range.first <= m.range.last && m.range.first <= it.range.last }) keys += dateKey(m.value)
    }
    return keys.size
}

/**
 * آخر خطوة في الشكل (القارئين): الشكل الواضح بيفضل واضح بس لو الرسالة فيها تاريخ واحد بس، وتاريخ العملية مش بعد يوم الوصول
 * ([arrivalDay] بتوقيت البلد؛ null = مش معروف ⇒ ما بيتفحصش)، والرسالة الأصلية [raw] مفيهاش حروف مخفية ولا أشكال عرض عربية
 * (الجولة السادسة — `SmsHiddenText.kt`).
 */
internal fun gateShape(shape: SmsShape, body: String, date: IsoDate, arrivalDay: IsoDate?, raw: String): SmsShape = when {
    !shape.clear -> shape
    distinctDateCount(body) > 1 -> SmsShape.KeywordFallback
    arrivalDay != null && date > arrivalDay -> SmsShape.KeywordFallback
    arrivalDay != null && olderThanWindow(date, arrivalDay) -> SmsShape.KeywordFallback
    hasHiddenOrPresentationChars(raw) -> SmsShape.KeywordFallback
    else -> shape
}

/** أقصى فرق بين تاريخ العملية ويوم الوصول عشان الرسالة تتسجل لوحدها — نفس نافذة التاريخ من غير سنة (`SmsDates.kt`). */
private const val WINDOW_DAYS = 60

/**
 * الجولة السابعة: التاريخ بسنة كاملة **أقدم من 60 يوم** قبل الوصول ⇒ تستنى. القراية نفسها زي ما هي (ملف المرجع بيقبل التاريخ القديم
 * بسنة — سؤال (و) لسه مفتوح للمالك)، بس ما بتتسجلش لوحدها: «On: 10/07/2026» يوم 8 أكتوبر كانت بتتسجل 10 يوليو، والقراية التانية
 * (7 أكتوبر) هي اللي في النافذة · «On: 2025-11-02» كانت بتتسجل في شهر مالي قديم.
 */
private fun olderThanWindow(date: IsoDate, arrivalDay: IsoDate): Boolean =
    toDayNumber(parseIsoDate(date)) < toDayNumber(parseIsoDate(arrivalDay)) - WINDOW_DAYS
