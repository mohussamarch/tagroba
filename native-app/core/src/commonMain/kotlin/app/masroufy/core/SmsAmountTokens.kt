package app.masroufy.core

/**
 * الرقم المكتوب **جنب** عملة البلد في سطر من رسالة البنك — مشترك بين قارئ السعودية وقارئ مصر (الجولة التانية من المراجعة).
 *
 * قبل كده الرقم كان بيتاخد من أي ناحية وأول واحد يكسب: «Card 6604 SAR 64.25» اتسجلت 6,604 ريال (آخر 4 من الكارت)، و«SR2026030512»
 * (رقم مرجع) اتسجل ملياري ريال، و«SAR 64,25» اتسجلت 6,425 ريال، و«SAR 1 234.50» ريال واحد. دلوقتي لكل عملة:
 * - رقم من الناحيتين ⇒ [Near.Ambiguous] (الرسالة تترفض «أكتر من مبلغ» — ما بنختارش).
 * - فواصل مش مظبوطة (فاصلة الآلاف مش كل 3 أرقام · مسافة أو «،» بين الآلاف · كسور أكتر من منزلتين) ⇒ [Near.Malformed].
 * - الرقم جزء من تاريخ أو ساعة («2026-03-05 SAR» · «09:10 SAR») ⇒ مش مبلغ.
 * - كود لازق في 5 أرقام أو أكتر من غير فواصل ولا كسور («SR2026030512») ⇒ رقم مرجع مش مبلغ.
 *
 * الجولة التالتة: أي علامة مش مسافة ولا حرف لازقة في الرقم («1'234.50» · «1’234») ⇒ مش صالح (كانت بتقص أول الرقم في صمت) ·
 * استثناء ملف المرجع في السعودية («شراء 1.234 SAR» = 234.00) بقى **الشكل ده بالظبط** (رقم واحد · نقطة · 3 أرقام) — «12.345 SAR» ·
 * «1,234.567 SAR» · «1.234.50 SAR» · «64.255 ريال» ⇒ مش صالح · الكود اللازق في رقم صحيح من غير فواصل ([Near.Value.glued]) بيتعلّم
 * عشان القارئ يفرّقه عن رقم مرجع («Ref SR4821»).
 */
internal sealed interface Near {
    /** [start]/[end] = أول وآخر الرقم والعملة مع بعض في السطر (السياق اللي قبلهم بيتفحص: رصيد · رسوم …). */
    val start: Int
    val end: Int

    /** [glued] = العملة لازقة في رقم صحيح من غير فواصل ولا كسور («SR4821» · «شراءSR25») — ممكن يبقى رقم مرجع. */
    data class Value(override val start: Int, override val end: Int, val number: String, val glued: Boolean = false) : Near
    data class Malformed(override val start: Int, override val end: Int) : Near
    data class Ambiguous(override val start: Int, override val end: Int) : Near
}

/** أكبر مبلغ ممكن في رسالة بنك شخصية: 100 مليون (بالوحدة الصغرى). اللي أكبر منه رقم مرجع اتقرى مبلغ ⇒ «المبلغ مش صالح». */
internal const val SMS_AMOUNT_CAP_MINOR: Long = 10_000_000_000L

/**
 * [SAUDI]: ملف المرجع `golden/sms.json` بيقفل «شراء 1.234 SAR» = 234.00 (سلوك التطبيق الحالي — سؤال مفتوح للمالك في OVERRIDES §75.1)،
 * فالنقطة قبل الرقم هناك ما بتخليهوش «مش مظبوط». [EGYPT]: المبلغ ممكن يبدأ بنقطة («.50» — التجاري الدولي)، ومفيش ملف مرجع.
 */
internal enum class AmountStyle { SAUDI, EGYPT }

private const val H = "[ \\t\\u00A0\\u2000-\\u200A\\u202F\\u3000]"
private const val NUMBER = "\\d(?:[\\d,٬]*\\d)?(?:[.٫]\\d{1,2})?"
private const val EG_NUMBER = "(?:$NUMBER|[.٫]\\d{1,2})"
private val BEFORE = mapOf(AmountStyle.SAUDI to Regex("($NUMBER)$H*$"), AmountStyle.EGYPT to Regex("($EG_NUMBER)$H*$"))
private val AFTER = mapOf(AmountStyle.SAUDI to Regex("^($H*[:：]?$H*)($NUMBER)"), AmountStyle.EGYPT to Regex("^($H*[:：]?$H*)($EG_NUMBER)"))
private val GROUPING = Regex("^\\d{1,3}(?:[,٬]\\d{3})+(?:[.٫]\\d{1,2})?$|^\\d+(?:[.٫]\\d{1,2})?$|^[.٫]\\d{1,2}$")
private val SPACE_GROUP_BEFORE = Regex("(?<![\\d:/.\\\\-])\\d{1,3}$H$")
private val SPACE_GROUP_AFTER = Regex("^$H\\d{3}(?!\\d)")

private sealed interface Side
private data class Num(val start: Int, val end: Int, val number: String, val glued: Boolean = false) : Side
private data class Bad(val start: Int, val end: Int) : Side

private fun isDigit(c: Char?) = c != null && c in '0'..'9'

