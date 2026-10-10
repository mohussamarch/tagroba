package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.PersonAllocation
import app.masroufy.core.Settlement
import app.masroufy.core.TransactionLedgerLinks
import app.masroufy.core.isTransferAskSettlement
import app.masroufy.core.transferAskAllocationId
import app.masroufy.core.transferAskObligationId
import app.masroufy.port.AllocationRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository

/**
 * دفتر الأشخاص اللي أسئلة التحويل (§75-5 «سلفة ولا دعم؟» · §75-9 «ده سداد؟») بتقرا منه وبتكتب فيه — نفس مستندات التطبيق القديم
 * (`obligations` · `settlements` · `allocations`) من غير أي حقل جديد. اللي السؤال كتبه بيتعرف بمعرّفاته الثابتة (`core/TransferAsks.kt`).
 */
data class PersonLedgerRepos(
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val allocations: AllocationRepository,
) {
    /** روابط العمليات دي في الدفتر — 3 قرايات للكل مهما كان العدد. */
    suspend fun linksOf(txnIds: List<Id>): Map<Id, TransactionLedgerLinks> {
        val ids = txnIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        val shares = allocations.listByTransactionIds(ids).groupBy { it.transactionId }
        val paid = settlements.listByTransactionIds(ids).groupBy { it.transactionId }
        val made = obligations.listByTransactionIds(ids).groupBy { it.originTransactionId }
        return ids.associateWith { TransactionLedgerLinks(shares[it].orEmpty(), paid[it].orEmpty(), made[it].orEmpty()) }
    }

    /**
     * اللي إجابات السؤال كتبته على العمليات اللي روابطها [links] وبطل صح: [withLoans] = «سلفة» (الدين ونصيب الشخص) كمان ·
     * [repayment] = أنهي تسوية «أيوه، سداد» تتشال. [AskWrites.blocking] = تسويات على سلفة منهم **مش** داخلة في الشيل.
     */
    suspend fun askWrites(links: Collection<TransactionLedgerLinks>, withLoans: Boolean, repayment: (Settlement) -> Boolean = { true }): AskWrites {
        val loans = if (!withLoans) emptyList() else links.flatMap { l -> l.originated.filter { o -> o.originTransactionId?.let { transferAskObligationId(it) } == o.id } }
        val shares = if (!withLoans) emptyList() else links.flatMap { l -> l.allocations.filter { it.id == transferAskAllocationId(it.transactionId) } }
        val repayments = links.flatMap { l -> l.settlements.filter { isTransferAskSettlement(it) && repayment(it) } }
        val removed = repayments.map { it.id }.toSet()
        val blocking = if (loans.isEmpty()) emptyList() else settlements.listByObligations(loans.map { it.id }).filter { it.id !in removed }
        return AskWrites(loans, shares, repayments, blocking)
    }

    /** الشيل: التسويات الأول وبعدين الدين ونصيبه — لو اتقطع في النص، نفس الطلب تاني بيلاقي الباقي ويشيله. */
    suspend fun remove(writes: AskWrites) {
        if (writes.repayments.isNotEmpty()) settlements.deleteMany(writes.repayments.map { it.id })
        if (writes.loans.isNotEmpty()) obligations.deleteMany(writes.loans.map { it.id })
        if (writes.shares.isNotEmpty()) allocations.deleteMany(writes.shares.map { it.id })
    }
}

/**
 * اللي إجابة سؤال كتبته وبطل صح (المالك غيّر إجابته بعد انقطاع، أو قال إن الطرف «حسابه التاني»). [blocking] = تسويات على سلفة
 * من دول اتسجلت **من مكان تاني** (بإيد المالك في شاشة الأشخاص، أو إجابة على عملية مش داخلة هنا) ⇒ الشيل بيترفض: ما بنمسحش حاجة
 * المالك كتبها، وما بنسيبش تسوية على دين اتمسح (النسخة الشاملة بترفض الرابط المقطوع).
 */
class AskWrites(
    val loans: List<Obligation>,
    val shares: List<PersonAllocation>,
    val repayments: List<Settlement>,
    val blocking: List<Settlement>,
)
