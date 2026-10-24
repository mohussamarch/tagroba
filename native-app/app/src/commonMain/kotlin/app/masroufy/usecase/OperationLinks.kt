package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Transaction
import app.masroufy.core.employerOf
import app.masroufy.core.recurringKey
import app.masroufy.port.AllocationRepository
import app.masroufy.port.AssetRepository
import app.masroufy.port.AssetSaleRepository
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonLinkRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.RecurringRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/** نوع الحاجة اللي العملية مربوطة بيها (فجوة ٣). */
enum class OpLinkKind { WALLET, PERSON, DEBT, PLAN, SUBSCRIPTION, ASSET_SALE, INCOME_SOURCE }

/** رابط من العملية: [id] = معرّف الحاجة (الشاشة بتفتح صفحتها بيه) · [name] = اسمها (فاضي لو مش معروف). */
data class OpLink(val kind: OpLinkKind, val id: Id, val name: String)

/** عملية مربوطة بدين أو خطة — سطر في صفحتهم بيفتح تفاصيلها. */
data class LinkedOp(val transactionId: Id, val day: String, val name: String, val amountMinor: Halalas, val currency: Currency, val direction: Direction)

class OperationLinksDeps(
    val txns: TransactionRepository,
    val wallets: WalletRepository,
    val people: PersonRepository,
    val allocations: AllocationRepository,
    val personLinks: PersonLinkRepository?,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val installmentPayments: InstallmentPaymentRepository,
    val installmentPlans: InstallmentPlanRepository,
    val recurring: RecurringRepository,
    val assets: AssetRepository,
    val assetSales: AssetSaleRepository,
    val incomeSources: IncomeSourceRepository,
)

/**
 * r06-links2 (فجوة ٣ — OVERRIDES §118-ج): كل اللي العملية مربوطة بيه — الحساب (والتاني لو تحويل) · الشخص · الدين (اللي عملته أو سددته) ·
 * خطة التقسيط أو «ادفع بعدين» · الاشتراك · بيع أصل · مصدر الدخل — والعكس: عمليات الدين وعمليات الخطة. قراية بس، مفيش حساب مبالغ.
 */
class OperationLinks(private val d: OperationLinksDeps) {
    suspend fun of(transactionId: Id): List<OpLink> {
        val t = d.txns.findByIds(listOf(transactionId)).firstOrNull() ?: return emptyList()
        val ids = listOf(t.id)
        val wallets = d.wallets.listAll().associateBy { it.id }
        val people = d.people.listAll().associateBy { it.id }
        val out = mutableListOf<OpLink>()
        listOfNotNull(t.walletId, t.transferToWalletId).distinct().forEach { w -> out += OpLink(OpLinkKind.WALLET, w, wallets[w]?.name.orEmpty()) }
        val personIds = d.allocations.listByTransactionIds(ids).map { it.personId } + d.personLinks?.listByTransactionIds(ids).orEmpty().map { it.personId }
        val origin = d.obligations.listByTransactionIds(ids)
        (personIds + origin.map { it.personId }).distinct().forEach { p -> out += OpLink(OpLinkKind.PERSON, p, people[p]?.name.orEmpty()) }
        val debtPerson = origin.associate { it.id to it.personId } +
            d.personLinks?.listByTransactionIds(ids).orEmpty().mapNotNull { l -> l.obligationId?.let { it to l.personId } }
        (origin.map { it.id } + d.settlements.listByTransactionIds(ids).map { it.obligationId }).distinct().forEach { o ->
            out += OpLink(OpLinkKind.DEBT, o, debtPerson[o]?.let { people[it]?.name }.orEmpty())
        }
        val plans = d.installmentPlans.listAll().associateBy { it.id }
        val planIds = d.installmentPayments.listByTransactionIds(ids).map { it.planId } + plans.values.filter { it.receivedTransactionId == t.id }.map { it.id }
        planIds.distinct().forEach { p -> out += OpLink(OpLinkKind.PLAN, p, plans[p]?.name.orEmpty()) }
        (if (t.observedDirection == Direction.OUT) d.recurring.listAll().firstOrNull { it.merchantKey == recurringKey(t) && it.currency == t.currency } else null)?.let { out += OpLink(OpLinkKind.SUBSCRIPTION, it.id, it.name) }
        val sales = d.assetSales.listAll().filter { it.transactionId == t.id }
        if (sales.isNotEmpty()) {
            val assets = d.assets.listAll().associateBy { it.id }
            sales.map { it.assetId }.distinct().forEach { a -> out += OpLink(OpLinkKind.ASSET_SALE, a, assets[a]?.name.orEmpty()) }
        }
        if (t.observedDirection == Direction.IN) employerOf(t, d.incomeSources.listAll())?.let { out += OpLink(OpLinkKind.INCOME_SOURCE, it.id, it.name) }
        return out
    }

    /** عمليات الدين: اللي عملته (أصل الدين لو ليه عملية) + عمليات السداد — الأقدم الأول. */
    suspend fun opsOfDebt(obligationId: Id, personId: Id): List<LinkedOp> {
        val originTransactionId = d.obligations.listByPerson(personId).firstOrNull { it.id == obligationId }?.originTransactionId
        val ids = listOfNotNull(originTransactionId) + d.settlements.listByObligations(listOf(obligationId)).map { it.transactionId }.filter { it.isNotEmpty() }
        return linked(ids)
    }

    /** عمليات الخطة: الأقساط المدفوعة + الفلوس اللي استلمتها (التمويل). */
    suspend fun opsOfPlan(planId: Id): List<LinkedOp> {
        val received = d.installmentPlans.listAll().firstOrNull { it.id == planId }?.receivedTransactionId
        return linked(listOfNotNull(received) + d.installmentPayments.listByPlan(planId).map { it.transactionId })
    }

    private suspend fun linked(ids: List<Id>): List<LinkedOp> {
        if (ids.isEmpty()) return emptyList()
        return d.txns.findByIds(ids.distinct()).sortedBy { it.occurredAt }.map(::linkedOp)
    }

    private fun linkedOp(t: Transaction) =
        LinkedOp(t.id, t.occurredAt.take(10), t.rawMerchantName.orEmpty(), t.amountMinor, t.currency, t.observedDirection)
}
