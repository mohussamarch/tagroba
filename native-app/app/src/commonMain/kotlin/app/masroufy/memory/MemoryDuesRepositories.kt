package app.masroufy.memory

import app.masroufy.core.DebtTerms
import app.masroufy.core.Id
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.port.DebtTermsRepository
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.InstallmentPlanRepository
import app.masroufy.port.RoscaEntryRepository
import app.masroufy.port.RoscaRepository

/**
 * «المستحقات» في الذاكرة — للاختبار (CLAUDE.md #6). الروابط (`RoscaEntry` و`InstallmentPayment`)
 * بتتكتب مع تعديل نوع العملية في وحدة عمل واحدة، فلازم ترجع لو الكتابة فشلت (`Snapshotable`).
 */
class MemoryRoscaRepository(seed: List<Rosca> = emptyList()) : RoscaRepository {
    private val items = LinkedHashMap<Id, Rosca>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<Rosca> = items.values.toList()

    override suspend fun save(rosca: Rosca) {
        items[rosca.id] = rosca
    }
}

class MemoryRoscaEntryRepository(seed: List<RoscaEntry> = emptyList()) : RoscaEntryRepository, Snapshotable {
    private var items = LinkedHashMap<Id, RoscaEntry>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listByRosca(roscaId: Id): List<RoscaEntry> = items.values.filter { it.roscaId == roscaId }

    override suspend fun listByTransactionIds(ids: List<Id>): List<RoscaEntry> = ids.toSet().let { s -> items.values.filter { it.transactionId in s } }

    override suspend fun saveMany(entries: List<RoscaEntry>) {
        for (e in entries) items[e.id] = e
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    fun all(): List<RoscaEntry> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, RoscaEntry>)
    }
}

class MemoryInstallmentPlanRepository(seed: List<InstallmentPlan> = emptyList()) : InstallmentPlanRepository {
    private val items = LinkedHashMap<Id, InstallmentPlan>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<InstallmentPlan> = items.values.toList()

    override suspend fun save(plan: InstallmentPlan) {
        items[plan.id] = plan
    }
}

class MemoryInstallmentPaymentRepository(seed: List<InstallmentPayment> = emptyList()) : InstallmentPaymentRepository, Snapshotable {
    private var items = LinkedHashMap<Id, InstallmentPayment>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listByPlan(planId: Id): List<InstallmentPayment> = items.values.filter { it.planId == planId }

    override suspend fun listByTransactionIds(ids: List<Id>): List<InstallmentPayment> =
        ids.toSet().let { s -> items.values.filter { it.transactionId in s } }

    override suspend fun saveMany(payments: List<InstallmentPayment>) {
        for (p in payments) items[p.id] = p
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    fun all(): List<InstallmentPayment> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, InstallmentPayment>)
    }
}

class MemoryDebtTermsRepository(seed: List<DebtTerms> = emptyList()) : DebtTermsRepository {
    private val items = LinkedHashMap<Id, DebtTerms>().apply { seed.forEach { put(it.obligationId, it) } }

    override suspend fun listAll(): List<DebtTerms> = items.values.toList()

    override suspend fun save(terms: DebtTerms) {
        items[terms.obligationId] = terms
    }

    override suspend fun remove(obligationId: Id) {
        items.remove(obligationId)
    }
}
