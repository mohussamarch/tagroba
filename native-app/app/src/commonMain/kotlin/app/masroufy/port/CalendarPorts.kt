package app.masroufy.port

import app.masroufy.core.Id
import app.masroufy.core.PrepItem
import app.masroufy.core.Reservation

/**
 * المبالغ المحجوزة في التقويم وبنود تجهيز الأحداث (OVERRIDES §65) — مجموعات كوتلن بس.
 * التقويم نفسه **مش متخزن** (بيتحسب من الكيانات كل مرة) — المتخزن الحجز بس.
 * `listAll` مسموح: الحجوزات والبنود بإيد المستخدم (عشرات مش آلاف).
 */
interface ReservationRepository {
    suspend fun listAll(): List<Reservation>

    /** حجز واحد لكل مرة من الميعاد — المعرّف ثابت (`reservationId`) فالحفظ التاني بيستبدل الأول. */
    suspend fun save(reservation: Reservation)

    suspend fun remove(id: Id)
}

interface PrepItemRepository {
    suspend fun listByEvent(eventId: Id): List<PrepItem>

    suspend fun listAll(): List<PrepItem>

    suspend fun saveMany(items: List<PrepItem>)

    suspend fun deleteMany(ids: List<Id>)
}
