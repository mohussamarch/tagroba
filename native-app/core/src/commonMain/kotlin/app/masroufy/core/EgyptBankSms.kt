package app.masroufy.core

import app.masroufy.core.JsText.B

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

/** عملة مش جنيه. الريال والـSR = رسالة سعودية (قارئ السعودية هو اللي يقراها). */
private val EG_OTHER_CURRENCY = Regex("$B(?:USD|EUR|GBP|SAR|AED|SR|KWD|BHD|QAR|OMR|JOD)$B|دولار|يورو|ريال|ر\\.س", EG_I)

/** نوع صرف (شراء · سحب · شحن) واتجاهه داخل ⇒ الرسالة متناقضة (مراجعة جلسة 33: «تم خصم … وتم إضافة 50 نقطة» اتسجلت دخل). */
private val SPENDING_KINDS = setOf(SmsKind.PURCHASE, SmsKind.CASH_WITHDRAWAL, SmsKind.BILL)

private fun contradicts(direction: Direction, kind: SmsKind) = direction == Direction.IN && kind in SPENDING_KINDS

/** عملية بعملة أجنبية: سبب الرفض القديم + اللي اتقري لو كله واضح (مش ريال — دي رسالة سعودية مش أجنبية). */
private fun foreignOnly(body: String, receivedAt: String): SmsParseResult.Rejected {
    val reason = uiText(TextKey.SMS_NOT_EGP)
    val foreign = foreignAmountOf(body, skip = "EGP")?.takeIf { it.currency != "SAR" } ?: return SmsParseResult.Rejected(reason)
    val direction = egyptDirection(body) ?: return SmsParseResult.Rejected(reason)
    val date = egyptTransactionDate(body, receivedAt) ?: return SmsParseResult.Rejected(reason)
    val kind = egyptKind(body, direction)
    if (contradicts(direction, kind)) return SmsParseResult.Rejected(reason)
    return SmsParseResult.Rejected(reason, SmsForeignPending(date, foreign, direction, redactSms(egyptMerchant(body, kind)), kind, ownLast4Of(body, direction)))
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
    return smsRow(message, body, lineNumber, date, amount, direction, egyptMerchant(body, kind), kind, null)
}

/** قارئ مصر لحزمة البلد. */
object EgyptBankSmsReader : BankSmsReader {
    override val id: String = "eg"

    override fun parse(message: BankSmsMessage, lineNumber: Int): SmsParseResult =
        parseEgyptBankSms(message, lineNumber)
}
