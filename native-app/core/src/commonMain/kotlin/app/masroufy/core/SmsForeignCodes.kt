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

/**
 * مسافة **في نفس السطر** بين الرقم والعملة (الجولة التالتة): «Card *6604\nINR 2500» كانت بتتقري «6604 INR» وتاكل الكود من المبلغ
 * الحقيقي اللي بعده.
 */
internal const val SP = "[ \\t\\u00A0\\u2000-\\u200A\\u202F]"

/** الرقم زي ما هو مكتوب: الكسور **كلها** (الدينار 3) — التحقق من عددها في [parseForeignMinor]. */
internal const val FOREIGN_NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d+)?"

private val GROUPED = Regex("^\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?$|^\\d+(?:\\.\\d+)?$")

/** عملات من غير كسور (ISO 0) + الروبية الإندونيسية (كسورها مش مستعملة): النقطة فيها فاصل آلاف («1.250.000»). */
private val DOT_THOUSANDS = ZERO_DECIMALS + "IDR"
private val DOT_GROUPED = Regex("^\\d{1,3}(?:\\.\\d{3})+$")

/**
 * المبلغ بالوحدة الصغرى **بتاعة العملة نفسها** («KWD 12.345» = 12345 فلس · «JPY 4500» = 4500 ين) — من غير أي عملية عشرية.
 * فواصل غلط («64,25») أو كسور أكتر من العملة («USD 1.234») ⇒ null (ما بنخمّنش).
 */
internal fun parseForeignMinor(raw: String, code: String): Long? {
    val decimals = foreignDecimals(code)
    val text = raw.replace('٬', ',').replace('٫', '.')
    // الجولة السابعة: «Rp 150.000» · «JPY 4.500» — العملات اللي من غير كسور مستعملة بتتجمع بالنقطة: «150.000» = 150,000 (كانت 150.00)
    if (code.uppercase() in DOT_THOUSANDS && DOT_GROUPED.matches(text)) {
        val whole = text.replace(".", "")
        return if (whole.length + decimals > 15) null else (whole + "0".repeat(decimals)).toLong()
    }
    if (!GROUPED.matches(text)) return null
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
    "(?<![A-Za-z])($ISO_CODES)$SP*[:：]?$SP*($FOREIGN_NUMBER)(?![\\d])|(?<![\\d.,٬٫])($FOREIGN_NUMBER)$SP*($ISO_CODES)(?![A-Za-z])",
)

/** كلمة مبلغ قبل الكود أو الرقم على طول (في نفس السطر). */
private val AMOUNT_LABEL_BEFORE = Regex(
    "(?:بمبلغ|المبلغ|مبلغ|بقيمة|قيمة|بـ|${B}amount|${B}for|${B}of|${B}charged)$S*[:：]?$S*$",
    setOf(RegexOption.IGNORE_CASE),
)

/**
 * مبلغ بعملة أجنبية: مكانه في النص + الكود + الرقم زي ما هو مكتوب. [code] null = العملة أجنبية أكيد بس مش معروفة بالظبط
 * («300 دولار» من غير بلد · «¥4500» ين ولا يوان؟) — القارئ ما بيخمّنش الكود (الرسالة بتستنى من غير تفاصيل).
 */
internal data class IsoMoney(val range: IntRange, val code: String?, val number: String)

/**
 * كل المبالغ بكود عملة أجنبية (مش ريال ولا جنيه) اللي شكلها فلوس فعلًا. [relaxed] (الجولة التالتة): الرقم الصحيح من غير كسور
 * ولا كلمة مبلغ بيتقبل كمان — بس لما الرسالة **فيها دليل تاني** إنها أجنبية (عنوان «دولي/International» ومعاها مبلغ محلي، أو
 * الرقم ده هو اللي قبل المقابل المحلي على طول «JPY 4500 (SAR 112.50)») — عشان «TOP 10 MARKET» ما تبقاش عملة. فلتر الجهاز
 * بيستعمله كمان عشان **ما يحجبش** الرقم ده (`SmsVocabulary.foreignMoneyRanges`) — ده مش دليل إن الرسالة أجنبية.
 */
