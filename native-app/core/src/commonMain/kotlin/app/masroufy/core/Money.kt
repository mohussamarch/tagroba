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
 * بيقبل: "96.47" · "1,234.5" · الأرقام العربية · "-12" · "12.4-" · "(12.40)"
 * بيرفض: "" · "NaN" · "1.2.3" · أكتر من خانتين كسريتين غير صفرية (ما بيقربش بصمت)
 */
fun parseMoney(raw: String, currency: Currency = Currency.SAR): Halalas {
    var s = JsText.trim(normalizeDigits(raw))
    if (s.isEmpty()) throw MoneyError(uiText(TextKey.MONEY_EMPTY), raw)

    // فواصل الآلاف والمسافات بأنواعها
    s = s.filterNot { JsText.isWhitespace(it) || it == ',' }

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

    val dot = s.indexOf('.')
    val intRaw = if (dot < 0) s else s.substring(0, dot)
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
    if (digits.length > 16) throw MoneyError("المبلغ خارج المدى الآمن", raw)
    val value = digits.toLong()
    if (value > MAX_SAFE_HALALAS) throw MoneyError("المبلغ خارج المدى الآمن", raw)
    return if (negative) -value else value
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
