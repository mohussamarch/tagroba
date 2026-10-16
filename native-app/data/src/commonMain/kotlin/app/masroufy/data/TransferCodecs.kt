package app.masroufy.data

import app.masroufy.core.AutoMeaning
import app.masroufy.core.EconomicKind
import app.masroufy.core.PERSON_LINKS_GROUP
import app.masroufy.core.PersonLink
import app.masroufy.core.PersonMeaning
import app.masroufy.core.ReviewState
import app.masroufy.core.TRANSFER_PARTIES_GROUP
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict

/**
 * «زون التحويلات» (OVERRIDES §60) — مجموعة جديدة في كوتلن بس. المعرّف = مفتاح الطرف.
 * آخر 4 أرقام بس تحت اسم `accountLast4` (نفس اسم المحفظة) — فحص النسخة الشاملة بيرفض أي حاجة غير 4 أرقام (قاعدة 11).
 */
object TransferCodecs {
    val transferParties: DocCodec<TransferParty> = codec(
        TRANSFER_PARTIES_GROUP, { it.key },
        { p ->
            doc {
                req("key", p.key); req("label", p.label); opt("accountLast4", p.last4); req("verdict", p.verdict.wire)
                opt("personId", p.personId); req("decidedAt", p.decidedAt)
                // «سجّل تحويلاته لوحده» (r05) — بيتكتبوا لو موجودين بس ⇒ المستندات القديمة زي ما هي
                opt("autoMeaning", p.autoMeaning?.wire); opt("autoSince", p.autoSince)
                // 2026-10-16: «شركة أو جهة» · «حاجة تانية» · «مستحقات دايمًا» — بتتكتب بس لو ليها قيمة (المستند القديم زي ما هو)
                opt("kind", p.kind?.wire); opt("note", p.note); opt("dues", p.dues.takeIf { it })
            }
        },
        { d ->
            TransferParty(
                d.str("key"), d.str("label"), d.strOrNull("accountLast4"), d.wire("verdict", TransferVerdict::fromWire), d.strOrNull("personId"),
                d.str("decidedAt"), d.strOrNull("autoMeaning")?.let(AutoMeaning::fromWire), d.strOrNull("autoSince"),
                kind = d.strOrNull("kind")?.let(EconomicKind::fromWire), note = d.strOrNull("note"), dues = d.boolOrNull("dues") ?: false,
            )
        },
    )

    /** ربط عملية بشخص (r05 — `PersonLinks.kt`): مستند لكل عملية، والمعرّف = معرّف العملية. */
    val personLinks: DocCodec<PersonLink> = codec(
        PERSON_LINKS_GROUP, { it.transactionId },
        { l ->
            doc {
                req("id", l.transactionId); req("transactionId", l.transactionId); req("personId", l.personId); req("meaning", l.meaning.wire)
                req("amountMinor", l.amountMinor); req("createdAt", l.createdAt); opt("obligationId", l.obligationId); opt("allocationId", l.allocationId)
                if (l.settlementIds.isNotEmpty()) req("settlementIds", l.settlementIds) else opt("settlementIds", null)
                opt("kindBefore", l.kindBefore?.wire); opt("kindConfirmedBefore", l.kindConfirmedBefore); opt("reviewBefore", l.reviewBefore?.wire)
                opt("suggestedBefore", l.suggestedBefore?.wire); opt("auto", l.auto.takeIf { it })
            }
        },
        { d ->
            PersonLink(
                d.str("transactionId"), d.str("personId"), d.wire("meaning", PersonMeaning::fromWire), d.long("amountMinor"), d.str("createdAt"),
                d.strOrNull("obligationId"), d.strOrNull("allocationId"), d.strings("settlementIds").orEmpty(),
                d.strOrNull("kindBefore")?.let(EconomicKind::fromWire), d.boolOrNull("kindConfirmedBefore"),
                d.strOrNull("reviewBefore")?.let(ReviewState::fromWire), d.strOrNull("suggestedBefore")?.let(EconomicKind::fromWire),
                d.boolOrNull("auto") ?: false,
            )
        },
    )
}
