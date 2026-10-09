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
private val OTHER_NAME =
    "(?<![A-Za-z])(?:USD|EUR|GBP|SAR|AED|SR|KWD|BHD|QAR|OMR|JOD)(?![A-Za-z])|(?<![\\u0600-\\u06FF])(?:دولار|يورو|ريال|ر\\.س|$OTHER_RIYAL" +
        "|دينار|درهم|ليرة|روب[يى][ةه]|(?:جنيه$S*)?[إا]سترلين[يى]|جنيه$S*(?:جنوب$S*)?سودان[يى]|يوان|فرنك|روبل|رينغيت|رينجت|ين" +
        // الجولة السادسة
        "|بات|وون|بيزو|كرون[ةه]|كرونا|شيكل|دونغ|دونج|فورنت|فورينت|ل\\.ل|ل\\.س)(?![\\u0600-\\u06FF])" +
        "|(?<![A-Za-z])(?:Ft|Kč|L\\.L)(?![A-Za-z])"

/**
 * الجولة الخامسة: العملة لازم **جنبها رقم** («300.00 SAR» · «30 دولار» · «USD15.00») — اسم المحل «ريال للعطور» · «صيدلية يوروفارم» ·
 * «SR TOYS» · «@SR TECH» كان بيخلي شراء بالجنيه يستنى بسبب «مش جنيه» (ومن غير ما يتسأل عن حاجة).
 */
private val EG_OTHER_CURRENCY = Regex("\\d$SP*(?:$OTHER_NAME)|(?:$OTHER_NAME)$SP*[:：]?$SP*\\d", EG_I)
private val EGP_TOKEN = Regex(EG_CURRENCY, EG_I)

/** نوع صرف (شراء · سحب · شحن) واتجاهه داخل ⇒ الرسالة متناقضة (مراجعة جلسة 33: «تم خصم … وتم إضافة 50 نقطة» اتسجلت دخل). */
private val SPENDING_KINDS = setOf(SmsKind.PURCHASE, SmsKind.CASH_WITHDRAWAL, SmsKind.BILL)

private fun contradicts(direction: Direction, kind: SmsKind) = direction == Direction.IN && kind in SPENDING_KINDS

/** عنوان «International/دولي» = رسالة بنك سعودي (البنوك المصرية في البحث ما بتكتبهوش). */
private val INTERNATIONAL_TITLE = Regex("(?<![\\u0600-\\u06FF])(?:دولي|دولية)(?![\\u0600-\\u06FF])|(?<![A-Za-z])International(?![A-Za-z])", EG_I)

/**
 * الرسالة الأجنبية دي **بتاعة مصر** (عشان ما تستناش في البلدين — الصندوق بيتقري بقارئ كل بلد): الريال = رسالة سعودية — إلا لو فيها
 * جنيه **في مكان الرصيد أو الحد بس** (كارت مصري اتخصم بالريال). الجولة التالتة: لو الجنيه هو **مبلغ العملية** والريال مقابله
 * («Amount: EGP 500.00 (SAR 37.50)» — دي 360) يبقى كارت سعودي اتخصم بالجنيه ⇒ بتاعة السعودية بس (كانت بتستنى في البلدين)،
 * إلا لو الجنيه نفسه **مقابل** بعد مبلغ الريال («charged SAR 75.00 (EGP 980.00)» = كارت مصري اتخصم بالريال).
 * أي عملة تانية: مفيهاش ريال سعودي (رسالة البنك السعودي بتكتب المقابل أو الرسوم أو الرصيد بالريال) وفيها جنيه أو سطر واحد.
 */
private fun egyptianForeign(body: String, currency: String): Boolean = when {
    // الجولة التامنة: «دولي/International» **عنوان** (أول سطر في رسالة سطور) بس — «You sent USD 40.00 … via InstaPay International on …» جملة
    // مصرية واحدة كانت بتستنى من غير تفاصيل المبلغ الأجنبي
    '\n' in body.trim() && INTERNATIONAL_TITLE.containsMatchIn(smsTitleLine(body)) -> false
    // الجولة الخامسة: جملة على قالب مصري معروف والريال هو العملة الوحيدة («تم خصم 300.00 SAR من بطاقة الخصم المباشر رقم …») = كارت مصري
    currency == "SAR" -> (EGP_TOKEN.containsMatchIn(body) && (!hasEgyptianTransactionAmount(body) || hasLocalConversion(body, EGYPT_LOCAL))) ||
        (hasEgyptianKnownHead(body) && !EGP_TOKEN.containsMatchIn(body))
    else -> !hasSaudiCurrency(body) && (EGP_TOKEN.containsMatchIn(body) || '\n' !in body)
}

/**
 * عملية بعملة أجنبية (قرار المالك §75-12: تتسجل وتسأل عن المبلغ المحلي): سبب الرفض القديم + اللي اتقري لو كله واضح + المقابل
 * بالجنيه لو مكتوب **بعد المبلغ الأجنبي** (بين قوسين · «بما يعادل» · Equivalent — `SmsForeignEvidence.kt`) اقتراح بس؛ قوسين بعد
 * «رسوم» أو «الحد المتاح» مش مقابل (الجولة التالتة).
 */
