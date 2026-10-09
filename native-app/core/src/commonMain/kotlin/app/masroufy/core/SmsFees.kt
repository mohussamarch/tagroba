package app.masroufy.core

import app.masroufy.core.JsText.B
import app.masroufy.core.JsText.S

/**
 * رسوم الحوالة والمحفظة المكتوبة في رسالة البنك (§77-B — قرار المالك 2026-10-09: «رسوم التحويل ورسوم المحفظة = عملية لوحدها «رسوم بنكية»
 * جنب العملية الأصلية» عشان الرصيد يفضل مظبوط). القارئين بينادوا [saudiFeeOf] و[egyptFeeOf] عند `smsRow` والنتيجة في [SmsRow.fee] —
 * **القراية نفسها ما اتغيرتش** (المبلغ والاتجاه والتاريخ زي ما هم، وملفات المرجع ما اتلمستش).
 *
 * - الرسوم = الرسوم **+ الضريبة المكتوبة عليها** بعملة البلد (الاتنين اتخصموا — تفسير Claude، وفصل الضريبة سؤال مفتوح للمالك).
 * - **ما بنخترعش رقم** (قاعدة 10) ⇒ null لو: الرسوم بعملة تانية · سطر رسوم رقمه مش مقروء بالعملة · رقم ممكن يبقى رسوم أو رصيد · «شاملة
 *   الرسوم/الضريبة» (ممكن تكون جوه المبلغ) · ضريبة من غير رسوم (ممكن تبقى ضريبة الشراء نفسه) · فلوس داخلة (السعودية: مش واضح الرسوم
 *   اتخصمت من المبلغ ولا لوحدها) · «إجمالي المبلغ المستحق» مش = المبلغ + الرسوم.
 * - [SmsFee.includedInAmount] = مبلغ الصف هو الإجمالي (القارئ السعودي بياخد «إجمالي المبلغ المستحق» لما = المبلغ + الرسوم + الضريبة).
 * - رسالة الرسوم نفسها ([SmsKind.FEE] — «خصم رسوم» · «Debit fees») مالهاش رسوم زيادة: المبلغ كله رسوم (`SmsFeeEffect` بيصنّفها).
 */

private val FI = setOf(RegexOption.IGNORE_CASE)
private const val H = "[ \\t]"
private const val NUM = "(?:\\d[\\d,٬]*(?:[.٫]\\d{1,2})?)"

/** كلمة رسوم: رسوم · عمولة · مصاريف · تكلفة الخدمة · fee(s) · commission · charge(s). */
private val FEE_WORD = Regex("رسوم|عمولة|مصاريف|تكلفة$S*(?:ال)?خدمة|$B(?:fees?|commission|charges?)$B", FI)

/** ضريبة (على الرسوم لو جنبها رسوم). */
private val VAT_WORD = Regex("ضريبة|$B(?:VAT|tax)$B", FI)

/** كلمة بتقول إن الرقم **مش** رسوم: رصيد · متاح · حد · مستحق · نقاط · كاش باك · سعر صرف … */
private val OTHER_WORD = Regex(
    "الرصيد|رصيد|المتاح|متاح|سقف|المستحق|نقاط|نقطة|قسط|الأدنى|معلق|كاش$S*باك|سعر$S*(?:ال)?صرف|(?<![\\u0600-\\u06FF])حد(?![\\u0600-\\u06FF])" +
        "|$B(?:balance|available|bal|limit|outstanding|overdue|discount|points?|cashback|installment|minimum|pending|rate)$B",
    FI,
)

/** «شامل/شاملة الرسوم» · including · incl. — الرسوم ممكن تكون جوه المبلغ ومش عارفين ⇒ ما بنقسمش. */
private val INCLUDED_WORDING = Regex("شامل|${B}incl(?:uding|uded|udes)?$B|${B}incl\\.", FI)

/** «إجمالي المبلغ المستحق» / «Total due amount». */
private val TOTAL_DUE = Regex("Total$S*due$S*amount|إجمال[يى]$S*المبلغ$S*المستحق", FI)

