package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * **دليل إن العملية بعملة أجنبية** — مشترك بين قارئ السعودية وقارئ مصر (الجولة التالتة من المراجعة، قرار المالك §75-12).
 *
 * قبل كده الأجنبي كان **قايمة** (كود من العشرة · كود ISO بكسور أو بعد كلمة مبلغ · اسم عربي معروف)، فالشراء الأجنبي المكتوب بشكل
 * تاني كان بيتسجل لوحده بالمبلغ المحلي المكتوب جنبه («$23.40 (SAR 87.75)» · «JPY 4500 (SAR 112.50)» · «INR 2500 = SAR 112.50» ·
 * «12.50 Swiss Francs» · «4500 ين ياباني … ما يعادل» · «300 يوان (156.20 ريال)» · «£12.00 … Equivalent EGP 750.00») — وده ضد قرار
 * المالك. دلوقتي أي واحد من دول = أجنبي:
 * 1. **رمز عملة** جنب رقم ($ · US$ · € · £ · ¥ · ₹ · ₺) — «E£»/«£E» جنيه مصري.
 * 2. **اسم عملة بالإنجليزي** جنب رقم (US Dollars · Euro · Swiss Francs · yen · yuan …) أو بالعربي (`SmsForeignCodes.kt`).
 * 3. **الشكل نفسه:** مبلغ محلي بين قوسين **بعد مبلغ تاني على طول** («X (SAR 87.75)») أو بعد «=» جنب مبلغ، أو بعد «ما يعادل» ·
 *    «بما يعادل» · «يعادل» · Equivalent · approx — المقابل المحلي معناه إن العملية نفسها بعملة تانية.
 * 4. عنوان «دولي/International» **ومعاه** مبلغ محلي: كود ISO جنب رقم صحيح بيتقبل («International Purchase TRY 450 …»).
 *
 * والمقابل المحلي **المكتوب** ([localConversion]) بيمشي مع العملية اقتراح بس — من الشكل 3 بس (أو «المبلغ بالريال:»): القوسين اللي
 * قبلهم كلمة رسوم أو رصيد أو حد («International fee (SAR 2.20)» · «Available limit (EGP 38,500.00)») **مش** مقابل (قاعدة 10).
 */

/** عملة البلد: الكود + نمط كتابتها (الريال في قارئ السعودية، الجنيه في قارئ مصر) + أنماط المقابل المحلي (بتتبني مرة واحدة). */
internal class LocalCurrency(val code: String, token: String) {
    val regex = Regex(token, setOf(RegexOption.IGNORE_CASE))
    val pattern = token
    val parens by lazy { parensOf(this) }
    val equals by lazy { equalsOf(this) }
    val equivalent by lazy { equivalentOf(this) }
    val label by lazy { localLabelOf(this) }
}

private val FI = setOf(RegexOption.IGNORE_CASE)
private const val AR = "\\u0600-\\u06FF"

/** المبلغ المحلي: فاصلة آلاف وكسرين بالكتير. */
private const val LOCAL_NUM = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d{1,2})?"
private val LOCAL_GROUPING = Regex("^\\d{1,3}(?:[,٬]\\d{3})+(?:[.٫]\\d{1,2})?$|^\\d+(?:[.٫]\\d{1,2})?$")

// ── 1. رموز العملات ─────────────────────────────────────────────────────

private const val SYMBOL = "(?:US|[A-Z]{1,2})?\\$|E?£E?|€|¥|₹|₺"
private val SYMBOL_MONEY = Regex("(?<![A-Za-z])($SYMBOL)$SP*($FOREIGN_NUMBER)|($FOREIGN_NUMBER)$SP*($SYMBOL)(?![A-Za-z])")
private val DOLLAR_PREFIX = mapOf("US" to "USD", "" to "USD", "C" to "CAD", "CA" to "CAD", "A" to "AUD", "AU" to "AUD", "HK" to "HKD", "S" to "SGD", "SG" to "SGD", "NZ" to "NZD")

/** «$» لوحده = دولار أمريكي (العرف في رسايل بنوك الخليج ومصر) · «C$/A$/HK$…» بلدهم · «¥» ين ولا يوان ⇒ null. */
private fun symbolCode(symbol: String): String? = when {
    symbol.endsWith("$") -> DOLLAR_PREFIX[symbol.dropLast(1)]
    symbol == "€" -> "EUR"
    symbol == "£" -> "GBP"
    '£' in symbol -> "EGP" // «E£» / «£E»
    symbol == "₹" -> "INR"
    symbol == "₺" -> "TRY"
    else -> null
}

