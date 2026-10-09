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

/** «لابل: قيمة» — [value] نمط القيمة كله. S1 (§77-A): القيمة = أول مجموعة التقاط، عشان بصمة الشكل تشيلها وتسيب اللابل ([labelOf]). */
private fun field(labels: String, value: String) = Regex("^(?:$labels)$SEP($value)$")

/** اللابل = السطر من غير القيمة اللي في آخره (المجموعة الأولى) — من غير النقطتين والمسافات. */
private fun labelOf(key: String, m: MatchResult): String = key.dropLast(m.groupValues[1].length).trim(' ', ':')

private val AMOUNT = field("مبلغ|المبلغ|بمبلغ|القيمة|القسط|amount|ب|اعادة مبلغ|المبلغ المتبقي", MONEY)
private val FEE_OR_BALANCE = field(
    "رسوم|الرسوم|fee|fees|vat|ضريبة القيمة المضافة|رسوم العملية|رسوم وضريبة" +
        "|total due amount|اجمالي المبلغ المستحق|رصيد|الرصيد|الرصيد المتاح|الرصيد المتبقي|remaining balance|available balance|balance",
    MONEY_OR_NUM,
)

/** سعر الصرف أو رسوم تحويل العملات — رقم (السعر) أو مبلغ بالريال (الرسوم). */
private val RATE_LINE = field("exchange rate|سعر الصرف~?|رسوم تحويل العملات", "(?:($RATE)|$MONEY)")
private val COUNTRY_LINE = field("دولة|الدولة|country", "([a-z\\u0600-\\u06FF][a-z\\u0600-\\u06FF .]{1,40})")
private val DATE_FIELD = Regex("^(?:(?:في|on|at|date|بتاريخ|التاريخ|تاريخ العملية)$SEP|في(?=\\d))($DT)$")
private val CARD_FIELD = field(
    "بطاقة|البطاقة|البطاقة الائتمانية|بطاقة مدي|لبطاقة مدي|ببطاقة مدي|للبطاقة|card|via|by|عبر|مدي|مدي-$LETTERS+",
    "$SA_CARD_HEAD$ACCT$SA_CARD_TAIL",
)
private val ACCOUNT_FIELD = field(
    "حساب|الحساب|في حساب|لحساب|من حساب|الي حساب|حساب صاحب الشيك|acc|account|account number|from account|iban|ايبان|الايبان|خصمت من حساب",
    "$ACCT(?: ?- ?$ACCOUNT_TAIL)?",
)

/**
 * خانات قيمتها حرة **بالنقطتين** (اسم المحل · الطرف · البنك · الخدمة · المرجع). الجولة السابعة: الشكل من غير «:» («To be credited
 * within 2 working days» · «من المتوقع إيداع المبلغ …» · «عند تحصيل الشيك سيضاف المبلغ» · «الى حين استكمال التحقق …») كان بيتقري
 * «To: be credited …» واسم الطرف «be credited …» — بقى محصور في [BARE_FIELD].
 */
private val FREE_FIELD = Regex(
    "^(?:لدي|عند|at|التاجر|من البائع|من|الي|to|from|ل|المستفيد|عبر|مصرف|البنك|من بنك|المفوتر|مفوتر|الخدمة|لخدمة|الجهة" +
        "|mtcn|مرجع|الرقم المرجعي|ref|ref\\. no\\.|ref no|مكان السحب|الصراف|merchant|transaction|receiver|biller|service) ?: ?(.+)$",
)

/**
 * من غير نقطتين — **بس** الكلمات اللي البحث كاتبها كده: الأهلي والإنماء «من <محل/اسم>» (#40 #59 #66) · الفرنسي «إلى <اسم>» (#77) ·
 * إس تي سي «From/To <اسم/بنك/رقم>» (#100 #101 #105) · الإنماء «عبر <محفظة>» (#55) · «Ref. No. <رقم>» (#100) · «من7719;…» (#15).
 * القيمة هنا لازم **اسم بس** ([MAX_BARE_WORDS] كلمات بالكتير، من غير كلمة مش اسم — `SmsSlotWords.kt`).
 */
