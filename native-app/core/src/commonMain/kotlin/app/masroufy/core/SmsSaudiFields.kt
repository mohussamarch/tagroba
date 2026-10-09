package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * خانات رسالة البنك السعودي: المبلغ (والعملة الأجنبية) والمحل. القواعد الأولانية في كل دالة = التطبيق الحالي بالظبط
 * (ملف المرجع `golden/sms.json`)، والإضافات من أشكال البحث ومكتوب جنب كل واحدة بنكها.
 */

private val I = setOf(RegexOption.IGNORE_CASE)
private val IM = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

// «ريال سعودى 87.50» (الإنماء — العملة بالكلام قبل الرقم). «ريال قطري» مش ريال سعودي (الجولة التانية من المراجعة)
private val CURRENCY = "(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\\.$S?س\\.?|ريال(?!$S*(?:$OTHER_RIYAL_TAIL))(?:$S+سعود[يى])?)"
private val CURRENCY_TOKEN = Regex(CURRENCY, I)

/** الريال لدليل العملة الأجنبية (`SmsForeignEvidence.kt`). */
internal val SAUDI_LOCAL = LocalCurrency("SAR", CURRENCY)

/**
 * سطر فيه كلمة من دول قبل المبلغ ⇒ مش مبلغ العملية. الإضافات: الضريبة (إس تي سي) · «اعادة مبلغ» = الباقي من الحجز (الراجحي) ·
 * «Bal» · charges · commission (الجولة التانية من المراجعة) · tax · discount · points (الجولة التالتة — «Tax: SAR 8.38» كانت
 * بتبقى المبلغ لما المبلغ نفسه من غير عملة). «كاش باك» **مش** هنا: رسالة الكاش باك نفسها مبلغها جنبه.
 */
private val NOT_TRANSACTION_AMOUNT = Regex(
    "الرصيد|رصيد|balance|المتاح|متاح|available|الحد|limit|رسوم|${B}fees?$B|عمولة|المتبقي" + "|${B}VAT$B|ضريبة|اعادة|إعادة" +
        "|${B}bal$B|${B}charges?$B|commission|${B}tax$B|${B}discount$B|${B}points$B" +
        // الجولة الخامسة: حد/سقف البطاقة · «المستحق» (مش «المبلغ المستحق» — ده الإجمالي) · outstanding · النقاط (مش «نقاط البيع»)
        "|(?<![\\u0600-\\u06FF])حد(?![\\u0600-\\u06FF])|سقف|(?<!المبلغ$S)المستحق|${B}outstanding$B|${B}overdue$B" +
        "|(?:ال)?(?:نقاط|نقطة)(?!$S*(?:ال)?بيع)",
    I,
)

/**
 * كلمة رصيد أو رسوم **بعد** المبلغ ومفيش رقم تاني بعدها في السطر («SAR 4,100.00 is your available balance» · «SAR 4,100.00 Available»
 * · الجولة التالتة: «SAR 5.75 fee» · «SAR 8.38 VAT» · «4,100.00 after last purchase»).
 */
private val TRAILING_LABEL = Regex(
    "^$S*(?:is$S+)?(?:your$S+)?(?:(?:available|current|remaining|new)$S+)?(?:balance|limit|available|${B}bal$B)|^$S*(?:الرصيد|رصيد|المتاح|متاح)" +
        "|^$S*(?:(?:transfer|transaction|service|bank)$S+)?(?:${B}fees?$B|${B}charges?$B|commission|${B}VAT$B|${B}tax$B)|^$S*(?:ال)?(?:رسوم|عمولة|ضريبة)" +
        "|^$S*after$S+(?:the$S+|your$S+)?(?:last$S+)?(?:purchase|transaction|payment)",
    I,
)

/** سطر فوق المبلغ فيه كلمة رصيد (ومفيهوش رقم) ⇒ المبلغ اللي تحته لوحده رصيد («الرصيد المتاح بعد عملية الشراء\n4,100.00 ر.س»). */
private val BALANCE_WORD_LINE = Regex("(?:ال)?رصيد|(?:ال)?متاح|balance|available", I)

/** رقم مرجع قبل العملة على طول («Ref SR4821» · «رقم المرجع: SR 48213») ⇒ مش مبلغ. */
private val REFERENCE_BEFORE = Regex("(?:${B}ref(?:erence)?|${B}no\\.?|#|مرجع|المرجع|رقم$S*(?:ال)?(?:مرجع|عملية|العملية)?)$S*[:：.]?$S*$", I)