/** سطر أوله اسم رسوم أو ضريبة (السعودية) — لو فيه رقم ومفيش مبلغ بالريال ⇒ الرسوم مش مقروءة (عملة تانية أو من غير عملة). */
private val FEE_LABEL_LINE = Regex("^$S*(?:ال)?(?:رسوم|عمولة|ضريبة)|^$S*(?:fees?|VAT|tax|commission|charges?)$B", FI)

/** سطر سعر الصرف («رسوم تحويل العملات: 3.7612» — البحث بيقول ممكن يبقى سعر، ما بيتجمعش أبدًا). */
private val FX_LINE = Regex("تحويل$S*(?:ال)?عملات|سعر$S*(?:ال)?صرف|${B}exchange$B|${B}rate$B", FI)

/** سطر كله اسم رسوم أو ضريبة من غير رقم («رسوم:») ⇒ المبلغ اللي في السطر اللي بعده لوحده قيمته. */
private val LABEL_ONLY_LINE = Regex("^$S*(?:(?:ال)?(?:رسوم|عمولة|ضريبة)(?:$S+[^\\d\\s:：]+)?|fees?|charges?|commission|VAT)$S*[:：]?$S*$", FI)

/** بعد المبلغ على طول («SAR 5.75 fee» · «SAR 0.86 VAT» · «4,100.00 available balance»). */
private val TRAILING_FEE = Regex("^$S*(?:(?:transfer|transaction|service|bank)$S+)?(?:fees?|charges?|commission)$B|^$S*(?:ال)?(?:رسوم|عمولة|مصاريف)", FI)
private val TRAILING_VAT = Regex("^$S*(?:VAT|tax)$B|^$S*(?:ال)?ضريبة", FI)
private val TRAILING_OTHER = Regex(
    "^$S*(?:is$S+)?(?:your$S+)?(?:(?:available|current|remaining|new)$S+)?(?:balance|limit|available|bal)$B|^$S*(?:الرصيد|رصيد|المتاح|متاح)|^$S*after$B",
    FI,
)

/** مصر: «وخصم 5 جنيه من محفظتك» في تحويل أو استلام = مصاريف مخصومة صريح (مش رسالة شحن الرصيد — هناك ده المبلغ نفسه). */
private val WALLET_DEBIT = Regex("وخصم$H*($NUM)$H*(?:$EG_CURRENCY$H*)?من$H*محفظتك", FI)
private val RECHARGE = Regex("شحن$S*رصيد$S*موبايلك", FI)

/** مصر: اسم رسوم وبعده رقم على طول («مصاريف الخدمة 1») — لو الرقم ده ما اتقراش بالجنيه ⇒ الرسوم مش واضحة. */
private val EG_FEE_LABEL_NUMBER = Regex(
    "(?:مصاريف|رسوم|عمولة|تكلفة)(?:$H+(?:ال)?(?:خدمة|تحويل|استلام|سحب|عملية))?$H*[:：]?$H*($NUM)|$B(?:fees?|charges?|commission)$H*[:：]?$H*($NUM)",
    FI,
)

private enum class Role { FEE, VAT, OTHER, NONE, AMBIGUOUS }

/** دور الرقم من الكلام اللي قبله (من آخر رقم قبله في نفس السطر)، أو من الكلمة اللي بعده على طول. رسوم وكلمة رصيد مع بعض ⇒ مش واضح. */
private fun roleOf(context: String, rest: String, labelAbove: Role?): Role {
    val fee = FEE_WORD.containsMatchIn(context)
    val vat = VAT_WORD.containsMatchIn(context)
    val other = OTHER_WORD.containsMatchIn(context)
    return when {
        other && (fee || vat) -> Role.AMBIGUOUS
        fee -> Role.FEE
        vat -> Role.VAT
        other -> Role.OTHER
        // الكلمة بعد المبلغ بتحكم بس لو مفيش رقم تاني بعدها («شراء 25 SAR fees 2 SAR» — الـ25 مش رسوم)
        rest.any { it in '0'..'9' } -> Role.NONE
        TRAILING_FEE.containsMatchIn(rest) -> Role.FEE
        TRAILING_VAT.containsMatchIn(rest) -> Role.VAT
        TRAILING_OTHER.containsMatchIn(rest) -> Role.OTHER
        context.isBlank() && rest.isBlank() && labelAbove != null -> labelAbove
        else -> Role.NONE
    }
}

