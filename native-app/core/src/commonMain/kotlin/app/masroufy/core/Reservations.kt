package app.masroufy.core

/**
 * «محسوب في الميزانية» = **مبلغ محجوز لكل سطر** في التقويم (اختيار المالك §65 — مش المقترح) — وتوضيح المالك: يعني «احسب الميعاد
 * الجاي ده من الفلوس اللي معايا دلوقتي»، بضغطة واحدة من قسم الميزانيات، وفي الآخر يتقاله «فاضلك تقريبًا» (`Leftover.kt`).
 * 🔒 **علامة تخطيط بس:** الحجز **عمره ما بيغيّر** رصيد محفظة ولا ميزانية ولا مجموع شهر ولا مبلغ السطر نفسه — الدالة الوحيدة اللي
 * بتقراه هي «فاضلك تقريبًا»، وده رقم توقّع لوحده (اختبار بيثبّت ده).
 * الحجز مربوط **بمرة واحدة** من الميعاد (النوع + المصدر + التاريخ) ⇒ القسط الجاي ليه حجزه لوحده.
 */
data class Reservation(
    val id: Id,
    val itemType: CalendarItemType,
    val sourceId: Id,
    val occurrenceDate: IsoDate,
    val amountMinor: Halalas,
    val currency: Currency,
    val createdAt: String,
)

class ReservationError(message: String) : IllegalArgumentException(message)

/** معرّف ثابت من الميعاد نفسه ⇒ حجز واحد بس لكل مرة (الحجز التاني بيستبدل الأول — مش بيتجمع عليه). */
fun reservationId(type: CalendarItemType, sourceId: Id, occurrenceDate: IsoDate): Id = "rsv-${type.wire}-$sourceId-$occurrenceDate"

fun reservationIdOf(item: CalendarItem): Id = reservationId(item.type, item.sourceId, item.date)

/**
 * المبلغ اللي هيتحسب: اللي المستخدم كتبه، وإلا **مبلغ الميعاد المعروف**؛ الميعاد اللي مالوش مبلغ والمستخدم ما كتبش ⇒ خطأ
 * (ما بنخترعش رقم — القاعدة 10). وبعدين الفحص: أكبر من صفر · ميعاد جاي (النهارده أو بعده) · **مش فلوس جاية ليك**
 * (المرتب · قبض الجمعية · دين ليك) — حساب فلوس هتستلمها من اللي معاك مالوش معنى.
 */
fun countedAmount(item: CalendarItem, typedMinor: Halalas?, today: IsoDate): Halalas {
    if (item.flow == DueFlow.RECEIVE) throw ReservationError(uiText(TextKey.RESERVATION_RECEIVE))
    if (item.date < today) throw ReservationError(uiText(TextKey.RESERVATION_PAST))
    val amount = typedMinor ?: item.amountMinor ?: throw ReservationError(uiText(TextKey.RESERVATION_AMOUNT_NEEDED))
    if (amount <= 0 || amount > MAX_SAFE_HALALAS) throw ReservationError(uiText(TextKey.RESERVATION_AMOUNT))
    return amount
}

/** بيحط على كل سطر حجزه (لو موجود). **مبلغ السطر ما بيتلمسش**. */
fun applyReservations(items: List<CalendarItem>, reservations: List<Reservation>): List<CalendarItem> {
    if (reservations.isEmpty()) return items
    val byId = reservations.associateBy { it.id }
    return items.map { item -> byId[reservationIdOf(item)]?.let { item.copy(reservedMinor = it.amountMinor) } ?: item }
}

enum class ReservationState {
    NOT_RESERVED,

    /** محسوب ومبلغ الميعاد مش معروف ⇒ «محسوب X» من غير «من كام». */
    RESERVED,

    /** محسوب أقل من مبلغ الميعاد. */
    PARTIAL,

    /** محسوب قد المبلغ أو أكتر. */
    COVERED,
}

fun reservationState(item: CalendarItem): ReservationState {
    val reserved = item.reservedMinor ?: return ReservationState.NOT_RESERVED
    val amount = item.amountMinor ?: return ReservationState.RESERVED
    return if (reserved >= amount) ReservationState.COVERED else ReservationState.PARTIAL
}

/** نص السطر: «مش محسوب» · «محسوب X» · «محسوب X من Y». */
fun reservationText(item: CalendarItem): String {
    val reserved = item.reservedMinor ?: return uiText(TextKey.RESERVATION_NONE)
    val amount = item.amountMinor ?: return uiText(TextKey.RESERVATION_ONLY, formatMoney(reserved, item.currency))
    return uiText(TextKey.RESERVATION_OF, formatMoney(reserved, item.currency), formatMoney(amount, item.currency))
}
