package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.SourceRecord
import app.masroufy.port.Clock
import app.masroufy.port.TransactionRepository

/**
 * تراجع دفعة فيها طرف من زوج «العملية اللي رجعت» (§77-D) — بيتوصّل في `RevertDeps.undoers` وبيتنادى جوه وحدة عمل التراجع قبل المسح:
 * - **الرجوع** هيتمسح والأصلية لأ ⇒ الأصلية بترجع زي ما كانت قبل ما تتلغي لوحدها: «غير محددة» · مش مؤكدة · محتاجة مراجعة · من غير ربط.
 * - **الأصلية** هتتمسح والرجوع لأ ⇒ الرجوع بيرجع «استرداد» مقترح ويسأل (زي «ما لقيناهاش») — ولا ربط بيفضل بيشاور على عملية مش موجودة
 *   (النسخة الشاملة كانت هترفض العلاقة).
 * الاتنين هيتمسحوا (نفس الدفعة) ⇒ ولا حاجة.
 */
class ReversalUndo(private val txns: TransactionRepository, private val clock: Clock) : BatchUndo {
    override suspend fun undo(batchId: Id, records: List<SourceRecord>, deleting: List<Id>) {
        if (deleting.isEmpty()) return
        val gone = deleting.toSet()
        val now = clock.nowIso()
        for (t in txns.findByIds(deleting)) {
            val originalId = t.reversalOfId
            if (originalId != null && originalId !in gone) {
                val original = txns.findByIds(listOf(originalId)).singleOrNull()
                if (original?.reversedById == t.id) txns.update(originalId, restoredOriginalPatch(now))
            }
            val returnId = t.reversedById
            if (returnId != null && returnId !in gone) {
                val ret = txns.findByIds(listOf(returnId)).singleOrNull()
                if (ret?.reversalOfId == t.id) txns.update(returnId, reopenedReturnPatch(now))
            }
        }
    }
}
