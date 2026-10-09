package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * شكل رسالة البنك بعد القراية — مشترك بين قارئ السعودية (`BankSmsParser.kt`) وقارئ مصر (`EgyptBankSms.kt`).
 * الخانات الأولانية في [SmsRow] هي نفس التطبيق الحالي (`smsRowsJson` بيكتبها بنفس الترتيب)، والجديدة **دليل** للي بعد القارئ
 * (قرارات OVERRIDES §75) — ما بتدخلش في بصمة الملف ولا في ملفات المرجع.
 */
data class BankSmsMessage(val sender: String, val receivedAt: String, val body: String)

/** نوع العملية زي ما الرسالة بتقوله (العنوان أو الكلام). **دليل مش حكم** — النوع الاقتصادي بيتقرر بعدين. */
enum class SmsKind(val wire: String) {
    PURCHASE("purchase"),

    /** استرداد أو عكس عملية من محل — §75-6: يتقترح «استرداد» ويستنى تأكيد المالك. */
    REFUND("refund"),

    /** سحب كاش (صرّاف · فرع · وكيل محفظة) — §75-4: يتنقل لمحفظة الكاش. */
    CASH_WITHDRAWAL("cash_withdrawal"),

    /** الجولة التامنة: شراء **ومعاه** سحب كاش من نفس المحل («PoS Purchase & Cashback» · «شراء ونقد عبر نقاط البيع») — جزء منه للكاش (§75-4). */
    PURCHASE_WITH_CASH("purchase_with_cash"),

    /** إيداع كاش في البنك (صرّاف · فرع) — الجولة التامنة: نقل من محفظة الكاش للبنك، مش دخل (§75-4 بالعكس — لسه ما اتبناش). */
    CASH_DEPOSIT("cash_deposit"),
    TRANSFER_IN("transfer_in"),
    TRANSFER_OUT("transfer_out"),

    /** بين حساباتك — §75-11: الطرف = آخر 4 أرقام حسابك التاني. */
    OWN_TRANSFER("own_transfer"),
    SALARY("salary"),
    BILL("bill"),
    FEE("fee"),

    /** سداد بطاقة ائتمانية (تحويل داخلي غالبًا). */
    CARD_PAYMENT("card_payment"),
    OTHER("other"),
}

/**
 * مبلغ بعملة أجنبية: [currency] كود العملة (USD…)، و[amountMinor] بالوحدة الصغرى **بتاعة العملة دي** حسب ISO 4217
 * ([decimals]: الدولار 2 · الدينار الكويتي 3 · الين 0) — «KWD 12.345» = 12345، «JPY 4500» = 4500.
 */
data class SmsForeignAmount(val currency: String, val amountMinor: Long) {
    val decimals: Int get() = foreignDecimals(currency)
}

/**
 * القارئ فهم الرسالة **إزاي** — ده أساس التسجيل التلقائي (OVERRIDES §72: «المفهومة بتتسجل لوحدها» — والجولة الرابعة: المفهومة =
 * **شكل معروف**، `SmsKnownShapes*.kt`). [clear] = بتتسجل لوحدها في الخلفية؛ غير كده بتستنى تأكيد المالك (الشاشة بتعرضها جاهزة).
 */
sealed interface SmsShape {
    val clear: Boolean

    /** للتقارير والاختبارات: `known:<البنك>/<القالب>` · `sama-title` · `keyword-fallback`. */
    val wire: String

    /**
     * الرسالة على قالب بنك معروف من البحث (`research/banks/` — ملفين `sms-formats` للسعودية ومصر) أو من عينات المالك، واتجاه القالب = اتجاه القارئ.
     * [bankFamily] و[templateId] للتتبع بس — **مش** شرط إن المرسل هو البنك ده (أسماء المرسلين مش منشورة، `research/banks/README.md`).
     */
    data class KnownShape(val bankFamily: String, val templateId: String) : SmsShape {
        override val clear: Boolean get() = true
        override val wire: String get() = "known:$bankFamily/$templateId"
    }

    /** أول سطر = عنوان من عناوين البنك المركزي السعودي الموحّدة **بالحرف** (تعميم 42023876 — لأي بنك سعودي)، واتجاهه = اتجاه القارئ. */
    data object SamaTitle : SmsShape {
        override val clear: Boolean get() = true
        override val wire: String get() = "sama-title"
    }

    /**
     * مفيش شكل معروف: الاتجاه جه من **كلمات عامة** بس (القاعدة القديمة — «شراء/سحب/حوالة صادرة» في أي مكان)، أو الشكل المعروف
     * اتجاهه عكس اللي القارئ قراه ⇒ **ما بتتسجلش لوحدها** — بتستنى تأكيد المالك ومعاها سبب (`TextKey.SMS_WAIT_UNKNOWN_SHAPE`).
     */
    data object KeywordFallback : SmsShape {
        override val clear: Boolean get() = false
        override val wire: String get() = "keyword-fallback"
    }
}