private fun labelOfLine(line: String): Role? = if (!LABEL_ONLY_LINE.matches(line)) null else if (FEE_WORD.containsMatchIn(line)) Role.FEE else Role.VAT

private fun sar(number: String): Halalas? = tryParseMoney(number.replace('٬', ',').replace('٫', '.'))?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR }

private fun egp(number: String): Halalas? = tryParseMoney(number.replace('٬', ',').replace('٫', '.'), Currency.EGP)?.takeIf { it in 1..SMS_AMOUNT_CAP_MINOR }

/** اللي اتقري من سطور الرسالة: الرسوم والضريبة · المبالغ اللي مالهاش اسم (المبلغ نفسه) · وأماكن أرقام الرسوم في النص. */
private class FeeRead {
    var fee = 0L
    var vat = 0L
    val bases = mutableSetOf<Long>()
    val counted = mutableListOf<IntRange>()
}

/**
 * يقرا كل مبلغ بالعملة المحلية في [lines] ودوره. null = حاجة مش واضحة (رقم رسوم مش مقروء · رسوم ورصيد مع بعض) ⇒ ما فيش رسوم.
 * سطور «إجمالي المبلغ المستحق» بتتساب هنا (السعودية بتقراها لوحدها).
 */
private fun readFees(
    body: String, currency: Regex, style: AmountStyle, money: (String) -> Halalas?, skipLines: Set<Int> = emptySet(), skipAt: List<IntRange> = emptyList(),
): FeeRead? {
    val read = FeeRead()
    var offset = 0
    var labelAbove: Role? = null
    for ((index, line) in body.split('\n').withIndex()) {
        val lineStart = offset
        offset += line.length + 1
        if (index in skipLines || line.isBlank()) {
            if (line.isNotBlank()) labelAbove = null
            continue
        }
        var previousEnd = 0
        for (near in amountsNearCurrency(line, currency, style)) {
            val context = line.substring(minOf(previousEnd, near.start), near.start)
            previousEnd = near.end
            val at = (lineStart + near.start) until (lineStart + near.end)
            if (skipAt.any { it.first in at }) continue
            val role = roleOf(context, line.substring(near.end), labelAbove)
            if (role == Role.AMBIGUOUS) return null
            if (role == Role.OTHER) continue
            val value = (near as? Near.Value)?.let { money(it.number) }
            if (role == Role.NONE) {
                value?.let(read.bases::add)
                continue
            }
            value ?: return null
            if (role == Role.FEE) read.fee = addMoney(read.fee, value) else read.vat = addMoney(read.vat, value)
            read.counted += at
        }
        labelAbove = labelOfLine(line)
    }
    return read
}

/**
 * الرسالة السعودية صادرة؟ زي القارئ: العنوان ⇒ سطر الاسم في الحوالة القديمة ⇒ (من الكلام) النوع اللي القارئ بيديه للصادر بس
 * (شراء · سحب كاش · حوالة صادرة). نوع ممكن يبقى في الاتجاهين (راتب · غيره) من غير عنوان ⇒ لأ (ما بنقراش رسوم — قاعدة 10).
 */
private fun saudiOutgoing(body: String, kind: SmsKind): Boolean {
    (saudiTitle(body)?.direction ?: undirectedTransferDirection(body))?.let { return it == Direction.OUT }
    return kind == SmsKind.PURCHASE || kind == SmsKind.CASH_WITHDRAWAL || kind == SmsKind.TRANSFER_OUT
}

/**
 * السعودية. [amountMinor] = مبلغ الصف زي ما القارئ قراه (الإجمالي لو فيه «إجمالي المبلغ المستحق»). الصادر بس.
 */
