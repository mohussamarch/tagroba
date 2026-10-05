package app.masroufy.data

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
            }
        },
        { d -> TransferParty(d.str("key"), d.str("label"), d.strOrNull("accountLast4"), d.wire("verdict", TransferVerdict::fromWire), d.strOrNull("personId"), d.str("decidedAt")) },
    )
}
