package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Space
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.currencySymbol
import app.masroufy.core.dayMonth
import app.masroufy.core.sentenceNumber
import app.masroufy.core.spaceTransferRateText
import app.masroufy.ui.text.t

/**
 * رجل في زوج: علامة البلد · [title] المحفظة (أو «خرجت من السعودية» لو عملية الرجل ما اتقرتش) · [sub] «خرجت، 5 أكتوبر» · المبلغ بعملتها
 * زي ما هو في الزوج.
 */
data class LegView(val mark: String, val title: String, val sub: String?, val amountMinor: Halalas, val currency: Currency, val out: Boolean)

data class PairView(val id: Id, val route: String, val legs: List<LegView>, val rate: String, val note: String?)

/** اتجاه في «تحويل جديد لنفسك»: من بلد لبلد. */
data class Direction2(val from: Space, val to: Space) {
    val key: String get() = from.id + ">" + to.id
}

/** أول حرف من اسم البلد من غير «ال» (العلامة في المربع — النموذج: السعودية «س» · مصر «م»). */
fun spaceMark(space: Space?): String {
    val name = space?.name?.trim().orEmpty()
    return (if (name.startsWith("ال") && name.length > 2) name.drop(2) else name).take(1)
}

/** «من السعودية إلى مصر». */
fun routeName(from: Space?, to: Space?): String = t(UiKey.SPACE_TRANSFER_SCREEN_ROUTE, from?.name.orEmpty(), to?.name.orEmpty())

/**
 * السعر للعرض بس من المبلغين (`spaceTransferRateText` من `core` — نص العرض اللي الدومين نفسه بيطلّعه: أعداد صحيحة و4 أرقام والتقريب نص لفوق)
 * ⇒ «12.8200 ج.م لكل 1 ر.س». مش بيتخزن ومش بيحوّل أي رقم تاني. مبلغ صفر أو ناقص ⇒ `null`.
 */
fun rateLine(fromMinor: Halalas?, fromCurrency: Currency, toMinor: Halalas?, toCurrency: Currency): String? {
    if (fromMinor == null || toMinor == null || fromMinor <= 0 || toMinor <= 0) return null
    val text = spaceTransferRateText(SpaceTransfer("", "", "", fromMinor, fromCurrency, "", "", toMinor, toCurrency, ""))
    val number = text.substringAfter("= ").substringBefore(' ')
    return t(UiKey.SPACE_TRANSFER_SCREEN_RATE_LINE, number, currencySymbol(toCurrency), sentenceNumber(1), currencySymbol(fromCurrency))
}

/**
 * الأزواج (`TransferBetweenSpaces.list`) ⇒ كروت. [legs] عمليات الرجلين من بلدها (`OperationsDeps.legOf`) و[books] محافظ كل بلد — رجل
 * ما اتقرتش (البلد مش مفتوحة) ⇒ البلد والمبلغ بس.
 */
fun pairViews(pairs: List<SpaceTransfer>, books: List<SpaceWallets>, legs: Map<Id, Transaction> = emptyMap()): List<PairView> = pairs.map { p ->
    val from = books.firstOrNull { it.space.id == p.fromSpaceId }
    val to = books.firstOrNull { it.space.id == p.toSpaceId }
    PairView(
        id = p.id,
        route = routeName(from?.space, to?.space),
        legs = listOf(
            legView(from, legs[p.fromTransactionId], p.fromAmountMinor, p.fromCurrency, out = true),
            legView(to, legs[p.toTransactionId], p.toAmountMinor, p.toCurrency, out = false),
        ),
        rate = rateLine(p.fromAmountMinor, p.fromCurrency, p.toAmountMinor, p.toCurrency).orEmpty(),
        note = p.note?.takeIf { it.isNotBlank() }?.let { t(UiKey.SPACE_TRANSFER_SCREEN_NOTE_LINE, it) },
    )
}

private fun legView(book: SpaceWallets?, tx: Transaction?, amount: Halalas, currency: Currency, out: Boolean): LegView {
    val name = book?.space?.name.orEmpty()
    val wallet = tx?.walletId?.let { id -> book?.wallets?.firstOrNull { it.id == id }?.name }
    val title = wallet ?: t(if (out) UiKey.SPACE_TRANSFER_SCREEN_LEFT else UiKey.SPACE_TRANSFER_SCREEN_ARRIVED, name)
    val sub = tx?.let { t(if (out) UiKey.SPACE_TRANSFER_SCREEN_LEG_OUT else UiKey.SPACE_TRANSFER_SCREEN_LEG_IN, dayMonth(it.occurredAt)) }
    return LegView(spaceMark(book?.space), title, sub, amount, currency, out)
}

/** الاتجاهات الممكنة بين البلاد المفتوحة (البلد الشغالة الأول). */
fun directions(spaces: List<Space>, activeId: String): List<Direction2> {
    val ordered = spaces.sortedBy { if (it.id == activeId) 0 else 1 }
    return ordered.flatMap { a -> ordered.filter { it.id != a.id }.map { b -> Direction2(a, b) } }
}

/** حالة الشاشة: بيحمّل · خطأ · غير متاح (بلد واحدة بس) · فاضي · عادي. */
enum class SpaceTransferState { LOADING, FAILED, ONE_SPACE, EMPTY, READY }

fun spaceTransferState(books: List<SpaceWallets>?, pairs: List<PairView>?, failed: Boolean): SpaceTransferState = when {
    failed -> SpaceTransferState.FAILED
    books == null || pairs == null -> SpaceTransferState.LOADING
    books.size < 2 -> SpaceTransferState.ONE_SPACE
    pairs.isEmpty() -> SpaceTransferState.EMPTY
    else -> SpaceTransferState.READY
}
