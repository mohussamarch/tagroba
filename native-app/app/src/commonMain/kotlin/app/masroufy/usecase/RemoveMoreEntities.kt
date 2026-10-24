package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.R06Key
import app.masroufy.core.uiText

/**
 * r06-links2 (OVERRIDES §118-1 · §118-ج): المسح باللوحة الواحدة و«تراجع» للحاجات اللي كان ناقصها:
 * - **السداد الغلط** في صفحة الدين: السداد بس بيتشال — العملية نفسها بتفضل (فلوس اتحركت فعلًا) والدين بيرجع مفتوح بمبلغه. مالوش عمليات خاصة ⇒ «امسح».
 * - **خطة الادخار**: كانت أرشفة بس. «أرشفه» (زي الأول) · «امسح» (الخطة وإيداعاتها اليدوية — الإيداع سجل يدوي مش حركة بنكية، فمفيش عمليات بتتمسح).
 * - **القاعدة**: كانت إيقاف بس. القاعدة مالهاش عمليات (التصنيف اللي اتعمل بيها بيفضل) ⇒ «امسح» على طول.
 */
class RemoveSettlement internal constructor(private val d: RemovalDeps) : EntityRemover {
    override suspend fun ask(id: Id): RemovalAsk {
        d.settlementById(id) ?: removalGone()
        return askOf(uiText(R06Key.L2_ST_THIS), 0, emptyList())
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val s = d.settlementById(id) ?: removalGone()
        d.settlements.deleteMany(listOf(s.id))
        return RemovalDone(RemoveChoice.DELETE, uiText(R06Key.L2_ST_THIS)) { d.settlements.saveMany(listOf(s)) }
    }
}

/** السداد بمعرّفه — من غير قراية كل السدادات: الشاشة بتبعت معرّف الدين في [settlementKey] («دين|سداد»). */
internal suspend fun RemovalDeps.settlementById(key: Id): app.masroufy.core.Settlement? {
    val (obligationId, settlementId) = key.split('|').let { if (it.size == 2) it[0] to it[1] else return null }
    return settlements.listByObligations(listOf(obligationId)).firstOrNull { it.id == settlementId }
}

/** مفتاح السداد للوحة المسح: معرّف الدين ومعرّف السداد. */
fun settlementKey(obligationId: Id, settlementId: Id): Id = "$obligationId|$settlementId"

class RemoveSavingsGoal internal constructor(private val d: RemovalDeps) : EntityRemover {
    override suspend fun ask(id: Id): RemovalAsk {
        val goals = d.goals ?: removalGone()
        val g = goals.listAll().firstOrNull { it.id == id } ?: removalGone()
        // الإيداعات سجلات يدوية جوه الخطة (مش عمليات) ⇒ «أرشفه» أو «امسح» بس
        return RemovalAsk(g.name, 0, listOf(RemoveChoice.ARCHIVE, RemoveChoice.DELETE))
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val goals = d.goals ?: removalGone()
        val g = goals.listAll().firstOrNull { it.id == id } ?: removalGone()
        if (choice == RemoveChoice.ARCHIVE) {
            goals.save(g.copy(archived = true))
            return RemovalDone(choice, g.name) { goals.save(g) }
        }
        val contributions = d.goalContributions?.listAll().orEmpty().filter { it.goalId == id }
        contributions.forEach { d.goalContributions?.remove(it.id) }
        goals.delete(id)
        return RemovalDone(RemoveChoice.DELETE, g.name) {
            goals.save(g)
            contributions.forEach { d.goalContributions?.save(it) }
        }
    }
}

class RemoveRule internal constructor(private val d: RemovalDeps) : EntityRemover {
    override suspend fun ask(id: Id): RemovalAsk {
        val rules = d.rules ?: removalGone()
        val r = rules.listAll().firstOrNull { it.id == id } ?: removalGone()
        return askOf(r.matchText, 0, emptyList())
    }

    override suspend fun remove(id: Id, choice: RemoveChoice, moveTo: Id?): RemovalDone {
        val rules = d.rules ?: removalGone()
        val r = rules.listAll().firstOrNull { it.id == id } ?: removalGone()
        rules.deleteMany(listOf(id))
        return RemovalDone(RemoveChoice.DELETE, r.matchText) { rules.saveMany(listOf(r)) }
    }
}
