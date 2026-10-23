package app.masroufy.core

/**
 * سداد دين بعملة البلد التانية (قرار المالك — OVERRIDES §111-4): «بتكتب المبلغ بالجنيه، وبينزل من الدين بالريال بسعر اليوم، وتقدر تعدّل السعر».
 *
 * السعر عدد صحيح بمقياس 10^4 ([FX_RATE_SCALE]) = **كام وحدة من العملة المدفوعة قصاد وحدة واحدة من عملة الدين** (مثلًا 13.2500 جنيه للريال ⇒ 132500)
 * — زي `egpPerSar4`. **مفيش كسور عشرية في الحساب**: المبلغ اللي بينزل = المدفوع × 10^4 ÷ السعر، بتقريب واحد لأقرب هللة (`mulDivWide`).
 * مفيش سعر في ملف الأسعار للجنيه (`FX_USD_EGP` ناقص — cty-11) ⇒ الخانة فاضية والسعر **إجباري** (القاعدة 10 — ما بنخترعش سعر).
 */
const val FX_RATE_SCALE = 10_000L

/** أكبر سعر مقبول (100,000 للوحدة) — حد `mulDivWide`. */
private const val MAX_RATE4 = 1_000_000_000L

/** السداد بعملة تانية متسجل على التسوية: المدفوع بعملته والسعر اللي اتحسب بيه. */
data class SettlementFx(val paidMinor: Halalas, val paidCurrency: Currency, val rate4: Long)

class FxSettleError(message: String) : IllegalStateException(message)

fun validFxRate(rate4: Long?): Boolean = rate4 != null && rate4 in 1..MAX_RATE4

/** المبلغ اللي بينزل من الدين بعملته — null لو المدفوع أو السعر مش صالحين (الشاشة بتقول «اكتب السعر»، مش صفر). */
fun fxDebtMinor(paidMinor: Halalas?, rate4: Long?): Halalas? {
    if (paidMinor == null || paidMinor <= 0 || paidMinor > MAX_SAFE_HALALAS || !validFxRate(rate4)) return null
    return mulDivWide(paidMinor, FX_RATE_SCALE, rate4!!).takeIf { it > 0 }
}

/**
 * نص السعر زي ما المالك بيكتبه («13.25» أو «13,25») ⇒ عدد صحيح بمقياس 10^4 — من غير `Double`. أكتر من 4 أرقام بعد العلامة أو أي حرف تاني ⇒ null.
 */
fun parseFxRate4(text: String): Long? {
    val s = text.trim().replace(',', '.')
    if (s.isEmpty() || s.count { it == '.' } > 1 || s.any { it != '.' && it !in '0'..'9' }) return null
    val whole = s.substringBefore('.').ifEmpty { "0" }
    val frac = s.substringAfter('.', "")
    if (frac.length > 4 || whole.length > 6) return null
    val rate = whole.toLong() * FX_RATE_SCALE + frac.padEnd(4, '0').toLong()
    return rate.takeIf(::validFxRate)
}

/** السعر للعرض («13.2500» ⇒ «13.25»). */
fun fxRateLabel(rate4: Long): String {
    val frac = (rate4 % FX_RATE_SCALE).toString().padStart(4, '0').trimEnd('0')
    return (rate4 / FX_RATE_SCALE).toString() + if (frac.isEmpty()) "" else ".$frac"
}
