package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.PrepItem
import app.masroufy.core.Reservation
import app.masroufy.port.PrepItemRepository
import app.masroufy.port.ReservationRepository

/** المبالغ المحجوزة في الذاكرة — للاختبار (CLAUDE.md #6). */
class MemoryReservationRepository(seed: List<Reservation> = emptyList()) : ReservationRepository {
    private val items = LinkedHashMap<Id, Reservation>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<Reservation> = items.values.toList()

    override suspend fun save(reservation: Reservation) {
        items[reservation.id] = reservation
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }
}

/** بنود التجهيز — شيل بند بيفك ربطه من المصروف في نفس وحدة العمل ⇒ `Snapshotable`. */
class MemoryPrepItemRepository(seed: List<PrepItem> = emptyList()) : PrepItemRepository, Snapshotable {
    private var items = LinkedHashMap<Id, PrepItem>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listByEvent(eventId: Id): List<PrepItem> = items.values.filter { it.eventId == eventId }

    override suspend fun listAll(): List<PrepItem> = items.values.toList()

    override suspend fun saveMany(items: List<PrepItem>) {
        for (p in items) this.items[p.id] = p
    }

    override suspend fun deleteMany(ids: List<Id>) {
        for (id in ids) items.remove(id)
    }

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, PrepItem>)
    }
}
