package app.masroufy.core

/**
 * عرض التواريخ والأرقام **جوه الجمل** (DESIGN-SYSTEM «الخط والأرقام»): التواريخ والعدّ بالأرقام العربية الشرقية (١٨ أكتوبر) في العربي،
 * واللاتيني في الإنجليزي. **المبالغ مش من هنا** — المبالغ دايمًا `formatMoney` (لاتيني، `dir=ltr`، أرقام جدولية).
 * عرض بس، من غير أي حساب فلوس.
 */

private const val EASTERN_DIGITS = "٠١٢٣٤٥٦٧٨٩"

/** الأرقام اللاتيني في [text] ⇒ عربي شرقي (لو اللغة عربي). */
fun sentenceDigits(text: String): String =
    if (Texts.language == Language.EN) text else text.map { c -> if (c in '0'..'9') EASTERN_DIGITS[c - '0'] else c }.joinToString("")

/** عدد في جملة (٧ · ٢٠٢٦ — من غير فواصل آلاف). */
fun sentenceNumber(n: Long): String = sentenceDigits(n.toString())

fun sentenceNumber(n: Int): String = sentenceNumber(n.toLong())

/** رمز العملة لوحده (الرقم الكبير بخط والعملة بخط أصغر — البطاقة البطلة). */
fun currencySymbol(currency: Currency): String = currencyLabel(currency)

/** اسم العملة بالكلام (لوحة تبديل البلد) — العملات اللي ليها بلد في التطبيق بس، وغيرها رمزها. */
fun currencyName(currency: Currency): String = when (currency) {
    Currency.SAR -> uiText(TextKey.CURRENCY_NAME_SAR)
    Currency.EGP -> uiText(TextKey.CURRENCY_NAME_EGP)
    else -> currencyLabel(currency)
}

private val MONTH_KEYS = listOf(
    TextKey.MONTH_1, TextKey.MONTH_2, TextKey.MONTH_3, TextKey.MONTH_4, TextKey.MONTH_5, TextKey.MONTH_6,
    TextKey.MONTH_7, TextKey.MONTH_8, TextKey.MONTH_9, TextKey.MONTH_10, TextKey.MONTH_11, TextKey.MONTH_12,
)

/** أيام الأسبوع بترتيب ISO (الإتنين = 1 … الأحد = 7) — زي [isoWeekday]. */
private val WEEKDAY_KEYS = listOf(
    TextKey.WEEKDAY_MON, TextKey.WEEKDAY_TUE, TextKey.WEEKDAY_WED, TextKey.WEEKDAY_THU, TextKey.WEEKDAY_FRI, TextKey.WEEKDAY_SAT, TextKey.WEEKDAY_SUN,
)

/** رأس شبكة التقويم: **السبت أول الأسبوع في البلدين** (رد المالك OVERRIDES §76). */
private val SHORT_FROM_SATURDAY = listOf(
    TextKey.WEEKDAY_SHORT_SAT, TextKey.WEEKDAY_SHORT_SUN, TextKey.WEEKDAY_SHORT_MON, TextKey.WEEKDAY_SHORT_TUE,
    TextKey.WEEKDAY_SHORT_WED, TextKey.WEEKDAY_SHORT_THU, TextKey.WEEKDAY_SHORT_FRI,
)

fun monthName(month: Int): String {
    require(month in 1..12) { "month $month" }
    return uiText(MONTH_KEYS[month - 1])
}

fun weekdayName(date: IsoDate): String = uiText(WEEKDAY_KEYS[isoWeekday(date) - 1])

/** أسماء الأيام المختصرة من السبت للجمعة. */
fun weekdayShortNamesFromSaturday(): List<String> = SHORT_FROM_SATURDAY.map { uiText(it) }

/** مكان أول يوم في الشهر في شبكة بتبدأ بالسبت (السبت = ٠ … الجمعة = ٦). */
fun saturdayColumnOf(date: IsoDate): Int = (isoWeekday(date) + 1) % 7

/** «٧ أكتوبر». */
fun dayMonth(date: IsoDate): String {
    val p = parseIsoDate(date)
    return uiText(TextKey.DATE_DAY_MONTH, sentenceNumber(p.day), monthName(p.month))
}

/** «الأربعاء، ٧ أكتوبر». */
fun weekdayDayMonth(date: IsoDate): String = uiText(TextKey.DATE_WEEKDAY_DAY_MONTH, weekdayName(date), dayMonth(date))

/** «أكتوبر ٢٠٢٦». */
fun monthYear(year: Int, month: Int): String = uiText(TextKey.DATE_MONTH_YEAR, monthName(month), sentenceNumber(year))
