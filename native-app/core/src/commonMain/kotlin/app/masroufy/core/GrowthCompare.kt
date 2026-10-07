package app.masroufy.core

/**
 * مقارنة حاسبة الادخار (طلب المالك §69: «المبلغ ده لو اتحول لذهب … هيوصل لكام … ويقارن لو حولهم لشقة»): نفس المبلغ الشهري لنفس المدة
 * في الخمسة ([GrowthClass]) كل واحد بمعدله. دالة نقية — المعدلات ومصادرها بتيجي من حالة الاستخدام (الافتراضي أو اللي المستخدم كتبه).
 *
 * - الذهب · العقار · الأسهم: متوسط سنوي مركّب ⇒ [projectGrowth].
 * - الوديعة: فايدة البنك المعلنة ⇒ `projectMonthly` (السنوي ÷ 12 كل شهر — اختيار Claude، المالك يقدر يغيّره).
 * - الكاش: صفر بالتعريف ⇒ اللي حطيته بالظبط.
 * - «بقيمة فلوس النهارده»: الناتج ÷ (1 + التضخم الرسمي)^(الشهور ÷ 12) ([deflate]). التضخم مش موجود ⇒ «غير متاح» (مش الرقم من غير خصم).
 */

/** سطر نوع واحد. [rateBp] null ⇒ مفيش معدل (المستخدم ما كتبش) ⇒ كل الأرقام null («غير متاح»). */
data class GrowthLine(
    val growthClass: GrowthClass,
    val rateBp: Int?,
    val reachedMinor: Halalas?,
    /** الزيادة فوق اللي حطيته (ممكن تبقى بالسالب). */
    val gainMinor: Halalas?,
    /** بقيمة فلوس النهارده — null لو الزرار مقفول أو التضخم مش معروف أو المعدل مش معروف. */
    val todayMoneyMinor: Halalas?,
)

data class GrowthComparison(
    val monthlyMinor: Halalas,
    val months: Int,
    val startMinor: Halalas,
    /** اللي حطيته من جيبك: البداية + الشهري × الشهور. */
    val paidInMinor: Halalas,
    val endDate: IsoDate,
    val lines: List<GrowthLine>,
    /** الزرار شغال؟ */
    val todayMoney: Boolean,
    /** التضخم المستخدم في الخصم — null لو مش معروف. */
    val inflationBp: Int?,
)

fun compareGrowth(
    monthlyMinor: Halalas,
    months: Int,
    startMinor: Halalas,
    fromDate: IsoDate,
    rates: Map<GrowthClass, Int?>,
    todayMoney: Boolean = false,
    inflationBp: Int? = null,
): GrowthComparison {
    if (monthlyMinor <= 0 || monthlyMinor > MAX_SAFE_HALALAS) throw GrowthError(uiText(TextKey.CALC_MONTHLY_POSITIVE))
    if (months < 1 || months > MAX_CALC_MONTHS) throw GrowthError(uiText(TextKey.CALC_MONTHS_RANGE, MAX_CALC_MONTHS.toString()))
    if (startMinor < 0 || startMinor > MAX_SAFE_HALALAS) throw GrowthError(uiText(TextKey.CALC_SAVED_NOT_NEGATIVE))
    if (!isValidIsoDate(fromDate)) throw GrowthError(uiText(TextKey.CALC_DATE_AFTER_TODAY))
    inflationBp?.let(::checkAnnualRate)
    val paidIn = addMoney(startMinor, multiplyMoneyByInt(monthlyMinor, months.toLong()))
    val lines = GrowthClass.entries.map { cls ->
        val rate = if (cls == GrowthClass.CASH) 0 else rates[cls]
        if (rate == null) return@map GrowthLine(cls, null, null, null, null)
        checkAnnualRate(rate)
        val reached = when (cls) {
            GrowthClass.CASH -> paidIn
            GrowthClass.DEPOSIT -> projectMonthly(monthlyMinor, months, rate, startMinor)
            else -> projectGrowth(monthlyMinor, months, rate, startMinor)
        }
        val today = if (todayMoney && inflationBp != null) deflate(reached, months, inflationBp) else null
        GrowthLine(cls, rate, reached, subtractMoney(reached, paidIn), today)
    }
    return GrowthComparison(monthlyMinor, months, startMinor, paidIn, addMonthsClamped(fromDate, months), lines, todayMoney, inflationBp)
}
