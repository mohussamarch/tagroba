package app.masroufy.data

import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatCollectability
import app.masroufy.core.ZakatFact
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPayment
import app.masroufy.core.ZakatPurpose
import app.masroufy.core.ZakatShareHolding
import app.masroufy.core.ZakatSubject
import app.masroufy.core.ZakatYear
import app.masroufy.core.ZakatYearLine
import app.masroufy.core.uiText

/**
 * الزكاة (OVERRIDES §62) — مجموعات جديدة في كوتلن بس، على نفس قواعد الباقي (أسماء حقول زي الكيان · الأعداد صحيحة ·
 * الاختياري ما بيتكتبش). قواعد فايربيز بتسمح بأي مجموعة تحت حساب صاحبها ⇒ مش محتاجة نشر.
 * الواقعة بتكتب معرّف الأصل أو الدين في حقله (`assetId` / `obligationId`) عشان علاقات النسخة الشاملة تتفحص.
 */
object ZakatCodecs {
    private fun <E> DocReader.wireOrNull(name: String, parse: (String) -> E): E? = if (has(name)) wire(name, parse) else null

    private fun lineOf(group: String, raw: String): ZakatLineKind =
        runCatching { ZakatLineKind.fromWire(raw) }.getOrElse { throw DocumentError(uiText(TextKey.DOC_FIELD_VALUE, group, "lines", raw)) }

    val zakatFacts: DocCodec<ZakatFact> = codec(
        "zakatFacts", { it.subjectId },
        { f ->
            doc {
                req("id", f.subjectId); req("subject", f.subject.wire)
                opt("assetId", f.subjectId.takeIf { f.subject == ZakatSubject.ASSET })
                opt("obligationId", f.subjectId.takeIf { f.subject == ZakatSubject.OBLIGATION })
                opt("purpose", f.purpose?.wire); opt("holding", f.holding?.wire); opt("collectability", f.collectability?.wire)
                opt("karat", f.karat); opt("fineness", f.fineness); req("updatedAt", f.updatedAt)
            }
        },
        { d ->
            val subject = d.wire("subject", ZakatSubject::fromWire)
            ZakatFact(
                subjectId = (if (subject == ZakatSubject.ASSET) d.strOrNull("assetId") else d.strOrNull("obligationId")) ?: d.str("id"),
                subject = subject,
                purpose = d.wireOrNull("purpose", ZakatPurpose::fromWire),
                holding = d.wireOrNull("holding", ZakatShareHolding::fromWire),
                collectability = d.wireOrNull("collectability", ZakatCollectability::fromWire),
                karat = d.intOrNull("karat"), fineness = d.intOrNull("fineness"), updatedAt = d.str("updatedAt"),
            )
        },
    )

    /** السنة المفتوحة من غير سطور ولا نصاب؛ المتثبّتة بسطورها كما اتحسبت. */
    val zakatYears: DocCodec<ZakatYear> = codec(
        "zakatYears", { it.id },
        { y ->
            doc {
                req("id", y.id); req("hawlStart", y.hawlStart); req("dueAt", y.dueAt); req("currency", y.currency.name)
                req("confirmedAt", y.confirmedAt); opt("closedAt", y.closedAt); opt("nisabMinor", y.nisabMinor)
                if (y.lines.isNotEmpty()) req("lines", y.lines.map { l -> doc { req("kind", l.kind.wire); req("zakatableMinor", l.zakatableMinor); req("dueMinor", l.dueMinor) } })
                else opt("lines", null)
            }
        },
        { d ->
            ZakatYear(
                d.str("id"), d.str("hawlStart"), d.str("dueAt"), d.wire("currency", Currency::valueOf), d.str("confirmedAt"),
                d.strOrNull("closedAt"), d.longOrNull("nisabMinor"),
                d.maps("lines").map { l -> ZakatYearLine(lineOf("zakatYears", l.str("kind")), l.long("zakatableMinor"), l.long("dueMinor")) },
            )
        },
    )

    /** الدفعة الكاش من غير عملية: `transactionId` ما بيتكتبش. */
    val zakatPayments: DocCodec<ZakatPayment> = codec(
        "zakatPayments", { it.id },
        { p ->
            doc {
                req("id", p.id); req("yearId", p.yearId); opt("transactionId", p.transactionId); req("amountMinor", p.amountMinor)
                req("lines", p.lines.map { it.wire }); req("paidAt", p.paidAt); req("createdAt", p.createdAt)
            }
        },
        { d ->
            ZakatPayment(
                d.str("id"), d.str("yearId"), d.strOrNull("transactionId"), d.long("amountMinor"),
                d.strings("lines").orEmpty().map { lineOf("zakatPayments", it) }, d.str("paidAt"), d.str("createdAt"),
            )
        },
    )
}
