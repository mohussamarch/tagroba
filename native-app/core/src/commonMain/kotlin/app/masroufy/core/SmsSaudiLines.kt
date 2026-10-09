package app.masroufy.core

/**
 * **كل سطر بعد العنوان** في رسالة البنك السعودي لازم يبقى خانة معروفة (الجولة الخامسة — قايمة بيضا، مش قايمة سودا): المبلغ ·
 * المحل/الطرف · الكارت · الحساب · التاريخ · الرصيد · الرسوم والضريبة · المرجع · الفاتورة · الدولة · سطر اسم البنك · سطر تحذير
 * معروف في الآخر. أي سطر تاني («Status: Processing» · «الحالة: تحت الإجراء» · «حوالة مجدولة» · «Auth hold: SAR 600» ·
 * «حد الائتمان: …» · «رمز لمرة واحدة 731905» · «تاريخ الاستحقاق: …» · جملة حرة) ⇒ الشكل مش معروف ⇒ الرسالة **تستنى** تأكيد المالك.
 * الخانات من قوالب البحث (`research/banks/saudi-sms-formats.json`) — والقياس (`SmsTemplateCoverageTest`) بيمسك إن كل قالب لسه معروف.
 * الأنماط على [shapeKey] **بالنقطتين** (حروف صغيرة · «ا» مكان «أ/إ/آ» · «ي» مكان «ى» · مسافة واحدة). القيمة الحرة (اسم المحل أو
 * الطرف أو البنك) بتتفحص بـ[hasShapeDoubt] (كلمة حالة أو عكس جوه الاسم ⇒ تستنى).
 */

private const val NUM = "[\\d,٬]+(?:[.٫]\\d+)?"
private const val CUR = "(?:sar|sr|ريال(?: سعودي)?|ر\\.? ?س\\.?)"
private const val MONEY = "(?:$CUR ?$NUM|$NUM ?$CUR)"
private const val MONEY_OR_NUM = "(?:$MONEY|$NUM)"
private const val DATE = "\\d{1,4}[-/.\\\\]\\d{1,2}[-/.\\\\]\\d{1,4}"
private const val TIME = "\\d{1,2}:\\d{2}(?::\\d{2})?(?: ?(?:am|pm|ص|م))?"
private const val DT = "(?:$DATE|$TIME)(?:,? (?:$DATE|$TIME))*"
private const val ACCT = "(?:sa)?[*•x#]*[\\d*•x#][\\d*•x# ]*"
private const val LETTERS = "[a-z\\u0600-\\u06FF]"
private const val CARD = "(?:$LETTERS[a-z\\u0600-\\u06FF ]{0,24} )?$ACCT(?: ?[;,\\- ] ?[a-z\\u0600-\\u06FF ()\\-;.]{0,40})?"
private const val SEP = "(?: ?: ?| )"

/** «لابل: قيمة» — [value] نمط القيمة كله. */
private fun field(labels: String, value: String) = Regex("^(?:$labels)$SEP$value$")

private val AMOUNT = field("مبلغ|المبلغ|بمبلغ|القيمة|القسط|amount|ب|اعادة مبلغ|المبلغ المتبقي", MONEY)
private val FEE_OR_BALANCE = field(
    "رسوم|الرسوم|fee|fees|vat|ضريبة القيمة المضافة|رسوم العملية|رسوم تحويل العملات|رسوم وضريبة|exchange rate|سعر الصرف~?" +
        "|total due amount|اجمالي المبلغ المستحق|رصيد|الرصيد|الرصيد المتاح|الرصيد المتبقي|remaining balance|available balance|balance",
    MONEY_OR_NUM,
)
private val DATE_FIELD = Regex("^(?:(?:في|on|at|date|بتاريخ|التاريخ|تاريخ العملية)$SEP|في(?=\\d))$DT$")
private val CARD_FIELD = field(
    "بطاقة|البطاقة|البطاقة الائتمانية|بطاقة مدي|لبطاقة مدي|ببطاقة مدي|للبطاقة|card|via|by|مدي|مدي-$LETTERS+",
    CARD,
)
private val ACCOUNT_FIELD = field(
    "حساب|الحساب|في حساب|لحساب|من حساب|الي حساب|حساب صاحب الشيك|acc|account|account number|from account|iban|ايبان|الايبان|خصمت من حساب",
    "$ACCT(?: ?- ?$LETTERS[a-z\\u0600-\\u06FF ]*)?",
)

