package app.masroufy.usecase

import app.masroufy.core.BalanceDirection
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.PeopleTotal
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonCircleError
import app.masroufy.core.PersonOutlineState
import app.masroufy.core.ReviewState
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.defaultSpace
import app.masroufy.core.eventLinkId
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryOccasionRepository
import app.masroufy.memory.MemoryPersonProfileRepository
import app.masroufy.memory.MemoryPersonRelationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** شاشة الأشخاص (جلسة 16) بحالات الاستخدام على مستودعات الذاكرة — أسماء ومبالغ مخترعة. */
class PeopleOverviewFlowTest {
    private val clock = FixedClock("2026-10-05T10:00:00.000Z")
    private val egypt = Space("eg", "مصر", "EG", Currency.EGP, "2026-10-04T10:00:00.000Z")
    private val people = MemoryPersonRepository(listOf(Person("p-1", "أم وهمية"), Person("p-2", "بنت وهمية"), Person("p-3", "زميل وهمي")))
    private val profiles = MemoryPersonProfileRepository()
    private val relations = MemoryPersonRelationRepository()
    private val circles = ManagePersonCircles(ManagePersonCirclesDeps(people, profiles, relations, clock))

    private fun managePeople() = ManagePeople(
        ManagePeopleDeps(
            people, MemoryObligationRepository(), MemorySettlementRepository(),
            MemorySettlementWriter(MemoryObligationRepository(), MemorySettlementRepository()), MemoryAllocationRepository(), MemoryTransactionRepository(),
            PassthroughUnitOfWork(), SequentialIdGenerator(), clock,
        ),
    )

    @Test fun circleAndRelationNeverTouchThePersonDocument() = runBlocking<Unit> {
        val before = people.listAll()
        val p = circles.setProfile("p-1", PersonCircle.FAMILY, "  أمي ")!!
        assertEquals("أمي", p.relationLabel)
        circles.relate("p-2", "p-1", "أمها")
        assertEquals(before, people.listAll(), "مستند الشخص زي ما هو")
        assertNull(circles.setProfile("p-1", null, " "), "الاتنين فاضيين ⇒ المستند بيتشال")
        assertTrue(profiles.listAll().isEmpty())
        assertFailsWith<PersonCircleError> { circles.setProfile("p-404", PersonCircle.WORK, null) }
        assertFailsWith<PersonCircleError> { circles.setProfile("p-1", PersonCircle.WORK, "ا".repeat(31)) }
    }

    @Test fun relatingTwiceInAnyOrderUpdatesTheSameRelation() = runBlocking<Unit> {
        val first = circles.relate("p-1", "p-2", "أمها")
        val again = circles.relate("p-2", "p-1", "بنتها")
        assertEquals(first.id, again.id)
        assertEquals(first.createdAt, again.createdAt)
        assertEquals(listOf("بنتها"), relations.listAll().map { it.label })
        assertFailsWith<PersonCircleError> { circles.relate("p-1", "p-1", null) }
        assertFailsWith<PersonCircleError> { circles.relate("p-1", "p-404", null) }
        circles.unrelate("p-2", "p-1")
        assertTrue(relations.listAll().isEmpty())
        assertFailsWith<PersonCircleError> { circles.unrelate("p-1", "p-2") }
    }

    @Test fun archivingAPersonHidesTheirRelationsAndUnarchivingBringsThemBack() = runBlocking<Unit> {
        circles.relate("p-1", "p-2", "أمها")
        circles.relate("p-1", "p-3", null)
        managePeople().archivePerson("p-2", true)
        assertEquals(listOf("p-3"), circles.visible("p-1").map { it.personBId })
        assertEquals(2, relations.listAll().size, "الأرشفة ما بتمسحش")
        assertFailsWith<PersonCircleError> { circles.relate("p-2", "p-3", null) }
        managePeople().archivePerson("p-2", false)
        assertEquals(listOf("p-2", "p-3"), circles.visible("p-1").map { it.personBId }.sorted())
    }