// ── 2. أسماء العملات بالإنجليزي ─────────────────────────────────────────

/** الأدق الأول؛ الاسم اللي ممكن يبقى أكتر من عملة ⇒ null. «riyal» = ريال سعودي (عملة البلد في قارئ السعودية). */
private val ENGLISH_NAMES: List<Pair<String, String?>> = listOf(
    "(?:US|U\\.S\\.|American)$S*dollars?" to "USD", "Canadian$S*dollars?" to "CAD", "Australian$S*dollars?" to "AUD",
    "Hong$S*Kong$S*dollars?" to "HKD", "Singapore$S*dollars?" to "SGD", "euros?" to "EUR",
    "(?:British|GB)$S*pounds?|pounds?$S*sterling|sterling" to "GBP", "Egyptian$S*pounds?" to "EGP",
    "(?:Japanese$S*)?yen" to "JPY", "(?:Chinese$S*)?(?:yuan|renminbi)" to "CNY", "Swiss$S*francs?" to "CHF",
    "(?:UAE|Emirati)$S*dirhams?" to "AED", "Moroccan$S*dirhams?" to "MAD", "Kuwaiti$S*dinars?" to "KWD",
    "Bahraini$S*dinars?" to "BHD", "Jordanian$S*dinars?" to "JOD", "Indian$S*rupees?" to "INR", "Pakistani$S*rupees?" to "PKR",
    "Turkish$S*lir[ae]" to "TRY", "Qatari$S*riy?als?" to "QAR", "Omani$S*riy?als?" to "OMR", "(?:Saudi$S*)?riy?als?" to "SAR",
    "ringgit" to "MYR", "baht" to "THB", "rupiah" to "IDR", "(?:ruble|rouble)s?" to "RUB",
    "dollars?|pounds?|francs?|dirhams?|dinars?|rupees?|lir[ae]|pesos?" to null,
)
private val ENGLISH_NAME_RES = ENGLISH_NAMES.map { (name, code) -> Regex("^(?:$name)$", FI) to code }
private val ENGLISH_ALT = ENGLISH_NAMES.joinToString("|") { "(?:${it.first})" }
private val ENGLISH_MONEY = Regex(
    "($FOREIGN_NUMBER)$SP*($ENGLISH_ALT)(?![A-Za-z])|(?<![A-Za-z])($ENGLISH_ALT)$SP*[:：]?$SP*($FOREIGN_NUMBER)(?![\\d])",
    FI,
)

/** المبالغ بعملة مكتوبة برمز أو باسمها (عربي أو إنجليزي) — من غير الكود (`isoMoneyIn`). */
internal fun namedMoneyIn(text: String): List<IsoMoney> {
    val symbols = SYMBOL_MONEY.findAll(text).map { m ->
        IsoMoney(m.range, symbolCode(m.groups[1]?.value ?: m.groups[4]!!.value), m.groups[2]?.value ?: m.groups[3]!!.value)
    }
    val english = ENGLISH_MONEY.findAll(text).map { m ->
        val name = m.groups[2]?.value ?: m.groups[3]!!.value
        IsoMoney(m.range, ENGLISH_NAME_RES.firstOrNull { it.first.matches(name) }?.second, m.groups[1]?.value ?: m.groups[4]!!.value)
    }
    return ((symbols + english).toList() + arabicMoneyIn(text)).filter { it.inOneLine(text) }
}

/** الرقم والعملة في نفس السطر («Currency: EUR\n2026-03-05» مش 2026 يورو). */
internal fun IsoMoney.inOneLine(text: String): Boolean = text.indexOf('\n', range.first).let { it < 0 || it > range.last }

// ── 3. المقابل المحلي (الشكل) ───────────────────────────────────────────

/**
 * كلمة قبل المبلغ اللي قبل القوسين ⇒ القوسين قيمة الكلمة دي، مش مقابل مبلغ العملية: رسوم («Fee: USD 2.20 (SAR 8.25)» — مقابل
 * الرسوم مش الشراء) · رصيد · حد · ضريبة · نقاط («Points earned: 64 (SAR 6.40)» في رسالة شراء محلية عادية) · كاش باك.
 */