private fun foreignOnly(body: String, receivedAt: String): SmsParseResult.Rejected {
    val reason = uiText(TextKey.SMS_NOT_EGP)
    val read = foreignAmountOf(body, EGYPT_LOCAL) ?: riyalAmountAsForeign(body)
    // الجولة التامنة: أجنبية أكيد بس المبلغ الأجنبي مش مقروء بالظبط («200 دولار» لوحده — سؤال (س)) ⇒ بتستنى ومعاها اللي اتقري (جملة مصرية بس)
    if (read == null) return foreignUnread(body, receivedAt, reason)
    val foreign = read.takeIf { egyptianForeign(body, it.currency) } ?: return SmsParseResult.Rejected(reason)
    val direction = egyptDirection(body) ?: return SmsParseResult.Rejected(reason)
    val date = egyptTransactionDate(body, receivedAt) ?: return SmsParseResult.Rejected(reason)
    val kind = egyptKind(body, direction)
    if (contradicts(direction, kind)) return SmsParseResult.Rejected(reason)
    val merchant = redactSms(egyptMerchant(body, kind))
    val suggestion = localConversion(body, EGYPT_LOCAL)
    return SmsParseResult.Rejected(reason, SmsForeignPending(date, foreign, direction, merchant, kind, ownLast4Of(body, direction), suggestion))
}

private fun foreignUnread(body: String, receivedAt: String, reason: String): SmsParseResult.Rejected {
    val egyptian = !hasSaudiCurrency(body) && (EGP_TOKEN.containsMatchIn(body) || '\n' !in body.trim())
    val direction = egyptDirection(body)
    val date = egyptTransactionDate(body, receivedAt)
    if (!egyptian || direction == null || date == null) return SmsParseResult.Rejected(reason)
    val kind = egyptKind(body, direction)
    if (contradicts(direction, kind)) return SmsParseResult.Rejected(reason)
    val (code, written) = foreignUnreadDetails(body, EGYPT_LOCAL)
    val unread = SmsForeignUnread(
        date, code, written, direction, redactSms(egyptMerchant(body, kind)), kind, ownLast4Of(body, direction), localConversion(body, EGYPT_LOCAL),
    )
    return SmsParseResult.Rejected(reason, foreignUnread = unread)
}

/** الجولة السادسة: العملة بعد «Ref/No./#/مرجع» على طول رقم مرجع («Ref: SR4471») مش عملة — زي فلتر الجهاز. */
private val REFERENCE_BEFORE = Regex("(?:(?<![A-Za-z])ref(?:erence)?|(?<![A-Za-z])no\\.?|#|مرجع|المرجع)[ \\t]*[:：.#]?[ \\t]*$", EG_I)

private fun hasOtherCurrency(body: String): Boolean = EG_OTHER_CURRENCY.findAll(body).any { m ->
    !REFERENCE_BEFORE.containsMatchIn(body.substring(body.lastIndexOf('\n', m.range.first - 1) + 1, m.range.first))
}

