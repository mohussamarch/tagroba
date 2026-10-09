package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.sentenceNumber
import app.masroufy.core.spaceTransferRateText
import app.masroufy.ui.text.t

/** رجل في زوج: البلد · طلعت/وصلت · المبلغ بعملتها. */
data class LegView(val mark: String, val title: String, val amountMinor: Halalas, val currency: Currency, val out: Boolean)

data class PairView(val id: Id, val route: String, val legs: List<LegView>, val rate: String, val note: String?)

/** اتجاه في «تحويل جديد لنفسك»: من بلد لبلد. */
data class Direction2(val from: Space, val to: Space) {
    val key: String get() = from.id + ">" + to.id
}

/** أول حرف من اسم البلد (العلامة في المربع). */
fun spaceMark(space: Space?): String = space?.name?.trim()?.take(1).orEmpty()

/** «من السعودية إلى مصر». */
fun routeName(from: Space?, to: Space?): String = t(TextKey.SPACE_TRANSFER_SCREEN_ROUTE, from?.name.orEmpty(), to?.name.orEmpty())

/**
 * السعر للعرض بس من المبلغين (`spaceTransferRateText` من `core` — أعداد صحيحة و٤ أرقام والتقريب نص لفوق) ⇒ «12.8200 ج.م لكل ١ ر.س».
 * مش بيتخزن ومش بيحوّل أي رقم تاني. مبلغ صفر أو ناقص ⇒ `null`.
 */
fun rateLine(fromMinor: Halalas?, fromCurrency: Currency, toMinor: Halalas?, toCurrency: Currency): String? {
    if (fromMinor == null || toMinor == null || fromMinor <= 0 || toMinor <= 0) return null
    val text = spaceTransferRateText(SpaceTransfer("", "", "", fromMinor, fromCurrency, "", "", toMinor, toCurrency, ""))
    val number = text.substringAfter("= ").substringBefore(' ')
    return t(TextKey.SPACE_TRANSFER_SCREEN_RATE_LINE, number, currencySymbol(toCurrency), sentenceNumber(1), currencySymbol(fromCurrency))
}

/**
 * الأزواج (`TransferBetweenSpaces.list`) ⇒ كروت. ⚠️ المحفظة وتاريخ كل رجل في عمليتها جوه بلدها — مفيش حالة استخدام بتقرا عملية من بلد تانية
 * ⇒ الكارت بيعرض البلد والمبلغ بس.
 */
fun pairViews(pairs: List<SpaceTransfer>, spaces: List<Space>): List<PairView> = pairs.map { p ->
    val from = spaces.firstOrNull { it.id == p.fromSpaceId }
    val to = spaces.firstOrNull { it.id == p.toSpaceId }
    PairView(
        id = p.id,
        route = routeName(from, to),
        legs = listOf(
            LegView(spaceMark(from), t(TextKey.SPACE_TRANSFER_SCREEN_LEFT, from?.name.orEmpty()), p.fromAmountMinor, p.fromCurrency, out = true),
            LegView(spaceMark(to), t(TextKey.SPACE_TRANSFER_SCREEN_ARRIVED, to?.name.orEmpty()), p.toAmountMinor, p.toCurrency, out = false),
        ),
        rate = rateLine(p.fromAmountMinor, p.fromCurrency, p.toAmountMinor, p.toCurrency).orEmpty(),
        note = p.note?.takeIf { it.isNotBlank() }?.let { t(TextKey.SPACE_TRANSFER_SCREEN_NOTE_LINE, it) },
    )
}

/** الاتجاهات الممكنة بين البلاد المفتوحة (البلد الشغالة الأول). */
fun directions(spaces: List<Space>, activeId: String): List<Direction2> {
    val ordered = spaces.sortedBy { if (it.id == activeId) 0 else 1 }
    return ordered.flatMap { a -> ordered.filter { it.id != a.id }.map { b -> Direction2(a, b) } }
}
