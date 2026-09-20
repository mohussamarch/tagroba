package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * محلل رسايل البنك — نقل `src/infrastructure/import/bankSmsParser.ts` بنفس الأنماط ونفس أسباب الرفض.
 * المبلغ = رقم جنبه عملة الريال في سطر مش رصيد ولا حد ولا رسوم (قيمة واحدة بس). التاريخ بسنتين بيتفهم
 * بتاريخ وصول الرسالة (تاريخ واحد بس من 60 يوم قبل لحد يوم بعد). كل رسالة محتاجة تأكيد المستخدم.
 * الأنماط بـ`S` و`B` بدل `\s` و`\b` عشان تطابق جافاسكربت بالظبط (JsText).
 */
data class BankSmsMessage(val sender: String, val receivedAt: String, val body: String)

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
)

sealed interface SmsParseResult {
    data class Ok(val row: SmsRow) : SmsParseResult
    data class Rejected(val reason: String) : SmsParseResult
}

private val I = setOf(RegexOption.IGNORE_CASE)
private val IM = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

// «ننصح بعدم مشاركة الرمز… الرمز:123456» — رسالة التحقق اللي قبل كل شراء إنترنت (مش عملية)
private val SENSITIVE = Regex("${B}OTP$B|verification${S}*code|one.time$S*(?:password|code)|رمز$S*(?:التحقق|التوثيق|التفعيل|الدخول)|كلمة$S*(?:المرور|السر)|مشاركة$S*الرمز|الرمز$S*[:：]?$S*\\d{4,8}", I)
private val OFFER = Regex("عرض|سيتم|عرض خاص|offer|will be|scheduled", I)
private val DECLINED = Regex("مرفوض|رفض العملية|لم تتم|غير ناجح|declined|failed|unsuccessful", I)
private val OUT = Regex("شراء|سحب|خصم|سداد|مدفوعات|دفع|(?:حوالة|تحويل)[^\\n]{0,20}صادر|purchase|withdrawal|outgoing transfer", I)
private val INCOMING = Regex("(?:حوالة|تحويل)[^\\n]{0,20}وارد|إيداع|ايداع|راتب|استرداد|مرتجع|incoming transfer|salary|deposit|refund", I)
private val FOREIGN = Regex("$B(?:USD|EUR|EGP|AED|GBP)$B|دولار|يورو|جنيه", I)

private val CURRENCY = "(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\\.$S?س\\.?|ريال)"
private const val NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d{1,2})?"
private val CURRENCY_AMOUNT = Regex("$CURRENCY$S*[:：]?$S*($NUMBER)|($NUMBER)$S*$CURRENCY", I)
private val NOT_TRANSACTION_AMOUNT = Regex("الرصيد|رصيد|balance|المتاح|متاح|available|الحد|limit|رسوم|${B}fees?$B|عمولة|المتبقي", I)
private val BARE_AMOUNT = Regex("(?:بمبلغ|المبلغ|مبلغ|amount|بـ|قيمة)$S*[:：]?$S*\\d", I)
private const val DAY_MS = 86_400_000L

/** علامات الاتجاه المخفية حوالين الأرقام والإنجليزي — بتقطع الأنماط من غير ما تبان. */
private val BIDI_CODES = setOf(0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069, 0x061C)

/** `SA[\d\s]{20,}` — الأرقام والمسافات (بمعنى جافاسكربت) في فئة واحدة. */
private val REDACT_IBAN = Regex("SA[\\d" + S.substring(1, S.length - 1) + "]{20,}", I)
private val REDACT_LONG = Regex("$B(?:\\d[ -]*){12,34}$B")
private val REDACT_DIGITS = Regex("\\d{5,}")
private val REDACT_KEEP = Regex(
    "(?:(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)$S*[:：]?$S*)?(?:(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ريال|ر\\.?س\\.?)$S*[:：]?$S*[\\d,٬]+(?:[.٫]\\d{1,2})?|[\\d,٬]+(?:[.٫]\\d{1,2})?$S*(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ريال|ر\\.?س\\.?))",
    I,
)

private fun lastFour(text: String) = if (text.length <= 4) text else text.substring(text.length - 4)

