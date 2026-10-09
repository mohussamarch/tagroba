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

/**
 * «تم» كلمة لوحدها (أو «وتم») — الجولة التالتة: «سوف يتم خصم» · «هيتم خصم» · «لن يتم خصم» كانت بتتقري «تم خصم» (خصم خلص) لأن
 * «يتم» جواها «تم». الحراس بترفض الجاي والمنفي قبل القارئ، ودي حماية تانية.
 */
private const val TM = "(?<![\\u0600-\\u06FF])و?تم"

/**
 * الجنيه بكل كتاباته: EGP · LE · ج.م · جنيه · جنية (فودافون) · جم (الأهلي) · ج (فودافون «تم سحب 500 ج»). الجولة الخامسة: «E£»/«£E»
 * (كانت «المبلغ مش واضح») · «جنيه إسترليني» **مش** جنيه مصري (كانت بتخلي الشراء بالإسترليني في السعودية يترفض من غير ما يتسأل).
 */
internal const val EG_CURRENCY =
    "(?:(?<![A-Za-z])(?:EGP|L\\.?E|E£)(?![A-Za-z])|£E(?![A-Za-z])|ج\\.م\\.?|(?:جنيه|جنية)(?![ \\t]*[إا]سترلين)" +
        "|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|(?<![\\u0600-\\u06FF])ج(?![\\u0600-\\u06FF.]))"

/**
 * «.50» (التجاري الدولي: المبلغ ممكن يبدأ بنقطة). فواصل الأرقام العربية «١٬٢٥٠» و«٢٥٠٫٥٠» (الأهلي المصري — `latinizeDigits` بيحوّل
 * الأرقام بس مش الفواصل): من غيرها «١٬٢٥٠» كانت بتتقري 250 في صمت (مراجعة جلسة 33).
 */
private const val NUM = "(?:\\d[\\d,٬]*(?:[.٫]\\d{1,2})?|[.٫]\\d{1,2})"
private val EG_CURRENCY_TOKEN = Regex(EG_CURRENCY, EI)

/** فيه جنيه في الرسالة (حتى لو في الرصيد أو حد البطاقة) — علامة إن الرسالة من بنك مصري. */
internal fun hasEgyptianCurrency(body: String): Boolean = EG_CURRENCY_TOKEN.containsMatchIn(body)

/** الجنيه لدليل العملة الأجنبية (`SmsForeignEvidence.kt`). */
internal val EGYPT_LOCAL = LocalCurrency("EGP", EG_CURRENCY)

/** الكلام بين المبلغ اللي قبله والمبلغ ده فيه كلمة من دول ⇒ ده رصيد أو رسوم أو حد أو قسط، مش مبلغ العملية. */
private val NOT_THE_AMOUNT = Regex(
    "المتاح|متاح|رصيد|الرصيد|balance|available|${B}bal$B|limit|الحد|مصاريف|رسوم|عمولة|${B}fees?$B|قسط|installment|الأدنى|minimum" +
        // الجولة الخامسة: «تكلفة الخدمة 1.50 جنيه» · «incl. VAT EGP 42.00» كانوا بيبقوا مبلغ العملية لما المبلغ نفسه من غير عملة أو اتساب
        "|تكلفة|ضريبة|${B}VAT$B|${B}tax$B",
    EI,
)

/** بعد المبلغ على طول «after last purchase» / «بعد آخر عملية» ⇒ ده الرصيد بعد العملية، مش مبلغها (الجولة التالتة). */
private val BALANCE_AFTER = Regex(
    "^$H*(?:after$H+(?:the$H+|your$H+)?(?:last$H+)?(?:purchase|transaction|payment)|بعد$H*(?:آخر|اخر)$H*(?:عملية|معاملة))|^$H*(?:is$H+)?(?:your$H+)?(?:available$H+)?balance",
    EI,
)

/** فيه مبلغ بالجنيه **هو** مبلغ العملية (مش رصيد ولا حد ولا رسوم) — كارت سعودي اتخصم بالجنيه بيبان كده («Amount: EGP 500.00 (SAR 37.50)»). */
internal fun hasEgyptianTransactionAmount(body: String): Boolean = egyptAmountValues(body).isNotEmpty()

