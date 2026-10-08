package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * خانات رسالة البنك السعودي: المبلغ (والعملة الأجنبية) والمحل. القواعد الأولانية في كل دالة = التطبيق الحالي بالظبط
 * (ملف المرجع `golden/sms.json`)، والإضافات من أشكال البحث ومكتوب جنب كل واحدة بنكها.
 */

private val I = setOf(RegexOption.IGNORE_CASE)
private val IM = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

// «ريال سعودى 87.50» (الإنماء — العملة بالكلام قبل الرقم). «ريال قطري» مش ريال سعودي (الجولة التانية من المراجعة)
private val CURRENCY = "(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\\.$S?س\\.?|ريال(?!$S*(?:$OTHER_RIYAL_TAIL))(?:$S+سعود[يى])?)"
private val CURRENCY_TOKEN = Regex(CURRENCY, I)

/**
 * سطر فيه كلمة من دول قبل المبلغ ⇒ مش مبلغ العملية. الإضافات: الضريبة (إس تي سي) · «اعادة مبلغ» = الباقي من الحجز (الراجحي) ·
 * «Bal» · charges · commission (الجولة التانية من المراجعة).
 */
private val NOT_TRANSACTION_AMOUNT = Regex(
    "الرصيد|رصيد|balance|المتاح|متاح|available|الحد|limit|رسوم|${B}fees?$B|عمولة|المتبقي" + "|${B}VAT$B|ضريبة|اعادة|إعادة" +
        "|${B}bal$B|${B}charges?$B|commission",
    I,
)

/** كلمة رصيد **بعد** المبلغ ومفيش رقم تاني بعدها في السطر («SAR 4,100.00 is your available balance» · «SAR 4,100.00 Available»). */
private val TRAILING_LABEL = Regex(
    "^$S*(?:is$S+)?(?:your$S+)?(?:(?:available|current|remaining|new)$S+)?(?:balance|limit|available|${B}bal$B)|^$S*(?:الرصيد|رصيد|المتاح|متاح)",
    I,
)

/** سطر كله اسم خانة رصيد أو رسوم من غير رقم («الرصيد» · «رسوم:») ⇒ المبلغ اللي في السطر اللي بعده لوحده هو قيمتها. */
private val LABEL_ONLY_LINE = Regex(
    "^$S*(?:(?:ال)?رصيد(?:ك)?(?:$S+(?:ال)?(?:متاح|حالي|متبقي|متبقى))?|(?:ال)?متاح|(?:ال)?رسوم|(?:ال)?عمولة|(?:ال)?ضريبة|" +
        "(?:available$S+|current$S+|remaining$S+)?balance|fees?|charges?|commission|VAT)$S*[:：]?$S*$",
    I,
)
private val BARE_AMOUNT = Regex("(?:بمبلغ|المبلغ|مبلغ|amount|بـ|قيمة)$S*[:：]?$S*\\d", I)

/** «إجمالي المبلغ المستحق» / «Total due amount» (إس تي سي · بنك غير معروف): المبلغ + الرسوم + الضريبة = المخصوم فعلًا. */
private val TOTAL_DUE_LINE = Regex("^[^\\n]*(?:Total$S*due$S*amount|إجمال[يى]$S*المبلغ$S*المستحق)[^\\n]*$", IM)

// القايمة القديمة (أي حالة حروف، والكود لوحده كفاية). باقي أكواد ISO في `SmsForeignCodes.kt` (حروف كبيرة وجنبها مبلغ)
private const val FOREIGN_CODES = "USD|EUR|EGP|AED|GBP|KWD|BHD|QAR|OMR|JOD"
// «EGP900.00» (فودافون) لازق في الرقم ⇒ الحد بعد الكود حرف لاتيني بس، مش رقم
private val FOREIGN = Regex(
    "(?<![A-Za-z])(?:$FOREIGN_CODES)(?![A-Za-z])|دولار|يورو|جنيه|ج\\.م|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|$ARABIC_FOREIGN_WORD",
    I,
)

