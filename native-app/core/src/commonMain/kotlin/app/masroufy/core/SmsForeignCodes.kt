package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * عملة أجنبية بكود ISO 4217 جنب مبلغ — **أي عملة**، مش قايمة العشر عملات بس (مراجعة جلسة 33: «مبلغ:TRY 450.00» كانت بتترفض
 * «المبلغ مش واضح» أو فلتر الجهاز بيرميها في صمت، وقرار §75-12 بيقول العملية الأجنبية تتسجل وتسأل عن المبلغ المحلي).
 *
 * عشان اسم محل زي «TOP 10» أو «ALL 4 KIDS» ما يتقريش عملة: الكود **حروف كبيرة بس**، ولازق في رقم، والرقم يا إما فيه كسور
 * («450.00») يا إما جاي بعد كلمة مبلغ مباشرة («مبلغ:JPY 4500» · «Amount: 4500 JPY»).
 * الريال والجنيه مش هنا: دول عملة البلد، وليهم قواعدهم في كل قارئ.
 *
 * الجولة التانية من المراجعة: **عدد الكسور حسب العملة** (الدينار الكويتي 3 · الين 0 — «KWD 12.345» كانت بتتقري 12.34 في صمت)،
 * و**أسماء العملات بالعربي** («120 ريال قطري» كانت بتتسجل 120 ريال سعودي).
 */
private const val ISO_CODES =
    "AED|AFN|ALL|AMD|ANG|AOA|ARS|AUD|AWG|AZN|BAM|BBD|BDT|BGN|BHD|BIF|BMD|BND|BOB|BRL|BSD|BTN|BWP|BYN|BZD|CAD|CDF|CHF|CLP|CNY|" +
        "COP|CRC|CUP|CVE|CZK|DJF|DKK|DOP|DZD|ERN|ETB|EUR|FJD|FKP|GBP|GEL|GHS|GIP|GMD|GNF|GTQ|GYD|HKD|HNL|HTG|HUF|IDR|ILS|INR|IQD|" +
        "IRR|ISK|JMD|JOD|JPY|KES|KGS|KHR|KMF|KPW|KRW|KWD|KYD|KZT|LAK|LBP|LKR|LRD|LSL|LYD|MAD|MDL|MGA|MKD|MMK|MNT|MOP|MRU|MUR|MVR|" +
        "MWK|MXN|MYR|MZN|NAD|NGN|NIO|NOK|NPR|NZD|OMR|PAB|PEN|PGK|PHP|PKR|PLN|PYG|QAR|RON|RSD|RUB|RWF|SBD|SCR|SDG|SEK|SGD|SHP|SLE|" +
        "SLL|SOS|SRD|SSP|STN|SVC|SYP|SZL|THB|TJS|TMT|TND|TOP|TRY|TTD|TWD|TZS|UAH|UGX|USD|UYU|UZS|VES|VND|VUV|WST|XAF|XCD|XCG|XOF|" +
        "XPF|YER|ZAR|ZMW|ZWG|ZWL"

/** عملات ISO 4217 بـ3 كسور (الفلس) — وباقي العملات منزلتين ما عدا [ZERO_DECIMALS]. */
private val THREE_DECIMALS = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")
private val ZERO_DECIMALS = setOf("BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF")

/** عدد الكسور في الوحدة الصغرى للعملة (ISO 4217). */
internal fun foreignDecimals(code: String): Int = when (code.uppercase()) {
    in THREE_DECIMALS -> 3
    in ZERO_DECIMALS -> 0
    else -> 2
}

/** الرقم زي ما هو مكتوب: الكسور **كلها** (الدينار 3) — التحقق من عددها في [parseForeignMinor]. */
internal const val FOREIGN_NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d+)?"

private val GROUPED = Regex("^\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?$|^\\d+(?:\\.\\d+)?$")

/**
 * المبلغ بالوحدة الصغرى **بتاعة العملة نفسها** («KWD 12.345» = 12345 فلس · «JPY 4500» = 4500 ين) — من غير أي عملية عشرية.
 * فواصل غلط («64,25») أو كسور أكتر من العملة («USD 1.234») ⇒ null (ما بنخمّنش).
 */
internal fun parseForeignMinor(raw: String, code: String): Long? {
    val text = raw.replace('٬', ',').replace('٫', '.')
    if (!GROUPED.matches(text)) return null
    val decimals = foreignDecimals(code)
    val plain = text.replace(",", "")
    val intPart = plain.substringBefore('.')
    val frac = if ('.' in plain) plain.substringAfter('.') else ""
    if (frac.length > decimals && frac.substring(decimals).any { it != '0' }) return null
    val digits = intPart + frac.take(decimals).padEnd(decimals, '0')
    if (digits.length > 15) return null
    return digits.toLong()
}

