package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.DebtTerms
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Settlement
import app.masroufy.core.UiKey
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.UnitOfWork

/**
 * «امسح» لدين اتكتب غلط (HANDOVER §6.5 بند 12 — المالك 2026-10-15: «كنت بجرب اكتب دين على [شخص] ومعرفتش امسحه»).
 * الدين بيتشال **بكل اللي اتكتب معاه أو عليه** — ولا رابط بيفضل بيشاور على دين مش موجود (النسخة الشاملة بترفض الرابط المقطوع):
 * - **نصيب الشخص من العملية** ([PersonAllocation]) اللي اتكتب مع الدين في نفس الخطوة (`linkToPerson` · سؤال التحويل): معرّفه `alloc-X`
 *   قصاد `obl-X`، أو (معرّفات قديمة عشوائية) نفس العملية والشخص والمبلغ. من غيره المصروف كان هيفضل ناقص نصيبه.
 * - **التسويات** (سداد/تحصيل) على الدين ⇒ بتتشال معاه، و**العمليات نفسها بتفضل** زي ما هي (الفلوس اتحركت فعلًا).
 * - **ميعاد الدين** (`DebtTerms`).
 * قاعدة Claude المحافظة (سؤال مفتوح للمالك): التسويات بتتشال مع الدين **بعد تأكيد بيعدّها** ([DebtRemoval.settlements]) بدل ما المسح
 * يترفض — مفيش شاشة تمسح تسوية لوحدها، فالرفض كان هيقفل الدين للأبد. العملية اللي الدين اتعمل منها ما بتتلمسش (نوعها قرار لوحده).
 * [restore] = «تراجع»: بيرجّع نفس المستندات بنفس معرّفاتها بالظبط.
 */
data class DebtRemoval(
    val obligation: Obligation,
    val settlements: List<Settlement>,
    val allocations: List<PersonAllocation>,
    val terms: DebtTerms?,
) {
    /** مجموع التسويات اللي هتتشال مع الدين (للتأكيد). */
    val settledMinor: Halalas get() = sumMoney(settlements.map { it.amountMinor })

    /** الدين اتعمل من عملية (العملية بتفضل — بس بترجع من غير نصيب الشخص). */
    val fromTransaction: Boolean get() = obligation.originTransactionId != null
}

data class DeleteDebtDeps(
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
    val uow: UnitOfWork,
    /** مواعيد الديون — null (اختبارات قديمة) ⇒ مفيش مواعيد تتشال. */
    val terms: DebtTermsRepository? = null,
)

class DeleteDebt(private val deps: DeleteDebtDeps) {
    /** اللي هيتشال — **من غير أي كتابة** (بيتعرض في التأكيد جوه الصفحة). */
    suspend fun preview(personId: Id, obligationId: Id): DebtRemoval {
        val mine = deps.obligations.listByPerson(personId)
        val obligation = mine.firstOrNull { it.id == obligationId } ?: throw IllegalStateException(uiText(UiKey.ERASE_DEBT_GONE))
        val settlements = deps.settlements.listByObligations(listOf(obligation.id))
        val terms = deps.terms?.listAll()?.firstOrNull { it.obligationId == obligation.id }
        return DebtRemoval(obligation, settlements, pairedAllocations(obligation, mine), terms)
    }

    /** بيمسح الدين واللي معاه ذرّيًا (نفس وحدة العمل) وبيرجّع اللي اتشال عشان «تراجع». */
    suspend fun delete(personId: Id, obligationId: Id): DebtRemoval {
        val removal = preview(personId, obligationId)
        deps.uow.run {
            if (removal.settlements.isNotEmpty()) deps.settlements.deleteMany(removal.settlements.map { it.id })
            if (removal.allocations.isNotEmpty()) deps.allocations.deleteMany(removal.allocations.map { it.id })
            if (removal.terms != null) deps.terms?.remove(removal.obligation.id)
            deps.obligations.deleteMany(listOf(removal.obligation.id))
        }
        return removal
    }

    /** «تراجع»: نفس المستندات بنفس المعرّفات — الدين الأول وبعدين اللي بيشاور عليه. */
    suspend fun restore(removal: DebtRemoval) {
        deps.uow.run {
            deps.obligations.saveMany(listOf(removal.obligation))
            if (removal.allocations.isNotEmpty()) deps.allocations.saveMany(removal.allocations)
            if (removal.settlements.isNotEmpty()) deps.settlements.saveMany(removal.settlements)
            removal.terms?.let { deps.terms?.save(it) }
        }
    }

    /**
     * نصيب الشخص اللي اتكتب مع الدين: بالمعرّف المتزاوج (`obl-X` ⇒ `alloc-X`) لو موجود، وإلا (معرّفات عشوائية) تخصيص «لك عنده» على نفس
     * العملية لنفس الشخص بنفس المبلغ — **بس لو زيادة** عن الديون التانية اللي بنفس الشكل (كل دين بياخد نصيب واحد بس).
     */
    private suspend fun pairedAllocations(obligation: Obligation, personObligations: List<Obligation>): List<PersonAllocation> {
        val txn = obligation.originTransactionId ?: return emptyList()
        val onTxn = deps.allocations.listByTransactionIds(listOf(txn))
        if (obligation.id.startsWith(OBL)) {
            val paired = onTxn.firstOrNull { it.id == ALLOC + obligation.id.removePrefix(OBL) }
            if (paired != null) return listOf(paired)
        }
        fun sameShape(a: PersonAllocation) =
            a.personId == obligation.personId && a.allocationKind == AllocationKind.RECEIVABLE && a.amountMinor == obligation.originalMinor
        val others = personObligations.filter {
            it.id != obligation.id && it.originTransactionId == txn && it.originalMinor == obligation.originalMinor
        }
        val claimed = others.filter { it.id.startsWith(OBL) }.map { ALLOC + it.id.removePrefix(OBL) }.toSet()
        val free = onTxn.filter { sameShape(it) && it.id !in claimed }
        val unclaimedOthers = others.count { o -> o.id.startsWith(OBL).not() || (ALLOC + o.id.removePrefix(OBL)) !in onTxn.map { it.id } }
        return if (free.size > unclaimedOthers) listOf(free.first()) else emptyList()
    }

    private companion object {
        const val OBL = "obl-"
        const val ALLOC = "alloc-"
    }
}
