package app.masroufy.core

/**
 * محلل رسايل البنوك السعودية — نقل `src/infrastructure/import/bankSmsParser.ts` (ملف المرجع `golden/sms.json` بيمسك سلوكه حرف بحرف)
 * + أشكال البحث (`research/banks/saudi-sms-formats.json`، جلسة 32). كل رسالة بتعدّي على:
 * 1. **التجاهل** (`SmsGuards.kt`): عرض · رمز تحقق (حتى لو فيه مبلغ ومحل) · مرفوضة/رصيد مش كفاية · حجز وتفويض وطلب ومعلومة.
 * 2. **الاتجاه**: عنوان الرسالة لو معروف (`SmsSaudiTitles.kt` — عناوين البنك المركزي الموحّدة + البنوك) ⇒ حوالة الراجحي القديمة
 *    من غير كلمة اتجاه (من مكان الاسم) ⇒ القاعدة القديمة: كلمات الصرف والدخل في الرسالة كلها، ولو الاتنين أو ولا واحد ⇒ ترفض.
 * 3. **المبلغ** (`SmsSaudiFields.kt`): «إجمالي المبلغ المستحق» لو موجود ⇒ رقم جنبه الريال في سطر مش رصيد ولا رسوم ولا ضريبة (قيمة واحدة).
 *    عملة أجنبية: مقابلها بالريال لو مكتوب، وإلا ترفض **ومعاها كل اللي اتقري** عشان المالك يكتب المبلغ بالريال (§75-12).
 * 4. **التاريخ** (`SmsDates.kt`): تاريخ واحد بس من 60 يوم قبل الوصول لحد يوم بعده. رسالة الأهلي السعودي اللي شكلها ما فيهوش تاريخ
 *    خالص ⇒ يوم الوصول بتوقيت السعودية (نفس قرار رسالة الكارت المصرية §40.3-١).
 * الشكل المجهول بيترفض بسبب واضح ويستنى المالك (§72).
 */

private val I = setOf(RegexOption.IGNORE_CASE)
private val OUT_WORDS = Regex("شراء|سحب|خصم|سداد|مدفوعات|دفع|(?:حوالة|تحويل)[^\\n]{0,20}صادر|purchase|withdrawal|outgoing transfer|مشتريات", I)
private val IN_WORDS = Regex("(?:حوالة|تحويل)[^\\n]{0,20}وارد|إيداع|ايداع|راتب|استرداد|مرتجع|incoming transfer|salary|deposit|refund", I)

/** القاعدة القديمة: «حوالة داخلية صادرة» و«حوالة محلية واردة» — كلمة الاتجاه ممكن تيجي بعد نوع الحوالة. */
private fun keywordDirection(body: String): Direction? {
    val out = OUT_WORDS.containsMatchIn(body)
    val incoming = IN_WORDS.containsMatchIn(body)
    return if (out == incoming) null else if (incoming) Direction.IN else Direction.OUT
}

/** الأهلي السعودي: آخر سطر «مدى *1234» / «مدى-ابل *1234» والرسالة مفيهاش تاريخ أصلًا (البحث: «no date in the message»). */
private val SNB_CARD_LINE = Regex("^[ \\t]*(?:مدى|بطاقة)(?:-[^\\s*]+)?[ \\t]*\\*\\d{4}[ \\t]*$", setOf(RegexOption.MULTILINE))
private const val SAUDI_UTC_OFFSET_HOURS = 3

private fun datelessDate(body: String, receivedAt: String): IsoDate? =
    if (!hasDateToken(body) && SNB_CARD_LINE.containsMatchIn(body)) localDayOf(receivedAt, SAUDI_UTC_OFFSET_HOURS) else null

private fun dateOf(body: String, receivedAt: String): IsoDate? = saudiTransactionDate(body, receivedAt) ?: datelessDate(body, receivedAt)

/** عملة أجنبية من غير مقابل بالريال: سبب الرفض القديم + اللي اتقري (لو المبلغ الأجنبي والتاريخ واضحين). الجنيه رسالة مصرية مش أجنبية. */
private fun foreignOnly(body: String, receivedAt: String, foreign: SmsForeignAmount?, direction: Direction, kind: SmsKind): SmsParseResult.Rejected {
    val reason = uiText(TextKey.SMS_FOREIGN_CURRENCY)
    val amount = foreign?.takeIf { it.currency != "EGP" } ?: return SmsParseResult.Rejected(reason)
    val date = dateOf(body, receivedAt) ?: return SmsParseResult.Rejected(reason)
    return SmsParseResult.Rejected(reason, SmsForeignPending(date, amount, direction, redactSms(saudiMerchantOf(body, kind)), kind, ownLast4Of(body, direction)))
}

/** قوالب سعودية؛ الشكل المجهول بيترفض بسبب واضح ويتضاف باليد. */
fun parseBankSms(message: BankSmsMessage, lineNumber: Int): SmsParseResult {
    val body = normalizeSmsBody(message.body)
    smsIgnoreReason(body)?.let { return SmsParseResult.Rejected(uiText(it)) }
    val title = saudiTitle(body)
    val direction = title?.direction ?: undirectedTransferDirection(body) ?: keywordDirection(body)
        ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    val kind = title?.kind ?: saudiKindFromWords(body, direction)
    val amount = when (val a = saudiAmount(body)) {
        is SaudiAmount.Fail -> return SmsParseResult.Rejected(a.reason)
        is SaudiAmount.ForeignOnly -> return foreignOnly(body, message.receivedAt, a.foreign, direction, kind)
        is SaudiAmount.Ok -> a
    }
    val date = dateOf(body, message.receivedAt) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DATE_UNCLEAR))
    return smsRow(message, body, lineNumber, date, amount.amountMinor, direction, saudiMerchantOf(body, kind), kind, amount.foreign)
}