/** خانات قيمتها حرة (اسم المحل · الطرف · البنك · الخدمة · المرجع · الدولة) — الإنجليزي من غير «:» محصور في اللي البحث كاتبه كده. */
private val FREE_FIELD = Regex(
    "^(?:(?:لدي|عند|at|التاجر|من البائع|من|الي|to|from|ل|المستفيد|عبر|مصرف|البنك|من بنك|المفوتر|مفوتر|الخدمة|لخدمة|الفاتورة|الجهة" +
        "|mtcn|مرجع|الرقم المرجعي|ref|ref\\. no\\.|ref no|مكان السحب|الصراف|دولة|الدولة|country)$SEP" +
        "|(?:merchant|transaction|receiver|biller|service|number) ?: ?|من(?=[\\d*•x]))(.+)$",
)

/** سطر لوحده: اسم البنك («بنك الرياض» · «Riyad Bank») · المبلغ لوحده («1,250.00 SAR») · التاريخ والساعة · «{amount} SAR :المبلغ». */
private val BANK_LINE = Regex("^(?:(?:بنك|مصرف) [\\u0600-\\u06FF ]{2,40}|(?:[a-z]+ ){1,3}bank|bank [a-z ]{2,40})$")
private val ALONE = Regex("^(?:$MONEY|$DT|$MONEY ?: ?المبلغ)$")
private val STATUS_DONE = Regex("^(?:status|الحالة) ?: ?(?:successful|success|completed|approved|done|ناجحة|ناجح|مكتملة|تمت|منفذة|مقبولة)$")

/**
 * سطر تحذير معروف في آخر الرسالة («للاعتراض … اتصل» · «If you have not authorized …» · «لا تشارك …» · «For info call …»). أي رقم
 * فيه لازم يبقى رقم تليفون بعد «اتصل/call/على/ب» — رقم 4-8 لوحده (رمز) ⇒ مش سطر تحذير.
 */
private val FOOTER = Regex(
    "^(?:للاعتراض|لايقاف|لالغاء|اذا لم|في حال|if you|if this|to dispute|never share|do not share|لا تشارك|تذكير: لا تشارك|for info" +
        "|for more details|for details|للمزيد|للاستفسار|call us|not attempted|no authori[sz]ation|lost card)",
)
private val PHONE = Regex("(?:call|اتصل|اتصال|على|علي|on|ب)(?: ب)? ?:? ?\\+?\\d[\\d -]{3,15}")
private val CODE = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

/** خانات زيادة في قالب بنك واحد بس (السطر ده مش خانة عامة). */
private val TEMPLATE_EXTRA: Map<String, Regex> = mapOf(
    "stc/fee" to Regex("^reason ?: ?(.+)$"), // #112 «Reason: {reason}»
    "stc/reversal-ar" to Regex("^في ?: ?(.+)$"), // #96 «في: <المحل>» والتاريخ في «بتاريخ:»
    "alrajhi/deposit-government-support" to Regex("^([\\u0600-\\u06FF ]{2,40})$"), // #26 «{note}» = «حساب المواطن»
)

/** «لـTEST» · «بـSAR 25» · «بـ:87.50» ⇒ «ل: TEST» · «ب: SAR 25» (التطويل بيتشال في [shapeKey]، فـ«لـمطعم» كانت هتبقى «لمطعم»). */
private val GLUED_LAM_BA = Regex("^[ \\t]*([لب])ـ+[ \\t]*[:：]?[ \\t]*")

private fun keyOf(line: String): String = shapeKey(GLUED_LAM_BA.replace(line, "$1: "), dropColons = false)

/** سطر واحد معروف؟ القيمة الحرة (لو فيه) لازم ما يبقاش فيها كلمة حالة أو عكس. */
private fun knownLine(line: String, template: String?): Boolean {
    val key = keyOf(line)
    if (key.isEmpty()) return true
    if (AMOUNT.matches(key) || FEE_OR_BALANCE.matches(key) || DATE_FIELD.matches(key) || CARD_FIELD.matches(key) ||
        ACCOUNT_FIELD.matches(key) || ALONE.matches(key) || STATUS_DONE.matches(key)
    ) {
        return true
    }
    if (FOOTER.containsMatchIn(key)) return !CODE.containsMatchIn(PHONE.replace(key, " "))
    if (BANK_LINE.matches(key)) return !hasShapeDoubt(key)
    val free = FREE_FIELD.matchEntire(key) ?: template?.let { TEMPLATE_EXTRA[it]?.matchEntire(key) } ?: return false
    return !hasShapeDoubt(free.groupValues[1])
}

/** كل السطور [lines] (اللي بعد العنوان) معروفة. [template] = `<البنك>/<القالب>` لو قالب بنك (للخانات الخاصة بيه). */
internal fun allLinesKnown(lines: List<String>, template: String?): Boolean = lines.all { knownLine(it, template) }