/** المبلغ الأجنبي وجنبه مقابله بالريال بين قوسين (دي 360 «USD 23.40 (SAR 87.50)» · «23.40 USD (87.50 ريال)»). */
private const val LOCAL_NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d{1,2})?"
private val LOCAL_IN_PARENS = Regex("\\($S*(?:$CURRENCY$S*($LOCAL_NUMBER)|($LOCAL_NUMBER)$S*$CURRENCY)$S*\\)", I)
private val FOREIGN_AMOUNT = Regex("(?<![A-Za-z])($FOREIGN_CODES)$S*[:：]?$S*($FOREIGN_NUMBER)|(?<![\\d.,٬٫])($FOREIGN_NUMBER)$S*($FOREIGN_CODES)(?![A-Za-z])", I)

internal sealed interface SaudiAmount {
    data class Ok(val amountMinor: Halalas) : SaudiAmount

    /**
     * عملية بعملة أجنبية (§75-12 — قرار المالك: تتسجل وتسأل عن المبلغ المحلي، **مش** تتسجل لوحدها لو المحلي مكتوب). [foreign] =
     * المبلغ الأجنبي لو واحد وواضح، و[localSuggestion] = المبلغ بالريال المكتوب في الرسالة (الإجمالي المستحق أو بين قوسين) — اقتراح بس.
     */
    data class ForeignOnly(val foreign: SmsForeignAmount?, val localSuggestion: Halalas?) : SaudiAmount
    data class Fail(val reason: String) : SaudiAmount
}

private fun parse(number: String): Halalas? = tryParseMoney(number.replace('٬', ',').replace('٫', '.'))

/** المبلغ ده قيمة رصيد أو رسوم مكتوب اسمها **بعده** أو في **السطر اللي قبله** (مش قبله في نفس السطر). */
private fun labelledElsewhere(lines: List<String>, index: Int, near: Near): Boolean {
    val line = lines[index]
    val rest = line.substring(near.end)
    if (TRAILING_LABEL.containsMatchIn(rest) && rest.none { it in '0'..'9' }) return true
    if (line.substring(0, near.start).isNotBlank() || rest.isNotBlank()) return false
    val previous = lines.subList(0, index).lastOrNull { it.isNotBlank() } ?: return false
    return LABEL_ONLY_LINE.matches(previous)
}

/**
 * القاعدة القديمة: رقم جنبه عملة الريال في سطر مش رصيد ولا حد ولا رسوم — قيمة واحدة بس. الجولة التانية: رقم من الناحيتين
 * («Card 6604 SAR 64.25») ⇒ أكتر من مبلغ · فواصل غلط ⇒ مش صالح · أكبر من [SMS_AMOUNT_CAP_MINOR] ⇒ مش صالح (`SmsAmountTokens.kt`).
 */
private fun oneLocalAmount(text: String, body: String): SaudiAmount {
    val values = LinkedHashSet<Long>()
    val lines = text.split('\n')
    for ((index, line) in lines.withIndex()) {
        for (near in amountsNearCurrency(line, CURRENCY_TOKEN, AmountStyle.SAUDI)) {
            if (NOT_TRANSACTION_AMOUNT.containsMatchIn(line.substring(0, near.start))) continue
            if (labelledElsewhere(lines, index, near)) continue
            val number = when (near) {
                is Near.Ambiguous -> return SaudiAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
                is Near.Malformed -> return SaudiAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
                is Near.Value -> near.number
            }
            val amount = parse(number)
            if (amount == null || amount <= 0 || amount > SMS_AMOUNT_CAP_MINOR) return SaudiAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
            values.add(amount)
        }
    }
    if (values.size == 1) return SaudiAmount.Ok(values.first())
    if (values.size > 1) return SaudiAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    return SaudiAmount.Fail(if (BARE_AMOUNT.containsMatchIn(body)) uiText(TextKey.SMS_CURRENCY_UNCLEAR) else uiText(TextKey.SMS_AMOUNT_UNCLEAR))
}

/**
 * المبلغ الأجنبي الوحيد في الرسالة (مش في سطر رصيد أو رسوم)، أو null. [skip] = عملة البلد (الجنيه في قارئ مصر).
 * القايمة القديمة + أي كود ISO جنب مبلغ (`SmsForeignCodes.kt` — «TRY 450.00» كانت بتضيع) + اسم العملة بالعربي («ريال قطري»).
 * المبلغ بكسور العملة نفسها («KWD 12.345» = 12345 فلس) — رقم مش مظبوط ⇒ null (ما بنخمّنش).
 */
