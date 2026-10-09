package app.masroufy.core

/**
 * **كل سطر بعد العنوان** في رسالة البنك السعودي لازم يبقى خانة معروفة (الجولة الخامسة — قايمة بيضا، مش قايمة سودا): المبلغ ·
 * المحل/الطرف · الكارت · الحساب · التاريخ · الرصيد · الرسوم والضريبة · سعر الصرف · المرجع · الفاتورة · الدولة · سطر اسم البنك · سطر
 * تحذير معروف في الآخر. أي سطر تاني ⇒ الشكل مش معروف ⇒ الرسالة **تستنى** تأكيد المالك.
 * الخانات من قوالب البحث (`research/banks/saudi-sms-formats.json`) — والقياس (`SmsTemplateCoverageTest`) بيمسك إن كل قالب لسه معروف.
 * الأنماط على [shapeKey] **بالنقطتين** (حروف صغيرة · «ا» مكان «أ/إ/آ» · «ي» مكان «ى» · مسافة واحدة).
 *
 * **الجولة السادسة** (مراجعة عدائية تانية — الخانات الحرة كانت بتعدّي كلام حالة):
 * - سطر التحذير واسم البنك وذيل الكارت والحساب **قوايم مقفولة** (`SmsSaudiVocab.kt`).
 * - القيمة الحرة (المحل · الطرف · المرجع · الخدمة) **محصورة** ([freeValueOk]): من غير أقواس ولا « - » ولا فاصلة بعدها كلام ولا
 *   علامة استفهام، 8 كلمات بالكتير، ومفيهاش كلمة حالة ([hasShapeDoubt]) — «QASR HOTEL - AUTH ONLY» · «WADI FUEL (مبلغ مؤقت)» ·
 *   «سامر التجريبي - تم استرجاعها» ما بقوش اسم.
 * - المبلغ لازم بفواصل سليمة ومنزلتين بالكتير («1.250 SAR» قراية ملف المرجع — مش سطر مبلغ معروف).
 * - سعر صرف ≠ 1 أو دولة غير السعودية ⇒ العملية برّه البلد ([abroadLines] — `SmsKnownShapesSaudi.kt` بيخليها تستنى، §75-12).
 */

private const val NUM = "(?:\\d{1,3}(?:[,٬]\\d{3})+|\\d+)(?:[.٫]\\d{1,2})?"
private const val RATE = "\\d+(?:[.٫]\\d+)?"
private const val CUR = "(?:sar|sr|ريال(?: سعودي)?|ر\\.? ?س\\.?)"
private const val MONEY = "(?:$CUR ?$NUM|$NUM ?$CUR)"
private const val MONEY_OR_NUM = "(?:$MONEY|$NUM)"
private const val DATE = "\\d{1,4}[-/.\\\\]\\d{1,2}[-/.\\\\]\\d{1,4}"
private const val TIME = "\\d{1,2}:\\d{2}(?::\\d{2})?(?: ?(?:am|pm|ص|م))?"
private const val DT = "(?:$DATE|$TIME)(?:,? (?:$DATE|$TIME))*"
private const val ACCT = "(?:sa)?[*•x#]*[\\d*•x#][\\d*•x# ]*"
private const val LETTERS = "[a-z\\u0600-\\u06FF]"
private const val SEP = "(?: ?: ?| )"

/** «لابل: قيمة» — [value] نمط القيمة كله. */
private fun field(labels: String, value: String) = Regex("^(?:$labels)$SEP$value$")

private val AMOUNT = field("مبلغ|المبلغ|بمبلغ|القيمة|القسط|amount|ب|اعادة مبلغ|المبلغ المتبقي", MONEY)
private val FEE_OR_BALANCE = field(
    "رسوم|الرسوم|fee|fees|vat|ضريبة القيمة المضافة|رسوم العملية|رسوم وضريبة" +
        "|total due amount|اجمالي المبلغ المستحق|رصيد|الرصيد|الرصيد المتاح|الرصيد المتبقي|remaining balance|available balance|balance",
    MONEY_OR_NUM,
)

/** سعر الصرف أو رسوم تحويل العملات — رقم (السعر) أو مبلغ بالريال (الرسوم). */
private val RATE_LINE = field("exchange rate|سعر الصرف~?|رسوم تحويل العملات", "(?:($RATE)|$MONEY)")
private val COUNTRY_LINE = field("دولة|الدولة|country", "([a-z\\u0600-\\u06FF][a-z\\u0600-\\u06FF .]{1,40})")
private val DATE_FIELD = Regex("^(?:(?:في|on|at|date|بتاريخ|التاريخ|تاريخ العملية)$SEP|في(?=\\d))$DT$")
private val CARD_FIELD = field(
    "بطاقة|البطاقة|البطاقة الائتمانية|بطاقة مدي|لبطاقة مدي|ببطاقة مدي|للبطاقة|card|via|by|عبر|مدي|مدي-$LETTERS+",
    "$SA_CARD_HEAD$ACCT$SA_CARD_TAIL",
)
private val ACCOUNT_FIELD = field(
    "حساب|الحساب|في حساب|لحساب|من حساب|الي حساب|حساب صاحب الشيك|acc|account|account number|from account|iban|ايبان|الايبان|خصمت من حساب",
    "$ACCT(?: ?- ?$ACCOUNT_TAIL)?",
)

