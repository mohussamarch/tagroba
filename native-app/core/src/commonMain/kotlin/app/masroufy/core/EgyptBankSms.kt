package app.masroufy.core

import app.masroufy.core.JsText.S

/**
 * قارئ رسايل بنوك ومحافظ مصر. بدأ على عينات حقيقية من **QNB مصر** بعت بيها المالك (OVERRIDES §40.3)، واتوسّع (جلسة 32) على
 * أشكال البحث (`research/banks/egypt-sms-formats.json`): الأهلي المصري (من غير مسافات أحيانًا «رقم4821»، والتاريخ من غير سنة)،
 * التجاري الدولي، بيت التمويل الكويتي، البنك العربي، HSBC، بريدفاست، فودافون كاش، اتصالات/أورانج/وي.
 * ⚠️ العينات نفسها **مش في المستودع** (بيانات حقيقية، والمستودع عام)، والاختبارات برسايل مخترعة بنفس الشكل.
 *
 * الخطوات: التجاهل (`SmsGuards.kt`) ⇒ عملة تانية (ريال = رسالة سعودية؛ دولار ويورو وأي كود عملة تاني = عملية أجنبية تستنى المبلغ بالجنيه §75-12) ⇒
 * الاتجاه والنوع والمبلغ والمحل (`EgyptSmsShapes.kt`) ⇒ التاريخ (`SmsDates.kt` — الرسالة اللي مفيهاش تاريخ خالص = يوم الوصول §40.3-١).
 * **مفيش ملف مرجع (golden) للقارئ ده**: التطبيق الحالي مش بيقرا رسايل مصرية أصلًا. الضمان اختبارات مكتوبة بالإيد.
 */

private val EG_I = setOf(RegexOption.IGNORE_CASE)

/**
 * عملة مش جنيه. الريال والـSR = رسالة سعودية (قارئ السعودية هو اللي يقراها) — إلا لو الرسالة فيها جنيه (كارت مصري اتخصم بالريال).
 * الحد حرف لاتيني مش حد كلمة («USD15.00» لازق في الرقم — الجولة التانية) + أسماء العملات بالعربي («ريال قطري» · دينار · درهم · ليرة).
 */
private val EG_OTHER_CURRENCY = Regex(
    "(?<![A-Za-z])(?:USD|EUR|GBP|SAR|AED|SR|KWD|BHD|QAR|OMR|JOD)(?![A-Za-z])|دولار|يورو|ريال|ر\\.س|$ARABIC_FOREIGN_WORD",
    EG_I,
)
private val EGP_TOKEN = Regex(EG_CURRENCY, EG_I)

/** المقابل بالجنيه بين قوسين جنب المبلغ الأجنبي («USD 14.90 (EGP 720.00)») — اقتراح بس للسؤال (§75-12). */
private val EGP_IN_PARENS = Regex("\\($S*(?:$EG_CURRENCY$S*(\\d[\\d,٬]*(?:[.٫]\\d{1,2})?)|(\\d[\\d,٬]*(?:[.٫]\\d{1,2})?)$S*$EG_CURRENCY)$S*\\)", EG_I)

/** نوع صرف (شراء · سحب · شحن) واتجاهه داخل ⇒ الرسالة متناقضة (مراجعة جلسة 33: «تم خصم … وتم إضافة 50 نقطة» اتسجلت دخل). */
private val SPENDING_KINDS = setOf(SmsKind.PURCHASE, SmsKind.CASH_WITHDRAWAL, SmsKind.BILL)

private fun contradicts(direction: Direction, kind: SmsKind) = direction == Direction.IN && kind in SPENDING_KINDS

private fun egpInParens(body: String): Halalas? {
    val values = EGP_IN_PARENS.findAll(body).mapNotNull { m ->
        tryParseMoney((m.groups[1]?.value ?: m.groups[2]!!.value).replace('٬', ',').replace('٫', '.'), Currency.EGP)
    }.toSet()
    return values.singleOrNull()?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR }
}

/**
 * الرسالة الأجنبية دي **بتاعة مصر** (عشان ما تستناش في البلدين — الصندوق بيتقري بقارئ كل بلد): الريال = رسالة سعودية — إلا لو فيها
 * جنيه (حد البطاقة أو الرصيد بالجنيه = كارت مصري اتخصم بالريال). أي عملة تانية: مفيهاش ريال سعودي (رسالة البنك السعودي بتكتب
 * المقابل أو الرسوم أو الرصيد بالريال) وفيها جنيه أو سطر واحد (رسايل مصر سطر واحد، والسعودية سطور).
 */
private fun egyptianForeign(body: String, currency: String): Boolean = when (currency) {
    "SAR" -> EGP_TOKEN.containsMatchIn(body)
    else -> !hasSaudiCurrency(body) && (EGP_TOKEN.containsMatchIn(body) || '\n' !in body)
}

/**
 * عملية بعملة أجنبية (قرار المالك §75-12: تتسجل وتسأل عن المبلغ المحلي): سبب الرفض القديم + اللي اتقري لو كله واضح + المقابل
 * بالجنيه لو مكتوب بين قوسين (اقتراح بس).
 */
private fun foreignOnly(body: String, receivedAt: String): SmsParseResult.Rejected {
    val reason = uiText(TextKey.SMS_NOT_EGP)
    val foreign = (foreignAmountOf(body, skip = "EGP") ?: riyalAmountAsForeign(body))?.takeIf { egyptianForeign(body, it.currency) }
        ?: return SmsParseResult.Rejected(reason)
    val direction = egyptDirection(body) ?: return SmsParseResult.Rejected(reason)
    val date = egyptTransactionDate(body, receivedAt) ?: return SmsParseResult.Rejected(reason)
    val kind = egyptKind(body, direction)
    if (contradicts(direction, kind)) return SmsParseResult.Rejected(reason)
    val merchant = redactSms(egyptMerchant(body, kind))
    return SmsParseResult.Rejected(reason, SmsForeignPending(date, foreign, direction, merchant, kind, ownLast4Of(body, direction), egpInParens(body)))
}

/** بنوك ومحافظ مصر. الشكل المجهول بيترفض بسبب واضح ويتضاف باليد — نفس قاعدة القارئ السعودي. */
fun parseEgyptBankSms(message: BankSmsMessage, lineNumber: Int): SmsParseResult {
    val body = normalizeSmsBody(message.body)
    smsIgnoreReason(body)?.let { return SmsParseResult.Rejected(uiText(it)) }
    // أي كود عملة أجنبية جنب مبلغ (TRY 300.00 …) زي الدولار بالظبط (§75-12)
    if (EG_OTHER_CURRENCY.containsMatchIn(body) || isoMoneyIn(body).isNotEmpty()) return foreignOnly(body, message.receivedAt)
    val direction = egyptDirection(body) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    val amount = when (val a = egyptAmount(body)) {
        is EgyptAmount.Fail -> return SmsParseResult.Rejected(a.reason)
        is EgyptAmount.Ok -> a.amountMinor
    }
    val date = egyptTransactionDate(body, message.receivedAt) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DATE_UNCLEAR))
    val kind = egyptKind(body, direction)
    if (contradicts(direction, kind)) return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    return smsRow(message, body, lineNumber, date, amount, direction, egyptMerchant(body, kind), kind)
}

/** قارئ مصر لحزمة البلد. */
object EgyptBankSmsReader : BankSmsReader {
    override val id: String = "eg"

    override fun parse(message: BankSmsMessage, lineNumber: Int): SmsParseResult =
        parseEgyptBankSms(message, lineNumber)
}