/** سطر كله اسم خانة رصيد أو رسوم من غير رقم («الرصيد» · «رسوم:») ⇒ المبلغ اللي في السطر اللي بعده لوحده هو قيمتها. */
private val LABEL_ONLY_LINE = Regex(
    "^$S*(?:(?:ال)?رصيد(?:ك)?(?:$S+(?:ال)?(?:متاح|حالي|متبقي|متبقى))?|(?:ال)?متاح|(?:ال)?رسوم|(?:ال)?عمولة|(?:ال)?ضريبة|" +
        "(?:available$S+|current$S+|remaining$S+)?balance|fees?|charges?|commission|VAT)$S*[:：]?$S*$",
    I,
)
private val BARE_AMOUNT = Regex("(?:بمبلغ|المبلغ|مبلغ|amount|بـ|قيمة)$S*[:：]?$S*\\d", I)

/** «إجمالي المبلغ المستحق» / «Total due amount» (إس تي سي · بنك غير معروف): المبلغ + الرسوم + الضريبة = المخصوم فعلًا. */
private val TOTAL_DUE_LINE = Regex("^[^\\n]*(?:Total$S*due$S*amount|إجمال[يى]$S*المبلغ$S*المستحق)[^\\n]*$", IM)

// القايمة القديمة (أي حالة حروف، والكود لوحده كفاية). باقي أكواد ISO في `SmsForeignCodes.kt` (حروف كبيرة وجنبها مبلغ)
private const val FOREIGN_CODES = "USD|EUR|EGP|AED|GBP|KWD|BHD|QAR|OMR|JOD"
// «EGP900.00» (فودافون) لازق في الرقم ⇒ الحد بعد الكود حرف لاتيني بس، مش رقم
private val FOREIGN = Regex(
    "(?<![A-Za-z])(?:$FOREIGN_CODES)(?![A-Za-z])|دولار|يورو|جنيه|ج\\.م|(?<![\\u0600-\\u06FF])جم(?![\\u0600-\\u06FF])|$ARABIC_FOREIGN_WORD",
    I,
)

/** المبلغ بكود من القايمة القديمة (أي حالة حروف). */
private val FOREIGN_AMOUNT = Regex("(?<![A-Za-z])($FOREIGN_CODES)$SP*[:：]?$SP*($FOREIGN_NUMBER)|(?<![\\d.,٬٫])($FOREIGN_NUMBER)$SP*($FOREIGN_CODES)(?![A-Za-z])", I)

internal sealed interface SaudiAmount {
    data class Ok(val amountMinor: Halalas) : SaudiAmount

    /**
     * عملية بعملة أجنبية (§75-12 — قرار المالك: تتسجل وتسأل عن المبلغ المحلي، **مش** تتسجل لوحدها لو المحلي مكتوب). [foreign] =
     * المبلغ الأجنبي لو واحد وواضح، و[localSuggestion] = المبلغ بالريال المكتوب في الرسالة (الإجمالي المستحق أو بين قوسين) — اقتراح بس.
     */
    data class ForeignOnly(val foreign: SmsForeignAmount?, val localSuggestion: Halalas?) : SaudiAmount
    data class Fail(val reason: String) : SaudiAmount
}

private fun parse(number: String): Halalas? = tryParseMoney(number.replace('٬', ',').replace('٫', '.'))

/** المبلغ ده قيمة رصيد أو رسوم مكتوب اسمها **بعده** أو في **السطر اللي قبله** (مش قبله في نفس السطر). */
private fun labelledElsewhere(lines: List<String>, index: Int, near: Near): Boolean {
    val line = lines[index]
    val rest = line.substring(near.end)
    if (TRAILING_LABEL.containsMatchIn(rest) && rest.none { it in '0'..'9' }) return true
    if (line.substring(0, near.start).isNotBlank() || rest.isNotBlank()) return false
    val previous = lines.subList(0, index).lastOrNull { it.isNotBlank() } ?: return false
    return LABEL_ONLY_LINE.matches(previous) || (BALANCE_WORD_LINE.containsMatchIn(previous) && previous.none { it in '0'..'9' })
}