    private fun txn(id: String, currency: Currency, direction: Direction, kind: EconomicKind, amount: Long) = Transaction(
        id = id, occurredAt = "2026-09-01", datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
        observedDirection = direction, amountMinor = amount, currency = currency, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c",
    )

    private fun overview(): LoadPeopleOverview {
        val saGift = txn("t-1", Currency.SAR, Direction.IN, EconomicKind.EVENT_GIFT, 200_000)
        val sa = PeopleSpaceSource(
            defaultSpace(),
            MemoryObligationRepository(listOf(Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 60_000, Currency.SAR))),
            MemorySettlementRepository(),
            MemoryLifeEventRepository(listOf(LifeEvent("ev-1", "فرح وهمي", "فرح وهمي", LifeEventKind.WEDDING, "2026-05-01", mine = true, createdAt = "c"))),
            MemoryEventLinkRepository(listOf(EventLink(eventLinkId("t-1"), "ev-1", "t-1", EventRole.GIFT_IN, "p-2", "c"))),
            MemoryTransactionRepository(listOf(saGift)),
        )
        val eg = PeopleSpaceSource(
            egypt,
            MemoryObligationRepository(
                listOf(
                    Obligation("o-1", "p-1", null, ObligationKind.LOAN_PAYABLE, 30_000, Currency.EGP),
                    Obligation("o-2", "p-1", null, ObligationKind.RECEIVABLE, 5_000, Currency.SAR),
                ),
            ),
            MemorySettlementRepository(), MemoryLifeEventRepository(), MemoryEventLinkRepository(), MemoryTransactionRepository(),
        )
        val occasions = MemoryOccasionRepository(listOf(Occasion("occ-1", "p-3", OccasionKind.BIRTHDAY, month = 10, day = 20, yearly = true, createdAt = "c")))
        return LoadPeopleOverview(LoadPeopleOverviewDeps(people, profiles, relations, occasions, listOf(sa, eg)))
    }

    @Test fun activeCountryShowsOnlyItsBalancesAndAcrossShowsEachCountryApart() = runBlocking<Unit> {
        circles.setProfile("p-1", PersonCircle.FAMILY, "أمي")
        circles.relate("p-1", "p-2", "أمها")
        val saudi = overview().forSpace("2026-10-05", DEFAULT_SPACE_ID)
        val mom = saudi.rows.single { it.person.id == "p-1" }
        assertEquals(listOf(BalanceDirection.OWED_TO_YOU), mom.balances.map { it.direction })
        assertEquals(PersonOutlineState.OWED_TO_YOU, mom.state)
        assertEquals(listOf(PeopleTotal(DEFAULT_SPACE_ID, defaultSpace().name, Currency.SAR, 60_000, 0)), saudi.totals)
        assertEquals(PersonOutlineState.OCCASION_SOON, saudi.rows.single { it.person.id == "p-3" }.state)
        assertEquals(listOf(200_000L), saudi.rows.single { it.person.id == "p-2" }.giftBadges.map { it.badge.amountMinor })
        assertEquals(1, saudi.relations.size)

        val all = overview().acrossSpaces("2026-10-05")
        val momAll = all.rows.single { it.person.id == "p-1" }
        assertEquals(PersonOutlineState.YOU_OWE, momAll.state, "عليها دين في مصر ⇒ عليك أولًا")
        assertEquals(
            listOf(
                PeopleTotal(DEFAULT_SPACE_ID, defaultSpace().name, Currency.SAR, 60_000, 0),
                PeopleTotal("eg", "مصر", Currency.EGP, 0, 30_000),
                PeopleTotal("eg", "مصر", Currency.SAR, 5_000, 0),
            ),
            all.totals,
            "ريال مصر ما بيتجمعش مع ريال السعودية",
        )
        assertEquals(PersonCircle.FAMILY, momAll.circle)
        assertFailsWith<IllegalArgumentException> { overview().forSpace("2026-10-05", "xx") }
    }
}