data class SmsRow(
    val lineNumber: Int,
    val date: IsoDate,
    val amountMinor: Halalas,
    val direction: Direction,
    val merchantName: String,
    val reference: String?,
    val sourceName: String,
    val description: String,
    val raw: String,
    val kind: SmsKind = SmsKind.OTHER,
    /** آخر 4 أرقام حسابك أو كارتك اللي الرسالة عنها (§75-11 — `SmsOwnAccount.kt`)، أو null. */
    val ownLast4: String? = null,
    /**
     * القارئ فهمها إزاي (القارئين بيحطوها). **الافتراضي = كلمات عامة** (الأأمن): أي صف اتعمل من غير القارئين ما بيتسجلش لوحده.
     * مش جوه بصمة الملف ([smsRowsJson]) ولا ملفات المرجع.
     */
    val shape: SmsShape = SmsShape.KeywordFallback,
)

/**
 * عملية بعملة أجنبية (قرار المالك §75-12: «تتسجل وتسأل عن المبلغ بالعملة المحلية» — ورفض «تتسجل لوحدها لو المبلغ المحلي مكتوب»):
 * **ما بتتسجلش بأي مبلغ** لحد ما شاشة السؤال تتبني — كل اللي اتقري بيمشي معاها. القارئ ما بيخترعش مبلغ (قاعدة 10).
 * [localSuggestion] = المبلغ بعملة البلد **المكتوب في الرسالة** (دي 360 «(SAR 87.50)» · «إجمالي المبلغ المستحق») — اقتراح يتعرض
 * على المالك في السؤال، مش مبلغ متسجل؛ null = مش مكتوب.
 */
data class SmsForeignPending(
    val date: IsoDate,
    val foreign: SmsForeignAmount,
    val direction: Direction,
    val merchantName: String,
    val kind: SmsKind,
    val ownLast4: String? = null,
    val localSuggestion: Halalas? = null,
)

/**
 * الجولة التامنة: عملية **أجنبية أكيد** بس المبلغ الأجنبي مش مقروء بالظبط (اسم عملة ليه أكتر من بلد «25.000 دينار» · «200 دولار» لوحده (سؤال
 * (س) لسه مفتوح) · سطر «Currency: USD» من غير مبلغ جنبه) — كانت بتستنى **من غير أي تفاصيل**. دلوقتي بتستنى ومعاها اللي اتقري بس (قاعدة 10):
 * [currency] لو الكود مكتوب صريح · [writtenAmount] الرقم **زي ما هو مكتوب** جنب العملة (كسوره بتتحدد لما العملة تتعرف) · المقابل المحلي
 * المكتوب [localSuggestion] اقتراح.
 */
data class SmsForeignUnread(
    val date: IsoDate,
    val currency: String?,
    val writtenAmount: String?,
    val direction: Direction,
    val merchantName: String,
    val kind: SmsKind,
    val ownLast4: String? = null,
    val localSuggestion: Halalas? = null,
)

sealed interface SmsParseResult {
    data class Ok(val row: SmsRow) : SmsParseResult

    /** [foreign] موجود بس لو السبب «عملة أجنبية» والباقي كله اتقري (§75-12) · [foreignUnread] لو أجنبية والمبلغ الأجنبي مش مقروء بالظبط. */
    data class Rejected(val reason: String, val foreign: SmsForeignPending? = null, val foreignUnread: SmsForeignUnread? = null) : SmsParseResult
}

internal const val DAY_MS = 86_400_000L

/** علامات الاتجاه المخفية حوالين الأرقام والإنجليزي — بتقطع الأنماط من غير ما تبان. */
internal val BIDI_CODES = setOf(0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069, 0x061C)

private val RI = setOf(RegexOption.IGNORE_CASE)

/** `SA[\d\s]{20,}` — الأرقام والمسافات (بمعنى جافاسكربت) في فئة واحدة. */
private val REDACT_IBAN = Regex("SA[\\d" + S.substring(1, S.length - 1) + "]{20,}", RI)
private val REDACT_LONG = Regex("$B(?:\\d[ -]*){12,34}$B")
private val REDACT_DIGITS = Regex("\\d{5,}")

/** الجولة السابعة: موبايل بفواصل («010-6441-8273» · «055 123 4567») — كان بيعدّي الحجب (12 رقم أو 5 ورا بعض بس) ويتكتب في الوصف كامل. */
private val REDACT_PHONE = Regex("(?<![\\d])0(?:1[0125]|5\\d)[ .\\-]\\d{3,4}[ .\\-]\\d{3,4}(?![\\d])")

