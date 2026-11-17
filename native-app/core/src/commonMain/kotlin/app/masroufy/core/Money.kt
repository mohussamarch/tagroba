package app.masroufy.core

import kotlin.math.abs

/**
 * نموذج المال — الوحدة الصغرى. نقل `src/domain/money.ts` بنفس السلوك ونفس رسايل الأخطاء.
 *
 * كل مبلغ عدد صحيح بالهللة (`Long`): 96.47 ر.س = 9647. **ممنوع `Double` في أي حساب مالي** (CLAUDE.md #1).
 * الحد الآمن نفس التطبيق الحالي (2^53−1) عشان المبلغ اللي بيترفض هناك يترفض هنا.
 */
typealias Halalas = Long

enum class Currency { SAR, EGP, USD, EUR, GBP, AED }

/** عدد الوحدات الصغرى في الوحدة — كلها 100 دلوقتي. */
fun minorUnitsOf(@Suppress("UNUSED_PARAMETER") currency: Currency): Long = 100

/** علامة الناقص الرياضية U+2212 — بتيجي من بعض الكشوف. */
private const val MINUS_SIGN = '−'

/** نفس `Number.MAX_SAFE_INTEGER` في التطبيق الحالي. */
const val MAX_SAFE_HALALAS: Long = 9_007_199_254_740_991L

class MoneyError(message: String, val input: String? = null) : IllegalArgumentException(message)

fun assertHalalas(value: Long, context: String = uiText(TextKey.AMOUNT_CONTEXT_DEFAULT)) {
    if (value > MAX_SAFE_HALALAS || value < -MAX_SAFE_HALALAS) {
        throw MoneyError(uiText(TextKey.MONEY_OUT_OF_RANGE_CONTEXT, context), value.toString())
    }
}

/** الأرقام العربية الهندية والفارسية للاتينية، والفاصلة العشرية العربية لنقطة، وفاصل الآلاف العربي لفاصلة. */
fun normalizeDigits(text: String): String {
    val out = StringBuilder(text.length)
    for (c in text) {
        when {
            JsText.easternDigit(c) != null -> out.append(JsText.easternDigit(c))
            c.code == 0x066B -> out.append('.') // الفاصلة العشرية العربية
            c.code == 0x066C -> out.append(',') // فاصل الآلاف العربي
            else -> out.append(c)
        }
    }
    return out.toString()
}

/**
 * نص مالي ⇒ هللات **من غير أي عملية عشرية**.
 * بيقبل: "96.47" · "1,234.5" · "12,50" (فاصلة عشرية §120-3) · الأرقام العربية · "-12" · "12.4-" · "(12.40)"
 * بيرفض: "" · "NaN" · "1.2.3" · أكتر من خانتين كسريتين غير صفرية (ما بيقربش بصمت)
 */
