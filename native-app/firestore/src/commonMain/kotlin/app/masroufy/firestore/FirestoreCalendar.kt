package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.PrepItem
import app.masroufy.core.Reservation
import app.masroufy.data.CalendarCodecs
import app.masroufy.port.PrepItemRepository
import app.masroufy.port.ReservationRepository

/**
 * المبالغ المحجوزة وبنود تجهيز الأحداث (OVERRIDES §65) — مجموعات كوتلن بس، والقواعد بتسمح بيها من غير نشر (`CalendarCodecs`).
 * ⚠️ لما «حساب لكل بلد» يتبني: الاتنين **جوه مساحة كل بلد** (الحجز بعملة البلد، والبنود بتاعة حدث في البلد).
 */
class FirestoreReservationRepository(private val space: FirestoreSpace) : ReservationRepository {
    private val codec = CalendarCodecs.reservations

    override suspend fun listAll(): List<Reservation> = space.select(codec)

    override suspend fun save(reservation: Reservation) = space.saveAll(codec, listOf(reservation))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}

class FirestorePrepItemRepository(private val space: FirestoreSpace) : PrepItemRepository {
    private val codec = CalendarCodecs.eventPrep

    override suspend fun listByEvent(eventId: Id): List<PrepItem> = space.select(codec, DocQuery(listOf(Cond.Eq("eventId", eventId))))

    override suspend fun listAll(): List<PrepItem> = space.select(codec)

    override suspend fun saveMany(items: List<PrepItem>) = space.saveAll(codec, items)

    override suspend fun deleteMany(ids: List<Id>) = space.deleteAll(codec.group, ids)
}
