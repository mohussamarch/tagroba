package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.PersonLink
import app.masroufy.data.TransferCodecs
import app.masroufy.port.PersonLinkRepository

/** ربط العمليات بالأشخاص (r05) — مستند لكل عملية في البلد، والمعرّف معرّف العملية. */
class FirestorePersonLinkRepository(private val space: FirestoreSpace) : PersonLinkRepository {
    private val codec = TransferCodecs.personLinks

    override suspend fun listByPerson(personId: Id): List<PersonLink> = space.select(codec, DocQuery(listOf(Cond.Eq("personId", personId))))

    override suspend fun listByTransactionIds(ids: List<Id>): List<PersonLink> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(links: List<PersonLink>) = space.saveAll(codec, links)

    override suspend fun deleteMany(transactionIds: List<Id>) = space.deleteAll(codec.group, transactionIds)
}