/**
 * فودافون كاش شحن رصيد: «ب 50 بنجاح وخصم 57 من محفظتك شاملة الضريبة» ⇒ المخصوم = 57. الجولة الخامسة: **في رسالة الشحن بس**
 * ([RECHARGE]) — في رسالة تحويل أو استلام «وخصم 5.00 جنيه من محفظتك» = مصاريف، وكانت بتبقى مبلغ العملية (واستلام 2,000 اتسجل 10 دخل).
 * برّه الشحن الرقم ده مبلغ تاني عادي ⇒ «أكتر من مبلغ» (بتستنى).
 */
private val WALLET_DEBIT = Regex("وخصم$H*($NUM)$H*(?:$EG_CURRENCY$H*)?من$H*محفظتك", EI)
private val RECHARGE = Regex("(?<![\\u0600-\\u06FF])تم$H*شحن$H*رصيد$H*موبايلك", EI)

internal sealed interface EgyptAmount {
    data class Ok(val amountMinor: Halalas) : EgyptAmount
    data class Fail(val reason: String) : EgyptAmount
}

private fun egp(raw: String): Halalas? {
    val number = raw.replace('٬', ',').replace('٫', '.')
    return tryParseMoney(if (number.startsWith(".")) "0$number" else number, Currency.EGP)
}

private fun invalid() = EgyptAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))

/**
 * المبلغ = رقم جنب الجنيه والكلام قبله (من المبلغ اللي قبله) مش رصيد ولا مصاريف ولا قسط. الجولة التانية من المراجعة
 * (`SmsAmountTokens.kt`): رقم من الناحيتين («debit card 6604 EGP 500.00») ⇒ أكتر من مبلغ · فواصل غلط («64,25» · «1 234.50» ·
 * «1.234 جم») ⇒ مش صالح · أكبر من [SMS_AMOUNT_CAP_MINOR] ⇒ مش صالح.
 */
internal fun egyptAmount(body: String): EgyptAmount {
    WALLET_DEBIT.find(body)?.takeIf { RECHARGE.containsMatchIn(body) }?.let { m ->
        val amount = egp(m.groupValues[1])
        return if (amount == null || amount <= 0 || amount > SMS_AMOUNT_CAP_MINOR) invalid() else EgyptAmount.Ok(amount)
    }
    val values = egyptAmountValues(body)
    if (null in values) return problemOf(body)
    return when (values.size) {
        1 -> EgyptAmount.Ok(values.first()!!)
        0 -> EgyptAmount.Fail(uiText(TextKey.SMS_AMOUNT_UNCLEAR))
        else -> EgyptAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    }
}

/** المبالغ بالجنيه اللي ممكن تبقى مبلغ العملية (مش رصيد ولا حد ولا رسوم)؛ null = مبلغ فيه مشكلة ([problemOf] بيقول إيه). */
private fun egyptAmountValues(body: String): List<Long?> {
    val values = mutableListOf<Long?>()
    for (line in body.split('\n')) {
        var previousEnd = 0
        for (near in amountsNearCurrency(line, EG_CURRENCY_TOKEN, AmountStyle.EGYPT)) {
            val context = line.substring(minOf(previousEnd, near.start), near.start)
            previousEnd = near.end
            if (NOT_THE_AMOUNT.containsMatchIn(context) || BALANCE_AFTER.containsMatchIn(line.substring(near.end))) continue
            val amount = (near as? Near.Value)?.let { egp(it.number) }?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR }
            if (amount == null || amount !in values) values.add(amount)
        }
    }
    return values
}

/** أول مبلغ فيه مشكلة: رقم من الناحيتين ⇒ أكتر من مبلغ · غير كده ⇒ مش صالح. */
private fun problemOf(body: String): EgyptAmount.Fail {
    for (line in body.split('\n')) {
        var previousEnd = 0
        for (near in amountsNearCurrency(line, EG_CURRENCY_TOKEN, AmountStyle.EGYPT)) {
            val context = line.substring(minOf(previousEnd, near.start), near.start)
            previousEnd = near.end
            if (NOT_THE_AMOUNT.containsMatchIn(context) || BALANCE_AFTER.containsMatchIn(line.substring(near.end))) continue
            if (near is Near.Ambiguous) return EgyptAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
            if (near !is Near.Value || egp(near.number)?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR } == null) return invalid()
        }
    }
    return invalid()
}

