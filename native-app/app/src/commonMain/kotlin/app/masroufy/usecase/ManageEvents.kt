package app.masroufy.usecase

import app.masroufy.core.EventError
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.EventSummary
import app.masroufy.core.GiftBadge
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.checkEventFields
import app.masroufy.core.checkEventName
import app.masroufy.core.eventShareMinor
import app.masroufy.core.personGiftBadges
import app.masroufy.core.summarizeEvent
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.TransactionRepository

/**
 * «الأحداث» (OVERRIDES §44.1 و§64): عمل وتعديل وأرشفة حدث، وملخصه، وبادجات النقوط في بروفايل الشخص.
 * الحسابات في `core/Events.kt`؛ هنا قراية وكتابة بس. ربط العمليات وتسجيل النقوط في `EventGifts`.
 * 🔒 الملخص **مفيهوش صافي** — المصروف رقم، والنقوط رقم تاني للمعلومية (قرار المالك §64).
 */
data class ManageEventsDeps(
    val events: LifeEventRepository,
    val links: EventLinkRepository,
    val txns: TransactionRepository,
    val people: PersonRepository,
    val ids: IdGenerator,
    val clock: Clock,
)

data class EventRow(val event: LifeEvent, val summary: EventSummary)

data class EventLists(val active: List<EventRow>, val archived: List<EventRow>)

/** [shareMinor] نصيب الحدث من العملية بالنسبة المتخزنة على الربط (العملية كلها للنقطة). */
data class EventLinkedTransaction(val link: EventLink, val transaction: Transaction, val personName: String?, val shareMinor: Halalas)

data class EventDetail(
    val event: LifeEvent,
    val summary: EventSummary,
    /** صاحب الحدث لو لحد تاني. */
    val hostName: String?,
    /** الأحدث الأول. */
    val transactions: List<EventLinkedTransaction>,
)

/** بيانات الحدث اللي المستخدم بيدخلها (عمل أو تعديل). */
data class EventInput(
    val name: String,
    val kind: LifeEventKind,
    val date: IsoDate,
    val mine: Boolean,
    val hostPersonId: Id? = null,
)

class ManageEvents(private val deps: ManageEventsDeps) {
    private suspend fun find(id: Id, all: List<LifeEvent>? = null): LifeEvent =
        (all ?: deps.events.listAll()).firstOrNull { it.id == id } ?: throw EventError(uiText(TextKey.EVENT_NOT_FOUND))

    private suspend fun checkInput(input: EventInput) {
        checkEventFields(input.date, input.mine, input.hostPersonId)
        val host = input.hostPersonId ?: return
        if (deps.people.listAll().none { it.id == host }) throw EventError(uiText(TextKey.EVENT_PERSON_NOT_FOUND))
    }

    private suspend fun linked(event: LifeEvent): Pair<List<EventLink>, List<Transaction>> {
        val links = deps.links.listByEvent(event.id)
        val txns = if (links.isEmpty()) emptyList() else deps.txns.findByIds(links.map { it.transactionId }.distinct())
        return links to txns
    }

    private suspend fun row(event: LifeEvent): EventRow {
        val (links, txns) = linked(event)
        return EventRow(event, summarizeEvent(event, links, txns))
    }

    /** الشغالة بالأحدث تاريخًا، والمؤرشفة لوحدها. */
    suspend fun list(): EventLists {
        val rows = deps.events.listAll().sortedWith(compareByDescending<LifeEvent> { it.date }.thenByDescending { it.createdAt }).map { row(it) }
        return EventLists(rows.filter { !it.event.archived }, rows.filter { it.event.archived })
    }

    suspend fun create(input: EventInput): LifeEvent {
        val checked = checkEventName(input.name, deps.events.listAll())
        checkInput(input)
        val event = LifeEvent(
            id = deps.ids.next("event"), name = checked.name, normalizedName = checked.normalizedName, kind = input.kind,
            date = input.date, mine = input.mine, hostPersonId = input.hostPersonId, archived = false, createdAt = deps.clock.nowIso(),
        )
        deps.events.save(event)
        return event
    }

    /** تعديل — حدث عليه نقوط جاتلك ما يتحوّلش لحدث حد تاني (النقوط دي كانت هتستخبى من غير ما حد يعرف). */
    suspend fun update(id: Id, input: EventInput): LifeEvent {
        val all = deps.events.listAll()
        val event = find(id, all)
        val checked = checkEventName(input.name, all, id)
        checkInput(input)
        if (event.mine && !input.mine && deps.links.listByEvent(id).any { it.role == EventRole.GIFT_IN }) {
            throw EventError(uiText(TextKey.EVENT_GIFT_IN_NOT_MINE))
        }
        val updated = event.copy(
            name = checked.name, normalizedName = checked.normalizedName, kind = input.kind, date = input.date,
            mine = input.mine, hostPersonId = input.hostPersonId,
        )
        deps.events.save(updated)
        return updated
    }

    /** مفيش مسح للحدث — أرشفة، وعملياته ونقوطه بتفضل (زي المشروع). */
    suspend fun setArchived(id: Id, archived: Boolean) {
        deps.events.save(find(id).copy(archived = archived))
    }

    suspend fun detail(id: Id): EventDetail {
        val event = find(id)
        val (links, txns) = linked(event)
        val names = deps.people.listAll().associate { it.id to it.name }
        val byId = txns.associateBy { it.id }
        val rows = links.mapNotNull { l -> byId[l.transactionId]?.let { EventLinkedTransaction(l, it, l.personId?.let(names::get), eventShareMinor(it.amountMinor, l.sharePercent)) } }
            .sortedWith(compareByDescending<EventLinkedTransaction> { it.transaction.occurredAt }.thenByDescending { it.transaction.sourceOrder })
        return EventDetail(event, summarizeEvent(event, links, txns), event.hostPersonId?.let(names::get), rows)
    }

    /** بادجات النقوط في بروفايل الشخص (§44.1) — معلومة بس، من غير التزام ولا تذكير إجباري. */
    suspend fun personBadges(personId: Id): List<GiftBadge> {
        val links = deps.links.listByPerson(personId)
        if (links.isEmpty()) return emptyList()
        val txns = deps.txns.findByIds(links.map { it.transactionId }.distinct())
        return personGiftBadges(personId, deps.events.listAll(), links, txns)
    }
}
