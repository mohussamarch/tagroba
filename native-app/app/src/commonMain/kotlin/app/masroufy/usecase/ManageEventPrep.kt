package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EventError
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LifeEvent
import app.masroufy.core.PrepError
import app.masroufy.core.PrepItem
import app.masroufy.core.PrepSummary
import app.masroufy.core.TextKey
import app.masroufy.core.checkPrepAssignment
import app.masroufy.core.checkPrepName
import app.masroufy.core.checkPrepPlanned
import app.masroufy.core.prepAllowed
import app.masroufy.core.prepSuggestions
import app.masroufy.core.summarizePrep
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.PrepItemRepository
import app.masroufy.port.TransactionRepository
import app.masroufy.port.UnitOfWork

/**
 * تجهيزات الحدث الجاي (OVERRIDES §65): بنود · اقتراح أسامي حسب النوع **من غير مبالغ** · ربط مصروف الحدث ببنده · الملخص.
 * العزا مالوش تجهيزات. الحسابات في `core/EventPrep.kt`؛ هنا قراية وكتابة بس.
 */
data class ManageEventPrepDeps(
    val events: LifeEventRepository,
    val links: EventLinkRepository,
    val prep: PrepItemRepository,
    val txns: TransactionRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

class ManageEventPrep(private val deps: ManageEventPrepDeps) {
    private suspend fun event(id: Id): LifeEvent = deps.events.listAll().firstOrNull { it.id == id } ?: throw EventError(uiText(TextKey.EVENT_NOT_FOUND))

    private suspend fun preparable(id: Id): LifeEvent = event(id).also { if (!prepAllowed(it)) throw PrepError(uiText(TextKey.PREP_NOT_ALLOWED)) }

    private suspend fun item(id: Id): PrepItem = deps.prep.listAll().firstOrNull { it.id == id } ?: throw PrepError(uiText(TextKey.PREP_ITEM_NOT_FOUND))

    /** البنود بترتيبها. */
    suspend fun items(eventId: Id): List<PrepItem> = deps.prep.listByEvent(eventId).sortedWith(compareBy({ it.order }, { it.createdAt }, { it.id }))

    /** الأسامي المقترحة (مش متحفظة) — حدث جاي غير العزا ومن غير ولا بند. */
    suspend fun suggestions(eventId: Id, today: IsoDate): List<String> = prepSuggestions(event(eventId), deps.prep.listByEvent(eventId), today)

    /**
     * يضيف بنود بالأسامي (من الاقتراح أو بإيده) **من غير مبالغ** — الكل أو ولا حاجة: أي اسم غلط أو متكرر (مع الموجود أو مع بعض)
     * بيرفض الطلب كله قبل أي كتابة.
     */
    suspend fun addMany(eventId: Id, names: List<String>): List<PrepItem> = create(eventId, names.map { it to null })

    /** بند واحد، والمبلغ اختياري (لو اتكتب يبقى أكبر من صفر). */
    suspend fun add(eventId: Id, name: String, plannedMinor: Halalas? = null): PrepItem = create(eventId, listOf(name to plannedMinor)).single()

    private suspend fun create(eventId: Id, rows: List<Pair<String, Halalas?>>): List<PrepItem> {
        preparable(eventId)
        val existing = deps.prep.listByEvent(eventId)
        val now = deps.clock.nowIso()
        var order = existing.maxOfOrNull { it.order } ?: 0
        val made = mutableListOf<PrepItem>()
        for ((name, planned) in rows) {
            checkPrepPlanned(planned)
            val clean = checkPrepName(name, existing + made)
            made += PrepItem(deps.ids.next("prep"), eventId, clean, planned, ++order, false, now)
        }
        deps.prep.saveMany(made)
        return made
    }

    /** تعديل الاسم والمبلغ (null = يشيل المبلغ ويرجع «من غير مبلغ»). */
    suspend fun update(itemId: Id, name: String, plannedMinor: Halalas?): PrepItem {
        val current = item(itemId)
        checkPrepPlanned(plannedMinor)
        val clean = checkPrepName(name, deps.prep.listByEvent(current.eventId), itemId)
        return current.copy(name = clean, plannedMinor = plannedMinor).also { deps.prep.saveMany(listOf(it)) }
    }

    suspend fun setDone(itemId: Id, done: Boolean) {
        deps.prep.saveMany(listOf(item(itemId).copy(done = done)))
    }

    /** شيل بند: المصروف اللي كان عليه **بيفضل على الحدث** («مش على بند») — والاتنين في وحدة عمل واحدة. */
    suspend fun remove(itemId: Id) {
        val current = item(itemId)
        val pointing = deps.links.listByEvent(current.eventId).filter { it.prepItemId == itemId }
        deps.uow.run {
            if (pointing.isNotEmpty()) deps.links.saveMany(pointing.map { it.copy(prepItemId = null) })
            deps.prep.deleteMany(listOf(itemId))
        }
    }

    /** مصروف الحدث (الربط موجود من `EventGifts.link`) يتحط على بند أو يتشال من بنده ([prepItemId] null). */
    suspend fun assignSpend(eventId: Id, transactionId: Id, prepItemId: Id?) {
        val link = deps.links.listByTransactionIds(listOf(transactionId)).firstOrNull { it.eventId == eventId }
            ?: throw EventError(uiText(TextKey.EVENT_LINK_NOT_FOUND))
        val target = prepItemId?.let { item(it) }
        checkPrepAssignment(link, target)
        deps.links.saveMany(listOf(link.copy(prepItemId = prepItemId)))
    }

    /** المخطط · المصروف على كل بند (نصيب الحدث بالنسبة المتخزنة) · اللي مش على بند · اللي لسه. */
    suspend fun summary(eventId: Id, currency: Currency): PrepSummary {
        val event = event(eventId)
        val links = deps.links.listByEvent(eventId)
        val txns = if (links.isEmpty()) emptyList() else deps.txns.findByIds(links.map { it.transactionId }.distinct())
        return summarizePrep(event, currency, deps.prep.listByEvent(eventId), links, txns)
    }
}
