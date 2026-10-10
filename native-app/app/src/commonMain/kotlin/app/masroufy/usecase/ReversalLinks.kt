package app.masroufy.usecase

import app.masroufy.core.AssetLot
import app.masroufy.core.AssetSale
import app.masroufy.core.Id
import app.masroufy.core.InstallmentPlan
import app.masroufy.port.AllocationRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository

/**
 * اللي مربوط بعملية (§77-D أمان): الأصلية المربوطة **ما بتتلغيش لوحدها** (بتتسأل)، و«أيوه نلغيهم» من المالك **بيترفض** لحد ما الربط يتفك
 * (`RefundAsks.confirmReversal`) — الدين اللي على شخص أو النقوط أو الحدث كانوا هيفضلوا بيعدّوا عملية اتلغت.
 */
enum class ReversalLink { ALLOCATION, OBLIGATION, SETTLEMENT, ROSCA, INSTALLMENT, FINANCING, ZAKAT, ASSET, EVENT, PROJECT, SPACE_TRANSFER }

/** كل اللي بيشاور على عملية بمعرّفها: نفس روابط التراجع + الديون والتخصيصات والتسويات. */
data class ReversalLinkDeps(
    val allocations: AllocationRepository,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val links: RevertLinkDeps,
)

/**
 * بيقرا روابط العمليات اللي **اتلاقت** بس (مش كل المرشحين): بالمعرّف لما المستودع بيسمح، والمجموعات اللي مالهاش بحث بالعملية (خطط
 * التمويل المستلمة · شراء وبيع الأصول) **بتتقري مرة واحدة** للقارئ كله (الدفعة كلها) — كانت بتتقري كاملة لكل سطر رجوع (قرايات فايربيز).
 */
internal class ReversalLinkReader(private val deps: ReversalLinkDeps) {
    private var plans: List<InstallmentPlan>? = null
    private var lots: List<AssetLot>? = null
    private var sales: List<AssetSale>? = null

    suspend fun linksOf(id: Id): Set<ReversalLink> {
        val ids = listOf(id)
        val l = deps.links
        val out = LinkedHashSet<ReversalLink>()
        if (deps.allocations.listByTransactionIds(ids).isNotEmpty()) out += ReversalLink.ALLOCATION
        if (deps.obligations.listByTransactionIds(ids).any { it.originTransactionId == id }) out += ReversalLink.OBLIGATION
        if (deps.settlements.listByTransactionIds(ids).isNotEmpty()) out += ReversalLink.SETTLEMENT
        if (l.roscaEntries.listByTransactionIds(ids).isNotEmpty()) out += ReversalLink.ROSCA
        if (l.installmentPayments.listByTransactionIds(ids).isNotEmpty()) out += ReversalLink.INSTALLMENT
        val plans = plans ?: l.installmentPlans.listAll().also { plans = it }
        if (plans.any { it.receivedTransactionId == id }) out += ReversalLink.FINANCING
        if (l.zakatPayments.listByTransactionIds(ids).isNotEmpty()) out += ReversalLink.ZAKAT
        val lots = lots ?: l.assetLots.listAll().also { lots = it }
        val sales = sales ?: l.assetSales.listAll().also { sales = it }
        if (lots.any { it.transactionId == id } || sales.any { it.transactionId == id }) out += ReversalLink.ASSET
        if (l.eventLinks.listByTransactionIds(ids).isNotEmpty()) out += ReversalLink.EVENT
        if (l.projectLinks.listByTransaction(id).isNotEmpty()) out += ReversalLink.PROJECT
        if (l.spaceLegs?.legsAmong(ids)?.contains(id) == true) out += ReversalLink.SPACE_TRANSFER
        return out
    }
}
