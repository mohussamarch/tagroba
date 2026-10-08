package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * أشكال رسايل البنوك والمحافظ المصرية (`research/banks/egypt-sms-formats.json`): الأهلي المصري · التجاري الدولي · بيت التمويل
 * الكويتي · البنك العربي · HSBC · بريدفاست · فودافون كاش ومحافظ تانية · QNB (الشكل القديم). الرسايل سطر واحد غالبًا،
 * فالاتجاه من **العبارة** مش من عنوان: «من حسابك» صادر و«إلى حسابك» وارد (التجاري الدولي: نفس الفعل للاتجاهين).
 */

private val EI = setOf(RegexOption.IGNORE_CASE)
private const val H = "[ \\t]"

/** الجنيه بكل كتاباته: EGP · LE · ج.م · جنيه · جنية (فودافون) · جم (الأهلي) · ج (فودافون «تم سحب 500 ج»). */
internal const val EG_CURRENCY =
    "(?:(?<![A-Za-z])(?:EGP|L\\.?E)(?![A-Za-z])|ج\\.م\\.?|جنيه|جنية|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.]))"

/**
 * «.50» (التجاري الدولي: المبلغ ممكن يبدأ بنقطة). فواصل الأرقام العربية «١٬٢٥٠» و«٢٥٠٫٥٠» (الأهلي المصري — `latinizeDigits` بيحوّل
 * الأرقام بس مش الفواصل): من غيرها «١٬٢٥٠» كانت بتتقري 250 في صمت (مراجعة جلسة 33).
 */
private const val NUM = "(?:\\d[\\d,٬]*(?:[.٫]\\d{1,2})?|[.٫]\\d{1,2})"
private val EG_CURRENCY_AMOUNT = Regex("$EG_CURRENCY$S*($NUM)|($NUM)$S*$EG_CURRENCY", EI)

/** الكلام بين المبلغ اللي قبله والمبلغ ده فيه كلمة من دول ⇒ ده رصيد أو رسوم أو حد أو قسط، مش مبلغ العملية. */
private val NOT_THE_AMOUNT = Regex(
    "المتاح|متاح|رصيد|الرصيد|balance|available|${B}bal$B|limit|الحد|مصاريف|رسوم|عمولة|${B}fees?$B|قسط|installment|الأدنى|minimum",
    EI,
)

/** فودافون كاش شحن رصيد: «ب 50 بنجاح وخصم 57 من محفظتك شاملة الضريبة» ⇒ المخصوم = 57. */
private val WALLET_DEBIT = Regex("وخصم$H*($NUM)$H*(?:$EG_CURRENCY$H*)?من$H*محفظتك", EI)

internal sealed interface EgyptAmount {
    data class Ok(val amountMinor: Halalas) : EgyptAmount
    data class Fail(val reason: String) : EgyptAmount
}

private fun egp(raw: String): Halalas? {
    val number = raw.replace('٬', ',').replace('٫', '.')
    return tryParseMoney(if (number.startsWith(".")) "0$number" else number, Currency.EGP)
}

internal fun egyptAmount(body: String): EgyptAmount {
    WALLET_DEBIT.find(body)?.let { m ->
        val amount = egp(m.groupValues[1])
        return if (amount == null || amount <= 0) EgyptAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID)) else EgyptAmount.Ok(amount)
    }
    val values = LinkedHashSet<Long>()
    for (line in body.split('\n')) {
        var previousEnd = 0
        for (match in EG_CURRENCY_AMOUNT.findAll(line)) {
            val context = line.substring(previousEnd, match.range.first)
            previousEnd = match.range.last + 1
            if (NOT_THE_AMOUNT.containsMatchIn(context)) continue
            val amount = egp(match.groups[1]?.value ?: match.groups[2]!!.value)
            if (amount == null || amount <= 0) return EgyptAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
            values.add(amount)
        }
    }
    return when (values.size) {
        1 -> EgyptAmount.Ok(values.first())
        0 -> EgyptAmount.Fail(uiText(TextKey.SMS_AMOUNT_UNCLEAR))
        else -> EgyptAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    }
}

// ── الاتجاه والنوع ───────────────────────────────────────────────────────

private val STRONG_IN = Regex("has$H+been$H+refunded|تم$H*رد|${B}returned$B|تم$H*قيد$H*مبلغ|من$H*جهة$H*العمل", EI)
private val OUT_FROM_ACCOUNT = Regex("تم$H*تنفيذ$H*تحويل[^\\n]{0,60}?من$H*حسابك", EI)

/** «إلى حسابك» / «لحسابكم» — حسابك **إنت** (التجاري الدولي والأهلي). «إلى حساب <رقم>» من غير «ك» ممكن يبقى صادر فما بتتحسبش. */
private val IN_TO_ACCOUNT = Regex("(?:إلى|الى)$H*حسابك|لحسابك|على$H*حسابكم|لبطاقتك", EI)
private val OUT_TARGET = Regex("لرقم|${B}deducted$B|${B}debited$B|from$H+your$H+AC$B", EI)
/** كلمات الوارد — بحدود كلمة («non-refundable» · «TEST HOTEL DEPOSIT» جوه كلمة تانية ما تتحسبش). */
private val IN_VERB = Regex(
    "تم$H*استلام|تم$H*(?:إضافة|اضافة)|${B}received$B|${B}credited$B|${B}deposit(?:ed)?$B|${B}salary$B|${B}refund(?:ed)?$B|إيداع",
    EI,
)

