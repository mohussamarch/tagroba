package app.masroufy.data

import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.PrepItem
import app.masroufy.core.Reservation

/**
 * المبالغ المحجوزة في التقويم وبنود تجهيز الأحداث (OVERRIDES §65) — مجموعات كوتلن بس، على نفس قواعد الباقي (أسماء الحقول زي
 * الكيان · الأعداد صحيحة · الاختياري ما بيتكتبش). قواعد فايربيز بتسمح بأي مجموعة تحت حساب صاحبها ⇒ مش محتاجة نشر.
 */
object CalendarCodecs {
    val reservations: DocCodec<Reservation> = codec(
        "reservations", { it.id },
        { r ->
            doc {
                req("id", r.id); req("itemType", r.itemType.wire); req("sourceId", r.sourceId); req("occurrenceDate", r.occurrenceDate)
                req("amountMinor", r.amountMinor); req("currency", r.currency.name); req("createdAt", r.createdAt)
            }
        },
        { d ->
            Reservation(
                d.str("id"), d.wire("itemType", CalendarItemType::fromWire), d.str("sourceId"), d.str("occurrenceDate"),
                d.long("amountMinor"), d.wire("currency", Currency::valueOf), d.str("createdAt"),
            )
        },
    )

    /** البند من غير مبلغ: `plannedMinor` ما بيتكتبش (مش صفر). */
    val eventPrep: DocCodec<PrepItem> = codec(
        "eventPrep", { it.id },
        { p ->
            doc {
                req("id", p.id); req("eventId", p.eventId); req("name", p.name); opt("plannedMinor", p.plannedMinor)
                req("order", p.order); req("done", p.done); req("createdAt", p.createdAt)
            }
        },
        { d -> PrepItem(d.str("id"), d.str("eventId"), d.str("name"), d.longOrNull("plannedMinor"), d.int("order"), d.bool("done"), d.str("createdAt")) },
    )
}
