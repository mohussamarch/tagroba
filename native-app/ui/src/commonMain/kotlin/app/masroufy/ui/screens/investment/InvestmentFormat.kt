package app.masroufy.ui.screens.investment

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.HijriDate
import app.masroufy.core.IsoDate
import app.masroufy.core.Language
import app.masroufy.core.Quantity
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.dayMonth
import app.masroufy.core.formatQuantity
import app.masroufy.core.parseIsoDate
import app.masroufy.core.sentenceDigits
import app.masroufy.core.sentenceNumber
import app.masroufy.core.uiText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.icons.Lucide

/**
 * عرض بس (من غير أي حساب فلوس — CLAUDE.md #4): الكميات والنسب والتواريخ جوه الجمل بالأرقام العربية في العربي، والمبالغ دايمًا
 * `amountLabel` (لاتيني، `ltr`). المبالغ نفسها جاية جاهزة من حالات الاستخدام بالهللة.
 */

/** كمية في جملة: «١٢٠» · «٠٫٥» (من غير أصفار زيادة). */
internal fun qtyText(q: Quantity): String {
    val raw = formatQuantity(q)
    return if (Texts.language == Language.EN) raw else sentenceDigits(raw).replace('.', '٫')
}

/** «١٢٠ جرام» — الوحدة متخزنة مع الأصل (ما بتتترجمش). */
internal fun qtyUnit(q: Quantity, unit: String): String = uiText(TextKey.INVEST_QTY_UNIT, qtyText(q), unit)

/** نسبة من نقاط أساس: 174 ⇒ «١٫٧٤٪» · -250 ⇒ «−٢٫٥٪». عرض بس — زي `formatBp` في `core`. */
internal fun pctText(bp: Int): String {
    val abs = if (bp < 0) -bp else bp
    val frac = abs % 100
    val raw = if (frac == 0) "${abs / 100}" else "${abs / 100}." + frac.toString().padStart(2, '0').trimEnd('0')
    val local = if (Texts.language == Language.EN) raw else sentenceDigits(raw).replace('.', '٫')
    return (if (bp < 0) "−" else "") + uiText(TextKey.INVEST_PERCENT, local)
}

/** «١٢ مارس ٢٠٢٤». */
internal fun dateText(date: IsoDate): String = uiText(TextKey.INVEST_DATE_YEAR, dayMonth(date), sentenceNumber(parseIsoDate(date).year))

/** «اليوم» لو التاريخ النهارده، وإلا «١٢ مارس ٢٠٢٤». */
internal fun dateOrToday(date: IsoDate, today: IsoDate): String = if (date == today) uiText(TextKey.ASSET_DETAIL_TODAY) else dateText(date)

/** سنة في جملة («٢٠٣٠»). */
internal fun yearText(year: Int): String = sentenceNumber(year)

/** مبلغ بعملته في جملة («4,800.00 ر.س»). `null` ⇒ «غير متاح». */
internal fun moneyText(minor: Halalas?, currency: Currency): String = amountLabel(minor, currency)

/** مبلغ بإشارته («+1,200.00» · «−300.00») — المكسب والخسارة. */
internal fun signedText(minor: Halalas?, currency: Currency, showCurrency: Boolean = false): String =
    amountLabel(minor, currency, if (minor != null && minor < 0) AmountTone.EXPENSE else AmountTone.INCOME, showCurrency)

private val HIJRI_MONTHS = listOf(
    TextKey.HAWL_MONTH_1, TextKey.HAWL_MONTH_2, TextKey.HAWL_MONTH_3, TextKey.HAWL_MONTH_4, TextKey.HAWL_MONTH_5, TextKey.HAWL_MONTH_6,
    TextKey.HAWL_MONTH_7, TextKey.HAWL_MONTH_8, TextKey.HAWL_MONTH_9, TextKey.HAWL_MONTH_10, TextKey.HAWL_MONTH_11, TextKey.HAWL_MONTH_12,
)

internal fun hijriMonthName(month: Int): String = uiText(HIJRI_MONTHS[(month - 1).coerceIn(0, 11)])

/** «١ جمادى الأولى ١٤٤٨». */
internal fun hijriText(h: HijriDate): String = uiText(TextKey.HAWL_DATE, sentenceNumber(h.day), hijriMonthName(h.month), sentenceNumber(h.year))

/** «١ جمادى الأولى» (من غير سنة). */
internal fun hijriDayMonth(month: Int, day: Int): String = uiText(TextKey.DATE_DAY_MONTH, sentenceNumber(day), hijriMonthName(month))

/** رمز النوع (المعروض — `displayKind`). */
internal fun kindIcon(kind: String): Lucide = when (kind) {
    "gold", "silver" -> InvestmentIcons.GOLD
    "stock", "fund" -> Lucide.TRENDING_UP
    "digital" -> Lucide.COINS
    "realEstate" -> InvestmentIcons.HOME
    else -> Lucide.GEM
}

/** لون أيقونة النوع (النموذج: الذهب كهرماني · الأسهم أزرق · العقار أخضر). */
internal enum class KindTone { AMBER, BLUE, GREEN }

internal fun kindTone(kind: String): KindTone = when (kind) {
    "gold", "silver" -> KindTone.AMBER
    "stock", "fund", "digital" -> KindTone.BLUE
    else -> KindTone.GREEN
}