/** فعل خصم صريح — لو معاه فعل وارد في نفس الرسالة («تم خصم … وتم إضافة 50 نقطة») الاتجاه مش واضح. */
private val DEBIT_VERB = Regex(
    "تم$H*خصم|تم$H*سحب|تم$H*شحن|تم$H*سداد|${B}charged$B|${B}Trx$H+using|recharged|transfer$H+sent",
    EI,
)

/** إشارة صرف أضعف (اسم الكارت أو كلمة شراء) — بتخسر قدام فعل وارد صريح. */
private val DEBIT_HINT = Regex("debit$H+card|credit$H+card|purchase", EI)

/**
 * القواعد بالترتيب وأول واحدة بتكسب: الاسترداد ⇒ «من حسابك»/«إلى حسابك» ⇒ «لرقم»/خصم ⇒ فعل وارد **وفعل خصم صريح مع بعض = مش واضح**
 * ⇒ أفعال الوارد ⇒ أفعال الصادر. (مراجعة جلسة 33: الوارد كان بيكسب الخصم الصريح فعملية شراء اتسجلت دخل.)
 */
internal fun egyptDirection(body: String): Direction? {
    when {
        STRONG_IN.containsMatchIn(body) -> return Direction.IN
        OUT_FROM_ACCOUNT.containsMatchIn(body) -> return Direction.OUT
        IN_TO_ACCOUNT.containsMatchIn(body) -> return Direction.IN
        OUT_TARGET.containsMatchIn(body) -> return Direction.OUT
    }
    val incoming = IN_VERB.containsMatchIn(body)
    val debit = DEBIT_VERB.containsMatchIn(body)
    return when {
        incoming && debit -> null
        incoming -> Direction.IN
        debit || DEBIT_HINT.containsMatchIn(body) -> Direction.OUT
        else -> null
    }
}

private val REFUND_WORDS = Regex("has$H+been$H+refunded|تم$H*رد|${B}returned$B", EI)
private val SALARY_WORDS = Regex("جهة$H*العمل|salary|راتب", EI)
private val CASH_WORDS = Regex("تم$H*سحب|${B}ATM", EI)
private val CARD_PAYMENT_WORDS = Regex("تم$H*سداد[^\\n]{0,40}بطاقت", EI)
private val BILL_WORDS = Regex("تم$H*شحن|recharged", EI)
private val TRANSFER_WORDS = Regex("تحويل|${B}IPN$B|transfer|لرقم|من$H*رقم|received$H+(?:EGP$H*)?[\\d,.]+$H*(?:EGP$H+)?from", EI)
private val PURCHASE_WORDS = Regex("تم$H*خصم|${B}charged$B|${B}Trx$H+using|debit$H+card|credit$H+card|purchase", EI)

internal fun egyptKind(body: String, direction: Direction): SmsKind = when {
    REFUND_WORDS.containsMatchIn(body) -> SmsKind.REFUND
    SALARY_WORDS.containsMatchIn(body) -> SmsKind.SALARY
    direction == Direction.OUT && CASH_WORDS.containsMatchIn(body) -> SmsKind.CASH_WITHDRAWAL
    CARD_PAYMENT_WORDS.containsMatchIn(body) -> SmsKind.CARD_PAYMENT
    BILL_WORDS.containsMatchIn(body) -> SmsKind.BILL
    TRANSFER_WORDS.containsMatchIn(body) -> if (direction == Direction.IN) SmsKind.TRANSFER_IN else SmsKind.TRANSFER_OUT
    PURCHASE_WORDS.containsMatchIn(body) -> SmsKind.PURCHASE
    else -> SmsKind.OTHER
}

// ── المحل (الشراء والاسترداد والسحب بس — التحويل طرفه في «زون التحويلات») ──

private val MERCHANTS = listOf(
    Regex("@$S*([^,\\n]+)"), // QNB «@store.com,»
    // الأهلي «عندNBE ATM… يوم14/09» · التجاري الدولي «عند … في 14/09»
    Regex("عند$H*(.+?)$H*(?:(?:يوم|في|فى)(?=$H|\\d)|،|$)", setOf(RegexOption.MULTILINE)),
    Regex("${B}at$H+(.+?)$H+on$H", EI), // التجاري الدولي «at … on»
    Regex("${B}from$H+(.+?)$H+(?:for|with)$H", EI), // البنك العربي «from … for» · التجاري الدولي استرداد «from … with»
    Regex("تم$H*رد[^\\n]*?${H}من$H+(.+?)$H*$", setOf(RegexOption.MULTILINE)), // التجاري الدولي «لقد تم رد … من <المحل>»
)

internal fun egyptMerchant(body: String, kind: SmsKind): String {
    if (kind != SmsKind.PURCHASE && kind != SmsKind.REFUND && kind != SmsKind.CASH_WITHDRAWAL) return ""
    for (regex in MERCHANTS) {
        val raw = JsText.trim(regex.find(body)?.groupValues?.get(1) ?: continue)
        if (raw.isNotEmpty() && !raw.all { it in '0'..'9' || JsText.isWhitespace(it) || it in "*•.:-/" }) return raw
    }
    return ""
}
