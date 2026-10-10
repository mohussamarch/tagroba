package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_SPACE_ID
import app.masroufy.core.DueFlow
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.Person
import app.masroufy.core.defaultSpace
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
import app.masroufy.ui.components.AmountTone
import app.masroufy.usecase.LoadPeopleOverview
import app.masroufy.usecase.LoadPeopleOverviewDeps
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.PeopleSpaceSource
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «الأشخاص» و«لك» و«عليك»: من نتايج `LoadPeopleOverview.forSpace` و`ManagePeople.listWithBalances` (مستودعات الذاكرة، أسماء ومبالغ مخترعة)
 * لحالة الشاشة — الترتيب والشرايح والإجماليات (من غير أي حساب في الشاشة) وحالة الخطأ «غير متاح» مش صفر.
 */
class PeoplePresenterTest {
    @BeforeTest fun start() = resetTexts()

    @AfterTest fun end() = resetTexts()

    private val persons = listOf(
        Person("p-1", "سارة وهمية"), Person("p-2", "خالد وهمي"), Person("p-3", "منى وهمية"), Person("p-4", "سامي وهمي"),
        Person("p-5", "علي وهمي", archived = true), Person("p-6", "هند وهمية"), Person("p-7", "رامي وهمي"),
    )
    private val obligations = listOf(
        Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 60_000, Currency.SAR),
        Obligation("o-2", "p-2", "t-9", ObligationKind.LOAN_PAYABLE, 200_000, Currency.SAR),
        Obligation("o-3", "p-6", "t-8", ObligationKind.RECEIVABLE, 15_000, Currency.SAR),
    )
    private val people = MemoryPersonRepository(persons)
    private val obligationRepo = MemoryObligationRepository(obligations)
    private val settlements = MemorySettlementRepository()

    private fun overview() = LoadPeopleOverview(
        LoadPeopleOverviewDeps(
            people, MemoryPersonProfileRepository(), MemoryPersonRelationRepository(),
            MemoryOccasionRepository(listOf(Occasion("occ-1", "p-3", OccasionKind.BIRTHDAY, month = 10, day = 15, yearly = true, createdAt = "c"))),
            listOf(PeopleSpaceSource(defaultSpace(), obligationRepo, settlements, MemoryLifeEventRepository(), MemoryEventLinkRepository(), MemoryTransactionRepository())),
        ),
    )

    private fun managePeople() = ManagePeople(
        ManagePeopleDeps(
            people, obligationRepo, settlements, MemorySettlementWriter(obligationRepo, settlements), MemoryAllocationRepository(),
            MemoryTransactionRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), testClock,
        ),
    )

    @Test fun balancesFirstThenOccasionsAndTheArchivedWithoutMoneyStayOut() = runBlocking<Unit> {
        val ui = peopleTabUi(overview().forSpace(TODAY, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID, Currency.SAR)
        assertEquals(listOf("p-1", "p-6", "p-2", "p-3"), ui.orbit.take(4).map { it.id }, "لك ثم عليك ثم مناسبة قريبة")
        assertEquals(ORBIT_SIZE, ui.orbit.size)
        assertTrue(ui.orbit.none { it.id == "p-5" } && ui.rest.none { it.id == "p-5" }, "المؤرشف من غير رصيد ما بيظهرش")
        assertEquals(6, ui.total)
        assertEquals("لك 600.00", ui.orbit[0].chip)
        assertEquals(AmountTone.INCOME, ui.orbit[0].tone)
        assertEquals("عليك 2,000.00", ui.orbit[2].chip)
        assertNull(ui.orbit[3].chip, "المناسبة القريبة من غير شريحة فلوس")
        assertEquals("س", ui.orbit[0].initial)
        assertEquals(listOf(MoneyLine(75_000, Currency.SAR)), ui.owed, "الإجمالي من `PeopleOverview.totals` زي ما هو")
        assertEquals(listOf(MoneyLine(200_000, Currency.SAR)), ui.owe)
        assertEquals(2, ui.owedPeople)
        assertEquals(1, ui.owePeople)
        assertTrue(!ui.balancesFailed)
    }

    @Test fun nobodyMeansAnEmptyTabWithAKnownZero() = runBlocking<Unit> {
        val empty = LoadPeopleOverview(
            LoadPeopleOverviewDeps(
                MemoryPersonRepository(), MemoryPersonProfileRepository(), MemoryPersonRelationRepository(), MemoryOccasionRepository(),
                listOf(PeopleSpaceSource(defaultSpace(), MemoryObligationRepository(), MemorySettlementRepository(), MemoryLifeEventRepository(), MemoryEventLinkRepository(), MemoryTransactionRepository())),
            ),
        )
        val ui = peopleTabUi(empty.forSpace(TODAY, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID, Currency.SAR)
        assertEquals(0, ui.total)
        assertEquals(listOf(MoneyLine(0, Currency.SAR)), ui.owed, "مفيش ديون خالص = صفر معروف (مش «غير متاح»)")
    }

    @Test fun whenBalancesFailThePeopleShowWithoutAnyNumber() = runBlocking<Unit> {
        val ui = peopleWithoutBalances(managePeople().listWithBalances())
        assertTrue(ui.balancesFailed)
        assertNull(ui.owed, "«غير متاح» مش صفر")
        assertNull(ui.owe)
        assertTrue((ui.orbit + ui.rest).all { it.chip == null })
        assertTrue((ui.orbit + ui.rest).none { it.id == "p-5" }, "المؤرشفين برّه")
        assertEquals(6, ui.total)
    }

    @Test fun owedListPutsOverdueFirstAndNeverNetsTheTwoSides() = runBlocking<Unit> {
        val rows = managePeople().listWithBalances()
        val dues = listOf(
            DueItem(DueSource.DEBT, "o-3", "دين", "2026-10-02", 15_000, Currency.SAR, DueFlow.RECEIVE, DueStatus.OVERDUE),
            DueItem(DueSource.DEBT, "o-1", "دين", "2026-10-12", 60_000, Currency.SAR, DueFlow.RECEIVE, DueStatus.SOON),
        )
        val ov = overview().forSpace(TODAY, DEFAULT_SPACE_ID)
        val owed = owedUi(OwedSide.OWED_TO_YOU, rows, ov, DEFAULT_SPACE_ID, dues, TODAY)
        assertEquals(listOf("o-3", "o-1"), owed.rows.map { it.obligationId }, "اللي فات ميعاده الأول")
        assertEquals(OwedSignal.OVERDUE, owed.rows[0].signal)
        assertEquals("فات موعدها منذ 7 أيام".replace("7", app.masroufy.core.sentenceNumber(7)), owed.rows[0].signalText)
        assertEquals(OwedSignal.SOON, owed.rows[1].signal)
        assertEquals("دين قديم", owed.rows[1].reason, "من غير عملية ⇒ دين قديم")
        assertEquals(listOf(MoneyLine(75_000, Currency.SAR)), owed.totals)
        assertEquals(2, owed.people)

        val owe = owedUi(OwedSide.YOU_OWE, rows, ov, DEFAULT_SPACE_ID, dues, TODAY)
        assertEquals(listOf("o-2"), owe.rows.map { it.obligationId })
        assertNull(owe.rows[0].signal, "من غير ميعاد")
        assertEquals(listOf(MoneyLine(200_000, Currency.SAR)), owe.totals, "«عليك» لوحده — من غير مقاصة مع «لك»")
        assertNull(owedUi(OwedSide.YOU_OWE, rows, null, DEFAULT_SPACE_ID, dues, TODAY).totals, "الإجمالي ما اتحمّلش ⇒ «غير متاح»")
    }

    @Test fun egyptianAndEnglishWordingFollowTheVariant() = runBlocking<Unit> {
        egyptian()
        val eg = peopleTabUi(overview().forSpace(TODAY, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID, Currency.SAR)
        assertEquals("ليك 600.00", eg.orbit[0].chip)
        english()
        val en = peopleTabUi(overview().forSpace(TODAY, DEFAULT_SPACE_ID), DEFAULT_SPACE_ID, Currency.SAR)
        assertEquals("Owes you 600.00", en.orbit[0].chip)
        assertEquals("You owe 2,000.00", en.orbit[2].chip)
    }
}
