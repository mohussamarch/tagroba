package app.masroufy.ui.screens.investment

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.HijriDate
import app.masroufy.core.IsoDate
import app.masroufy.core.Quantity
import app.masroufy.core.TextKey
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

/** كمية في جملة: «120» · «0.5» (من غير أصفار زيادة). */
internal fun qtyText(q: Quantity): String {
    val raw = formatQuantity(q)
    return sentenceDigits(raw)
}

/** «120 جرام» — الوحدة متخزنة مع الأصل (ما بتتترجمش). */
internal fun qtyUnit(q: Quantity, unit: String): String = uiText(UiKey.INVEST_QTY_UNIT, qtyText(q), unit)

/** نسبة من نقاط أساس: 174 ⇒ «1.74%» · -250 ⇒ «−2.5%». عرض بس — زي `formatBp` في `core`. */
internal fun pctText(bp: Int): String {
    val abs = if (bp < 0) -bp else bp
    val frac = abs % 100
    val raw = if (frac == 0) "${abs / 100}" else "${abs / 100}." + frac.toString().padStart(2, '0').trimEnd('0')
    val local = sentenceDigits(raw)
    return (if (bp < 0) "−" else "") + uiText(UiKey.INVEST_PERCENT, local)
}

/** «12 مارس 2024». */
internal fun dateText(date: IsoDate): String = uiText(UiKey.INVEST_DATE_YEAR, dayMonth(date), sentenceNumber(parseIsoDate(date).year))

/** «اليوم» لو التاريخ النهارده، وإلا «12 مارس 2024». */
internal fun dateOrToday(date: IsoDate, today: IsoDate): String = if (date == today) uiText(UiKey.ASSET_DETAIL_TODAY) else dateText(date)

/** سنة في جملة («2030»). */
internal fun yearText(year: Int): String = sentenceNumber(year)

/** مبلغ بعملته في جملة («4,800.00 ر.س»). `null` ⇒ «غير متاح». */
internal fun moneyText(minor: Halalas?, currency: Currency): String = amountLabel(minor, currency)

/** مبلغ بإشارته («+1,200.00» · «−300.00») — المكسب والخسارة. */
internal fun signedText(minor: Halalas?, currency: Currency, showCurrency: Boolean = false): String =
    amountLabel(minor, currency, if (minor != null && minor < 0) AmountTone.EXPENSE else AmountTone.INCOME, showCurrency)

private val HIJRI_MONTHS = listOf(
    UiKey.HAWL_MONTH_1, UiKey.HAWL_MONTH_2, UiKey.HAWL_MONTH_3, UiKey.HAWL_MONTH_4, UiKey.HAWL_MONTH_5, UiKey.HAWL_MONTH_6,
    UiKey.HAWL_MONTH_7, UiKey.HAWL_MONTH_8, UiKey.HAWL_MONTH_9, UiKey.HAWL_MONTH_10, UiKey.HAWL_MONTH_11, UiKey.HAWL_MONTH_12,
)

internal fun hijriMonthName(month: Int): String = uiText(HIJRI_MONTHS[(month - 1).coerceIn(0, 11)])

/** «1 جمادى الأولى 1448». */
internal fun hijriText(h: HijriDate): String = uiText(UiKey.HAWL_DATE, sentenceNumber(h.day), hijriMonthName(h.month), sentenceNumber(h.year))

/** «1 جمادى الأولى» (من غير سنة). */
internal fun hijriDayMonth(month: Int, day: Int): String = uiText(UiKey.DATE_DAY_MONTH, sentenceNumber(day), hijriMonthName(month))

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
