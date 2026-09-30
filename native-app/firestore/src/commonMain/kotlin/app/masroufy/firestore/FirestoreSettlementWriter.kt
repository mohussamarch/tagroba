package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.Settlement
import app.masroufy.core.prepareSettlement
import app.masroufy.data.LedgerCodecs
import app.masroufy.data.toStore
import app.masroufy.port.SettlementWriter
import dev.gitlive.firebase.firestore.Source

/**
 * تسوية دين **ذرّيًا على السيرفر** — نقل `firestoreSettlementWriter` (`settlementWriter.ts`) خطوة خطوة:
 * 1. عدّاد مراجعة (`concurrency/settlements`) بيتقرا **من السيرفر**، ومعاه تسويات نفس الدين.
 * 2. معاملة بتتأكد إن العدّاد ما اتحركش (جهاز تاني سوّى في النص) وبتقرا الدين والطلب نفسه.
 * 3. الفحص (`prepareSettlement`): المبلغ ما يعدّيش الباقي، ونفس الطلب مرتين = نفس التسوية (مش تسوية تانية).
 * 4. لو الطلب جديد: التسوية + العدّاد+1 في نفس المعاملة. العدّاد اتحرك ⇒ محاولة تانية (لحد 8).
 * **محتاجة نت** (قرار المالك §51): القراية من السيرفر بتفشل من غيره، والزرار بيستنى.
 */
class FirestoreSettlementWriter(private val space: FirestoreSpace) : SettlementWriter {
    private val settlements = LedgerCodecs.settlements
    private val obligations = LedgerCodecs.obligations
    private fun revisionRef() = space.db.document("${space.root}/concurrency/settlements")

    private class SnapshotMoved : RuntimeException()

    override suspend fun settle(input: Settlement, personId: Id): Settlement {
        val target = space.collection(settlements.group).document(input.id)
        repeat(MAX_ATTEMPTS) {
            val revision = revisionOf(revisionRef().get(Source.SERVER).rawData())
            val rows = settlements.decodeAll(
                space.collection(settlements.group).where { "obligationId" equalTo input.obligationId }.get(Source.SERVER),
            )
            try {
                return space.db.runTransaction {
                    val current = revisionOf(get(revisionRef()).rawData())
                    if (current != revision) throw SnapshotMoved()
                    val obligation = get(space.collection(obligations.group).document(input.obligationId)).rawData()?.let(obligations::decode)
                    val prior = get(target).rawData()?.let(settlements::decode)
                    val result = prepareSettlement(input, personId, obligation, if (prior != null) listOf(prior) + rows.filter { it.id != prior.id } else rows)
                    if (prior == null) {
                        set(target, settlements.toStore(result))
                        set(revisionRef(), mapOf("revision" to revision + 1))
                    }
                    result
                }
            } catch (_: SnapshotMoved) {
                // جهاز تاني سوّى في النص — نقرا تاني من الأول
            }
        }
        throw IllegalStateException("التسويات اتغيرت أثناء الحفظ. جرّب تاني بنفس الطلب")
    }

    private fun revisionOf(doc: Map<String, Any?>?): Long {
        val value = doc?.get("revision") ?: return 0
        if (value !is Long || value < 0) throw IllegalStateException("حالة التسويات غير سليمة وتحتاج مراجعة")
        return value
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}