internal fun isoMoneyIn(text: String, relaxed: Boolean = false): List<IsoMoney> = ISO_AMOUNT.findAll(text).mapNotNull { m ->
    val code = m.groups[1]?.value ?: m.groups[4]!!.value
    val number = m.groups[2]?.value ?: m.groups[3]!!.value
    val lineStart = text.lastIndexOf('\n', m.range.first - 1) + 1
    val looksLikeMoney = relaxed || number.any { it == '.' || it == '٫' } ||
        AMOUNT_LABEL_BEFORE.containsMatchIn(text.substring(lineStart, m.range.first))
    if (looksLikeMoney) IsoMoney(m.range, code, number) else null
}.toList()

// ── أسماء العملات بالعربي ────────────────────────────────────────────────

private const val YA = "[يى]"
private const val AR_LETTER = "\\u0600-\\u06FF"

/**
 * الاسم ⇒ كوده، والاسم اللي ممكن يبقى أكتر من عملة («دولار» · «دينار» · «درهم» · «ليرة» · «فرنك» لوحدهم) ⇒ null (أجنبي من غير كود).
 * الأدق الأول. الجولة التالتة: الين واليوان والفرنك السويسري والروبل والرينغيت · و**الجنيه المصري** (عملة أجنبية في قارئ السعودية —
 * كارت سعودي اتخصم «500.00 جم»؛ قارئ مصر بيشيله لأنه عملته).
 */
private val ARABIC_NAMES: List<Pair<String, String?>> = listOf(
    "ريال$S*قطر$YA" to "QAR", "ريال$S*عمان$YA" to "OMR", "ريال$S*يمن$YA" to "YER", "ريال$S*[إا]يران$YA" to "IRR",
    "دينار$S*كويت$YA" to "KWD", "دينار$S*بحرين$YA" to "BHD", "دينار$S*[أا]ردن$YA" to "JOD", "دينار$S*عراق$YA" to "IQD",
    "دينار$S*ليب$YA" to "LYD", "دينار$S*تونس$YA" to "TND", "درهم$S*[إا]مارات$YA" to "AED", "درهم$S*مغرب$YA" to "MAD",
    "ليرة$S*ترك$YA[ةه]" to "TRY", "ليرة$S*لبنان$YA[ةه]" to "LBP", "دولار$S*[أا]مريك$YA" to "USD", "دولار$S*كند$YA" to "CAD",
    "دولار$S*[أا]سترال$YA" to "AUD", "(?:جنيه$S*)?[إا]سترلين$YA" to "GBP", "يورو" to "EUR", "روب$YA[ةه]$S*هند$YA[ةه]" to "INR",
    "ين$S*$YA?ابان$YA" to "JPY", "ين" to "JPY", "يوان(?:$S*صين$YA)?" to "CNY", "فرنك$S*سويسر$YA" to "CHF", "روبل(?:$S*روس$YA)?" to "RUB",
    "رينغيت|رينجت" to "MYR",
    // ── الجولة السادسة: عملات كانت بتترمي «مفيهاش مبلغ» قبل الحفظ (§75-12) ──
    "بات(?:$S*تايلند$YA)?" to "THB", "وون(?:$S*كور$YA)?" to "KRW", "بيزو$S*مكسيك$YA" to "MXN", "بيزو$S*فلبين$YA" to "PHP",
    "بيزو$S*[أا]رجنتين$YA" to "ARS", "بيزو$S*تشيل$YA" to "CLP", "بيزو$S*كولومب$YA" to "COP", "كرون[ةه]$S*سويد$YA[ةه]" to "SEK",
    "كرون[ةه]$S*نرويج$YA[ةه]" to "NOK", "كرون[ةه]$S*دنمارك$YA[ةه]" to "DKK", "كرون[ةه]$S*تشيك$YA[ةه]" to "CZK", "شيكل" to "ILS",
    "دونغ|دونج" to "VND", "فورنت|فورينت" to "HUF", "ليرة$S*سور$YA[ةه]" to "SYP",
    // «جنيه سوداني» مش جنيه مصري (كان بيتقري جنيه مصري ويستنى «جاهز» بالجنيه)
    "جنيه$S*جنوب$S*سودان$YA" to "SSP", "جنيه$S*سودان$YA" to "SDG",
    "جنيه$S*مصر$YA|جنيه|جنية|ج\\.م\\.?|جم" to "EGP",
    "دولار|دينار|درهم|ليرة|فرنك|روب$YA[ةه]|بيزو|كرون[ةه]|كرونا" to null,
)
private val ARABIC_NAME_RES = ARABIC_NAMES.map { (name, code) -> Regex("^(?:$name)$") to code }
private val ARABIC_NAME_ALT = ARABIC_NAMES.joinToString("|") { "(?:${it.first})" }