private val NOT_CONVERSION = Regex(
    "${B}fees?$B|${B}charges?$B|commission|${B}VAT$B|${B}tax$B|balance|${B}bal$B|available|${B}limit$B|رسوم|عمولة|ضريبة|رصيد|متاح|الحد|مصاريف" +
        "|${B}points?$B|نقاط|نقطة|cash$S*back|كاش$S*باك",
    FI,
)

/** رقم كارت/حساب/مرجع قبل القوسين ⇒ مش مبلغ. */
private val ID_BEFORE = Regex(
    "(?:${B}ref(?:erence)?|${B}no\\.?|#|${B}card|${B}account|${B}acct?|بطاقة|بطاقتك|حساب|حسابك|مرجع|رقم|المنتهية$S*بـ?)$S*[:：.]?$S*$",
    FI,
)

/** «X (SAR 87.75)» بعد مبلغ · «= SAR 112.50» بعد مبلغ · «ما يعادل/Equivalent SAR …» · «المبلغ بالريال:» (اقتراح بس). */
private fun parensOf(local: LocalCurrency) =
    Regex("\\($S*(?:(?:${local.pattern})$S*[:：]?$S*($LOCAL_NUM)|($LOCAL_NUM)$S*(?:${local.pattern}))$S*\\)", FI)

private fun equalsOf(local: LocalCurrency) =
    Regex("=$S*(?:(?:${local.pattern})$S*[:：]?$S*($LOCAL_NUM)|($LOCAL_NUM)$S*(?:${local.pattern}))", FI)

private fun equivalentOf(local: LocalCurrency) = Regex(
    "(?:(?<![$AR])(?:بما|ما)?$S*يعادل|${B}equivalent(?:$S+(?:to|amount))?|${B}equiv\\.?|${B}approx(?:imately|\\.)?)$S*[:：]?$S*" +
        "(?:(?:${local.pattern})$S*[:：]?$S*($LOCAL_NUM)|($LOCAL_NUM)$S*(?:${local.pattern}))",
    FI,
)

private fun localLabelOf(local: LocalCurrency) = Regex(
    "(?<![$AR])(?:المبلغ$S*)?(?:بالريال|بالجنيه)(?:$S*(?:السعودي|المصري))?$S*[:：]?$S*" +
        "(?:(?:${local.pattern})$S*[:：]?$S*($LOCAL_NUM)|($LOCAL_NUM)$S*(?:${local.pattern}))",
    FI,
)

private val ISO_LIKE = Regex("^[A-Z]{3}$")
private val SYMBOL_ONLY = Regex("^(?:$SYMBOL)$")

/** الكلام بين الرقم والقوس كود أو رمز أو اسم عملة بس («12.50 Swiss Francs (SAR 52.10)» · «300 يوان\n(156.20 ريال)»). */
private fun isCurrencyTail(tail: String): Boolean =
    ISO_LIKE.matches(tail) || SYMBOL_ONLY.matches(tail) || namedMoneyIn("1 $tail").any { it.range.first == 0 && it.range.last == tail.length + 1 }

/**
 * قبل [anchor] (القوس أو «=») مبلغ على طول (ممكن في السطر اللي فوق): رقم — مش تاريخ ولا ساعة ولا «*6604» — وبعده بالكتير اسم/كود/رمز
 * عملة، وسطره مفهوش قبل الرقم كلمة رسوم أو رصيد أو نقاط ([NOT_CONVERSION]) ولا كارت/حساب/مرجع ([ID_BEFORE]). بيرجّع مكان الرقم ده.
 */
private fun amountBefore(body: String, anchor: Int, local: LocalCurrency): IntRange? {
    var end = anchor
    while (end > 0 && JsText.isWhitespace(body[end - 1])) end--
    var i = end
    while (i > 0 && !body[i - 1].isDigit() && body[i - 1] != '\n') i--
    val tail = body.substring(i, end).trim()
    if (i == 0 || !body[i - 1].isDigit()) return null
    if (tail.isNotEmpty() && (!isCurrencyTail(tail) || local.regex.containsMatchIn(tail))) return null
    var start = i
    while (start > 0 && (body[start - 1].isDigit() || body[start - 1] in ",.٬٫")) start--
    val prev = body.getOrNull(start - 1)
    if (prev != null && (prev in "*•xX#" || (prev in ":/\\-" && body.getOrNull(start - 2)?.isDigit() == true))) return null
    val lineStart = body.lastIndexOf('\n', start - 1) + 1
    val before = body.substring(lineStart, start)
    if (NOT_CONVERSION.containsMatchIn(before) || ID_BEFORE.containsMatchIn(before)) return null
    if (local.regex.containsMatchIn(before.takeLast(8))) return null // «SAR 87.50 (…)» المبلغ نفسه محلي
    return start until i
}

