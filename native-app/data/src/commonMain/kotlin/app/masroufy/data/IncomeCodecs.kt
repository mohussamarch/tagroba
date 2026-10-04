package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind

/**
 * مصادر الدخل (OVERRIDES §48 · §64) — مجموعة جديدة في كوتلن بس، على نفس قواعد الباقي (أسماء حقول زي الكيان · الأعداد صحيحة ·
 * الاختياري ما بيتكتبش). المصدر المقفول **ما بيتمسحش** — بيتكتب بتاريخ نهايته. مفاتيح الأطراف = الاسم + آخر 4 بس (زي `transferParties`).
 * قواعد فايربيز بتسمح بأي مجموعة تحت حساب صاحبها ⇒ مش محتاجة نشر.
 */
object IncomeCodecs {
    val incomeSources: DocCodec<IncomeSource> = codec(
        "incomeSources", { it.id },
        { s ->
            doc {
                req("id", s.id); req("name", s.name); req("normalizedName", s.normalizedName); req("kind", s.kind.wire)
                req("currency", s.currency.name); req("startedAt", s.startedAt); opt("endedAt", s.endedAt)
                opt("expectedDayOfMonth", s.expectedDayOfMonth); opt("expectedMinor", s.expectedMinor)
                opt("payerKeys", s.payerKeys.takeIf { it.isNotEmpty() }); opt("declinedPayerKeys", s.declinedPayerKeys.takeIf { it.isNotEmpty() })
                req("createdAt", s.createdAt)
            }
        },
        { d ->
            IncomeSource(
                id = d.str("id"), name = d.str("name"), normalizedName = d.str("normalizedName"), kind = d.wire("kind") { w -> IncomeSourceKind.entries.first { it.wire == w } },
                currency = d.wire("currency", Currency::valueOf), startedAt = d.str("startedAt"), endedAt = d.strOrNull("endedAt"),
                expectedDayOfMonth = d.intOrNull("expectedDayOfMonth"), expectedMinor = d.longOrNull("expectedMinor"), createdAt = d.str("createdAt"),
                payerKeys = d.strings("payerKeys").orEmpty(), declinedPayerKeys = d.strings("declinedPayerKeys").orEmpty(),
            )
        },
    )
}
