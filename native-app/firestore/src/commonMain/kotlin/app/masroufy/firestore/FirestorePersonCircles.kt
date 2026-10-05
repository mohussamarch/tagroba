package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.PersonProfile
import app.masroufy.core.PersonRelation
import app.masroufy.data.PersonCircleCodecs
import app.masroufy.port.PersonProfileRepository
import app.masroufy.port.PersonRelationRepository

/**
 * دواير الأشخاص والصلات بينهم (جلسة 16) — على مستوى الحساب (`users/{uid}`)، جنب `people`. مستند الشخص نفسه ما بيتلمسش.
 */
class FirestorePersonProfileRepository(private val space: FirestoreSpace) : PersonProfileRepository {
    private val codec = PersonCircleCodecs.personProfiles

    override suspend fun listAll(): List<PersonProfile> = space.select(codec)

    override suspend fun save(profile: PersonProfile) = space.saveAll(codec, listOf(profile))

    override suspend fun remove(personId: Id) = space.deleteAll(codec.group, listOf(personId))
}

class FirestorePersonRelationRepository(private val space: FirestoreSpace) : PersonRelationRepository {
    private val codec = PersonCircleCodecs.personRelations

    override suspend fun listAll(): List<PersonRelation> = space.select(codec)

    override suspend fun save(relation: PersonRelation) = space.saveAll(codec, listOf(relation))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}
