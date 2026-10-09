package app.masroufy.core

/**
 * محلل رسايل البنوك السعودية — نقل `src/infrastructure/import/bankSmsParser.ts` (ملف المرجع `golden/sms.json` بيمسك سلوكه حرف بحرف)
 * + أشكال البحث (`research/banks/saudi-sms-formats.json`، جلسة 32). كل رسالة بتعدّي على:
 * 1. **التجاهل** (`SmsGuards.kt`): عرض · رمز تحقق (حتى لو فيه مبلغ ومحل) · مرفوضة/رصيد مش كفاية · حجز وتفويض وطلب ومعلومة.
 * 2. **الاتجاه**: عنوان الرسالة لو معروف (`SmsSaudiTitles.kt` — عناوين البنك المركزي الموحّدة + البنوك) ⇒ حوالة الراجحي القديمة
 *    من غير كلمة اتجاه (من مكان الاسم) ⇒ القاعدة القديمة: كلمات الصرف والدخل في الرسالة كلها، ولو الاتنين أو ولا واحد ⇒ ترفض.
 *    عنوان داخل («تصحيح») والنص فيه «تم خصم» ⇒ ترفض · «شيك مرتجع» ⇒ ترفض (الاتجاه مش واضح — الجولة التانية من المراجعة).
 *    الجولة التالتة: عكس/استقطاع لحاجة ليها اتجاه («Refund Reversal» · «Salary Deduction») ⇒ ترفض · إلغاء ومعاه استرداد والاتجاه
 *    طالع ⇒ ترفض. والعملة الأجنبية بقت **دليل** (رمز · اسم · «X (SAR …)» — `SmsForeignEvidence.kt`) مش قايمة.
 * 3. **المبلغ** (`SmsSaudiFields.kt`): «إجمالي المبلغ المستحق» لو موجود ⇒ رقم جنبه الريال في سطر مش رصيد ولا رسوم ولا ضريبة (قيمة واحدة).
 *    **عملة أجنبية ⇒ ترفض دايمًا ومعاها كل اللي اتقري** (قرار المالك §75-12: تتسجل وتسأل عن المبلغ المحلي — مش تتسجل لوحدها حتى
 *    لو المقابل بالريال مكتوب؛ المكتوب بيمشي معاها اقتراح بس) لحد ما شاشة السؤال تتبني.
 * 4. **التاريخ** (`SmsDates.kt`): تاريخ واحد بس من 60 يوم قبل الوصول لحد يوم بعده (التاريخ بسنة كاملة: لحد يوم بعد الوصول).
 *    رسالة الأهلي السعودي اللي شكلها كله ما فيهوش تاريخ ⇒ يوم الوصول بتوقيت السعودية (نفس قرار رسالة الكارت المصرية §40.3-١).
 * 5. **الشكل** (الجولة الرابعة — `SmsKnownShapesSaudi.kt` و`SmsSamaTitles.kt`): أول سطر كله عنوان موحّد أو قالب بنك معروف واتجاهه نفس
 *    اتجاه القارئ ⇒ بتتسجل لوحدها؛ غير كده (الاتجاه من الكلمات العامة بس) ⇒ `SmsShape.KeywordFallback` ⇒ **بتستنى تأكيد المالك** (§72).
 *    الشكل ما بيغيّرش القراية نفسها (ملف المرجع `golden/sms.json` زي ما هو).
 * الشكل المجهول بيترفض بسبب واضح ويستنى المالك (§72).
 */

private val I = setOf(RegexOption.IGNORE_CASE)
private val OUT_WORDS = Regex("شراء|سحب|خصم|سداد|مدفوعات|دفع|(?:حوالة|تحويل)[^\\n]{0,20}صادر|purchase|withdrawal|outgoing transfer|مشتريات", I)
// «was reversed» (الجولة التانية): الشراء اتعكس = فلوس راجعة أو عملية اتلغت — مع «Purchase» الاتجاه مش واضح، مش صرف
private val IN_WORDS = Regex(
    "(?:حوالة|تحويل)[^\\n]{0,20}وارد|إيداع|ايداع|راتب|استرداد|مرتجع|incoming transfer|salary|deposit|refund|(?<![A-Za-z])revers(?:ed|al)(?![A-Za-z])",
    I,
)

/** القاعدة القديمة: «حوالة داخلية صادرة» و«حوالة محلية واردة» — كلمة الاتجاه ممكن تيجي بعد نوع الحوالة. */
private fun keywordDirection(body: String): Direction? {
    val out = OUT_WORDS.containsMatchIn(body)
    val incoming = IN_WORDS.containsMatchIn(body)
    return if (out == incoming) null else if (incoming) Direction.IN else Direction.OUT
}

/**
 * فعل خصم صريح في النص — مع عنوان داخل («تصحيح» · «استرداد») الرسالة متناقضة. الجولة الخامسة: «Debit from your account» ·
 * علامة سالب قبل المبلغ («مبلغ: -1,250.00 SAR» — كانت بتتسجل +1,250 داخل).
 */