// العملة المصرية اتضافت للمحمي (مبلغ 5 أرقام بالجنيه كان بيتقص في الوصف) — ملف المرجع مفيهوش جنيه بخمس أرقام
private const val KEEP_CURRENCY =
    "(?:(?<![A-Za-z])(?:SAR|SR|EGP)(?![A-Za-z])|ريال|ر\\.?س\\.?|جنيه|ج\\.م\\.?|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![A-Za-z])LE(?![A-Za-z]))"
private val REDACT_KEEP = Regex(
    "(?:(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)$S*[:：]?$S*)?(?:$KEEP_CURRENCY$S*[:：]?$S*[\\d,٬]+(?:[.٫]\\d{1,2})?|[\\d,٬]+(?:[.٫]\\d{1,2})?$S*$KEEP_CURRENCY)",
    RI,
)

private fun lastFour(text: String) = if (text.length <= 4) text else text.substring(text.length - 4)

/** حجب أرقام الحسابات والبطاقات — المبلغ اللي جنبه عملة ما بيتحجبش (زي SmsSafety.java). */
fun redactSms(input: String): String {
    val text = latinizeDigits(input)
    fun redact(value: String) = value
        .replace(REDACT_PHONE) { "••••" + lastFour(it.value.filter { c -> c in '0'..'9' }) }
        .replace(REDACT_IBAN) { "••••" + lastFour(it.value.filterNot(JsText::isWhitespace)) }
        .replace(REDACT_LONG) { "••••" + lastFour(it.value.filter { c -> c in '0'..'9' }) }
        .replace(REDACT_DIGITS) { "••••" + lastFour(it.value) }
    val out = StringBuilder()
    var end = 0
    for (match in REDACT_KEEP.findAll(text)) {
        out.append(redact(text.substring(end, match.range.first))).append(match.value)
        end = match.range.last + 1
    }
    return out.append(redact(text.substring(end))).toString()
}

/** صف الرسالة كصف استيراد عادي — منع التكرار والتصنيف والحفظ بعدها زي الكشف بالظبط. */
fun SmsRow.toParsedRow() = ParsedRow(lineNumber, date, amountMinor, direction, merchantName, reference, sourceName, description, raw)

/**
 * نفس `JSON.stringify(rows)` بالحرف وبترتيب مفاتيح التطبيق الحالي — النص ده محتوى «ملف» الرسايل،
 * وبصمته هي اللي بتعرّف إن نفس الرسايل اتسجلت قبل كده. الخانات الجديدة ([SmsRow.kind] …) **مش** جواه.
 */
fun smsRowsJson(rows: List<SmsRow>): String = rows.joinToString(",", "[", "]") { r ->
    "{\"lineNumber\":${r.lineNumber},\"date\":${JsText.jsonString(r.date)},\"amountMinor\":${r.amountMinor}," +
        "\"direction\":${JsText.jsonString(r.direction.wire)},\"merchantName\":${JsText.jsonString(r.merchantName)}," +
        "\"reference\":${r.reference?.let(JsText::jsonString) ?: "null"},\"sourceName\":${JsText.jsonString(r.sourceName)}," +
        "\"description\":${JsText.jsonString(r.description)},\"raw\":${JsText.jsonString(r.raw)}}"
}

/** الصف النهائي — نفس المرجع (`SMS:` + بصمة المرسل والوقت والنص) والوصف المقصوص في القارئين. */
internal fun smsRow(
    message: BankSmsMessage, body: String, lineNumber: Int, date: IsoDate, amount: Halalas, direction: Direction,
    merchant: String, kind: SmsKind, shape: SmsShape,
): SmsParseResult.Ok {
    val safeBody = redactSms(body)
    return SmsParseResult.Ok(
        SmsRow(
            lineNumber = lineNumber, date = date, amountMinor = amount, direction = direction,
            merchantName = redactSms(merchant),
            reference = "SMS:" + hashContent(message.sender + "|" + message.receivedAt + "|" + body),
            sourceName = message.sender, description = safeBody, raw = safeBody, kind = kind,
            ownLast4 = ownLast4Of(body, direction), shape = shape,
        ),
    )
}

/** اليوم بتوقيت البلد من وقت وصول الرسالة (UTC من `Instant.toString`) — للرسايل اللي مفيهاش تاريخ خالص. */
internal fun localDayOf(receivedAt: String, offsetHours: Int): IsoDate? {
    if (receivedAt.length == 10) return receivedAt.takeIf(::isValidIsoDate)
    val millis = JsText.parseIsoMillis(receivedAt) ?: return null
    return dayNumberToIso((millis + offsetHours * 3_600_000L).floorDiv(DAY_MS).toInt())
}