/**
 * القاعدة القديمة: رقم جنبه عملة الريال في سطر مش رصيد ولا حد ولا رسوم — قيمة واحدة بس. الجولة التانية: رقم من الناحيتين
 * («Card 6604 SAR 64.25») ⇒ أكتر من مبلغ · فواصل غلط ⇒ مش صالح · أكبر من [SMS_AMOUNT_CAP_MINOR] ⇒ مش صالح (`SmsAmountTokens.kt`).
 * الجولة التالتة: العملة بعد «Ref/رقم/No.» على طول = رقم مرجع مش مبلغ («Ref SR4821») · ولو المبلغ الوحيد كود لازق في رقم صحيح
 * («SR1234») والرسالة فيها «Amount 64.25» من غير عملة ⇒ العملة مش واضحة (مش هنختار).
 */
private fun oneLocalAmount(text: String, body: String): SaudiAmount {
    val values = LinkedHashSet<Long>()
    var onlyGlued = true
    val lines = text.split('\n')
    for ((index, line) in lines.withIndex()) {
        for (near in amountsNearCurrency(line, CURRENCY_TOKEN, AmountStyle.SAUDI)) {
            if (NOT_TRANSACTION_AMOUNT.containsMatchIn(line.substring(0, near.start))) continue
            if (REFERENCE_BEFORE.containsMatchIn(line.substring(0, near.start))) continue
            if (labelledElsewhere(lines, index, near)) continue
            val number = when (near) {
                is Near.Ambiguous -> return SaudiAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
                is Near.Malformed -> return SaudiAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
                is Near.Value -> near.number.also { if (!near.glued) onlyGlued = false }
            }
            val amount = parse(number)
            if (amount == null || amount <= 0 || amount > SMS_AMOUNT_CAP_MINOR) return SaudiAmount.Fail(uiText(TextKey.SMS_AMOUNT_INVALID))
            values.add(amount)
        }
    }
    val bare = BARE_AMOUNT.containsMatchIn(body)
    if (values.size == 1 && onlyGlued && bare && BARE_AMOUNT_NO_CURRENCY.containsMatchIn(body)) return SaudiAmount.Fail(uiText(TextKey.SMS_CURRENCY_UNCLEAR))
    // الجولة الخامسة: «مبلغ: 87.40» من غير عملة **ورقم تاني** جنبه عملة («حد الائتمان: 6,000.00 SAR» · «Trace SR 5317» · «لدى: SR 9 MART»)
    // ⇒ المبلغ الحقيقي ممكن يكون اللي من غير عملة — ما بنختارش (كان بيتسجل 6,000 ريال) — لازق أو مش لازق
    if (values.size == 1 && BARE_AMOUNT_NO_CURRENCY.findAll(text).any { parse(it.groupValues[1]) != values.first() }) {
        return SaudiAmount.Fail(uiText(TextKey.SMS_CURRENCY_UNCLEAR))
    }
    if (values.size == 1) return SaudiAmount.Ok(values.first())
    if (values.size > 1) return SaudiAmount.Fail(uiText(TextKey.SMS_MULTIPLE_AMOUNTS))
    return SaudiAmount.Fail(if (bare) uiText(TextKey.SMS_CURRENCY_UNCLEAR) else uiText(TextKey.SMS_AMOUNT_UNCLEAR))
}

/** «Amount 64.25» / «مبلغ: 64.25» من غير عملة جنبه (من الناحيتين). */
private val BARE_AMOUNT_NO_CURRENCY = Regex(
    "(?:بمبلغ|المبلغ|مبلغ|amount|قيمة)$S*[:：]?$S*(\\d[\\d,٬]*(?:[.٫]\\d{1,2})?)(?![\\d.,٬٫])(?!$S*(?:$CURRENCY))",
    I,
)

/**
 * المبلغ الأجنبي الوحيد في الرسالة (مش في سطر رصيد أو رسوم)، أو null. [local] = عملة البلد (بتتشال).
 * القايمة القديمة + أي كود ISO جنب مبلغ (`SmsForeignCodes.kt` — «TRY 450.00» كانت بتضيع) + اسم العملة بالعربي («ريال قطري») +
 * الجولة التالتة: رمز العملة («$23.40») · الاسم بالإنجليزي («12.50 Swiss Francs») · الكود جنب رقم صحيح قبل المقابل المحلي على طول
 * («JPY 4500 (SAR 112.50)») · «500.00 جم» في قارئ السعودية (`SmsForeignEvidence.kt`).
 * المبلغ بكسور العملة نفسها («KWD 12.345» = 12345 فلس) — رقم مش مظبوط أو عملة مش معروفة بالظبط («300 دولار») ⇒ null (ما بنخمّنش).
 */