private val EXPLICIT_DEBIT = Regex(
    "تم\\s*خصم|خصمت?\\s*من\\s*حساب|(?<![A-Za-z])debit(?:ed|\\s+from)(?![A-Za-z])|(?:^|[:：]|\\s)-\\s*\\d",
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)

/**
 * الجولة الخامسة: عنوان **صرف** وفي باقي الرسالة (مش العنوان) كلمة فلوس راجعة — استرداد · عكس · مرتجع · إرجاع · إيداع · reversed ·
 * credited · refund · returned · «SAR 87.40 CR» ⇒ الرسالة متناقضة («شراء\nتم استرداد مبلغ …» · «حوالة صادرة\nمرتجعة» · «سحب صراف
 * آلي\nتم إيداع مبلغ») ⇒ «الاتجاه مش واضح» بدل ما تتسجل صرف جديد (كانت بتتسجل، والسحب كان بيحط كاش وهمي في محفظة الكاش §75-4).
 */
private val BODY_MONEY_BACK = Regex(
    "استرداد|استرجاع|عكس|مرتجع|إرجاع|ارجاع|إيداع|ايداع|(?<![A-Za-z])(?:revers(?:ed|al|e)|credited|refund(?:ed)?|returned)(?![A-Za-z])|\\d\\s*CR(?![A-Za-z])",
    I,
)

private fun bodyAfterTitle(body: String): String = body.substringAfter('\n', "")

/** العنوان قال اتجاه وباقي الرسالة بيقول العكس (الجولة الخامسة — الفحص بقى في الناحيتين). */
private fun contradictsTitle(body: String, title: SmsTitle?): Boolean = when (title?.direction) {
    Direction.IN -> EXPLICIT_DEBIT.containsMatchIn(body)
    Direction.OUT -> BODY_MONEY_BACK.containsMatchIn(bodyAfterTitle(body))
    null -> false
}

/**
 * عكس أو استقطاع في العنوان **ومعاه** كلمة اتجاه تانية («Refund Reversal» · «ATM Withdrawal Reversal» · «عكس حوالة واردة» ·
 * «Salary Deduction» · «Debit Reversal» · «Cashback Reversal») ⇒ الاتجاه مش واضح (الجولة التالتة: كانت بتتسجل عكس اتجاهها —
 * وعكس سحب الصرّاف كان هيحط كاش وهمي في محفظة الكاش §75-4). العناوين المعروفة بمعنى «استرداد» ([KNOWN_REVERSAL]) زي ما هي.
 */
private val REVERSAL_WORD = Regex("(?<![A-Za-z])(?:revers(?:al|ed|e)?|deduct(?:ion|ed)?)(?![A-Za-z])|(?<![\\u0600-\\u06FF])(?:عكس|استقطاع)", I)
private val KNOWN_REVERSAL = Regex(
    "^(?:Purchase\\s*Reversal|Reverse\\s*Transaction|عكس\\s*(?:ال)?عملية|حوالة\\s*عكسية|كاش\\s*باك\\s*عكس)(?![A-Za-z])",
    I,
)
private val DIRECTION_WORD = Regex(
    OUT_WORDS.pattern + "|" + IN_WORDS.pattern.substringBefore("|(?<![A-Za-z])revers") +
        "|(?<![A-Za-z])(?:ATM|debit|credit|cash\\s*back|cashback|refund|salary|deposit|transfer)(?![A-Za-z])|كاش\\s*باك|حوالة|تحويل|استرجاع",
    I,
)

private fun reversalOfSomething(body: String): Boolean {
    val title = smsTitleLine(body)
    if (KNOWN_REVERSAL.containsMatchIn(title) || !REVERSAL_WORD.containsMatchIn(title)) return false
    return DIRECTION_WORD.containsMatchIn(REVERSAL_WORD.replace(title, " "))
}

/**
 * الأهلي السعودي (البحث: «no date in the message»): **الشكل كله** — عنوان معروف · سطر «بـ<مبلغ> SAR» أو «مبلغ <مبلغ> SAR» ·
 * آخر سطر «مدى *1234» / «مدى-ابل *1234». سطر الكارت لوحده مش كفاية (رمز أو عرض بنفس الشكل كان بياخد تاريخ النهارده).
 */
private val SNB_CARD_LINE = Regex("^[ \\t]*(?:مدى|بطاقة)(?:-[^\\s*]+)?[ \\t]*\\*\\d{4}[ \\t]*$")
private val SNB_AMOUNT_LINE = Regex("^[ \\t]*(?:بـ|مبلغ)[ \\t]*(?:SAR[ \\t]*)?\\d[\\d,.]*(?:[ \\t]*SAR)?[ \\t]*$", setOf(RegexOption.MULTILINE))
private const val SAUDI_UTC_OFFSET_HOURS = 3