internal fun saudiFeeOf(text: String, kind: SmsKind, amountMinor: Halalas): SmsFee? {
    if (kind == SmsKind.FEE || !saudiOutgoing(text, kind)) return null
    // العملة من غير التشكيل (زي القارئ)
    val body = withoutTashkeel(text)
    if (INCLUDED_WORDING.containsMatchIn(body)) return null
    val lines = body.split('\n')
    val totalLines = lines.indices.filter { TOTAL_DUE.containsMatchIn(lines[it]) }.toSet()
    for ((index, line) in lines.withIndex()) {
        // سطر رسوم أو ضريبة فيه رقم مش بالريال («Fee: USD 2.00» · «Fees: 5.75») ⇒ الرسوم مش مقروءة
        if (index in totalLines || !FEE_LABEL_LINE.containsMatchIn(line) || FX_LINE.containsMatchIn(line) || line.none { it in '0'..'9' }) continue
        if (amountsNearCurrency(line, SAUDI_LOCAL.regex, AmountStyle.SAUDI).none { it is Near.Value }) return null
    }
    val read = readFees(body, SAUDI_LOCAL.regex, AmountStyle.SAUDI, ::sar, skipLines = totalLines) ?: return null
    if (read.fee <= 0) return null
    val charge = addMoney(read.fee, read.vat)
    if (totalLines.isEmpty()) return SmsFee(charge, includedInAmount = false)
    // «إجمالي المبلغ المستحق» = المخصوم: لازم = مبلغ الصف، والمبلغ من غير الرسوم مكتوب في الرسالة (غير كده مش عارفين الرسوم جوه ولا برّه)
    val totals = totalLines.flatMap { amountsNearCurrency(lines[it], SAUDI_LOCAL.regex, AmountStyle.SAUDI) }
    val total = (totals.singleOrNull() as? Near.Value)?.let { sar(it.number) } ?: return null
    if (total != amountMinor) return null
    val principal = subtractMoney(total, charge)
    return if (principal > 0 && principal in read.bases) SmsFee(charge, includedInAmount = true) else null
}

/**
 * مصر: «مصاريف الخدمة 1 جنيه» (فودافون كاش) · «تكلفة الخدمة 1.50 جنيه» · «وخصم 5 جنيه من محفظتك» (مخصومة صريح — حتى في الاستلام).
 * المبلغ عمره ما بيبقى فيه الرسوم في الأشكال دي (القارئ بيسيب رقمها) ⇒ [SmsFee.includedInAmount] = false دايمًا. رسالة شحن الرصيد
 * («وخصم 57 من محفظتك شاملة الضريبة») ⇒ null: المخصوم هو المبلغ نفسه، والضريبة ضريبة الشحن مش رسوم.
 */
internal fun egyptFeeOf(text: String, kind: SmsKind, amountMinor: Halalas): SmsFee? {
    // الاتجاه زي القارئ بالظبط (`egyptDirection` على نفس النص) · العملة من غير التشكيل
    val direction = egyptDirection(text) ?: return null
    val body = withoutTashkeel(text)
    if (kind == SmsKind.FEE || RECHARGE.containsMatchIn(body) || INCLUDED_WORDING.containsMatchIn(body)) return null
    var walletFee = 0L
    val walletRanges = mutableListOf<IntRange>()
    for (m in WALLET_DEBIT.findAll(body)) {
        val number = m.groups[1]!!
        walletFee = addMoney(walletFee, egp(number.value) ?: return null)
        walletRanges += number.range
    }
    // نفس الرقم «وخصم … من محفظتك» ممكن يبقى جنب اسم رسوم كمان («مصاريف الخدمة وخصم 1.50 جنيه من محفظتك») ⇒ بيتحسب مرة واحدة
    val read = readFees(body, EGYPT_LOCAL.regex, AmountStyle.EGYPT, ::egp, skipAt = walletRanges) ?: return null
    // اسم رسوم وبعده رقم ما اتقراش بالجنيه («مصاريف الخدمة 1» من غير عملة) ⇒ مش واضحة
    for (m in EG_FEE_LABEL_NUMBER.findAll(body)) {
        val at = (m.groups[1] ?: m.groups[2])!!.range.first
        if ((read.counted + walletRanges).none { at in it }) return null
    }
    // الاستلام: الرسوم المخصومة صريح بس («وخصم … من محفظتك») — «رسوم» جنب مبلغ داخل ممكن تكون اتخصمت من المبلغ نفسه
    if (direction == Direction.IN && read.counted.isNotEmpty()) return null
    if (addMoney(walletFee, read.fee) <= 0) return null
    return SmsFee(addMoney(walletFee, read.fee, read.vat), includedInAmount = false)
}
