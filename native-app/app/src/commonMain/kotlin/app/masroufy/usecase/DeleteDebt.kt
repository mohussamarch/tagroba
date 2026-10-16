package app.masroufy.usecase

import app.masroufy.core.AllocationKind
import app.masroufy.core.DebtTerms
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.PersonAllocation
import app.masroufy.core.PersonLink
import app.masroufy.core.Settlement
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.sumMoney
import app.masroufy.core.uiText
import app.masroufy.port.AllocationRepository
import app.masroufy.port.Clock
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonLinkRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * «امسح» لدين اتكتب غلط (HANDOVER §6.5 بند 12 — المالك 2026-10-15: «كنت بجرب اكتب دين على [شخص] ومعرفتش امسحه»).
 * الدين بيتشال **بكل اللي اتكتب معاه أو عليه** — ولا رابط بيفضل بيشاور على دين مش موجود (النسخة الشاملة بترفض الرابط المقطوع):
 * - **نصيب الشخص من العملية** ([PersonAllocation]) اللي اتكتب مع الدين في نفس الخطوة (`linkToPerson` · سؤال التحويل): معرّفه `alloc-X`
 *   قصاد `obl-X`، أو (معرّفات قديمة عشوائية) نفس العملية والشخص والمبلغ. من غيره المصروف كان هيفضل ناقص نصيبه.
 * - **التسويات** (سداد/تحصيل) على الدين ⇒ بتتشال معاه، و**العمليات نفسها بتفضل** زي ما هي (الفلوس اتحركت فعلًا).
 * - **ميعاد الدين** (`DebtTerms`).
 * - **ربط العملية بالشخص** (`PersonLink` — r05 · F1): الربط اللي عمل الدين أو النصيب بيتشال ونوع العملية بيرجع زي ما كان قبل الربط؛ الربط
 *   اللي سدّد من الدين ده بس بيفضل من غير السدادات دي. من غيره النسخة الشاملة كانت بترفض (`BACKUP_RELATION_MISSING`).
 * قاعدة Claude المحافظة (سؤال مفتوح للمالك): التسويات بتتشال مع الدين **بعد تأكيد بيعدّها** ([DebtRemoval.settlements]) بدل ما المسح
 * يترفض — مفيش شاشة تمسح تسوية لوحدها، فالرفض كان هيقفل الدين للأبد. العملية اللي الدين اتعمل منها ما بتتلمسش (نوعها قرار لوحده).
 * [restore] = «تراجع»: بيرجّع نفس المستندات بنفس معرّفاتها بالظبط.
 */
data class DebtRemoval(
    val obligation: Obligation,
    val settlements: List<Settlement>,
    val allocations: List<PersonAllocation>,
    val terms: DebtTerms?,
    /** روابط الأشخاص زي ما كانت قبل المسح ([DeleteDebt.restore] بيرجّعها بالظبط). */
    val links: List<PersonLink> = emptyList(),
    /** العمليات اللي نوعها رجع لما ربطها اتشال — زي ما كانت قبل المسح. */
    val transactionsBefore: List<Transaction> = emptyList(),
    /** حقيقة الزكاة اللي على الدين (R3 — بتتشال معاه وبترجع مع «تراجع»). */
    val zakatFact: app.masroufy.core.ZakatFact? = null,
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
    /** روابط الأشخاص (r05 · F1) — null (اختبارات قديمة) ⇒ مفيش روابط. */
    val links: PersonLinkRepository? = null,
    val txns: TransactionRepository? = null,
    val clock: Clock? = null,
    /** حقايق الزكاة (R3) — null (اختبارات قديمة) ⇒ مفيش. */
    val zakatFacts: app.masroufy.port.ZakatFactRepository? = null,
)