/** بنوك ومحافظ مصر. الشكل المجهول بيترفض بسبب واضح ويتضاف باليد — نفس قاعدة القارئ السعودي. */
fun parseEgyptBankSms(message: BankSmsMessage, lineNumber: Int): SmsParseResult {
    val body = normalizeSmsBody(message.body)
    smsIgnoreReason(body)?.let { return SmsParseResult.Rejected(uiText(it)) }
    // الجولة السابعة: علامة قلب اتجاه (LRO/RLO) ⇒ الكلام المعروض غير المقروء («تم خصم ‮06.84‬ جم» بيتعرض 48.60) ⇒ ما بنقراش.
    // الجولة التامنة: أي علامة اتجاه **جوه رقم** (`SmsHiddenText.kt`) ⇒ نفس الحكم
    if (hasBidiOverride(message.body) || hasBidiInsideNumber(message.body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_HIDDEN_TEXT))
    // الجولة التامنة: رسالة سطور أولها عنوان بنك سعودي معروف («PoS Purchase\nAmount: EGP 1,250.00 …» = كارت سعودي اتخصم بالجنيه) ⇒ بتتقري
    // في السعودية (بتستنى هناك ومعاها المبلغ بالجنيه §75-12) — كانت بتبان هنا «جاهزة» شراء مصري
    if ('\n' in body.trim() && hasSaudiKnownTitle(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_OTHER_COUNTRY))
    // الجولة السادسة: العملة بتتعرف من غير التشكيل («ريال عُماني») — النص نفسه (البصمة والوصف) زي ما هو
    val plain = withoutTashkeel(body)
    // أي دليل عملة أجنبية (كود · رمز «$» · اسم «US Dollars»/«ين» · مقابل بالجنيه بعد مبلغ تاني) زي الدولار بالظبط (§75-12)
    if (hasOtherCurrency(plain) || hasForeignEvidence(plain, EGYPT_LOCAL)) return foreignOnly(plain, message.receivedAt)
    if (isReturnedCheque(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    val direction = egyptDirection(body) ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    if (direction == Direction.OUT && cancelledWithRefund(body)) return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    val amount = when (val a = egyptAmount(plain)) {
        is EgyptAmount.Fail -> return SmsParseResult.Rejected(a.reason)
        is EgyptAmount.Ok -> a.amountMinor
    }
    // الجولة السادسة: «IPN transfer dated <التاريخ الأصلي> … returned» (بيت التمويل) — الفلوس رجعت **يوم وصول الرسالة**، مش يوم
    // التحويل الأصلي (كانت بتتسجل في يوم قديم وممكن شهر مالي قفل). الرسالة مفيهاش تاريخ الرجوع ⇒ يوم الوصول بتوقيت القاهرة
    // §77-C: الجملة اللي على قالب معروف ومفيهاش تاريخ بتاخد يوم الوصول (حتى من غير عبارة «تم …») — القالب بيتشاف **قبل** التاريخ
    val layout = egyptShape(body, direction, amount)
    val date = (if (isReturnedTransferNotice(body)) cairoDayOf(message.receivedAt) else egyptTransactionDate(body, message.receivedAt, layout.clear))
        ?: return SmsParseResult.Rejected(uiText(TextKey.SMS_DATE_UNCLEAR))
    // الجولة السابعة: تاريخ العملية **بعد** يوم الوصول (بتوقيت القاهرة) = عملية لسه ما حصلتش ⇒ مرفوضة (مفيش ملف مرجع لمصر يقفل يوم بعد)
    val arrival = cairoDayOf(message.receivedAt)
    if (arrival != null && date > arrival) return SmsParseResult.Rejected(uiText(TextKey.SMS_NOT_TRANSACTION))
    val kind = refineSmsKind(body, egyptKind(body, direction), direction) // عقد C0 (§77-D — `SmsReturned.kt`)
    if (contradicts(direction, kind)) return SmsParseResult.Rejected(uiText(TextKey.SMS_DIRECTION_UNCLEAR))
    // الجولة السابعة: كلمة حالة أو طلب أو جاي في **أول جملة** («هيتأكد بكره الصبح» · «لحين القبول» · «واتحجزت لحد التوثيق» ·
    // «from … AWAITS APPROVAL») ⇒ **مرفوضة** (مش عملية خلصت) بدل «جاهزة» بمبلغ واتجاه و«سجّل الكل» يسجلها. سطر تحذير في جملة بعدها
    // («تذكير: لا تشارك …» · «If you have not authorized …») ما بيرفضهاش — بتستنى زي الأول
    if (hasShapeDoubt(headOf(body))) return SmsParseResult.Rejected(uiText(TextKey.SMS_NOT_TRANSACTION))
    // الجولة الرابعة: الشكل علامة جنب القراية — جملة على قالب معروف بس هي اللي بتتسجل لوحدها (§72). الجولة الخامسة: الجملة **كلها**
    // على القالب + المبلغ من خانة المبلغ + تاريخ واحد بس + مش بعد يوم الوصول (`SmsShapeGate.kt`). الجولة السادسة: حروف مخفية ⇒ تستنى.
    // الجولة السابعة: محل برّه مصر (كود البلد في آخر اسمه «SAMPLE CLOUD USA» — §75-12) ⇒ تستنى
    val merchant = egyptMerchant(body, kind)
    val known = layout.let { if (it.clear && foreignCountryTail(merchant, EGYPT_TAIL)) SmsShape.KeywordFallback else it }
    val shape = gateShape(egyptDateGate(known, body), body, date, arrival, message.body)
    // عقد C0: الرسوم (§77-B) · المرجع (§77-D) · بصمة الشكل (§77-A) — كل واحدة في ملفها
    return smsRow(
        message, body, lineNumber, date, amount, direction, merchant, kind, shape,
        fee = egyptFeeOf(body, kind, amount), bankReference = smsReferenceOf(body), learnKey = egyptLearnKey(body, shape),
    )
}

/**
 * الجولة التامنة: الشكل المعروف بيستنى لو **أكتر من تاريخ** بعد عدّ «يوم/شهر» من غير سنة في أي مكان. §77-C: **ساعة من غير تاريخ**
 * (فودافون كاش «23:58: Received …») ما بقتش بتستنى — اليوم بيتحسب من الساعة المكتوبة (`SmsArrivalDay.kt`)، إلا لو فيها أكتر من ساعة.
 */
private fun egyptDateGate(shape: SmsShape, body: String): SmsShape = when {
    !shape.clear -> shape
    !egyptHasDateToken(body) && ambiguousClock(body) -> SmsShape.KeywordFallback
    egyptDistinctDateCount(body) > 1 -> SmsShape.KeywordFallback
    else -> shape
}

/** قارئ مصر لحزمة البلد. */
object EgyptBankSmsReader : BankSmsReader {
    override val id: String = "eg"

    override fun parse(message: BankSmsMessage, lineNumber: Int): SmsParseResult =
        parseEgyptBankSms(message, lineNumber)
}
