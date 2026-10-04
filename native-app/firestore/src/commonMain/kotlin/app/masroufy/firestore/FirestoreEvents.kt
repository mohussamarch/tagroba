package app.masroufy.firestore

import app.masroufy.core.EventLink
import app.masroufy.core.Id
import app.masroufy.core.LifeEvent
import app.masroufy.core.Occasion
import app.masroufy.data.EventCodecs
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.OccasionRepository

/**
 * الأحداث ومناسبات الشخص (OVERRIDES §64) — مجموعات كوتلن بس، والقواعد بتسمح بيها من غير نشر (`EventCodecs`).
 * ⚠️ لما «حساب لكل بلد» يتبني: المناسبات بتروح على مستوى الحساب (`users/{uid}`) والأحداث جوه مساحة كل بلد.
 */
class FirestoreLifeEventRepository(private val space: FirestoreSpace) : LifeEventRepository {
    private val codec = EventCodecs.lifeEvents

    override suspend fun listAll(): List<LifeEvent> = space.select(codec)

    override suspend fun save(event: LifeEvent) = space.saveAll(codec, listOf(event))
}

class FirestoreEventLinkRepository(private val space: FirestoreSpace) : EventLinkRepository {
    private val codec = EventCodecs.eventLinks

    override suspend fun listByEvent(eventId: Id): List<EventLink> = space.select(codec, DocQuery(listOf(Cond.Eq("eventId", eventId))))

    override suspend fun listByPerson(personId: Id): List<EventLink> = space.select(codec, DocQuery(listOf(Cond.Eq("personId", personId))))

    override suspend fun listByTransactionIds(ids: List<Id>): List<EventLink> = space.findIn(codec, "transactionId", ids)

    override suspend fun saveMany(links: List<EventLink>) = space.saveAll(codec, links)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}

class FirestoreOccasionRepository(private val space: FirestoreSpace) : OccasionRepository {
    private val codec = EventCodecs.occasions

    override suspend fun listAll(): List<Occasion> = space.select(codec)

    override suspend fun save(occasion: Occasion) = space.saveAll(codec, listOf(occasion))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}