private val BARE_FIELD = Regex("^(?:(?:من|الي|to|from|عبر|ref\\. no\\.|ref no) |من(?=[\\d*•x]))(.+)$")
private const val MAX_BARE_WORDS = 6

/** رقم الفاتورة (الراجحي #29 «الفاتورة:» · إس تي سي #109 «Number:») = مرجع بس (أرقام وحروف من غير مسافة) — «الفاتورة: مستحقة بعد اسبوع» لأ. */
private val BILL_REF = Regex("^(?:الفاتورة|number) ?: ?([a-z]{0,4}[\\d*•#][\\d*•#./\\-]{2,30})$")

/** سطر لوحده: اسم بنك من القايمة · المبلغ لوحده («1,250.00 SAR») · التاريخ والساعة · «{amount} SAR :المبلغ». */
private val BANK_LINE = Regex("^(?:من خلال )?(?:$SAUDI_BANK_NAMES)$") // «من خلال الإنماء» (الإنماء #71)
private val ALONE = Regex("^(?:$MONEY|$DT|$MONEY ?: ?المبلغ)$")
private val STATUS_DONE = Regex("^(?:status|الحالة) ?: ?(?:successful|success|completed|approved|done|ناجحة|ناجح|مكتملة|تمت|منفذة|مقبولة)$")

/**
 * خانات زيادة في قالب بنك واحد بس (السطر ده مش خانة عامة). الجولة السابعة: ملاحظة «دعم حكومي» بقت **قايمة مقفولة** (كانت أي كلام
 * عربي لحد 40 حرف: «يتم الايداع خلال يومين» · «ستودع الدفعة خلال يومين» اتسجلوا دخل).
 */
private val TEMPLATE_EXTRA: Map<String, Regex> = mapOf(
    "stc/fee" to Regex("^reason ?: ?(.+)$"), // #112 «Reason: {reason}»
    "stc/reversal-ar" to Regex("^في ?: ?(.+)$"), // #96 «في: <المحل>» والتاريخ في «بتاريخ:»
    "alrajhi/deposit-government-support" to Regex("^((?:دعم )?(?:حساب المواطن|الضمان الاجتماعي|حافز|ساند))$"), // #26 «{note}» = «حساب المواطن»
)

/** «لـTEST» · «بـSAR 25» · «بـ:87.50» ⇒ «ل: TEST» · «ب: SAR 25» (التطويل بيتشال في [shapeKey]، فـ«لـمطعم» كانت هتبقى «لمطعم»). */
private val GLUED_LAM_BA = Regex("^[ \\t]*([لب])ـ+[ \\t]*[:：]?[ \\t]*")

private fun keyOf(line: String): String = shapeKey(GLUED_LAM_BA.replace(line, "$1: "), dropColons = false)

/**
 * جملة زيادة جوه القيمة الحرة: قوس · شَرطة جنب مسافة («X - Y» · «X -Y») · فاصلة بعدها كلام · علامة استفهام أو تعجب.
 * «AL-SAMPLEMART» (شَرطة جوه الكلمة) و«7719;خالد» (الراجحي 2026) ماشيين.
 */
private val CLAUSE = Regex("[()\\[\\]{}]|\\s[-–—]|[-–—]\\s|[,،]\\s*\\S|[!؟?]")
private const val MAX_FREE_WORDS = 8

/**
 * القيمة الحرة اسم بس (مش اسم وبعده جملة حالة): من غير جملة زيادة ([CLAUSE])، 8 كلمات بالكتير، ومن غير كلمة حالة ([hasShapeDoubt])
 * ولا كلمة **مش اسم** ([slotWordsOk] — الجولة السابعة: «YAZEED SAMPLE wasn't completed» · «on queue» · «OKAPI FUEL 14 ESTIMATED»).
 */
internal fun freeValueOk(value: String, maxWords: Int = MAX_FREE_WORDS): Boolean {
    val text = value.trim()
    return text.isNotEmpty() && !CLAUSE.containsMatchIn(text) && text.split(' ').count { it.isNotEmpty() } <= maxWords &&
        !hasShapeDoubt(text) && slotWordsOk(text)
}

