package app.masroufy.port

import app.masroufy.core.EventLink
import app.masroufy.core.Id
import app.masroufy.core.LifeEvent
import app.masroufy.core.Occasion

/**
 * الأحداث ومناسبات الشخص (OVERRIDES §64) — مجموعات جديدة في كوتلن بس.
 * `listAll` مسموح للأحداث والمناسبات: العدد صغير بطبيعته (مش عمليات). الروابط بتتقرا بالحدث أو الشخص أو العملية.
 * ⚠️ لما «حساب لكل بلد» يتبني: الأحداث وروابطها **جوه كل بلد** (عملياتها هناك)، والمناسبات **على الحساب كله** (بتاعة الأشخاص،
 * والشخص مشترك بين البلاد — §64). دلوقتي الاتنين في نفس المساحة.
 */
interface LifeEventRepository {
    suspend fun listAll(): List<LifeEvent>

    suspend fun save(event: LifeEvent)
}

interface EventLinkRepository {
    suspend fun listByEvent(eventId: Id): List<EventLink>

    suspend fun listByPerson(personId: Id): List<EventLink>

    suspend fun listByTransactionIds(ids: List<Id>): List<EventLink>

    suspend fun saveMany(links: List<EventLink>)

    suspend fun deleteMany(ids: List<Id>)
}

interface OccasionRepository {
    suspend fun listAll(): List<Occasion>

    suspend fun save(occasion: Occasion)

    suspend fun remove(id: Id)
}
