package app.masroufy.usecase

import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PeopleOverview
import app.masroufy.core.PeopleOverviewInput
import app.masroufy.core.PersonSpaceBalance
import app.masroufy.core.Space
import app.masroufy.core.SpaceGiftBadge
import app.masroufy.core.peopleOverview
import app.masroufy.core.personGiftBadges
import app.masroufy.port.EventLinkRepository
import app.masroufy.port.LifeEventRepository
import app.masroufy.port.ObligationRepository
import app.masroufy.port.OccasionRepository
import app.masroufy.port.PersonProfileRepository
import app.masroufy.port.PersonRelationRepository
import app.masroufy.port.PersonRepository
import app.masroufy.port.SettlementRepository
import app.masroufy.port.TransactionRepository

/** بلد واحدة في شاشة الأشخاص: ديونها (زي `PersonSpaceBook`) وأحداثها وروابطها وعملياتها (للبادجات). */
data class PeopleSpaceSource(
    val space: Space,
    val obligations: ObligationRepository,
    val settlements: SettlementRepository,
    val events: LifeEventRepository,
    val links: EventLinkRepository,
    val txns: TransactionRepository,
)

data class LoadPeopleOverviewDeps(
    val people: PersonRepository,
    val profiles: PersonProfileRepository,
    val relations: PersonRelationRepository,
    val occasions: OccasionRepository,
    /** كل البلاد اللي ممكن تتعرض (التشغيل بيدّي البلاد المتزامنة — المؤرشفة ما بتتزامنش §64). */
    val spaces: List<PeopleSpaceSource>,
)

/**
 * شاشة الأشخاص (جلسة 16): الدواير فوق والقايمة بالأرصدة تحت — الحساب كله في `core/PeopleOverview.kt`، هنا القراية بس.
 * الأرصدة من `PersonAcrossSpaces` نفسه (سطر لكل بلد · عملة، **من غير جمع**). البلد الشغالة بس ([forSpace]) أو كل البلاد ([acrossSpaces]).
 */
class LoadPeopleOverview(private val deps: LoadPeopleOverviewDeps) {
    suspend fun forSpace(today: IsoDate, spaceId: String): PeopleOverview {
        val source = deps.spaces.firstOrNull { it.space.id == spaceId } ?: throw IllegalArgumentException(app.masroufy.core.uiText(app.masroufy.core.TextKey.SPACE_NOT_FOUND))
        return load(today, listOf(source))
    }

    suspend fun acrossSpaces(today: IsoDate): PeopleOverview = load(today, deps.spaces)

    private suspend fun load(today: IsoDate, sources: List<PeopleSpaceSource>): PeopleOverview {
        val people = deps.people.listAll()
        val across = PersonAcrossSpaces(sources.map { PersonSpaceBook(it.space, it.obligations, it.settlements) })
        val balances = people.flatMap { p ->
            across.debts(p.id).map { d ->
                PersonSpaceBalance(p.id, d.spaceId, d.spaceLabel, d.currency, d.receivableMinor, d.payableLoanMinor, d.payableCustodyMinor)
            }
        }
        val badges = mutableListOf<Pair<Id, SpaceGiftBadge>>()
        for (s in sources) {
            val events = s.events.listAll()
            if (events.isEmpty()) continue
            for (p in people) {
                val links = s.links.listByPerson(p.id)
                if (links.isEmpty()) continue
                val txns = s.txns.findByIds(links.map { it.transactionId }.distinct())
                personGiftBadges(p.id, events, links, txns).forEach { badges += p.id to SpaceGiftBadge(s.space.id, s.space.name, it) }
            }
        }
        return peopleOverview(
            PeopleOverviewInput(today, people, deps.profiles.listAll(), deps.relations.listAll(), deps.occasions.listAll(), balances, badges),
        )
    }
}