/** اللي ممكن ييجي قبل الرقم أو بعده عادي: مسافة · حرف (ومنه التطويل «بـ») · قوس · نقطتين · «=» · علامة. غير كده = الرقم متقطع. */
private fun plainNeighbour(c: Char?) = c == null || JsText.isWhitespace(c) || c.isLetter() || c in "([{:：=+-/\\|>*#"

/** علامات تنصيص قبل الرقم عادي («25 SAR» بين علامتين) — إلا لو قبلها رقم (يبقى فاصل آلاف غريب «1'234»). */
private const val QUOTES = "«»\"“”'‘’`"

/** «شراء 1.234 SAR» بالظبط (ملف المرجع): رقم واحد قبل النقطة ومفيش رقم أو فاصلة أو نقطة قبله. */
private fun goldenDotShape(line: String, start: Int, number: String): Boolean {
    if (number.length != 3 || !number.all(::isDigit)) return false
    val before = line.getOrNull(start - 3)
    return isDigit(line.getOrNull(start - 2)) && (before == null || !(isDigit(before) || before in ",.٬٫،"))
}

/** فاصلة الآلاف كل 3 أرقام بالظبط (أو مفيش)، والكسور منزلتين بالكتير. */
private fun wellGrouped(number: String) = GROUPING.matches(number)

/** «3 أرقام صحيحة من غير فواصل ولا كسور» — نص رقم اتقسم بمسافة («1 234.50»). */
private fun threeDigitHead(number: String) = number.takeWhile { it != '.' && it != '٫' }.let { it.length == 3 && it.all(::isDigit) }

/** الرقم قبل العملة. */
private fun before(line: String, tokenStart: Int, style: AmountStyle): Side? {
    val head = line.substring(0, tokenStart)
    val m = BEFORE.getValue(style).find(head) ?: return null
    val g = m.groups[1]!!
    val start = g.range.first
    val prev = line.getOrNull(start - 1)
    val prev2 = line.getOrNull(start - 2)
    return when {
        prev != null && prev in ":/\\-" && isDigit(prev2) -> null // ساعة أو تاريخ
        prev == '،' && isDigit(prev2) -> Bad(start, tokenStart)
        (prev == '.' || prev == '٫') && isDigit(prev2) ->
            if (style == AmountStyle.SAUDI && goldenDotShape(line, start, g.value)) Num(start, tokenStart, g.value) else Bad(start, tokenStart)
        prev != null && !plainNeighbour(prev) && (isDigit(prev2) || prev !in QUOTES) -> Bad(start, tokenStart) // «1'234.50»
        !wellGrouped(g.value) -> Bad(start, tokenStart)
        threeDigitHead(g.value) && SPACE_GROUP_BEFORE.containsMatchIn(line.substring(0, start)) -> Bad(start, tokenStart)
        else -> Num(start, tokenStart, g.value)
    }
}

/** الرقم بعد العملة. */
private fun after(line: String, tokenStart: Int, tokenEnd: Int, style: AmountStyle): Side? {
    val tail = line.substring(tokenEnd)
    val m = AFTER.getValue(style).find(tail) ?: return null
    val number = m.groupValues[2]
    val start = tokenEnd + m.groups[2]!!.range.first
    val end = tokenEnd + m.range.last + 1
    val next = line.getOrNull(end)
    val next2 = line.getOrNull(end + 1)
    val glued = m.groupValues[1].isEmpty()
    return when {
        next != null && next in ":/\\-" && isDigit(next2) -> null // تاريخ أو ساعة
        glued && number.length >= 5 && number.all(::isDigit) -> null // «SR2026030512» رقم مرجع
        isDigit(next) -> Bad(tokenStart, end) // كسور أكتر من منزلتين («SAR 1.234,50» · «SAR 12.345»)
        next != null && !plainNeighbour(next) && isDigit(next2) -> Bad(tokenStart, end) // «SAR 1,234.5,0» · «SAR 1'234.50»
        !wellGrouped(number) -> Bad(tokenStart, end)
        number.length <= 3 && number.all(::isDigit) && SPACE_GROUP_AFTER.containsMatchIn(line.substring(end)) -> Bad(tokenStart, end)
        else -> Num(start, end, number, glued = glued && number.all(::isDigit))
    }
}

/** كل عملة في السطر والرقم اللي جنبها (أو مشكلته). العملة اللي مفيش جنبها رقم ما بتطلعش. */
internal fun amountsNearCurrency(line: String, currency: Regex, style: AmountStyle): List<Near> =
    currency.findAll(line).mapNotNull { t ->
        val tokenEnd = t.range.last + 1
        val b = before(line, t.range.first, style)
        val a = after(line, t.range.first, tokenEnd, style)
        when {
            b != null && a != null -> Near.Ambiguous(minOf(startOf(b), t.range.first), maxOf(endOf(a), tokenEnd))
            b is Num -> Near.Value(b.start, tokenEnd, b.number)
            a is Num -> Near.Value(t.range.first, a.end, a.number, a.glued)
            b is Bad -> Near.Malformed(b.start, tokenEnd)
            a is Bad -> Near.Malformed(t.range.first, a.end)
            else -> null
        }
    }.toList()

private fun startOf(s: Side) = when (s) { is Num -> s.start; is Bad -> s.start }

private fun endOf(s: Side) = when (s) { is Num -> s.end; is Bad -> s.end }