// ── الاتجاه والنوع ───────────────────────────────────────────────────────

private val STRONG_IN = Regex("has$H+been$H+refunded|$TM$H*رد|${B}returned$B|$TM$H*قيد$H*مبلغ|من$H*جهة$H*العمل", EI)
private val OUT_FROM_ACCOUNT = Regex("$TM$H*تنفيذ$H*تحويل[^\\n]{0,60}?من$H*حسابك", EI)

/** «إلى حسابك» / «لحسابكم» — حسابك **إنت** (التجاري الدولي والأهلي). «إلى حساب <رقم>» من غير «ك» ممكن يبقى صادر فما بتتحسبش. */
private val IN_TO_ACCOUNT = Regex("(?:إلى|الى)$H*حسابك|لحسابك|على$H*حسابكم|لبطاقتك", EI)

/** «لرقم» = تحويل لرقم تاني — **إلا** «لرقم محفظتك» (رقمك إنت في رسالة استلام — الجولة الخامسة: كانت بتتقري صرف). */
private val OUT_TARGET = Regex("لرقم(?!$H*محفظت)|${B}deducted$B|${B}debited$B|from$H+your$H+AC$B", EI)

/**
 * الجولة الخامسة: العملية **اتعكست أو اتلغت** («وتم عكس العملية» · «has been reversed» · «Reversal:» · «عملية مرتجعة» · «اتلغت») ⇒
 * الاتجاه مش واضح دايمًا (مفيش قالب مصري في البحث فيه الكلام ده — كانت بتتسجل في اتجاه أول الجملة).
 */
private val REVERSED = Regex("(?<![\\u0600-\\u06FF])(?:[وف]?(?:ال)?(?:عكس|مرتجع)|اتلغ[تى])|${B}revers(?:ed|al|e)$B", EI)

/** فلوس **راجعة** (استرداد · استرجاع · رد) — دليل وارد؛ مع فعل صرف أو «لرقم» ⇒ الاتجاه مش واضح («تم تحويل … وتم استرجاع المبلغ»). */
private val MONEY_BACK = Regex("استرداد|استرجاع|${B}refunded$B", EI)
/** كلمات الوارد — بحدود كلمة («non-refundable» · «TEST HOTEL DEPOSIT» جوه كلمة تانية ما تتحسبش). */
private val IN_VERB = Regex(
    "$TM$H*استلام|$TM$H*(?:إضافة|اضافة)|${B}received$B|${B}credited$B|${B}deposit(?:ed)?$B|${B}salary$B|${B}refund(?:ed)?$B|إيداع",
    EI,
)

/** فعل خصم صريح — لو معاه فعل وارد في نفس الرسالة («تم خصم … وتم إضافة 50 نقطة») الاتجاه مش واضح. */
private val DEBIT_VERB = Regex(
    "$TM$H*خصم|$TM$H*سحب|$TM$H*شحن|$TM$H*سداد|${B}charged$B|${B}Trx$H+using|recharged|transfer$H+sent|(?<![\\u0600-\\u06FF])اتخصم",
    EI,
)

/** إشارة صرف أضعف (اسم الكارت أو كلمة شراء) — بتخسر قدام فعل وارد صريح. */
private val DEBIT_HINT = Regex("debit$H+card|credit$H+card|purchase", EI)

/** «من حسابك … إلى حسابك/لحسابك» = بين حساباتك — الرسالة الواحدة فيها الطرفين (الجولة التالتة: كانت بتتسجل داخل). */
private val FROM_YOUR_ACCOUNT = Regex("من$H*حسابك", EI)
private val TO_YOUR_ACCOUNT = Regex("(?:إلى|الى)$H*حسابك|لحسابك", EI)

/**
 * شحن المحفظة نفسها («تم شحن محفظتك/رصيد محفظتك») = فلوس **داخلة** المحفظة — مش فاتورة ولا صرف (الجولة التالتة: كانت بتتسجل
 * فاتورة طالعة). شحن رصيد الموبايل من المحفظة («تم شحن رصيد موبايلك … وخصم … من محفظتك») لسه فاتورة طالعة.
 */