// من غير IGNORE_CASE: «try 3» أو «all 4» في الكلام مش عملة
private val ISO_AMOUNT = Regex(
    "(?<![A-Za-z])($ISO_CODES)$S*[:：]?$S*($FOREIGN_NUMBER)(?![\\d])|(?<![\\d.,٬٫])($FOREIGN_NUMBER)$S*($ISO_CODES)(?![A-Za-z])",
)

/** كلمة مبلغ قبل الكود أو الرقم على طول (في نفس السطر). */
private val AMOUNT_LABEL_BEFORE = Regex(
    "(?:بمبلغ|المبلغ|مبلغ|بقيمة|قيمة|بـ|${B}amount|${B}for|${B}of|${B}charged)$S*[:：]?$S*$",
    setOf(RegexOption.IGNORE_CASE),
)

/** مبلغ بعملة أجنبية: مكانه في النص + الكود + الرقم زي ما هو مكتوب. */
internal data class IsoMoney(val range: IntRange, val code: String, val number: String)

/** كل المبالغ بكود عملة أجنبية (مش ريال ولا جنيه) اللي شكلها فلوس فعلًا. */
internal fun isoMoneyIn(text: String): List<IsoMoney> = ISO_AMOUNT.findAll(text).mapNotNull { m ->
    val code = m.groups[1]?.value ?: m.groups[4]!!.value
    val number = m.groups[2]?.value ?: m.groups[3]!!.value
    val lineStart = text.lastIndexOf('\n', m.range.first - 1) + 1
    val looksLikeMoney = number.any { it == '.' || it == '٫' } || AMOUNT_LABEL_BEFORE.containsMatchIn(text.substring(lineStart, m.range.first))
    if (looksLikeMoney) IsoMoney(m.range, code, number) else null
}.toList()

// ── أسماء العملات بالعربي ────────────────────────────────────────────────

private const val YA = "[يى]"

/** الاسم اللي معناه واحد بس ⇒ كوده. «دينار» أو «درهم» أو «دولار» لوحدهم ممكن يبقوا أكتر من عملة ⇒ مش هنا (مبلغ من غير عملة مؤكدة). */
private val ARABIC_NAMES = listOf(
    "ريال$S*قطر$YA" to "QAR", "ريال$S*عمان$YA" to "OMR", "ريال$S*يمن$YA" to "YER", "ريال$S*[إا]يران$YA" to "IRR",
    "دينار$S*كويت$YA" to "KWD", "دينار$S*بحرين$YA" to "BHD", "دينار$S*[أا]ردن$YA" to "JOD", "دينار$S*عراق$YA" to "IQD",
    "دينار$S*ليب$YA" to "LYD", "دينار$S*تونس$YA" to "TND", "درهم$S*[إا]مارات$YA" to "AED", "درهم$S*مغرب$YA" to "MAD",
    "ليرة$S*ترك$YA[ةه]" to "TRY", "دولار$S*[أا]مريك$YA" to "USD", "جنيه$S*[إا]سترلين$YA" to "GBP", "يورو" to "EUR",
    "روب$YA[ةه]$S*هند$YA[ةه]" to "INR",
).map { (name, code) -> code to Regex("(?:($FOREIGN_NUMBER)$S*(?:$name)|(?:$name)$S*[:：]?$S*($FOREIGN_NUMBER))(?![\\d])") }

/** ريال بلد تاني — «ريال» من غير «سعودي» ما بيبقاش ريال سعودي لو بعده اسم بلد تاني. */
internal const val OTHER_RIYAL_TAIL = "قطر$YA|عمان$YA|يمن$YA|[إا]يران$YA|برازيل$YA|كمبود$YA"
internal val OTHER_RIYAL = "ريال$S*(?:$OTHER_RIYAL_TAIL)"

/** أي اسم عملة أجنبية بالعربي (حتى اللي معناه مش واحد) — الرسالة أجنبية حتى لو مبلغها مش مؤكد. */
internal val ARABIC_FOREIGN_WORD = "$OTHER_RIYAL|دينار|درهم|ليرة|روب$YA[ةه]|جنيه$S*[إا]سترلين$YA"

/** المبالغ المكتوبة باسم عملة بالعربي معناه واحد («120 ريال قطري» ⇒ QAR). */
internal fun arabicMoneyIn(line: String): List<IsoMoney> = ARABIC_NAMES.flatMap { (code, regex) ->
    regex.findAll(line).map { m -> IsoMoney(m.range, code, m.groups[1]?.value ?: m.groups[2]!!.value) }
}
