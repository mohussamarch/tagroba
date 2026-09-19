package app.masroufy.core

import kotlin.math.abs

/**
 * عرض المبالغ — نقل `src/domain/formatMoney.ts`. **المكان الوحيد** اللي الهللة بتبقى ريال فيه،
 * بقسمة صحيحة وباقي (مفيش 96.46999 أبدًا).
 */

private fun currencyLabel(currency: Currency): String = when (currency) {
    Currency.SAR -> "ر.س"
    Currency.EGP -> "ج.م"
    Currency.USD -> "$"
    Currency.EUR -> "€"
    Currency.GBP -> "£"
    Currency.AED -> "د.إ"
}

private fun groupThousands(digits: String): String {
    val out = StringBuilder()
    for (i in digits.indices) {
        val fromEnd = digits.length - i
        out.append(digits[i])
        if (fromEnd > 1 && (fromEnd - 1) % 3 == 0) out.append(',')
    }
    return out.toString()
}

/** 9647 ⇒ "96.47" · -9647 ⇒ "-96.47" · 0 ⇒ "0.00" · 100000 ⇒ "1,000.00" — من غير رمز العملة. */
fun formatAmount(
    amount: Halalas,
    currency: Currency = Currency.SAR,
    alwaysSign: Boolean = false,
    grouping: Boolean = true,
): String {
    assertHalalas(amount, "مبلغ للعرض")
    val scale = minorUnitsOf(currency)
    val decimals = scale.toString().length - 1
    val total = abs(amount)
    val whole = total / scale
    val frac = total - whole * scale
    val wholeText = if (grouping) groupThousands(whole.toString()) else whole.toString()
    val fracText = if (decimals > 0) "." + frac.toString().padStart(decimals, '0') else ""
    val sign = if (amount < 0) "-" else if (alwaysSign) "+" else ""
    return sign + wholeText + fracText
}

/** نص كامل برمز العملة: "96.47 ر.س". `hidden` = إخفاء المبالغ (spec/01). */
fun formatMoney(
    amount: Halalas,
    currency: Currency = Currency.SAR,
    showCurrency: Boolean = true,
    alwaysSign: Boolean = false,
    grouping: Boolean = true,
    hidden: Boolean = false,
): String {
    if (hidden) return if (showCurrency) "•••• ${currencyLabel(currency)}" else "••••"
    val number = formatAmount(amount, currency, alwaysSign, grouping)
    return if (showCurrency) "$number ${currencyLabel(currency)}" else number
}

/** القيمة المجهولة نص صريح مش صفر (CLAUDE.md #10). */
const val NOT_AVAILABLE = "غير متاح"

fun formatMoneyOrNA(amount: Halalas?, currency: Currency = Currency.SAR): String =
    if (amount == null) NOT_AVAILABLE else formatMoney(amount, currency)

/**
 * معدل الادخار بخانة عشرية واحدة، أو null لو الدخل صفر (spec/06: لا Infinity ولا NaN، والسالب ما يتخباش).
 * نسبة للعرض مش مبلغ — نفس حساب التطبيق الحالي بالظبط (قسمة double ثم تقريب جافاسكربت).
 */
fun savingsRatePercent(incomeMinor: Halalas, remainingMinor: Halalas): Double? {
    assertHalalas(incomeMinor, "الدخل")
    assertHalalas(remainingMinor, "المتبقي")
    if (incomeMinor == 0L) return null
    val tenths = JsText.round((remainingMinor.toDouble() * 1000.0) / incomeMinor.toDouble())
    return tenths / 10.0
}

/** "12.5%" — دقيق للقيم اللي فيها خانة عشرية واحدة (ناتج `savingsRatePercent`). */
fun formatPercentOrNA(percent: Double?): String {
    if (percent == null || !percent.isFinite()) return NOT_AVAILABLE
    val sign = if (percent < 0) "-" else ""
    val tenths = JsText.round(abs(percent) * 10.0).toLong()
    return "$sign${tenths / 10}.${tenths % 10}%"
}
