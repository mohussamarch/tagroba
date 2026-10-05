package app.masroufy.data

import app.masroufy.core.EVENT_SHARE_WHOLE
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind

/**
 * الأحداث وروابطها ومناسبات الشخص (OVERRIDES §64) — مجموعات جديدة في كوتلن بس، على نفس قواعد الباقي (أسماء حقول زي
 * الكيان · الأعداد صحيحة · الاختياري ما بيتكتبش). قواعد فايربيز بتسمح بأي مجموعة تحت حساب صاحبها (`users/{uid}` وكل اللي تحته)
 * ⇒ مش محتاجة نشر.
 */
object EventCodecs {
    val lifeEvents: DocCodec<LifeEvent> = codec(
        "lifeEvents", { it.id },
        { e ->
            doc {
                req("id", e.id); req("name", e.name); req("normalizedName", e.normalizedName); req("kind", e.kind.wire)
                req("date", e.date); req("mine", e.mine); opt("hostPersonId", e.hostPersonId); req("archived", e.archived)
                req("createdAt", e.createdAt)
            }
        },
        { d ->
            LifeEvent(
                d.str("id"), d.str("name"), d.str("normalizedName"), d.wire("kind", LifeEventKind::fromWire), d.str("date"),
                d.bool("mine"), d.strOrNull("hostPersonId"), d.bool("archived"), d.str("createdAt"),
            )
        },
    )

    /**
     * `sharePercent` (§64) بيتكتب دايمًا — النسبة متخزنة ومش بتتحسب من جديد. المستند القديم من غيرها = 100 (العملية كلها).
     */
    val eventLinks: DocCodec<EventLink> = codec(
        "eventLinks", { it.id },
        { l ->
            doc {
                req("id", l.id); req("eventId", l.eventId); req("transactionId", l.transactionId); req("role", l.role.wire)
                opt("personId", l.personId); req("createdAt", l.createdAt); req("sharePercent", l.sharePercent)
                opt("prepItemId", l.prepItemId)
            }
        },
        { d ->
            EventLink(
                d.str("id"), d.str("eventId"), d.str("transactionId"), d.wire("role", EventRole::fromWire), d.strOrNull("personId"),
                d.str("createdAt"), d.intOrNull("sharePercent") ?: EVENT_SHARE_WHOLE, d.strOrNull("prepItemId"),
            )
        },
    )

    /** مناسبتك إنت: `personId` ما بيتكتبش. السنة والمدة والاسم اختياريين. */
    val occasions: DocCodec<Occasion> = codec(
        "occasions", { it.id },
        { o ->
            doc {
                req("id", o.id); opt("personId", o.personId); req("kind", o.kind.wire); opt("label", o.label)
                req("month", o.month); req("day", o.day); opt("year", o.year); req("yearly", o.yearly); opt("leadDays", o.leadDays)
                opt("sourceEventId", o.sourceEventId); req("createdAt", o.createdAt)
            }
        },
        { d ->
            Occasion(
                d.str("id"), d.strOrNull("personId"), d.wire("kind", OccasionKind::fromWire), d.strOrNull("label"),
                d.int("month"), d.int("day"), d.intOrNull("year"), d.bool("yearly"), d.intOrNull("leadDays"),
                d.strOrNull("sourceEventId"), d.str("createdAt"),
            )
        },
    )
}
