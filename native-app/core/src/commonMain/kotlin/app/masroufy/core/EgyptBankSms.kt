package app.masroufy.core

/**
 * قارئ رسايل بنوك مصر — بُني على عينات حقيقية من **QNB مصر** بعت بيها المالك (OVERRIDES §40.3).
 * ⚠️ العينات نفسها **مش في المستودع** (بيانات حقيقية، والمستودع عام)، والاختبارات برسايل مخترعة بنفس الشكل.
 *
 * التلات أشكال اللي في العينات:
 * 1. `IPN transfer sent with amount of EGP 300.00 from 1234 on 29/07 at 12:04 PM. Ref# ab12cd34.` ⇒ صادر
 * 2. `IPN transfer received with amount of EGP 250.00 on 1234 on 29/07 at 03:01 PM. Ref# ab12cd34.` ⇒ وارد
 * 3. `Your Debit Card **1234 had a Successful transaction of EGP 41.25 @store.com,your available bal.EGP174.40` ⇒ صادر
 *
 * **مفيش ملف مرجع (golden) للقارئ ده**: التطبيق الحالي مش بيقرا رسايل مصرية أصلًا، فمفيش حاجة يتطابق معاها.
 * الضمان هنا اختبارات مكتوبة بالإيد على الأشكال دي وعلى اللي لازم **يترفض**.
 */

private val EG_I = setOf(RegexOption.IGNORE_CASE)

// المبلغ بيجي بعد «amount of» أو «transaction of» — والرصيد («bal.EGP…») مش بينطبق عليه
private val EG_AMOUNT = Regex("(?:amount|transaction)${JsText.S}+of${JsText.S}+EGP${JsText.S}*([\\d,]+(?:\\.\\d{1,2})?)", EG_I)
private val EG_SENT = Regex("transfer${JsText.S}+sent|debit${JsText.S}+card|credit${JsText.S}+card|purchase", EG_I)
private val EG_RECEIVED = Regex("transfer${JsText.S}+received|deposit|salary|refund", EG_I)
private val EG_MERCHANT = Regex("@${JsText.S}*([^,\\n]+)")
private val EG_DAY_MONTH = Regex("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d|/)")
private val EG_OTHER_CURRENCY = Regex("${JsText.B}(?:USD|EUR|GBP|SAR|AED)${JsText.B}|دولار|يورو|ريال", EG_I)

/** سنة اليوم/الشهر من تاريخ وصول الرسالة: سنة الوصول أو اللي قبلها، والمقبول واحد بس. */
private fun egyptDate(body: String, receivedAt: String): IsoDate? {
    val received = JsText.parseIsoMillis(receivedAt)
    val match = EG_DAY_MONTH.find(body)
    if (match == null) {
        // رسالة البطاقة مفيهاش تاريخ: بتوصل ساعة العملية، فتاريخ الوصول هو تاريخها.
        // ⚠️ طبقة أندرويد لازم تبعت `receivedAt` **بالتوقيت المحلي** عشان اليوم يطلع صح.
        val day = receivedAt.take(10)
        return if (isValidIsoDate(day)) day else null
    }
    if (received == null) return null
    val day = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val receivedYear = receivedAt.take(4).toIntOrNull() ?: return null
    val candidates = LinkedHashSet<String>()
    for (year in listOf(receivedYear, receivedYear - 1)) {
        val value = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        if (!isValidIsoDate(value)) continue
        val time = toDayNumber(parseIsoDate(value)).toLong() * DAY_MS
        if (time <= received + DAY_MS && time >= received - 60 * DAY_MS) candidates.add(value)
    }
    return if (candidates.size == 1) candidates.first() else null
}

/** التاجر من `@اسم,` — واللي أرقام بس (حساب) مش تاجر. التحويل مفيهوش اسم الطرف أصلًا. */
private fun egyptMerchant(body: String): String {
    val raw = JsText.trim(EG_MERCHANT.find(body)?.groupValues?.get(1) ?: "")
    return if (raw.all { it in '0'..'9' || JsText.isWhitespace(it) || it in "*•.:-" }) "" else raw
}

/** QNB مصر. الشكل المجهول بيترفض بسبب واضح ويتضاف باليد — نفس قاعدة القارئ السعودي. */
fun parseEgyptBankSms(message: BankSmsMessage, lineNumber: Int): SmsParseResult {
    val body = latinizeDigits(message.body).filterNot { it == '\r' || it.code in BIDI_CODES }
    if (SMS_OFFER_PATTERN.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_OFFER))
    if (SMS_SENSITIVE_PATTERN.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_SENSITIVE))
    if (SMS_DECLINED_PATTERN.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_DECLINED))
    if (EG_OTHER_CURRENCY.containsMatchIn(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_NOT_EGP))

    val out = EG_SENT.containsMatchIn(body)
    val incoming = EG_RECEIVED.containsMatchIn(body)
    if (out == incoming) return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))

    val amounts = EG_AMOUNT.findAll(body).map { it.groupValues[1] }.toList()
    if (amounts.isEmpty()) return SmsParseResult.Rejected(uiText(TextKey.SMS_AMOUNT_UNCLEAR))
    if (amounts.size > 1) return SmsParseResult.Rejected(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    val amount = tryParseMoney(amounts[0], Currency.EGP)
    if (amount == null || amount <= 0) return SmsParseResult.Rejected(uiText(TextKey.SMS_AMOUNT_INVALID))

    val date = egyptDate(body, message.receivedAt) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DATE_UNCLEAR))
    val safeBody = redactSms(body)
    return SmsParseResult.Ok(
        SmsRow(
            lineNumber = lineNumber, date = date, amountMinor = amount,
            direction = if (incoming) Direction.IN else Direction.OUT,
            merchantName = redactSms(egyptMerchant(body)),
            reference = "SMS:" + hashContent(message.sender + "|" + message.receivedAt + "|" + body),
            sourceName = message.sender, description = safeBody, raw = safeBody,
        ),
    )
}

/** قارئ مصر لحزمة البلد. */
object EgyptBankSmsReader : BankSmsReader {
    override val id: String = "eg"

    override fun parse(message: BankSmsMessage, lineNumber: Int): SmsParseResult =
        parseEgyptBankSms(message, lineNumber)
}
