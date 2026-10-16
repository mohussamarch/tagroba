package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.PersonLink
import app.masroufy.port.PersonLinkRepository

/** ربط العمليات بالأشخاص في الذاكرة — للاختبار (CLAUDE.md #6). `Snapshotable`: الربط وأثره بيرجعوا مع بعض لو حاجة فشلت. */
class MemoryPersonLinkRepository(seed: List<PersonLink> = emptyList()) : PersonLinkRepository, Snapshotable {
    private var items = LinkedHashMap<Id, PersonLink>().apply { seed.forEach { put(it.transactionId, it) } }

    override suspend fun listByPerson(personId: Id): List<PersonLink> = items.values.filter { it.personId == personId }

    override suspend fun listByTransactionIds(ids: List<Id>): List<PersonLink> = ids.toSet().let { s -> items.values.filter { it.transactionId in s } }

    override suspend fun saveMany(links: List<PersonLink>) {
        for (l in links) items[l.transactionId] = l
    }

    override suspend fun deleteMany(transactionIds: List<Id>) {
        for (id in transactionIds) items.remove(id)
    }

    fun all(): List<PersonLink> = items.values.toList()

    override fun snapshot(): Any = LinkedHashMap(items)

    @Suppress("UNCHECKED_CAST")
    override fun restore(state: Any) {
        items = LinkedHashMap(state as Map<Id, PersonLink>)
    }
}