private fun snbDatelessShape(body: String): Boolean {
    val lines = body.split('\n').filter { it.isNotBlank() }
    return lines.size in 3..5 && saudiTitle(body) != null && SNB_CARD_LINE.matches(lines.last()) && SNB_AMOUNT_LINE.containsMatchIn(body)
}

private fun datelessDate(body: String, receivedAt: String): IsoDate? =
    if (!hasDateToken(body) && snbDatelessShape(body)) localDayOf(receivedAt, SAUDI_UTC_OFFSET_HOURS) else null

private fun dateOf(body: String, receivedAt: String): IsoDate? = saudiTransactionDate(body, receivedAt) ?: datelessDate(body, receivedAt)

/** «شراء دولي» / «International …» في أول سطر — رسالة بنك سعودي عن عملية برّه (حتى لو مبلغها بالجنيه). */
private val INTERNATIONAL_TITLE = Regex("^[^\\n]*(?:دولي|دولية|International)", I)

/**
 * الرسالة الأجنبية دي **بتاعة البلد دي** (عشان ما تستناش في البلدين — الصندوق بيتقري بقارئ كل بلد): الجنيه = رسالة مصرية — إلا لو
 * فيها ريال سعودي (رصيد بالريال) أو عنوانها «دولي» (كارت سعودي اتخصم بالجنيه). أي عملة تانية: مفيهاش جنيه (الكارت المصري بيكتب
 * رصيده بالجنيه) والرسالة سطور أو فيها ريال (رسايل مصر سطر واحد).
 */
private fun saudiForeign(body: String, currency: String): Boolean = when (currency) {
    // الجولة التالتة: الجنيه **مقابل** بعد مبلغ تاني («charged SAR 75.00 (EGP 980.00)») = كارت مصري ⇒ مش هنا
    "EGP" -> (hasSaudiCurrency(body) || INTERNATIONAL_TITLE.containsMatchIn(smsTitleLine(body))) && !hasLocalConversion(body, EGYPT_LOCAL)
    else -> !hasEgyptianCurrency(body) && (hasSaudiCurrency(body) || '\n' in body)
}

/** عملة أجنبية (§75-12): سبب الرفض القديم + اللي اتقري (لو المبلغ الأجنبي والتاريخ واضحين) + المبلغ بالريال المكتوب كاقتراح. */
private fun foreignOnly(
    body: String, receivedAt: String, amount: SaudiAmount.ForeignOnly, direction: Direction, kind: SmsKind,
): SmsParseResult.Rejected {
    val reason = uiText(TextKey.SMS_FOREIGN_CURRENCY)
    val foreign = amount.foreign?.takeIf { saudiForeign(body, it.currency) } ?: return SmsParseResult.Rejected(reason)
    val date = dateOf(body, receivedAt) ?: return SmsParseResult.Rejected(reason)
    val pending = SmsForeignPending(
        date, foreign, direction, redactSms(saudiMerchantOf(body, kind)), kind, ownLast4Of(body, direction), amount.localSuggestion,
    )
    return SmsParseResult.Rejected(reason, pending)
}

/** قوالب سعودية؛ الشكل المجهول بيترفض بسبب واضح ويتضاف باليد. */
fun parseBankSms(message: BankSmsMessage, lineNumber: Int): SmsParseResult {
    val body = normalizeSmsBody(message.body)
    smsIgnoreReason(body)?.let { return SmsParseResult.Rejected(uiText(it)) }
    val unclear = SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    if (isReturnedCheque(body) || reversalOfSomething(body)) return unclear
    val title = saudiTitle(body)
    if (contradictsTitle(body, title)) return unclear
    val direction = title?.direction ?: undirectedTransferDirection(body) ?: keywordDirection(body) ?: return unclear
    // «Purchase Cancelled … Refund»: إلغاء ومعاه استرداد = فلوس راجعة، مش صرف جديد (§75-6) ⇒ ما نسجلهاش صرف
    if (direction == Direction.OUT && cancelledWithRefund(body)) return unclear
    val kind = title?.kind ?: saudiKindFromWords(body, direction)
    val amount = when (val a = saudiAmount(body)) {
        is SaudiAmount.Fail -> return SmsParseResult.Rejected(a.reason)
        is SaudiAmount.ForeignOnly -> return foreignOnly(body, message.receivedAt, a, direction, kind)
        is SaudiAmount.Ok -> a.amountMinor
    }
    val date = dateOf(body, message.receivedAt) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DATE_UNCLEAR))
    // الجولة الرابعة: القراية زي ما هي (ملف المرجع)، والشكل علامة جنبها — الكلمات العامة بس ⇒ ما بتتسجلش لوحدها (§72).
    // الجولة الخامسة: الشكل على الرسالة كلها + تاريخ واحد بس + مش بعد يوم الوصول (`SmsShapeGate.kt`)
    val shape = gateShape(saudiShape(body, direction), body, date, localDayOf(message.receivedAt, SAUDI_UTC_OFFSET_HOURS))
    return smsRow(message, body, lineNumber, date, amount, direction, saudiMerchantOf(body, kind), kind, shape)
}
