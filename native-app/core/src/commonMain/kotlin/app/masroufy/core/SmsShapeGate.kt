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

/** كلام بيقول إن الحركة لسه ما خلصتش · اتعكست أو رجعت · اترفضت · طلب أو حاجة جاية — في **مكان مش من القالب**. */
internal val SHAPE_DOUBT = Regex(
    "$B(?:revers(?:e|ed|al)|chargeback|pending|processing|in$S+process|awaiting|initiated|submitted|scheduled|queued|on$S+hold" +
        "|auth(?:ori[sz]ation)?$S+hold|reserved|held|declined|rejected|refused|failed|unsuccessful|cancel(?:l?ed|lation)|void(?:ed)?" +
        "|expired|incomplete|unpaid|overdue|reminder|blocked|suspended|frozen|e-?statement|statement|refund$S+(?:request|initiated)" +
        "|returned$S+(?:to|by)|request(?:ed)?|future.?dated|next$S+instal?l?ments?|standing$S+order)$B" +
        "|${B}not$S+(?:been$S+)?(?:accepted|sent|dispensed|paid|completed|authori[sz]ed|approved|successful|processed|executed|received|credited|done)$B" +
        "|${B}under$S+(?:review|clearing|collection|process(?:ing)?)$B" +
        // «معلقة» كلمة لوحدها بس («مطعم المعلقة الذهبية» اسم محل)
        "|$NA(?:معلق[ةه]?)$NZ" +
        "|$NA[وف]?(?:بال|لل|ال|ب|ل)?(?:عكس|إرجاع|ارجاع|مرتجع[ةه]?|استرداد|استرجاع|معاد[ةه]?|انتظار|مجدول[ةه]?|جدولة|مؤجل[ةه]?" +
        "|تعليق|مرفوض[ةه]?|فشلت?|تعذر|ملغا[ةه]|ملغي[ةه]?|ملغى|إلغاء|الغاء|اتلغت|اتلغ[ىي]|تذكير|محجوز[ةه]?|حجز|قادم[ةه]?)$NZ" +
        "|قيد$S*(?:ال)?(?:معالجة|تسوية|إجراء|اجراء|مراجعة|تحقق|انتظار|تنفيذ)|تحت$S*(?:ال)?(?:إجراء|اجراء|مراجعة|تحصيل|معالجة|تسوية|تحقق)" +
        "|جار[يى]$S*(?:ال)?(?:تنفيذ|معالجة|تأكيد|تاكيد|عمل|تحويل|إيداع|ايداع|سداد|خصم|مراجعة|تحقق)" +
        "|$NA(?:غير|عدم)$S*(?:مقبول|مكتمل|ناجح|منفذ|مسدد)|$NA(?:لم|لن|ماتمش|مانجحتش)$NZ|${NA}ما$S*(?:تمتش|نجحتش)$NZ" +
        "|منتهي[ةه]?$S*الصلاحية|طلب$S*(?:ال)?(?:استرداد|استرجاع|سحب|دفع|تحويل)|(?:يرجى|يرجي|برجاء|الرجاء)$S*(?:إيداع|ايداع|سداد|دفع|تحويل)",
    GI,
)

internal fun hasShapeDoubt(text: String): Boolean = SHAPE_DOUBT.containsMatchIn(guardText(text))

/** أي تاريخ: بسنة كاملة أو سنتين («26-03-05» · «05/03/2026») · يوم/شهر في مكانه («يوم 03-05» · «on 05/03») · الشهر بالاسم. */
private val DATE_TOKEN = Regex(
    "(?<![\\d.,])\\d{1,4}[-/\\\\]\\d{1,2}[-/\\\\]\\d{1,4}(?![\\d,])|(?<![\\d.,])\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}(?![\\d.,])" +
        "|(?:(?<![A-Za-z])(?:on|dated)|(?<![$AR])(?:يوم|في|فى|بتاريخ))[ \\t]*[:：]?[ \\t]*\\d{1,2}[-/\\\\]\\d{1,2}(?![\\d,/\\\\-])" +
        "|(?<![A-Za-z])(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?[ \\t]+\\d{1,2},?[ \\t]+\\d{4}",
    GI,
)
private val DIGITS = Regex("\\d+")

/** عدد التواريخ **المختلفة** في الرسالة (نفس التاريخ مكتوب مرتين = واحد). */
internal fun distinctDateCount(body: String): Int =
    DATE_TOKEN.findAll(body).map { m -> DIGITS.findAll(m.value).joinToString("-") { it.value.trimStart('0') } }.toSet().size

/**
 * آخر خطوة في الشكل (القارئين): الشكل الواضح بيفضل واضح بس لو الرسالة فيها تاريخ واحد بس، وتاريخ العملية مش بعد يوم الوصول
 * ([arrivalDay] بتوقيت البلد؛ null = مش معروف ⇒ ما بيتفحصش).
 */
internal fun gateShape(shape: SmsShape, body: String, date: IsoDate, arrivalDay: IsoDate?): SmsShape = when {
    !shape.clear -> shape
    distinctDateCount(body) > 1 -> SmsShape.KeywordFallback
    arrivalDay != null && date > arrivalDay -> SmsShape.KeywordFallback
    else -> shape
}
