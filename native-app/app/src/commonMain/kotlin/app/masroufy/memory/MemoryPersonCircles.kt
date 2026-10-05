package app.masroufy.memory

import app.masroufy.core.Id
import app.masroufy.core.PersonProfile
import app.masroufy.core.PersonRelation
import app.masroufy.port.PersonProfileRepository
import app.masroufy.port.PersonRelationRepository

/** دواير الأشخاص والصلات في الذاكرة — للاختبار (CLAUDE.md #6). */
class MemoryPersonProfileRepository(seed: List<PersonProfile> = emptyList()) : PersonProfileRepository {
    private val items = LinkedHashMap<Id, PersonProfile>().apply { seed.forEach { put(it.personId, it) } }

    override suspend fun listAll(): List<PersonProfile> = items.values.toList()

    override suspend fun save(profile: PersonProfile) {
        items[profile.personId] = profile
    }

    override suspend fun remove(personId: Id) {
        items.remove(personId)
    }
}

class MemoryPersonRelationRepository(seed: List<PersonRelation> = emptyList()) : PersonRelationRepository {
    private val items = LinkedHashMap<Id, PersonRelation>().apply { seed.forEach { put(it.id, it) } }

    override suspend fun listAll(): List<PersonRelation> = items.values.toList()

    override suspend fun save(relation: PersonRelation) {
        items[relation.id] = relation
    }

    override suspend fun remove(id: Id) {
        items.remove(id)
    }
}