/**
 * خانة السطر المعروف لفحص «كل لابل مرة واحدة» (الجولة السابعة): [group] نوع اللابل (مبلغ · محل · من · إلى · كارت · حساب …) ·
 * [type] نوع القيمة (رقم · بنك · اسم) · [value] القيمة. [group] فاضي = سطر ما بيتعدّش (تاريخ · رصيد · رسوم · تحذير …).
 * S1 (§77-A): [skeleton] = **شكل** السطر من غير قيمته (نوع الخانة · اللابل · نوع القيمة) — جزء من بصمة الشكل (`SmsLearnKeys.kt`).
 */
private class LineSlot(val group: String, val type: String, val value: String, val skeleton: String = "")

private fun noSlot(skeleton: String) = LineSlot("", "", "", skeleton)
private val NUMBER_ONLY = Regex("^[^a-z\\u0600-\\u06FF]+$")
private val BANK_VALUE = Regex("^(?:$SAUDI_BANK_NAMES)$")
private val MERCHANT_LABEL = Regex("^(?:لدي|عند|at|التاجر|merchant|من البائع|transaction)$")
private val FROM_LABEL = Regex("^(?:من|from|من حساب|from account)$")
private val TO_LABEL = Regex("^(?:الي|to|ل|المستفيد|receiver|الي حساب|لحساب)$")
private val PRIMARY_AMOUNT = Regex("^(?:مبلغ|المبلغ|بمبلغ|القيمة|القسط|amount|ب)$")
private val COLON_SPLIT = Regex("^(.+?) ?: ?(.+)$")
private val SPACE_SPLIT = Regex("^(من(?=[\\d*•x])|\\S+(?: no\\.?)?) ?(.+)$")

private fun groupOf(label: String): String = when {
    MERCHANT_LABEL.matches(label) -> "merchant"
    FROM_LABEL.matches(label) -> "from"
    TO_LABEL.matches(label) -> "to"
    PRIMARY_AMOUNT.matches(label) -> "amount"
    else -> label
}

/** لابل وقيمة سطر الخانة (أول «:»، أو أول مسافة في الشكل من غير نقطتين). */
private fun slotOfLine(key: String): LineSlot {
    val m = (if (':' in key) COLON_SPLIT else SPACE_SPLIT).matchEntire(key) ?: return noSlot("")
    val value = m.groupValues[2].trim()
    val type = when {
        NUMBER_ONLY.matches(value) -> "number"
        BANK_VALUE.matches(value) -> "bank"
        else -> "name"
    }
    return LineSlot(groupOf(m.groupValues[1].trim()), type, value)
}

/** خانة السطر ومعاها شكله ([LineSlot.skeleton] = «نوع الخانة:اللابل[:نوع القيمة]»). */
private fun slotWith(key: String, family: String, m: MatchResult, withType: Boolean = false): LineSlot {
    val slot = slotOfLine(key)
    val skeleton = "$family:${labelOf(key, m)}" + if (withType) ":${slot.type}" else ""
    return LineSlot(slot.group, slot.type, slot.value, skeleton)
}

private val MONEY_ONLY = Regex("^$MONEY$")

/**
 * سطر واحد معروف ⇒ خانته (اللي [LineSlot.group] بتاعها فاضي ما بتتعدّش)، أو null = مش معروف. القيمة الحرة (لو فيه) لازم تبقى اسم بس
 * ([freeValueOk]). S1 (§77-A): وكمان **شكله** من غير القيمة — المبلغ والكارت والحساب والتاريخ والاسم والمرجع ما بيغيّروش الشكل، واللابل
 * والجملة الثابتة (سطر التحذير · الحالة) بيغيّروه.
 */