fun parseMoney(raw: String, currency: Currency = Currency.SAR): Halalas {
    var s = JsText.trim(normalizeDigits(raw))
    if (s.isEmpty()) throw MoneyError(uiText(TextKey.MONEY_EMPTY), raw)

    // §121-15: مسافة بين رقمين = فاصل آلاف («1 234») ⇒ الفاصلة بعدها ما تبقاش عشرية
    val spaceGrouped = s.indices.any { i -> JsText.isWhitespace(s[i]) && hasDigitAround(s, i) }
    // المسافات بأنواعها. الفاصلة بتتشال تحت **بعد** التأكد إنها فاصل آلاف فعلًا ([groupedInt])
    s = s.filterNot { JsText.isWhitespace(it) }

    // إشارة سالبة قبل أو بعد، أو أقواس محاسبية
    var negative = false
    if (s.length >= 2 && s.first() == '(' && s.last() == ')') {
        negative = true
        s = s.substring(1, s.length - 1)
    }
    if (s.startsWith('-') || s.startsWith(MINUS_SIGN)) {
        negative = !negative
        s = s.substring(1)
    } else if (s.startsWith('+')) {
        s = s.substring(1)
    }
    if (s.endsWith('-')) {
        negative = !negative
        s = s.dropLast(1)
    }

    s = decimalComma(s, spaceGrouped) ?: throw MoneyError(uiText(TextKey.MONEY_BAD_FORMAT), raw)
    val dot = s.indexOf('.')
    val intRaw = groupedInt(if (dot < 0) s else s.substring(0, dot)) ?: throw MoneyError(uiText(TextKey.MONEY_BAD_FORMAT), raw)
    val fracRaw = if (dot < 0) "" else s.substring(dot + 1)
    val valid = intRaw.all(JsText::isAsciiDigit) && fracRaw.all(JsText::isAsciiDigit)
    if (!valid || (intRaw.isEmpty() && fracRaw.isEmpty())) throw MoneyError(uiText(TextKey.MONEY_BAD_FORMAT), raw)

    val intPart = intRaw.ifEmpty { "0" }
    val decimals = minorUnitsOf(currency).toString().length - 1 // 100 ⇒ 2

    if (fracRaw.length > decimals && fracRaw.substring(decimals).any { it != '0' }) {
        throw MoneyError(uiText(TextKey.MONEY_TOO_MANY_DECIMALS, decimals.toString()), raw)
    }
    val frac = fracRaw.take(decimals).padEnd(decimals, '0')

    // البنا من الأرقام مباشرة — لا ضرب ولا قسمة عشرية
    val digits = intPart + frac
    if (digits.length > 16) throw MoneyError(uiText(TextKey.MONEY_OUT_OF_SAFE_RANGE), raw)
    val value = digits.toLong()
    if (value > MAX_SAFE_HALALAS) throw MoneyError(uiText(TextKey.MONEY_OUT_OF_SAFE_RANGE), raw)
    return if (negative) -value else value
}

/**
 * مراجعة 2026-11-14 (money-1): الفاصلة فاصل آلاف **بس** لو كل مجموعة بعدها 3 أرقام بالظبط («1,234» · «12,345,678»). غير كده null ⇒
 * المبلغ بيترفض بدل ما «1,2345» تتسجل غلط (الفاصلة العشرية «12,50» بتتحول لنقطة قبلها في [decimalComma]) (نفس قاعدة رسايل البنك في `SmsAmountTokens.kt`). من غير فاصلة بيرجع زي ما هو.
 */
private fun groupedInt(int: String): String? {
    if (',' !in int) return int
    val groups = int.split(',')
    val ok = groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 } && groups.all { g -> g.all(JsText::isAsciiDigit) }
    return if (ok) groups.joinToString("") else null
}

/** مسافة في [i] بين رقم قبلها ورقم بعدها (بعد تخطي المسافات اللي جنبها). */
private fun hasDigitAround(s: String, i: Int): Boolean {
    var a = i - 1
    while (a >= 0 && JsText.isWhitespace(s[a])) a--
    var b = i + 1
    while (b < s.length && JsText.isWhitespace(s[b])) b++
    return a >= 0 && b < s.length && JsText.isAsciiDigit(s[a]) && JsText.isAsciiDigit(s[b])
}

/**
 * OVERRIDES §120-3 + §121-15: من غير نقطة، الفاصلة الأخيرة اللي بعدها رقم أو رقمين **بس** في الآخر = علامة عشرية
 * («12,50» = 12.50 · «12,5» = 12.50). «1,234» تفضل فاصل آلاف. الفاصلة العشرية مقبولة **بس** في رقم من غير فواصل آلاف:
 * فاصلة تانية قبلها («1,234,50») أو مسافة آلاف («1 234,00») ⇒ null (يترفض ويطلب يكتبها صح).
 */
private fun decimalComma(s: String, spaceGrouped: Boolean): String? {
    if ('.' in s) return s
    val c = s.lastIndexOf(',')
    if (c < 0 || (s.length - c - 1) !in 1..2) return s
    val head = s.substring(0, c)
    if (',' in head || spaceGrouped) return null
    return head + "." + s.substring(c + 1)
}