internal fun foreignAmountOf(body: String, skip: String? = null): SmsForeignAmount? {
    val found = LinkedHashSet<SmsForeignAmount>()
    for (line in body.split('\n')) {
        val listed = FOREIGN_AMOUNT.findAll(line).map { m ->
            IsoMoney(m.range, m.groups[1]?.value ?: m.groups[4]!!.value, m.groups[2]?.value ?: m.groups[3]!!.value)
        }
        for (m in listed + isoMoneyIn(line) + arabicMoneyIn(line)) {
            if (NOT_TRANSACTION_AMOUNT.containsMatchIn(line.substring(0, m.range.first))) continue
            val code = m.code.uppercase()
            if (code == skip) continue
            val amount = parseForeignMinor(m.number, code) ?: return null
            if (amount > 0) found += SmsForeignAmount(code, amount)
        }
    }
    return found.singleOrNull()
}

/** المقابل بالريال بين قوسين جنب المبلغ الأجنبي، أو null. */
private fun localInParens(body: String): Halalas? {
    val values = LOCAL_IN_PARENS.findAll(body).mapNotNull { m -> parse(m.groups[1]?.value ?: m.groups[2]!!.value) }.toSet()
    return values.singleOrNull()?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR }
}

/** فيه ريال سعودي في الرسالة (حتى لو في سطر الرصيد) — علامة إن الرسالة من بنك سعودي (لما مبلغها بالجنيه). */
internal fun hasSaudiCurrency(body: String): Boolean = CURRENCY_TOKEN.containsMatchIn(body)

/** الريال السعودي مكتوب صريح (مش «ريال» لوحدها — ممكن تبقى ريال بلد تاني في رسالة مصرية). */
private val EXPLICIT_SAR = Regex("(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\\.$S?س|ريال$S+سعود[يى]", I)

/** مبلغ العملية بالريال لو واحد وواضح — لقارئ مصر لما كارت مصري يتخصم بالريال (عملة أجنبية هناك — §75-12). */
internal fun riyalAmountAsForeign(body: String): SmsForeignAmount? =
    if (!EXPLICIT_SAR.containsMatchIn(body)) null else (oneLocalAmount(body, body) as? SaudiAmount.Ok)?.let { SmsForeignAmount("SAR", it.amountMinor) }

/**
 * مبلغ العملية: «إجمالي المبلغ المستحق» لو موجود (المبلغ + الرسوم + الضريبة) ⇒ وإلا القاعدة القديمة.
 * **الرسالة بعملة أجنبية ⇒ [SaudiAmount.ForeignOnly] دايمًا** (§75-12، قرار المالك ✗ على «يتسجل لو المحلي مكتوب»): المقابل
 * بالريال لو مكتوب (الإجمالي أو بين قوسين) بيمشي معاها **اقتراح** للسؤال، وما بيتسجلش لوحده.
 */
internal fun saudiAmount(body: String): SaudiAmount {
    val foreign = FOREIGN.containsMatchIn(body) || isoMoneyIn(body).isNotEmpty()
    val total = TOTAL_DUE_LINE.find(body)?.let { oneLocalAmount(it.value, body) as? SaudiAmount.Ok }
    if (foreign) return SaudiAmount.ForeignOnly(foreignAmountOf(body), total?.amountMinor ?: localInParens(body))
    return total ?: oneLocalAmount(body, body)
}

// ── المحل ────────────────────────────────────────────────────────────────

private val MERCHANT_AT = Regex("(?:لدى|عند|تاجر|${B}merchant$B|${B}at$B)$S*[:：]?$S*([^\\n]+?)(?=$S+(?:في|بتاريخ|${B}on$B|الرصيد|${B}balance$B)(?:$S|[:：])|$)", IM)
private val MERCHANT_LAM = Regex("^$S*لـ$S*[:：]?$S*([^\\n]+)$", setOf(RegexOption.MULTILINE))
private val MERCHANT_FROM_TO = Regex("^$S*(من|إلى|الى|${B}from$B|${B}to$B)$S*[:：]$S*([^\\n]+)$", IM)