private fun knownLine(line: String, template: String?): LineSlot? {
    val key = keyOf(line)
    if (key.isEmpty()) return noSlot("")
    AMOUNT.matchEntire(key)?.let { return slotWith(key, "amount", it) }
    CARD_FIELD.matchEntire(key)?.let { return slotWith(key, "card", it) }
    ACCOUNT_FIELD.matchEntire(key)?.let { return slotWith(key, "account", it) }
    FEE_OR_BALANCE.matchEntire(key)?.let { return noSlot("money:" + labelOf(key, it)) }
    RATE_LINE.matchEntire(key)?.let { return noSlot("rate:" + labelOf(key, it)) }
    COUNTRY_LINE.matchEntire(key)?.let { return noSlot("country:" + labelOf(key, it)) }
    DATE_FIELD.matchEntire(key)?.let { return noSlot("date:" + labelOf(key, it)) }
    if (ALONE.matches(key)) return noSlot(if (MONEY_ONLY.matches(key)) "alone:money" else if (MONEY_ALONE.matches(key)) "alone:money-label" else "alone:date")
    if (STATUS_DONE.matches(key)) return noSlot("status:$key")
    if (BANK_LINE.matches(key)) return noSlot(if (key.startsWith("من خلال ")) "bank:via" else "bank")
    if (isKnownFooter(key)) return noSlot("footer:" + maskLayoutValues(key))
    BILL_REF.matchEntire(key)?.let { return noSlot("bill-ref:" + labelOf(key, it)) }
    // الجولة السابعة: السطر كله (باللابل) من غير كلمة حالة — «To be credited …» كانت بتستخبى ورا اللابل «To». خط دفاع تاني: النهارده
    // كلمات الخانة ([slotWordsOk]) بتمسك كل حالة بيمسكها (التحوير ما لقاش رسالة بيمسكها هو لوحده)
    if (hasShapeDoubt(key)) return null
    FREE_FIELD.matchEntire(key)?.let { return if (freeValueOk(it.groupValues[1])) slotWith(key, "free", it, withType = true) else null }
    BARE_FIELD.matchEntire(key)?.let { return if (freeValueOk(it.groupValues[1], MAX_BARE_WORDS)) slotWith(key, "bare", it, withType = true) else null }
    val extra = template?.let { TEMPLATE_EXTRA[it]?.matchEntire(key) } ?: return null
    return if (freeValueOk(extra.groupValues[1])) noSlot("extra:" + labelOf(key, extra)) else null
}

/**
 * S1 (§77-A): شكل كل سطر من [lines] بالترتيب ([LineSlot.skeleton]) — null لو فيه سطر مش معروف. بيتنادى بس على رسالة شكلها واضح
 * (اللي عدّت [allLinesKnown] أصلًا)، فالفحص نفسه ما بيتكررش هنا.
 */
internal fun lineSkeletons(lines: List<String>, template: String?): List<String>? =
    lines.map { knownLine(it, template)?.skeleton ?: return null }.filter { it.isNotEmpty() }

/** سطرين مبلغ أساسي أو أكتر («Amount: …» مرتين) = أكتر من عملية في رسالة واحدة (الجولة السابعة) — القارئ بيرفضها «أكتر من مبلغ». */
internal fun repeatedAmountLines(body: String): Boolean {
    val lines = body.split('\n').map(JsText::trim).filter { it.isNotEmpty() }
    val amounts = lines.withIndex().filter { (_, line) -> isPrimaryAmountLine(keyOf(line)) }
    if (amounts.size <= 1) return false
    // الجولة التامنة: رسالة **بلغتين** (كتلة عربي وكتلة إنجليزي بعنوان موحّد، نفس المبلغ) مش عمليتين — بتتقري (وبتستنى: سطور الكتلة التانية مش خانات)
    return !(amounts.size == 2 && bilingualRepeat(lines, amounts[0].index, amounts[1].index))
}

private fun isPrimaryAmountLine(key: String): Boolean = AMOUNT.matches(key) && slotOfLine(key).group == "amount"

private val ARABIC_LETTER = Regex("[\\u0600-\\u06FF]")