/** حجب أرقام الحسابات والبطاقات — المبلغ اللي جنبه عملة ما بيتحجبش (زي SmsSafety.java). */
fun redactSms(input: String): String {
    val text = latinizeDigits(input)
    fun redact(value: String) = value
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

private sealed interface AmountResult {
    data class Ok(val amountMinor: Halalas) : AmountResult
    data class Fail(val reason: String) : AmountResult
}

private fun transactionAmount(body: String): AmountResult {
    val values = LinkedHashSet<Long>()
    for (line in body.split('\n')) {
        for (match in CURRENCY_AMOUNT.findAll(line)) {
            if (NOT_TRANSACTION_AMOUNT.containsMatchIn(line.substring(0, match.range.first))) continue
            val number = match.groups[1]?.value ?: match.groups[2]!!.value
            val amount = tryParseMoney(number.replace('٬', ',').replace('٫', '.'))
            if (amount == null || amount <= 0) return AmountResult.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
            values.add(amount)
        }
    }
    if (values.size == 1) return AmountResult.Ok(values.first())
    if (values.size > 1) return AmountResult.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    return AmountResult.Fail(if (BARE_AMOUNT.containsMatchIn(body)) uiText(TextKey.SMS_CURRENCY_UNCLEAR) else uiText(TextKey.SMS_AMOUNT_UNCLEAR))
}

private fun iso(y: Int, m: Int, d: Int) = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
private val LONG_YMD = Regex("(?<!\\d)(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)")
private val LONG_DMY = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})(?!\\d)")
private val SHORT = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)")

private fun transactionDate(body: String, receivedAt: String): IsoDate? {
    LONG_YMD.find(body)?.let { m ->
        val v = iso(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        return if (isValidIsoDate(v)) v else null
    }
    LONG_DMY.find(body)?.let { m ->
        val v = iso(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        return if (isValidIsoDate(v)) v else null
    }
    val received = JsText.parseIsoMillis(receivedAt) ?: return null
    val candidates = LinkedHashSet<String>()
    for (m in SHORT.findAll(body)) {
        val (a, b, c) = m.destructured.toList().map { it.toInt() }
        for (value in listOf(iso(2000 + a, b, c), iso(2000 + c, b, a))) {
            if (!isValidIsoDate(value)) continue
            val time = toDayNumber(parseIsoDate(value)).toLong() * DAY_MS
            if (time <= received + DAY_MS && time >= received - 60 * DAY_MS) candidates.add(value)
        }
    }
    return if (candidates.size == 1) candidates.first() else null
}

private val MERCHANT_AT = Regex("(?:لدى|عند|تاجر|${B}merchant$B|${B}at$B)$S*[:：]?$S*([^\\n]+?)(?=$S+(?:في|بتاريخ|${B}on$B|الرصيد|${B}balance$B)(?:$S|[:：])|$)", IM)
private val MERCHANT_LAM = Regex("^$S*لـ$S*[:：]?$S*([^\\n]+)$", setOf(RegexOption.MULTILINE))
private val MERCHANT_FROM_TO = Regex("^$S*(?:من|إلى|الى|${B}from$B|${B}to$B)$S*[:：]$S*([^\\n]+)$", IM)

/** «لدى:»/«عند»، أو سطر بيبدأ بـ«لـ»، أو «من:/إلى:». الأرقام بس (حساب) مش تاجر. */
private fun merchantOf(body: String): String {
    fun clean(value: String?): String {
        val text = JsText.trim(value ?: "")
        return if (text.all { it in '0'..'9' || JsText.isWhitespace(it) || it in "*•.:-" }) "" else text
    }
    return clean(MERCHANT_AT.find(body)?.groupValues?.get(1)).ifEmpty { null }
        ?: clean(MERCHANT_LAM.find(body)?.groupValues?.get(1)).ifEmpty { null }
        ?: clean(MERCHANT_FROM_TO.find(body)?.groupValues?.get(1))
}

/** قوالب سعودية محافظة؛ الشكل المجهول بيترفض بسبب واضح ويتضاف باليد. */
fun parseBankSms(message: BankSmsMessage, lineNumber: Int): SmsParseResult {
    val body = latinizeDigits(message.body).filterNot { it == '\r' || it.code in BIDI_CODES }
    if (OFFER.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_OFFER))
    if (SENSITIVE.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_SENSITIVE))
    if (DECLINED.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_DECLINED))
    // «حوالة داخلية صادرة» و«حوالة محلية واردة»: كلمة الاتجاه ممكن تيجي بعد نوع الحوالة
    val out = OUT.containsMatchIn(body)
    val incoming = INCOMING.containsMatchIn(body)
    if (out == incoming) return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    if (FOREIGN.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_FOREIGN_CURRENCY))
    val amount = when (val a = transactionAmount(body)) {
        is AmountResult.Fail -> return SmsParseResult.Rejected(a.reason)
        is AmountResult.Ok -> a.amountMinor
    }
    val date = transactionDate(body, message.receivedAt) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DATE_UNCLEAR))
    val safeBody = redactSms(body)
    return SmsParseResult.Ok(
        SmsRow(
            lineNumber = lineNumber, date = date, amountMinor = amount,
            direction = if (incoming) Direction.IN else Direction.OUT,
            merchantName = redactSms(merchantOf(body)),
            reference = "SMS:" + hashContent(message.sender + "|" + message.receivedAt + "|" + body),
            sourceName = message.sender, description = safeBody, raw = safeBody,
        ),
    )
}