/** خانات قيمتها حرة (اسم المحل · الطرف · البنك · الخدمة · المرجع) — الإنجليزي من غير «:» محصور في اللي البحث كاتبه كده. */
private val FREE_FIELD = Regex(
    "^(?:(?:لدي|عند|at|التاجر|من البائع|من|الي|to|from|ل|المستفيد|عبر|مصرف|البنك|من بنك|المفوتر|مفوتر|الخدمة|لخدمة|الفاتورة|الجهة" +
        "|mtcn|مرجع|الرقم المرجعي|ref|ref\\. no\\.|ref no|مكان السحب|الصراف)$SEP" +
        "|(?:merchant|transaction|receiver|biller|service|number) ?: ?|من(?=[\\d*•x]))(.+)$",
)

/** سطر لوحده: اسم بنك من القايمة · المبلغ لوحده («1,250.00 SAR») · التاريخ والساعة · «{amount} SAR :المبلغ». */
private val BANK_LINE = Regex("^(?:$SAUDI_BANK_NAMES)$")
private val ALONE = Regex("^(?:$MONEY|$DT|$MONEY ?: ?المبلغ)$")
private val STATUS_DONE = Regex("^(?:status|الحالة) ?: ?(?:successful|success|completed|approved|done|ناجحة|ناجح|مكتملة|تمت|منفذة|مقبولة)$")

/** خانات زيادة في قالب بنك واحد بس (السطر ده مش خانة عامة). */
private val TEMPLATE_EXTRA: Map<String, Regex> = mapOf(
    "stc/fee" to Regex("^reason ?: ?(.+)$"), // #112 «Reason: {reason}»
    "stc/reversal-ar" to Regex("^في ?: ?(.+)$"), // #96 «في: <المحل>» والتاريخ في «بتاريخ:»
    "alrajhi/deposit-government-support" to Regex("^([\\u0600-\\u06FF ]{2,40})$"), // #26 «{note}» = «حساب المواطن»
)

/** «لـTEST» · «بـSAR 25» · «بـ:87.50» ⇒ «ل: TEST» · «ب: SAR 25» (التطويل بيتشال في [shapeKey]، فـ«لـمطعم» كانت هتبقى «لمطعم»). */
private val GLUED_LAM_BA = Regex("^[ \\t]*([لب])ـ+[ \\t]*[:：]?[ \\t]*")

private fun keyOf(line: String): String = shapeKey(GLUED_LAM_BA.replace(line, "$1: "), dropColons = false)

/**
 * جملة زيادة جوه القيمة الحرة: قوس · شَرطة جنب مسافة («X - Y» · «X -Y») · فاصلة بعدها كلام · علامة استفهام أو تعجب.
 * «AL-OTHAIM» (شَرطة جوه الكلمة) و«7719;خالد» (الراجحي 2026) ماشيين.
 */
private val CLAUSE = Regex("[()\\[\\]{}]|\\s[-–—]|[-–—]\\s|[,،]\\s*\\S|[!؟?]")
private const val MAX_FREE_WORDS = 8

/** القيمة الحرة اسم بس (مش اسم وبعده جملة حالة). */
internal fun freeValueOk(value: String): Boolean {
    val text = value.trim()
    return text.isNotEmpty() && !CLAUSE.containsMatchIn(text) && text.split(' ').count { it.isNotEmpty() } <= MAX_FREE_WORDS && !hasShapeDoubt(text)
}

/** سطر واحد معروف؟ القيمة الحرة (لو فيه) لازم تبقى اسم بس ([freeValueOk]). */
private fun knownLine(line: String, template: String?): Boolean {
    val key = keyOf(line)
    if (key.isEmpty()) return true
    if (AMOUNT.matches(key) || FEE_OR_BALANCE.matches(key) || RATE_LINE.matches(key) || COUNTRY_LINE.matches(key) ||
        DATE_FIELD.matches(key) || CARD_FIELD.matches(key) || ACCOUNT_FIELD.matches(key) || ALONE.matches(key) ||
        STATUS_DONE.matches(key) || BANK_LINE.matches(key) || isKnownFooter(key)
    ) {
        return true
    }
    val free = FREE_FIELD.matchEntire(key) ?: template?.let { TEMPLATE_EXTRA[it]?.matchEntire(key) } ?: return false
    return freeValueOk(free.groupValues[1])
}

/** كل السطور [lines] (اللي بعد العنوان) معروفة. [template] = `<البنك>/<القالب>` لو قالب بنك (للخانات الخاصة بيه). */
internal fun allLinesKnown(lines: List<String>, template: String?): Boolean = lines.all { knownLine(it, template) }

private val ONE = Regex("^1(?:[.٫]0*)?$")

/**
 * العملية **برّه البلد** من سطورها (الجولة السادسة — §75-12 · اختيار (م)): سعر صرف ≠ 1 · رسوم تحويل عملات بمبلغ · دولة غير السعودية.
 * («Exchange rate: 4.6875» + «Country: GB» بمبلغ بالريال كانت بتتسجل لوحدها تحت «Online Purchase».)
 */
internal fun abroadLines(lines: List<String>): Boolean = lines.any { line ->
    val key = keyOf(line)
    val rate = RATE_LINE.matchEntire(key)
    val country = COUNTRY_LINE.matchEntire(key)
    when {
        rate != null -> rate.groupValues[1].let { it.isEmpty() || !ONE.matches(it) } // مبلغ رسوم تحويل عملات، أو سعر ≠ 1
        country != null -> !SAUDI_COUNTRY.matches(country.groupValues[1].trim())
        else -> false
    }
}