private fun localValue(m: MatchResult): Halalas? {
    val raw = m.groups[1]?.value ?: m.groups[2]!!.value
    if (!LOCAL_GROUPING.matches(raw)) return null
    return tryParseMoney(raw.replace('٬', ',').replace('٫', '.'))?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR }
}

/** المقابل المحلي في رسالة: المبلغ اللي قبله (لو مكتوب) + قيمته. */
private class Conversion(val foreignSide: IntRange?, val local: Halalas?)

private fun conversionsIn(body: String, local: LocalCurrency, withLabels: Boolean): List<Conversion> {
    val out = mutableListOf<Conversion>()
    for (regex in listOf(local.parens, local.equals)) {
        for (m in regex.findAll(body)) amountBefore(body, m.range.first, local)?.let { out += Conversion(it, localValue(m)) }
    }
    for (m in local.equivalent.findAll(body)) out += Conversion(null, localValue(m))
    if (withLabels) for (m in local.label.findAll(body)) out += Conversion(null, localValue(m))
    return out
}

/** المقابل المحلي **المكتوب** (اقتراح للسؤال — §75-12)، لو واحد بس وواضح. */
internal fun localConversion(body: String, local: LocalCurrency): Halalas? =
    conversionsIn(body, local, withLabels = true).mapNotNull { it.local }.toSet().singleOrNull()

/**
 * فيها مقابل بعملة [local] بعد مبلغ تاني — يعني الكارت **بتاع البلد دي** واتخصم بعملة تانية («charged SAR 75.00 (EGP 980.00)» =
 * كارت مصري؛ «EGP 500.00 (SAR 37.50)» = كارت سعودي). القارئين بيستعملوها عشان الرسالة تستنى في بلد واحد بس.
 */
internal fun hasLocalConversion(body: String, local: LocalCurrency): Boolean = conversionsIn(body, local, withLabels = false).isNotEmpty()

// ── 4. عنوان «دولي» + مبلغ محلي ─────────────────────────────────────────

private val INTERNATIONAL_HEAD = Regex("(?<![$AR])(?:دولي|دولية)(?![$AR])|${B}International$B", FI)
private val TRANSFER_WORD = Regex("حوالة|تحويل|${B}transfer|remittance", FI)

/** أول سطر «شراء دولي/International Purchase» (مش حوالة — الحوالة الدولية بالريال عادي) والرسالة فيها مبلغ محلي. */
private fun internationalWithLocal(body: String, local: LocalCurrency): Boolean {
    val title = smsTitleLine(body)
    return INTERNATIONAL_HEAD.containsMatchIn(title) && !TRANSFER_WORD.containsMatchIn(title) && local.regex.containsMatchIn(body)
}

// ── الدليل والمبالغ ─────────────────────────────────────────────────────

/** كل مبلغ بعملة أجنبية في الرسالة (كود · رمز · اسم · ورقم قبل المقابل المحلي)، من غير عملة البلد. */
internal fun foreignMoneyIn(body: String, local: LocalCurrency): List<IsoMoney> {
    val relaxed = internationalWithLocal(body, local)
    val beforeConversion = conversionsIn(body, local, withLabels = false).mapNotNull { it.foreignSide }
    val strict = isoMoneyIn(body).map { it.range }.toSet()
    val coded = isoMoneyIn(body, relaxed = true).filter { relaxed || it.range in strict || beforeConversion.any { r -> it.range.overlaps(r) } }
    return (coded.filter { it.inOneLine(body) } + namedMoneyIn(body)).filter { it.code?.uppercase() != local.code }
}

private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last

/** الرسالة بعملة أجنبية (أي دليل من الأربعة) — حتى لو المبلغ الأجنبي نفسه مش واضح. */
internal fun hasForeignEvidence(body: String, local: LocalCurrency): Boolean =
    foreignMoneyIn(body, local).isNotEmpty() || conversionsIn(body, local, withLabels = false).isNotEmpty()
