package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.Settlement
import app.masroufy.port.ObligationRepository
import app.masroufy.port.SettlementRepository

/** مستودعات الالتزامات والتسويات في الذاكرة — نفس فلترة التطبيق الحالي. */

class MemoryObligationRepository(seed: List<Obligation> = emptyList()) : ObligationRepository {
    private val items = LinkedHashMap<Id, Obligation>()

    init {
        for (o in seed) items[o.id] = o
    }

    override suspend fun listByPerson(personId: Id): List<Obligation> = items.values.filter { it.personId == personId }

    override suspend fun listByTransactionIds(ids: List<Id>): List<Obligation> {
        val wanted = ids.toSet()
        return items.values.filter { it.originTransactionId != null && it.originTransactionId in wanted }
    }

    override suspend fun saveMany(obligations: List<Obligation>) {
        for (o in obligations) items[o.id] = o
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}

class MemorySettlementRepository(seed: List<Settlement> = emptyList()) : SettlementRepository {
    private val items = LinkedHashMap<Id, Settlement>()

    init {
        for (s in seed) items[s.id] = s
    }

    override suspend fun listByObligations(obligationIds: List<Id>): List<Settlement> {
        val wanted = obligationIds.toSet()
        return items.values.filter { it.obligationId in wanted }
    }

    override suspend fun listByTransactionIds(ids: List<Id>): List<Settlement> {
        val wanted = ids.toSet()
        return items.values.filter { it.transactionId in wanted }
    }

    override suspend fun saveMany(settlements: List<Settlement>) {
        for (s in settlements) items[s.id] = s
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }
}
