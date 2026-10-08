package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * خانات رسالة البنك السعودي: المبلغ (والعملة الأجنبية) والمحل. القواعد الأولانية في كل دالة = التطبيق الحالي بالظبط
 * (ملف المرجع `golden/sms.json`)، والإضافات من أشكال البحث ومكتوب جنب كل واحدة بنكها.
 */

private val I = setOf(RegexOption.IGNORE_CASE)
private val IM = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

// «ريال سعودى 87.50» (الإنماء — العملة بالكلام قبل الرقم)
private val CURRENCY = "(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\\.$S?س\\.?|ريال(?:$S+سعود[يى])?)"
private const val NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d{1,2})?"
private val CURRENCY_AMOUNT = Regex("$CURRENCY$S*[:：]?$S*($NUMBER)|($NUMBER)$S*$CURRENCY", I)

/** سطر فيه كلمة من دول قبل المبلغ ⇒ مش مبلغ العملية. الإضافات: الضريبة (إس تي سي) · «اعادة مبلغ» = الباقي من الحجز (الراجحي). */
private val NOT_TRANSACTION_AMOUNT = Regex(
    "الرصيد|رصيد|balance|المتاح|متاح|available|الحد|limit|رسوم|${B}fees?$B|عمولة|المتبقي" + "|${B}VAT$B|ضريبة|اعادة|إعادة",
    I,
)
private val BARE_AMOUNT = Regex("(?:بمبلغ|المبلغ|مبلغ|amount|بـ|قيمة)$S*[:：]?$S*\\d", I)

/** «إجمالي المبلغ المستحق» / «Total due amount» (إس تي سي · بنك غير معروف): المبلغ + الرسوم + الضريبة = المخصوم فعلًا. */
private val TOTAL_DUE_LINE = Regex("^[^\\n]*(?:Total$S*due$S*amount|إجمال[يى]$S*المبلغ$S*المستحق)[^\\n]*$", IM)

// القايمة القديمة (أي حالة حروف، والكود لوحده كفاية). باقي أكواد ISO في `SmsForeignCodes.kt` (حروف كبيرة وجنبها مبلغ)
private const val FOREIGN_CODES = "USD|EUR|EGP|AED|GBP|KWD|BHD|QAR|OMR|JOD"
// «EGP900.00» (فودافون) لازق في الرقم ⇒ الحد بعد الكود حرف لاتيني بس، مش رقم
private val FOREIGN = Regex("(?<![A-Za-z])(?:$FOREIGN_CODES)(?![A-Za-z])|دولار|يورو|جنيه|ج\\.م|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])", I)

/** المبلغ الأجنبي وجنبه مقابله بالريال بين قوسين (دي 360 «USD 23.40 (SAR 87.50)» · «23.40 USD (87.50 ريال)»). */
private val LOCAL_IN_PARENS = Regex("\\($S*(?:$CURRENCY$S*$NUMBER|$NUMBER$S*$CURRENCY)$S*\\)", I)
private val FOREIGN_AMOUNT = Regex("(?<![A-Za-z])($FOREIGN_CODES)$S*[:：]?$S*($NUMBER)|($NUMBER)$S*($FOREIGN_CODES)(?![A-Za-z])", I)

internal sealed interface SaudiAmount {
    data class Ok(val amountMinor: Halalas, val foreign: SmsForeignAmount?) : SaudiAmount

    /** المبلغ بعملة أجنبية بس. [foreign] = المبلغ الأجنبي لو واحد وواضح. */
    data class ForeignOnly(val foreign: SmsForeignAmount?) : SaudiAmount
    data class Fail(val reason: String) : SaudiAmount
}

private fun parse(number: String): Halalas? = tryParseMoney(number.replace('٬', ',').replace('٫', '.'))

/** القاعدة القديمة: رقم جنبه عملة الريال في سطر مش رصيد ولا حد ولا رسوم — قيمة واحدة بس. */
private fun oneLocalAmount(text: String, body: String): SaudiAmount {
    val values = LinkedHashSet<Long>()
    for (line in text.split('\n')) {
        for (match in CURRENCY_AMOUNT.findAll(line)) {
            if (NOT_TRANSACTION_AMOUNT.containsMatchIn(line.substring(0, match.range.first))) continue
            val amount = parse(match.groups[1]?.value ?: match.groups[2]!!.value)
            if (amount == null || amount <= 0) return SaudiAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
            values.add(amount)
        }
    }
    if (values.size == 1) return SaudiAmount.Ok(values.first(), null)
    if (values.size > 1) return SaudiAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    return SaudiAmount.Fail(if (BARE_AMOUNT.containsMatchIn(body)) uiText(TextKey.SMS_CURRENCY_UNCLEAR) else uiText(TextKey.SMS_AMOUNT_UNCLEAR))
}

/**
 * المبلغ الأجنبي الوحيد في الرسالة (مش في سطر رصيد أو رسوم)، أو null. [skip] = عملة البلد (الجنيه في قارئ مصر).
 * القايمة القديمة + أي كود ISO جنب مبلغ (`SmsForeignCodes.kt` — «TRY 450.00» كانت بتضيع).
 */
internal fun foreignAmountOf(body: String, skip: String? = null): SmsForeignAmount? {
    val found = LinkedHashSet<SmsForeignAmount>()
    for (line in body.split('\n')) {
        val listed = FOREIGN_AMOUNT.findAll(line).map { m ->
            IsoMoney(m.range, m.groups[1]?.value ?: m.groups[4]!!.value, m.groups[2]?.value ?: m.groups[3]!!.value)
        }
        for (m in listed + isoMoneyIn(line)) {
            if (NOT_TRANSACTION_AMOUNT.containsMatchIn(line.substring(0, m.range.first))) continue
            val code = m.code.uppercase()
            if (code == skip) continue
            val amount = parse(m.number) ?: return null
            if (amount > 0) found += SmsForeignAmount(code, amount)
        }
    }
    return found.singleOrNull()
}

/**
 * مبلغ العملية: «إجمالي المبلغ المستحق» لو موجود (المبلغ + الرسوم + الضريبة) ⇒ وإلا القاعدة القديمة. الرسالة بعملة أجنبية:
 * لو مقابلها بالريال مكتوب (بين قوسين أو الإجمالي) ⇒ ده المبلغ؛ وإلا [SaudiAmount.ForeignOnly] (§75-12).
 */
internal fun saudiAmount(body: String): SaudiAmount {
    val foreign = FOREIGN.containsMatchIn(body) || isoMoneyIn(body).isNotEmpty()
    TOTAL_DUE_LINE.find(body)?.let { line ->
        val total = oneLocalAmount(line.value, body)
        if (total is SaudiAmount.Ok) return SaudiAmount.Ok(total.amountMinor, if (foreign) foreignAmountOf(body) else null)
    }
    if (foreign && !LOCAL_IN_PARENS.containsMatchIn(body)) return SaudiAmount.ForeignOnly(foreignAmountOf(body))
    return when (val local = oneLocalAmount(body, body)) {
        is SaudiAmount.Ok -> if (foreign) local.copy(foreign = foreignAmountOf(body)) else local
        else -> local
    }
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