class DeleteDebt(private val deps: DeleteDebtDeps) {
    /** اللي هيتشال — **من غير أي كتابة** (بيتعرض في التأكيد جوه الصفحة). */
    suspend fun preview(personId: Id, obligationId: Id): DebtRemoval {
        val mine = deps.obligations.listByPerson(personId)
        val obligation = mine.firstOrNull { it.id == obligationId } ?: throw IllegalStateException(uiText(UiKey.ERASE_DEBT_GONE))
        val settlements = deps.settlements.listByObligations(listOf(obligation.id))
        val terms = deps.terms?.listAll()?.firstOrNull { it.obligationId == obligation.id }
        val paired = pairedAllocations(obligation, mine)
        val links = linksTouching(obligation, settlements, paired)
        // النصيب اللي ربط الشراء كتبه مع الدين بيتشال معاه حتى لو معرّفه مش متزاوج
        val linkAllocations = links.filter { it.obligationId == obligation.id }.mapNotNull { it.allocationId }.filter { id -> paired.none { it.id == id } }
        val allocations = paired + if (linkAllocations.isEmpty()) emptyList() else deps.allocations.listByTransactionIds(links.map { it.transactionId }).filter { it.id in linkAllocations }
        // العمليات اللي الدين اتعمل منها أو ربطها اتشال: نوعها بيرجع وبتتعلّم إن القواعد ما ترجعش عليها (N2) ⇒ بتتحفظ زي ما هي لـ«تراجع»
        val touched = (links.filter { owns(it, obligation, allocations) }.map { it.transactionId } + listOfNotNull(obligation.originTransactionId)).distinct()
        val before = if (touched.isEmpty()) emptyList() else deps.txns?.findByIds(touched).orEmpty()
        val fact = deps.zakatFacts?.listAll()?.firstOrNull { it.subjectId == obligation.id }
        return DebtRemoval(obligation, settlements, allocations, terms, links, before, fact)
    }

    /** بيمسح الدين واللي معاه ذرّيًا (نفس وحدة العمل) وبيرجّع اللي اتشال عشان «تراجع». */
    suspend fun delete(personId: Id, obligationId: Id): DebtRemoval {
        val removal = preview(personId, obligationId)
        deps.uow.run {
            if (removal.settlements.isNotEmpty()) deps.settlements.deleteMany(removal.settlements.map { it.id })
            if (removal.allocations.isNotEmpty()) deps.allocations.deleteMany(removal.allocations.map { it.id })
            if (removal.terms != null) deps.terms?.remove(removal.obligation.id)
            if (removal.zakatFact != null) deps.zakatFacts?.remove(removal.obligation.id)
            deps.obligations.deleteMany(listOf(removal.obligation.id))
            val gone = removal.settlements.map { it.id }.toSet()
            for (l in removal.links) {
                if (owns(l, removal.obligation, removal.allocations)) {
                    deps.links?.deleteMany(listOf(l.transactionId))
                    deps.txns?.let { revertLinkKind(it, l, deps.clock?.nowIso()) }
                } else deps.links?.saveMany(listOf(l.copy(settlementIds = l.settlementIds.filter { it !in gone })))
            }
            // N2: المالك مسح الدين ⇒ «سجّل تحويلاته لوحده» ما ترجعش تعمله تاني من نفس العملية (لما التفاصيل تتفتح أو القاعدة تتشغل)
            deps.txns?.let { txns -> for (t in removal.transactionsBefore) markRulesOff(txns, t.id, deps.clock?.nowIso()) }
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
            removal.zakatFact?.let { deps.zakatFacts?.save(it) }
            if (removal.links.isNotEmpty()) deps.links?.saveMany(removal.links)
            if (removal.transactionsBefore.isNotEmpty()) deps.txns?.saveMany(removal.transactionsBefore)
        }
    }

    /** روابط الأشخاص اللي بتشاور على الدين ده أو نصيبه أو سداداته. */
    private suspend fun linksTouching(obligation: Obligation, settlements: List<Settlement>, allocations: List<PersonAllocation>): List<PersonLink> {
        val repo = deps.links ?: return emptyList()
        val txnIds = (listOfNotNull(obligation.originTransactionId) + settlements.map { it.transactionId } + allocations.map { it.transactionId })
            .filter { it.isNotEmpty() }.distinct()
        if (txnIds.isEmpty()) return emptyList()
        val stl = settlements.map { it.id }.toSet()
        return repo.listByTransactionIds(txnIds).filter { l ->
            l.obligationId == obligation.id || allocations.any { it.id == l.allocationId } || l.settlementIds.any { it in stl }
        }
    }

    /** الربط ده هو اللي عمل الدين أو النصيب ⇒ بيتشال كله (مش بس سداداته). */
    private fun owns(l: PersonLink, obligation: Obligation, allocations: List<PersonAllocation>): Boolean =
        l.obligationId == obligation.id || allocations.any { it.id == l.allocationId }

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