/** الكتلة التانية بتبدأ بعنوان موحّد بلغة غير لغة العنوان الأولاني (وهو كمان موحّد)، والمبلغين نفس القيمة. */
private fun bilingualRepeat(lines: List<String>, first: Int, second: Int): Boolean {
    if (!isSamaTitle(shapeKey(lines[0]))) return false
    val secondTitle = (first + 1 until second).firstOrNull { isSamaTitle(shapeKey(lines[it])) } ?: return false
    if (ARABIC_LETTER.containsMatchIn(lines[secondTitle]) == ARABIC_LETTER.containsMatchIn(lines[0])) return false
    return slotNumber(keyOf(lines[first])) == slotNumber(keyOf(lines[second]))
}

private val NUM_RE = Regex(NUM)
private val MONEY_RE = Regex(MONEY)
private val MONEY_ALONE = Regex("^(?:$MONEY|$MONEY ?: ?المبلغ)$")
private val TOTAL_DUE = field("total due amount|اجمالي المبلغ المستحق", MONEY_OR_NUM)

/** أول رقم في [text] بالوحدة الصغرى (فواصل سليمة). */
private fun slotNumber(text: String): Long? = NUM_RE.find(text)?.value?.let { tryParseMoney(it.replace('٬', ',').replace('٫', '.')) }

/**
 * الجولة التامنة (شكل معروف = خانة مبلغ واحدة): قيم **خانة المبلغ** في العنوان ([titleKey] — «شراء انترنت 25.00 SAR» · «حوالة داخلية واردة ب
 * SAR 500») وفي السطور ([lines]: «مبلغ/Amount/بـ» · مبلغ لوحده في سطر). [amount] المقروء لازم = القيمة الوحيدة دي أو الإجمالي المستحق
 * (`SmsSaudiFields.consistentTotal` اتأكد منه). من غير خانة مبلغ ⇒ المبلغ جه من خانة حرة (محل · طرف · مرجع · فاتورة) ⇒ مش شكل معروف.
 */
internal fun amountFromSlot(titleKey: String, lines: List<String>, amount: Long): Boolean {
    val values = mutableSetOf<Long?>()
    MONEY_RE.find(titleKey)?.let { values += slotNumber(it.value) }
    var totalDue: Long? = null
    for (line in lines) {
        val key = keyOf(line)
        when {
            MONEY_ALONE.matches(key) -> values += slotNumber(key)
            isPrimaryAmountLine(key) -> values += slotNumber(key.substringAfter(':'))
            TOTAL_DUE.matches(key) -> totalDue = slotNumber(key.substringAfter(':'))
        }
    }
    val slot = values.singleOrNull() ?: return false
    return slot == amount || totalDue == amount
}

/**
 * كل السطور [lines] (اللي بعد العنوان) معروفة **وكل لابل مرة واحدة** (الجولة السابعة): نفس اللابل مرتين بقيمتين مختلفتين من نفس
 * النوع («Amount … At: QUOLL BAKERY … Amount … At: MARLIN TOYS» = عمليتين · «From: **7741 / From: **2290» · «إلى: ريان / إلى: سلمان»)
 * ⇒ مش معروف (كانت أول قيمة بتكسب في صمت والتانية بتضيع). اسم + رقم حساب تحت نفس اللابل («الى:{اسم}\nالى:{حساب}» — الراجحي #19)
 * أو اسم + بنك (إس تي سي «From {name}\nFrom {bank}») ماشي. [template] = `<البنك>/<القالب>` لو قالب بنك (للخانات الخاصة بيه).
 */
internal fun allLinesKnown(lines: List<String>, template: String?): Boolean {
    val seen = HashMap<String, String>()
    for (line in lines) {
        val slot = knownLine(line, template) ?: return false
        if (slot.group.isEmpty()) continue
        val key = slot.group + "|" + slot.type
        val before = seen[key]
        if (before != null && before != slot.value) return false
        seen[key] = slot.value
    }
    return true
}

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
        // المجموعة 1 = القيمة كلها ([field])، و2 = السعر لو رقم
        rate != null -> rate.groupValues[2].let { it.isEmpty() || !ONE.matches(it) } // مبلغ رسوم تحويل عملات، أو سعر ≠ 1
        country != null -> !SAUDI_COUNTRY.matches(country.groupValues[2].trim())
        else -> false
    }
}