internal fun foreignAmountOf(body: String, local: LocalCurrency): SmsForeignAmount? {
    val listed = FOREIGN_AMOUNT.findAll(body).map { m ->
        IsoMoney(m.range, m.groups[1]?.value ?: m.groups[4]!!.value, m.groups[2]?.value ?: m.groups[3]!!.value)
    }.filter { it.code?.uppercase() != local.code && it.inOneLine(body) }
    val found = LinkedHashSet<SmsForeignAmount>()
    for (m in listed + foreignMoneyIn(body, local)) {
        val lineStart = body.lastIndexOf('\n', m.range.first - 1) + 1
        if (NOT_TRANSACTION_AMOUNT.containsMatchIn(body.substring(lineStart, m.range.first))) continue
        val code = m.code?.uppercase() ?: return null
        val amount = parseForeignMinor(m.number, code) ?: return null
        if (amount > 0) found += SmsForeignAmount(code, amount)
    }
    return found.singleOrNull()
}

/** فيه ريال سعودي في الرسالة (حتى لو في سطر الرصيد) — علامة إن الرسالة من بنك سعودي (لما مبلغها بالجنيه). */
internal fun hasSaudiCurrency(body: String): Boolean = CURRENCY_TOKEN.containsMatchIn(body)

/** الريال السعودي مكتوب صريح (مش «ريال» لوحدها — ممكن تبقى ريال بلد تاني في رسالة مصرية). */
private val EXPLICIT_SAR = Regex("(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\\.$S?س|ريال$S+سعود[يى]", I)

/** مبلغ العملية بالريال لو واحد وواضح — لقارئ مصر لما كارت مصري يتخصم بالريال (عملة أجنبية هناك — §75-12). */
internal fun riyalAmountAsForeign(body: String): SmsForeignAmount? =
    if (!EXPLICIT_SAR.containsMatchIn(body)) null else (oneLocalAmount(body, body) as? SaudiAmount.Ok)?.let { SmsForeignAmount("SAR", it.amountMinor) }

/**
 * مبلغ العملية: «إجمالي المبلغ المستحق» لو موجود (المبلغ + الرسوم + الضريبة) ⇒ وإلا القاعدة القديمة.
 * **الرسالة بعملة أجنبية ⇒ [SaudiAmount.ForeignOnly] دايمًا** (§75-12، قرار المالك ✗ على «يتسجل لو المحلي مكتوب»): المقابل
 * بالريال لو مكتوب (الإجمالي · بين قوسين بعد المبلغ الأجنبي · «ما يعادل» · «المبلغ بالريال») بيمشي معاها **اقتراح** للسؤال، وما
 * بيتسجلش لوحده. الجولة التالتة: الأجنبي = **أي دليل** (`SmsForeignEvidence.kt` — رمز · اسم · الشكل «X (SAR …)»)، مش القايمة بس.
 */
internal fun saudiAmount(body: String): SaudiAmount {
    val foreign = FOREIGN.containsMatchIn(body) || hasForeignEvidence(body, SAUDI_LOCAL)
    val total = TOTAL_DUE_LINE.find(body)?.let { oneLocalAmount(it.value, body) as? SaudiAmount.Ok }
    if (foreign) return SaudiAmount.ForeignOnly(foreignAmountOf(body, SAUDI_LOCAL), total?.amountMinor ?: localConversion(body, SAUDI_LOCAL))
    return total ?: oneLocalAmount(body, body)
}

// ── المحل ────────────────────────────────────────────────────────────────

// الجولة التالتة: المحل بيقف قبل «(SAR 93.75)» (مقابل المبلغ مش جزء من اسم المحل). الجولة الخامسة: بين الكلمة والاسم مسافة في
// **نفس السطر** بس — «Refund initiated by merchant\nAmount: SAR 245.60» كان بيطلّع المحل «Amount: SAR 245.60»
private val MERCHANT_AT = Regex(
    "(?:لدى|عند|تاجر|${B}merchant$B|${B}at$B)[ \\t]*[:：]?[ \\t]*([^\\n]+?)(?=$S+(?:في|بتاريخ|${B}on$B|الرصيد|${B}balance$B)(?:$S|[:：])|$S*\\($S*(?:$CURRENCY|\\d)|$)",
    IM,
)
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
