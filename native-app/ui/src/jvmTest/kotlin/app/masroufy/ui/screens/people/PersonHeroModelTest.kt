package app.masroufy.ui.screens.people

import app.masroufy.core.Currency
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.PersonProfile
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemorySettlementWriter
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.UpcomingOccasion
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** r06-person (OVERRIDES §116-ب): مكان المناسبات (سنوية فوق · مرة واحدة في الشريط) · السطر الثابت · من غير سطر الصافي المكرر · 4 أيقونات. بيانات مخترعة. */
class PersonHeroModelTest {
    @BeforeTest fun start() = resetTexts()

    @AfterTest fun end() = resetTexts()

    private fun occ(id: String, kind: OccasionKind, month: Int, day: Int, yearly: Boolean, year: Int? = null) =
        Occasion(id, "p-1", kind, null, month, day, year, yearly, null, null, "c")

    private val birthday = UpcomingOccasion(occ("o-b", OccasionKind.BIRTHDAY, 3, 2, yearly = true), "2027-03-02", "خالد وهمي")
    private val wedding = UpcomingOccasion(occ("o-w", OccasionKind.WEDDING, 10, 18, yearly = false, year = 2026).copy(leadDays = 14), "2026-10-18", "خالد وهمي")
    private val grad = UpcomingOccasion(occ("o-g", OccasionKind.OTHER, 6, 5, yearly = false, year = 2027), "2027-06-05", "خالد وهمي")

    @Test fun yearlyGoUpTopAndOneTimeGoToTheStrip() {
        val ui = personOccasionsUi(listOf(wedding, birthday, grad), TODAY)
        assertEquals(listOf("o-b"), ui.yearly.map { it.id }, "السنوية بس فوق في الأخضر")
        assertEquals(listOf("o-w", "o-g"), ui.once.map { it.id }, "مرة واحدة في الشريط")
        assertEquals(listOf("o-b", "o-w", "o-g"), ui.cards.map { it.id }, "الستارة: السنوية الأول وبعدين المرة الواحدة")
        assertEquals(OccGlyph.CAKE, ui.yearly.single().glyph)
        assertTrue(ui.once.first().wedding)
    }

    @Test fun noticeOnlyForASoonOneTimeOccasion() {
        // الفرح بعد 9 أيام (جوه تذكيره (14 يوم)) ⇒ تنبيه · عيد الميلاد السنوي ما بيعملش تنبيه هنا
        val soon = personOccasionsUi(listOf(birthday, wedding), TODAY)
        assertNotNull(soon.notice)
        assertEquals("o-w", soon.notice!!.id)
        assertNull(personOccasionsUi(listOf(birthday, grad), TODAY).notice)
    }

    @Test fun calendarMarksTheDaysOfThatMonth() {
        val marks = occasionMarks(listOf(birthday, wedding, grad), 2026, 10)
        assertEquals(listOf(18), marks.map { it.day })
        assertEquals(listOf(2), occasionMarks(listOf(birthday), 2031, 3).map { it.day }, "السنوية كل سنة")
        assertTrue(occasionMarks(listOf(grad), 2026, 6).isEmpty(), "المرة الواحدة في سنتها بس")
    }

    @Test fun fixedBalancesRowEvenWithZeroDebtsAndNoticeComesAfterIt() = runBlocking<Unit> {
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val people = ManagePeople(
            ManagePeopleDeps(
                MemoryPersonRepository(listOf(Person("p-1", "خالد وهمي"))), obligations, settlements, MemorySettlementWriter(obligations, settlements),
                MemoryAllocationRepository(), MemoryTransactionRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), testClock,
            ),
        )
        val ui = personPageUi(people.listWithBalances().single(), null, overviewLoaded = true, PersonProfile("p-1", PersonCircle.FRIEND, null, "c"), emptyList(), emptyList(), emptyList(), TODAY)
        assertEquals(emptyList(), ui.owed)
        val plain = personSections(ui, hasNotice = false, hasLedger = true)
        assertEquals(PersonSection.BALANCES, plain[2], "كروت «لك/عليك» مكانها ثابت")
        val withNotice = personSections(ui, hasNotice = true, hasLedger = true)
        assertEquals(PersonSection.BALANCES, withNotice[2], "التنبيه ما بيزقش الكروت")
        assertEquals(PersonSection.NOTICE, withNotice[3])
        assertEquals(withNotice.indexOf(PersonSection.ONCE), withNotice.indexOf(PersonSection.NOTICE) + 1)
    }

    @Test fun duplicateTotalsRowIsGone() {
        val ledger = LedgerUi(emptyList(), listOf(LedgerTotalsUi(Currency.SAR, 50_000, 0, 50_000)), emptyList(), emptyList(), empty = false)
        assertFalse("ledger-totals" in ledgerItemKeys(ledger), "سطر لك/عليك/الصافي اتشال — الكروت فوق بس")
        assertEquals(listOf("ledger-history"), ledgerItemKeys(ledger))
    }

    @Test fun lookComesFromTheProfile() = runBlocking<Unit> {
        val obligations = MemoryObligationRepository()
        val settlements = MemorySettlementRepository()
        val people = ManagePeople(
            ManagePeopleDeps(
                MemoryPersonRepository(listOf(Person("p-1", "خالد وهمي"))), obligations, settlements, MemorySettlementWriter(obligations, settlements),
                MemoryAllocationRepository(), MemoryTransactionRepository(), PassthroughUnitOfWork(), SequentialIdGenerator(), testClock,
            ),
        )
        val profile = PersonProfile("p-1", null, null, "c", heroColor = 2, badge = "⚽")
        val ui = personPageUi(people.listWithBalances().single(), null, overviewLoaded = true, profile, emptyList(), emptyList(), emptyList(), TODAY)
        assertEquals(PersonLookUi(2, "⚽"), ui.look)
    }

    @Test fun fanHasExactlyFourActions() {
        assertEquals(listOf(FanAction.DEBT, FanAction.SPLIT, FanAction.ROSCA, FanAction.PROJECT), fanActions())
        assertEquals(4, fanActions().size)
        assertEquals(165f, fanAngle(0, 4))
        assertEquals(257f, fanAngle(3, 4))
    }
}
