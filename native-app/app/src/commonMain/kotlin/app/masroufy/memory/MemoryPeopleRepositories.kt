package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.Obligation
import app.masroufy.core.Person
import app.masroufy.core.Settlement
import app.masroufy.core.prepareSettlement
import app.masroufy.port.ObligationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.SettlementWriter

/** مستودعات الأشخاص والالتزامات والتسويات في الذاكرة — نفس فلترة التطبيق الحالي. */

class MemoryPersonRepository(seed: List<Person> = emptyList()) : PersonRepository {
    private val items = LinkedHashMap<Id, Person>()

    init {
        for (p in seed) items[p.id] = p
    }

    override suspend fun listAll(): List<Person> = items.values.toList()

    override suspend fun save(person: Person) {
        items[person.id] = person
    }
}

/** كاتب التسوية في الذاكرة — نقل `memory/settlementWriter.ts` (المنطق نفسه في `prepareSettlement`). */
class MemorySettlementWriter(
    private val obligations: ObligationRepository,
    private val settlements: SettlementRepository,
) : SettlementWriter {
    override suspend fun settle(input: Settlement, personId: Id): Settlement {
        val obligation = obligations.listByPerson(personId).find { it.id == input.obligationId }
        val rows = settlements.listByObligations(listOf(input.obligationId))
        val result = prepareSettlement(input, personId, obligation, rows)
        if (rows.none { it.id == result.id }) settlements.saveMany(listOf(result))
        return result
    }
}

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

    fun all(): List<Obligation> = items.values.toList()
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

    fun all(): List<Settlement> = items.values.toList()
}
