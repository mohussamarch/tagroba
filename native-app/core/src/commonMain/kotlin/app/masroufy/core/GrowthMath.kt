package app.masroufy.core

/**
 * حساب «هتوصل لكام؟» (OVERRIDES §69.6) — أعداد صحيحة بس (`Long` بالهللة · نقاط أساس · جزء من 10^8)، من غير أي كسر عائم.
 *
 * **ليه دالة نمو تانية جنب `projectMonthly`؟** المتوسطات اللي في الملف (الذهب · التضخم · الأسهم · العقار) **معدل سنوي مركّب** (CAGR):
 * 14.88% يعني الأونصة بتبقى × 1.1488 كل سنة. `projectMonthly` بتقسم السنوي على 12 (زي فايدة البنك المعلنة) ⇒ لو اتدّتلها 14.88%
 * هتطلع فعليًا 15.9% في السنة (أعلى من الحقيقة بنقطة كل سنة). فـ:
 * - **المتوسطات (CAGR):** [projectGrowth] — المعدل الشهري **المكافئ** ([monthlyRateFromAnnual]): (1 + m)^12 = 1 + السنوي.
 * - **الوديعة:** `projectMonthly` زي ما هي (فايدة البنك المعلنة بتتقسم على 12 — اختيار Claude، المالك يقدر يغيّره).
 *
 * **قاعدة التقريب:**
 * 1. المعدل الشهري المكافئ = أقرب جزء من 10^8 (بحث ثنائي؛ الأس 12 بالتكرار وكل ضربة بتتقرب للأقرب). أقصى فرق ≈ 10^-8 في الشهر.
 * 2. كل شهر: الزيادة على رصيد أول الشهر **تتقرب لأقرب هللة (النص لبعيد عن الصفر)** وبعدين الإيداع آخر الشهر — نفس ترتيب `projectMonthly`.
 * 3. السنين الكاملة ([compoundYears] · [deflate]): × (10,000 + bp) ÷ 10,000 بالظبط وتقريب للهللة كل سنة.
 * 4. أي ناتج برا الحد الآمن ⇒ `MoneyError` (مش رقم غلط بصمت) — الضرب بيتقسم (الحاصل والباقي) عشان ما يعدّيش `Long`.
 */

/** دقة المعدل الشهري: جزء من 10^8. */
const val MONTHLY_RATE_SCALE = 100_000_000L

/** أعلى معدل شهري بيتدوّر عليه: 25% (1000% في السنة = 22.2% في الشهر) — حد أمان عشان الأس ما يطفحش. */
private const val MAX_MONTHLY_RATE = 25_000_000L

/** أقل معدل شهري: −60% (−99.99% في السنة = −53.6% في الشهر). */
private const val MIN_MONTHLY_RATE = -60_000_000L

class GrowthError(message: String) : IllegalArgumentException(message)

fun checkAnnualRate(annualBp: Int) {
    if (annualBp < MIN_ANNUAL_RATE_BP || annualBp > MAX_ANNUAL_RATE_BP) throw GrowthError(uiText(TextKey.CALC_RATE_RANGE))
}

/**
 * [a] × [num] ÷ [den] بتقريب واحد لأقرب هللة (النص لبعيد عن الصفر): a = q×den + r ⇒ q×num + (r×num ÷ den).
 * [den] من 1 لـ10^9 و|[num]| ≤ 10^9 ⇒ r×num < 10^18 (جوه `Long`)، و q×num بيتفحص (`multiplyMoneyByInt`).
 */
internal fun mulDivWide(a: Halalas, num: Long, den: Long): Halalas {
    assertHalalas(a)
    require(den in 1L..1_000_000_000L && num in -1_000_000_000L..1_000_000_000L)
    val negative = (a < 0) != (num < 0) && a != 0L && num != 0L
    val absA = if (a < 0) -a else a
    val absN = if (num < 0) -num else num
    val whole = multiplyMoneyByInt(absA / den, absN)
    val part = (absA % den) * absN
    val rounded = part / den + if ((part % den) * 2 >= den) 1 else 0
    val result = addMoney(whole, rounded)
    return if (negative) -result else result
}

/** (1 + m)^12 بمقياس 10^8 — الضرب بالتكرار وكل ضربة بتتقرب للأقرب (النص لفوق) عشان ما يبقاش فيه ميل لتحت. */
private fun pow12(m: Long): Long {
    var x = MONTHLY_RATE_SCALE
    repeat(12) { x = (x * (MONTHLY_RATE_SCALE + m) + MONTHLY_RATE_SCALE / 2) / MONTHLY_RATE_SCALE }
    return x
}

