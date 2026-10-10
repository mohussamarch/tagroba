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
    Currency.SAR -> uiText(UiKey.CURRENCY_NAME_SAR)
    Currency.EGP -> uiText(UiKey.CURRENCY_NAME_EGP)
    else -> currencyLabel(currency)
}

private val MONTH_KEYS = listOf(
    UiKey.MONTH_1, UiKey.MONTH_2, UiKey.MONTH_3, UiKey.MONTH_4, UiKey.MONTH_5, UiKey.MONTH_6,
    UiKey.MONTH_7, UiKey.MONTH_8, UiKey.MONTH_9, UiKey.MONTH_10, UiKey.MONTH_11, UiKey.MONTH_12,
)

/** أيام الأسبوع بترتيب ISO (الإتنين = 1 … الأحد = 7) — زي [isoWeekday]. */
private val WEEKDAY_KEYS = listOf(
    UiKey.WEEKDAY_MON, UiKey.WEEKDAY_TUE, UiKey.WEEKDAY_WED, UiKey.WEEKDAY_THU, UiKey.WEEKDAY_FRI, UiKey.WEEKDAY_SAT, UiKey.WEEKDAY_SUN,
)

/** رأس شبكة التقويم: **السبت أول الأسبوع في البلدين** (رد المالك OVERRIDES §76). */
private val SHORT_FROM_SATURDAY = listOf(
    UiKey.WEEKDAY_SHORT_SAT, UiKey.WEEKDAY_SHORT_SUN, UiKey.WEEKDAY_SHORT_MON, UiKey.WEEKDAY_SHORT_TUE,
    UiKey.WEEKDAY_SHORT_WED, UiKey.WEEKDAY_SHORT_THU, UiKey.WEEKDAY_SHORT_FRI,
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
    return uiText(UiKey.DATE_DAY_MONTH, sentenceNumber(p.day), monthName(p.month))
}

/** «الأربعاء، ٧ أكتوبر». */
fun weekdayDayMonth(date: IsoDate): String = uiText(UiKey.DATE_WEEKDAY_DAY_MONTH, weekdayName(date), dayMonth(date))

/** «أكتوبر ٢٠٢٦». */
fun monthYear(year: Int, month: Int): String = uiText(UiKey.DATE_MONTH_YEAR, monthName(month), sentenceNumber(year))
