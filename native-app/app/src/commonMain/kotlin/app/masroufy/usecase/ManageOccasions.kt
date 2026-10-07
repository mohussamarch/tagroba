package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionError
import app.masroufy.core.OccasionKind
import app.masroufy.core.TextKey
import app.masroufy.core.checkOccasion
import app.masroufy.core.daysBetween
import app.masroufy.core.nextOccurrence
import app.masroufy.core.occasionAlertCandidate
import app.masroufy.core.occasionSoonDays
import app.masroufy.core.ownEventOccasion
import app.masroufy.core.ownEventOccasionId
import app.masroufy.core.personGiftBadges
import app.masroufy.core.uiText
import app.masroufy.port.Clock
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.OccasionRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.TransactionRepository

/**
 * مناسبات الشخص (OVERRIDES §44.1 و§64) — ميلادي بس. عيد ميلاد · عيد جواز · فرح · غيره، سنوية أو مرة واحدة،
 * والتذكير السنوي بحدثك (فرحك) بالمدة اللي إنت بتحددها. التنبيه نفسه بيقرره محرك التنبيهات (§61) — هنا المرشحين بس.
 */
data class ManageOccasionsDeps(
    val occasions: OccasionRepository,
    val people: PersonRepository,
    val events: LifeEventRepository,
    val links: EventLinkRepository,
    val txns: TransactionRepository,
    val ids: IdGenerator,
    val clock: Clock,
)

/** بيانات المناسبة اللي المستخدم بيدخلها. [personId] null = مناسبتك إنت. */
data class OccasionInput(
    val personId: Id?,
    val kind: OccasionKind,
    val label: String? = null,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val yearly: Boolean = true,
    val leadDays: Int? = null,
)

data class UpcomingOccasion(val occasion: Occasion, val date: IsoDate, val personName: String?)

class ManageOccasions(private val deps: ManageOccasionsDeps) {
    private suspend fun find(id: Id): Occasion = deps.occasions.listAll().firstOrNull { it.id == id } ?: throw OccasionError(uiText(TextKey.OCCASION_NOT_FOUND))

    private suspend fun checkPerson(personId: Id?) {
        if (personId != null && deps.people.listAll().none { it.id == personId }) throw OccasionError(uiText(TextKey.EVENT_PERSON_NOT_FOUND))
    }

    private fun build(id: Id, input: OccasionInput, createdAt: String, sourceEventId: Id? = null) = checkOccasion(
        Occasion(id, input.personId, input.kind, input.label, input.month, input.day, input.year, input.yearly, input.leadDays, sourceEventId, createdAt),
    )

    suspend fun add(input: OccasionInput): Occasion {
        checkPerson(input.personId)
        val occasion = build(deps.ids.next("occasion"), input, deps.clock.nowIso())
        deps.occasions.save(occasion)
        return occasion
    }

    suspend fun update(id: Id, input: OccasionInput): Occasion {
        val old = find(id)
        checkPerson(input.personId)
        val occasion = build(id, input, old.createdAt, old.sourceEventId)
        deps.occasions.save(occasion)
        return occasion
    }

    /** المناسبة مش فلوس ⇒ بتتمسح عادي. */
    suspend fun remove(id: Id) {
        find(id)
        deps.occasions.remove(id)
    }

    /** مناسبات شخص (أو مناسباتك إنت لو null)، بالأقرب الأول. */
    suspend fun forPerson(personId: Id?, today: IsoDate): List<UpcomingOccasion> =
        upcoming(today).filter { it.occasion.personId == personId }

    /** كل المناسبات اللي ليها مرة جاية، بالأقرب الأول — الشخص المؤرشف بيتشال (اختيار Claude). */
    suspend fun upcoming(today: IsoDate): List<UpcomingOccasion> {
        val people = deps.people.listAll().associateBy { it.id }
        return deps.occasions.listAll().mapNotNull { o ->
            val person = o.personId?.let { people[it] ?: return@mapNotNull null }
            if (person?.archived == true) return@mapNotNull null
            nextOccurrence(o, today)?.let { UpcomingOccasion(o, it, person?.name) }
        }.sortedWith(compareBy<UpcomingOccasion> { it.date }.thenBy { it.occasion.id })
    }

    /**
     * تذكير سنوي بحدثك (§64) قبله بـ[leadDays] يوم (هدية لمراتك · احتفال). الطلب تاني بيعدّل نفس التذكير.
     * حدث مش بتاعك ⇒ مرفوض.
     */
    suspend fun remindOwnEvent(eventId: Id, leadDays: Int): Occasion {
        val event = deps.events.listAll().firstOrNull { it.id == eventId } ?: throw OccasionError(uiText(TextKey.EVENT_NOT_FOUND))
        val existing = deps.occasions.listAll().firstOrNull { it.id == ownEventOccasionId(eventId) }
        val occasion = ownEventOccasion(event, leadDays, deps.clock.nowIso(), existing)
        deps.occasions.save(occasion)
        return occasion
    }

    /**
     * المرشحين للتنبيه النهارده (لـ`GatherAlerts`). السطر جوه التطبيق فيه اسم الشخص، ولو نقّطك أو نقّطته قبل كده
     * بيتكتب للمعلومية («نقّطك 2,000 في فرحك»).
     */
    suspend fun alertCandidates(today: IsoDate): List<AlertCandidate> {
        val soon = upcoming(today).filter { daysBetween(today, it.date) <= occasionSoonDays(it.occasion) }
        if (soon.isEmpty()) return emptyList()
        val events = deps.events.listAll()
        val eventNames = events.associate { it.id to it.name }
        return soon.mapNotNull { u ->
            val o = u.occasion
            val badges = o.personId?.let { pid ->
                val links = deps.links.listByPerson(pid)
                if (links.isEmpty()) emptyList() else personGiftBadges(pid, events, links, deps.txns.findByIds(links.map { it.transactionId }.distinct()))
            }.orEmpty()
            occasionAlertCandidate(o, today, u.personName, o.sourceEventId?.let(eventNames::get), badges)
        }
    }
}