private val WALLET_TOP_UP = Regex("$TM$H*شحن$H*(?:رصيد$H*)?محفظت(?:ك|كم)", EI)
private val FROM_YOUR_CARD_OR_ACCOUNT = Regex("من$H*(?:بطاقت|حساب)(?:ك|كم)", EI)

/**
 * القواعد بالترتيب وأول واحدة بتكسب: بين حساباتك ⇒ مش واضح · شحن المحفظة ⇒ داخل (ولو من كارتك/حسابك ⇒ مش واضح) · الاسترداد ⇒
 * «من حسابك»/«إلى حسابك» ⇒ «لرقم»/خصم ⇒ فعل وارد **وفعل خصم صريح مع بعض = مش واضح** ⇒ أفعال الوارد ⇒ أفعال الصادر.
 * (مراجعة جلسة 33: الوارد كان بيكسب الخصم الصريح فعملية شراء اتسجلت دخل.)
 */
internal fun egyptDirection(body: String): Direction? {
    if (FROM_YOUR_ACCOUNT.containsMatchIn(body) && TO_YOUR_ACCOUNT.containsMatchIn(body)) return null
    if (REVERSED.containsMatchIn(body)) return null
    if (WALLET_TOP_UP.containsMatchIn(body)) return if (FROM_YOUR_CARD_OR_ACCOUNT.containsMatchIn(body)) null else Direction.IN
    val incoming = IN_VERB.containsMatchIn(body) || MONEY_BACK.containsMatchIn(body)
    val debit = DEBIT_VERB.containsMatchIn(body)
    // الجولة الخامسة: الوارد والصرف مع بعض **قبل** الاختصارات («تم اضافة تحويل لحظي لحسابكم … وتم سحب» كانت بتتسجل داخل من «لحسابكم»)
    if (incoming && (debit || OUT_TARGET.containsMatchIn(body) || OUT_FROM_ACCOUNT.containsMatchIn(body))) return null
    when {
        STRONG_IN.containsMatchIn(body) -> return Direction.IN
        OUT_FROM_ACCOUNT.containsMatchIn(body) -> return Direction.OUT
        IN_TO_ACCOUNT.containsMatchIn(body) -> return Direction.IN
        OUT_TARGET.containsMatchIn(body) -> return Direction.OUT
    }
    return when {
        incoming -> Direction.IN
        debit || DEBIT_HINT.containsMatchIn(body) -> Direction.OUT
        else -> null
    }
}

private val REFUND_WORDS = Regex("has$H+been$H+refunded|$TM$H*رد|${B}returned$B", EI)
private val SALARY_WORDS = Regex("جهة$H*العمل|salary|راتب", EI)
// «ATMOSPHERE LOUNGE» محل مش صرّاف (الجولة التانية) — «NBE ATM0417» (رقم الماكينة لازق) لسه صرّاف
private val CASH_WORDS = Regex("$TM$H*سحب|(?<![A-Za-z])ATM(?![A-Za-z])", EI)
private val CARD_PAYMENT_WORDS = Regex("$TM$H*سداد[^\\n]{0,40}بطاقت", EI)
private val BILL_WORDS = Regex("$TM$H*شحن|recharged", EI)
private val TRANSFER_WORDS = Regex("تحويل|${B}IPN$B|transfer|لرقم|من$H*رقم|received$H+(?:EGP$H*)?[\\d,.]+$H*(?:EGP$H+)?from", EI)
private val PURCHASE_WORDS = Regex("$TM$H*خصم|${B}charged$B|${B}Trx$H+using|debit$H+card|credit$H+card|purchase", EI)

internal fun egyptKind(body: String, direction: Direction): SmsKind = when {
    WALLET_TOP_UP.containsMatchIn(body) -> SmsKind.OTHER // فلوس داخلة المحفظة — مصدرها مش مكتوب (§75-1: الداخل المجهول بيستنى)
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
    Regex("لدى$H*[:：]?$H*(.+?)$H*(?:(?:يوم|في|فى|بتاريخ)(?=$H|\\d)|،|$)", setOf(RegexOption.MULTILINE)), // الجولة الخامسة: «لدى <المحل>»
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
