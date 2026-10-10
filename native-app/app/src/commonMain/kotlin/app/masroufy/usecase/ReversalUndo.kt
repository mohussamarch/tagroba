package app.masroufy.usecase

import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.SourceRecord
import app.masroufy.port.Clock
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository

/**
 * تراجع دفعة فيها طرف من زوج «العملية اللي رجعت» (§77-D) — بيتوصّل في `RevertDeps.undoers` (`ReturnsWiring.undoers`) وبيتنادى جوه وحدة
 * عمل التراجع قبل المسح:
 * - **الرجوع** هيتمسح والأصلية لأ ⇒ الأصلية بترجع **زي ما كانت** قبل ما تتلغي (`restoredKindOf`): النوع اللي المالك كان أكده (اتحفظ في
 *   `kindBeforeReversal` لما «أيوه نلغيهم» لغاها)، وإلا «غير محددة» تتراجع (الإلغاء لوحده بياخد النوع غير المؤكد بس).
 * - **الأصلية** هتتمسح والرجوع لأ ⇒ الرجوع بيرجع «استرداد» مقترح ويسأل (زي «ما لقيناهاش») — ولا ربط بيفضل بيشاور على عملية مش موجودة.
 * الاتنين هيتمسحوا (نفس الدفعة) ⇒ ولا حاجة. ⚠️ التراجع في **التطبيق القديم** ما بيعرفش الربط ⇒ `RepairReversals` بيصلّح بعده.
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
                if (original?.reversedById == t.id) {
                    // المالك غيّر نوع الأصلية بنفسه ⇒ نوعه بيفضل، الربط بس بيتشال
                    val patch = if (original.economicKind == EconomicKind.INTERNAL_TRANSFER) restoredOriginalPatch(original, now)
                    else TransactionPatch(clearReversedById = true, clearKindBeforeReversal = true, updatedAt = now)
                    txns.update(originalId, patch)
                }
            }
            val returnId = t.reversedById
            if (returnId != null && returnId !in gone) {
                val ret = txns.findByIds(listOf(returnId)).singleOrNull()
                if (ret?.reversalOfId == t.id) txns.update(returnId, reopenedReturnPatch(now))
            }
        }
    }
}