/** المعدل الشهري المكافئ لمعدل سنوي مركّب [annualBp] — أقرب m (جزء من 10^8) بحيث (1 + m)^12 ≈ 1 + السنوي. صفر ⇒ صفر بالظبط. */
fun monthlyRateFromAnnual(annualBp: Int): Long {
    checkAnnualRate(annualBp)
    if (annualBp == 0) return 0L
    val target = MONTHLY_RATE_SCALE + annualBp.toLong() * 10_000L
    var lo = MIN_MONTHLY_RATE
    var hi = MAX_MONTHLY_RATE
    while (lo < hi) { // أصغر m: (1 + m)^12 ≥ الهدف
        val mid = lo + (hi - lo) / 2
        if (pow12(mid) >= target) hi = mid else lo = mid + 1
    }
    val below = lo - 1
    return if (target - pow12(below) < pow12(lo) - target) below else lo
}

/**
 * مبلغ شهري [monthlyMinor] لمدة [months] شهر + مبلغ بداية [startMinor] بمعدل سنوي **مركّب** [annualBp] (متوسط CAGR).
 * الترتيب والتقريب زي `projectMonthly` (الزيادة على رصيد أول الشهر ثم الإيداع آخره) بس بالمعدل الشهري المكافئ.
 * معدل صفر ⇒ [startMinor] + الشهري × الشهور بالظبط.
 */
fun projectGrowth(monthlyMinor: Halalas, months: Int, annualBp: Int, startMinor: Halalas = 0L): Halalas {
    if (monthlyMinor < 0 || startMinor < 0) throw GrowthError(uiText(TextKey.CALC_MONTHLY_POSITIVE))
    if (months < 0 || months > MAX_CALC_MONTHS) throw GrowthError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    val m = monthlyRateFromAnnual(annualBp)
    var balance = startMinor
    repeat(months) { balance = addMoney(balance, mulDivWide(balance, m, MONTHLY_RATE_SCALE), monthlyMinor) }
    return balance
}

/** قيمة [valueMinor] بعد [years] سنة كاملة بمعدل سنوي [annualBp] — × (10,000 + bp) ÷ 10,000 وتقريب للهللة كل سنة. */
fun compoundYears(valueMinor: Halalas, years: Int, annualBp: Int): Halalas {
    checkAnnualRate(annualBp)
    if (years < 0 || years > MAX_CALC_MONTHS / 12) throw GrowthError(uiText(TextKey.GROWTH_YEARS_RANGE))
    var v = valueMinor
    repeat(years) { v = mulDivWide(v, BASIS_POINTS + annualBp, BASIS_POINTS) }
    return v
}

/**
 * «بقيمة فلوس النهارده»: [amountMinor] بعد [months] شهر ÷ (1 + التضخم)^(الشهور ÷ 12). السنين الكاملة ÷ (10,000 + bp) ÷ 10,000
 * بالظبط، والشهور الباقية بالمعدل الشهري المكافئ — تقريب للهللة كل خطوة.
 */
fun deflate(amountMinor: Halalas, months: Int, inflationBp: Int): Halalas {
    checkAnnualRate(inflationBp)
    if (months < 0 || months > MAX_CALC_MONTHS) throw GrowthError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    var v = amountMinor
    repeat(months / 12) { v = mulDivWide(v, BASIS_POINTS, BASIS_POINTS + inflationBp) }
    val rest = months % 12
    if (rest > 0) {
        val m = monthlyRateFromAnnual(inflationBp)
        repeat(rest) { v = mulDivWide(v, MONTHLY_RATE_SCALE, MONTHLY_RATE_SCALE + m) }
    }
    return v
}

/**
 * الجزء اللي من نزول العملة (OVERRIDES §69.3 — «جزء كبير من الزيادة سببه نزول الجنيه»): (1 + بالعملة المحلية) ÷ (1 + بالدولار) − 1،
 * بنقاط الأساس، النص لفوق. الاتنين لازم على نفس الأساس والفترة (`GOLD_EGP` مع `GOLD_USD_ANNUAL`).
 */
fun devaluationPartBp(localBp: Int, usdBp: Int): Int {
    checkAnnualRate(localBp)
    checkAnnualRate(usdBp)
    val num = (BASIS_POINTS + localBp) * BASIS_POINTS
    val den = BASIS_POINTS + usdBp
    return ((num * 2 + den) / (den * 2) - BASIS_POINTS).toInt()
}

/** نفس المعدل بالدولار لو العملة نزلت [currencyFallBp] في السنة: (1 + المحلي) ÷ (1 + النزول) − 1 — نفس الحسبة بالعكس. */
fun inUsdTermsBp(localBp: Int, currencyFallBp: Int): Int = devaluationPartBp(localBp, currencyFallBp)

/** "14.88%" من نقاط أساس — أعداد صحيحة بس. */
fun formatBp(bp: Int): String {
    val sign = if (bp < 0) "-" else ""
    val abs = if (bp < 0) -bp else bp
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}%"
}