/** الرقم جنب الاسم (من الناحيتين)، والاسم كلمة لوحدها («ينبع» مش ين · «جنيه إسترليني» مش جنيه مصري). */
private val ARABIC_MONEY = Regex(
    "($FOREIGN_NUMBER)$SP*($ARABIC_NAME_ALT)(?![$AR_LETTER])(?!$SP*[إا]سترلين)|(?<![$AR_LETTER])($ARABIC_NAME_ALT)(?![$AR_LETTER])$SP*[:：]?$SP*($FOREIGN_NUMBER)(?![\\d])",
)

/** ريال بلد تاني — «ريال» من غير «سعودي» ما بيبقاش ريال سعودي لو بعده اسم بلد تاني. */
internal const val OTHER_RIYAL_TAIL = "قطر$YA|عمان$YA|يمن$YA|[إا]يران$YA|برازيل$YA|كمبود$YA"
internal val OTHER_RIYAL = "ريال$S*(?:$OTHER_RIYAL_TAIL)"

/**
 * أي اسم عملة أجنبية بالعربي (حتى اللي معناه مش واحد) — الرسالة أجنبية حتى لو مبلغها مش مؤكد. الجولة التالتة: يوان · فرنك · روبل ·
 * رينغيت · و«ين» **جنب رقم** بس (كلمة من حرفين).
 */
internal val ARABIC_FOREIGN_WORD =
    "$OTHER_RIYAL|دينار|درهم|ليرة|روب$YA[ةه]|(?:جنيه$S*)?[إا]سترلين$YA|يوان|فرنك|روبل|رينغيت|رينجت" +
        "|\\d$S*ين(?![$AR_LETTER])|(?<![$AR_LETTER])ين$S*[:：]?$S*\\d" +
        // الجولة السادسة: «بات» و«وون» و«كرونة» كلمات قصيرة (جوه «حسابات») ⇒ جنب رقم بس
        "|بيزو|شيكل|دونغ|دونج|فورنت|فورينت|جنيه$S*(?:جنوب$S*)?سودان$YA|\\d$S*(?:بات|وون|كرون[ةه]|كرونا)(?![$AR_LETTER])"

/** المبالغ المكتوبة باسم عملة بالعربي («120 ريال قطري» ⇒ QAR · «300 دولار» ⇒ أجنبي من غير كود). */
internal fun arabicMoneyIn(line: String): List<IsoMoney> = ARABIC_MONEY.findAll(line).map { m ->
    val name = m.groups[2]?.value ?: m.groups[3]!!.value
    val code = ARABIC_NAME_RES.firstOrNull { it.first.matches(name) }?.second
    IsoMoney(m.range, code, m.groups[1]?.value ?: m.groups[4]!!.value)
}.toList()
