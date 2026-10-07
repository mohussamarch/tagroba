package app.masroufy.memory

import app.masroufy.core.EventLink
import app.masroufy.core.Id
import app.masroufy.core.LifeEvent
import app.masroufy.core.Occasion
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.OccasionRepository

/** الأحداث ومناسبات الشخص في الذاكرة — للاختبار (CLAUDE.md #6). الروابط بتتكتب مع العملية في وحدة عمل واحدة ⇒ `Snapshotable`. */
class MemoryLifeEventRepository(seed: List<LifeEvent> = emptyList()) : LifeEventRepository {
    private val items = LinkedHashMap<Id, LifeEvent>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<LifeEvent> = items.values.toList()

    override suspend fun save(event: LifeEvent) {
        items[event.id] = event
    }
}

class MemoryEventLinkRepository(seed: List<EventLink> = emptyList()) : EventLinkRepository, Snapshotable {
    private var items = LinkedHashMap<Id, EventLink>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listByEvent(eventId: Id): List<EventLink> = items.values.filter { it.eventId == eventId }

    override suspend fun listByPerson(personId: Id): List<EventLink> = items.values.filter { it.personId == personId }

    override suspend fun listByTransactionIds(ids: List<Id>): List<EventLink> = ids.toSet().let { s -> items.values.filter { it.transactionId in s } }

    override suspend fun saveMany(links: List<EventLink>) {
        for (l in links) items[l.id] = l
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    fun all(): List<EventLink> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, EventLink>)
    }
}

class MemoryOccasionRepository(seed: List<Occasion> = emptyList()) : OccasionRepository {
    private val items = LinkedHashMap<Id, Occasion>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<Occasion> = items.values.toList()

    override suspend fun save(occasion: Occasion) {
        items[occasion.id] = occasion
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }
}