/** زي `parseMoney` بس بيرجّع null بدل ما يرمي — للاستيراد اللي بيجمع الأخطاء بأرقام الصفوف. */
fun tryParseMoney(raw: String, currency: Currency = Currency.SAR): Halalas? =
    try { parseMoney(raw, currency) } catch (_: MoneyError) { null }

fun addMoney(vararg amounts: Halalas): Halalas {
    var total = 0L
    for (a in amounts) {
        assertHalalas(a)
        total += a
    }
    assertHalalas(total, uiText(TextKey.AMOUNT_CONTEXT_TOTAL))
    return total
}

/** مجموع قايمة فاضية صفر — صفر محسوب مش مفترض. */
fun sumMoney(amounts: List<Halalas>): Halalas = addMoney(*amounts.toLongArray())

fun subtractMoney(a: Halalas, b: Halalas): Halalas {
    assertHalalas(a)
    assertHalalas(b)
    val r = a - b
    assertHalalas(r, uiText(TextKey.AMOUNT_CONTEXT_DIFFERENCE))
    return r
}

fun negateMoney(a: Halalas): Halalas { assertHalalas(a); return -a }

fun absMoney(a: Halalas): Halalas { assertHalalas(a); return abs(a) }

/** ضرب في عدد صحيح (عدد الأقساط مثلًا). للنسب: `rateOfMoney` أو `splitMoney`. */
fun multiplyMoneyByInt(a: Halalas, times: Long): Halalas {
    assertHalalas(a)
    if (times != 0L && abs(a) > MAX_SAFE_HALALAS / abs(times)) {
        throw MoneyError(uiText(TextKey.MONEY_RESULT_OUT_OF_RANGE), "$a×$times")
    }
    return a * times
}

/** نسبة (بسط/مقام) بتقريب النص لفوق بعيد عن الصفر عند حد واحد معلن. عددين صحيحين — مفيش عشري. */
fun rateOfMoney(a: Halalas, numerator: Long, denominator: Long): Halalas {
    assertHalalas(a)
    if (denominator == 0L) throw MoneyError(uiText(TextKey.MONEY_DIVIDE_BY_ZERO))
    val sign = if ((a < 0) != (numerator < 0)) -1L else 1L
    val num = abs(a) * abs(numerator)
    val den = abs(denominator)
    val q = num / den
    val rem = num - q * den
    val rounded = if (rem * 2 >= den) q + 1 else q
    val r = sign * rounded
    assertHalalas(r, uiText(TextKey.AMOUNT_CONTEXT_SHARE))
    return r
}

/** تقسيم على n حصة **من غير ما هللة تضيع أو تتخلق**؛ الباقي هللة هللة على الأوائل. */
fun splitMoney(a: Halalas, parts: Int): List<Halalas> {
    assertHalalas(a)
    if (parts <= 0) throw MoneyError(uiText(TextKey.MONEY_PARTS_POSITIVE), parts.toString())
    val sign = if (a < 0) -1L else 1L
    val total = abs(a)
    val base = total / parts
    var remainder = total - base * parts
    return List(parts) {
        val extra = if (remainder > 0) 1L else 0L
        if (extra == 1L) remainder--
        sign * (base + extra)
    }
}

fun compareMoney(a: Halalas, b: Halalas): Int {
    assertHalalas(a)
    assertHalalas(b)
    return a.compareTo(b).coerceIn(-1, 1)
}

/** مقارنة بهامش نسبي بالألف (50 = ٥٪) — للبحث بالمبلغ ±5% (spec/06). */
fun withinRelativeTolerance(value: Halalas, target: Halalas, tolerancePerThousand: Long): Boolean {
    assertHalalas(value)
    assertHalalas(target)
    val margin = rateOfMoney(absMoney(target), tolerancePerThousand, 1000)
    return absMoney(subtractMoney(value, target)) <= margin
}