/** «من البائع:» (الإنماء — حوالة عكسية) · «Transaction:» (إس تي سي — إشعار استرداد). */
private val MERCHANT_LABEL = Regex("^[ \\t]*(?:من[ \\t]*البائع|Transaction)[ \\t]*[:：][ \\t]*([^\\n]+)$", IM)

/** إس تي سي «عكس عملية»: «في: <المحل>» و«بتاريخ: <التاريخ>». */
private val DATE_LABEL_LINE = Regex("^[ \\t]*بتاريخ[ \\t]*[:：]", setOf(RegexOption.MULTILINE))
private val FI_LINE = Regex("^[ \\t]*في[ \\t]*[:：][ \\t]*([^\\n]+)$", setOf(RegexOption.MULTILINE))

/** الراجحي 2026 الوارد: «من7719;<الاسم>» من غير نقطتين. */
private val FROM_ACCOUNT_NAME = Regex("^[ \\t]*من[ \\t]*([0-9*•]+[ \\t]*;[ \\t]*[^\\n]+)$", setOf(RegexOption.MULTILINE))

/** الأهلي السعودي والإنماء: «من <المحل>» من غير نقطتين في رسالة الشراء. «من حساب» و«من بنك» مش محل. */
private val FROM_NO_COLON = Regex("^[ \\t]*من[ \\t]+(?!حساب|بنك|البائع|رصيد|بطاقة)([^\\n:：]+)$", setOf(RegexOption.MULTILINE))

private val MASKED_IBAN = Regex("^(?:SA)?[0-9*•xX ]{6,}$", I)
private val ACCOUNT_THEN_NAME = Regex("^[0-9*•]+[ \\t]*;[ \\t]*(.+)$")

/** أرقام بس (حساب) · تاريخ وساعة · آيبان متقص ⇒ مش محل. «7719;خالد» (الراجحي الجديد) ⇒ الاسم بس. */
private fun cleanMerchant(value: String?): String {
    val text = JsText.trim(value ?: "")
    if (text.all { it in '0'..'9' || JsText.isWhitespace(it) || it in "*•.:-/\\" }) return ""
    if (MASKED_IBAN.matches(text)) return ""
    ACCOUNT_THEN_NAME.matchEntire(text)?.let { return JsText.trim(it.groupValues[1]) }
    return text
}

/**
 * «لدى:»/«عند»/At ⇒ «من البائع:»/Transaction: ⇒ «في:» لو التاريخ في «بتاريخ:» ⇒ سطر بيبدأ بـ«لـ» ⇒ «من:/إلى:» ⇒ «من7719;اسم» ⇒ «من <محل>».
 * في الشراء والاسترداد: «من:» اللي فيها اسم بس (إس تي سي: أول «من:» الكارت والتاني المحل)، و«إلى:» مش محل.
 */
internal fun saudiMerchantOf(body: String, kind: SmsKind): String {
    val shopping = kind == SmsKind.PURCHASE || kind == SmsKind.REFUND
    cleanMerchant(MERCHANT_AT.find(body)?.groupValues?.get(1)).ifEmpty { null }?.let { return it }
    cleanMerchant(MERCHANT_LABEL.find(body)?.groupValues?.get(1)).ifEmpty { null }?.let { return it }
    if (DATE_LABEL_LINE.containsMatchIn(body)) cleanMerchant(FI_LINE.find(body)?.groupValues?.get(1)).ifEmpty { null }?.let { return it }
    cleanMerchant(MERCHANT_LAM.find(body)?.groupValues?.get(1)).ifEmpty { null }?.let { return it }
    val fromTo = if (shopping) {
        MERCHANT_FROM_TO.findAll(body).filter { it.groupValues[1].lowercase() in setOf("من", "from") }
            .map { cleanMerchant(it.groupValues[2]) }.firstOrNull { it.isNotEmpty() }.orEmpty()
    } else {
        cleanMerchant(MERCHANT_FROM_TO.find(body)?.groupValues?.get(2))
    }
    if (fromTo.isNotEmpty()) return fromTo
    cleanMerchant(FROM_ACCOUNT_NAME.find(body)?.groupValues?.get(1)).ifEmpty { null }?.let { return it }
    if (shopping) return cleanMerchant(FROM_NO_COLON.find(body)?.groupValues?.get(1))
    return ""
}
