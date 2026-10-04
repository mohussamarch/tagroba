package app.masroufy.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.masroufy.core.CalendarItemType
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventRole
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Project
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.usecase.EventGifts
import app.masroufy.usecase.EventGiftsDeps
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadCalendarDeps
import app.masroufy.usecase.LoadDues
import app.masroufy.usecase.LoadDuesDeps
import app.masroufy.usecase.LoadLeftover
import app.masroufy.usecase.LoadLeftoverDeps
import app.masroufy.usecase.ManageEventPrep
import app.masroufy.usecase.ManageEventPrepDeps
import app.masroufy.usecase.ManageReservations
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * التقويم والحساب من الفلوس وتجهيزات الحدث (OVERRIDES §65) بحالات الاستخدام نفسها على Firestore Emulator. أسماء وأرقام مخترعة.
 * التشغيل زي `EventRepositoriesTest` (HANDOVER: Firestore Emulator على 8088 + محاكي أندرويد).
 */
@RunWith(AndroidJUnit4::class)
class CalendarRepositoriesTest {
    private fun space() = FirestoreSpace.forUser(Emulator.firestore(), "kt-" + java.util.UUID.randomUUID())

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(90_000) { block() } }

    @Test fun calendarCountingAndPrepWorkOnFirestore() = run {
        val c = FirestoreContainer(space())
        val today = "2026-10-04"
        c.wallets.save(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2026-01-01"))
        c.recurring.save(RecurringItem("r-1", "اشتراك وهمي", "manual:x", "subscription", 1, 5_000, Currency.SAR, "2026-10-10", true, true))
        c.projects.save(Project("pr-1", "تجديد وهمي", "x", false, "c", deadline = "2026-10-20"))
        c.lifeEvents.save(LifeEvent("ev-1", "فرحي الوهمي", "y", LifeEventKind.WEDDING, "2026-10-15", mine = true, createdAt = "c"))
        c.profile.save(emptyProfile().copy(payday = 28))
        val dues = LoadDues(LoadDuesDeps(c.roscas, c.roscaEntries, c.installmentPlans, c.installmentPayments, c.debtTerms, c.people, c.obligations, c.settlements, c.recurring, c.transactions))
        val calendar = LoadCalendar(LoadCalendarDeps(dues, c.projects, c.lifeEvents, c.eventPrep, c.occasions, c.people, c.profile, c.reservations, "SA", Currency.SAR, c.zakatYears, c.zakatPayments))
        assertEquals(listOf("recurring", "event", "project", "payday"), calendar.month(2026, 10, today).items.map { it.type.wire })
        assertEquals("2026-10-20", c.projects.listAll().single().deadline, "آخر ميعاد المشروع بيرجع من التخزين")

        val clock = FixedClock("2026-10-04T10:00:00.000Z")
        val counting = ManageReservations(calendar, c.reservations, clock)
        counting.countUpcomingItem(CalendarItemType.RECURRING, "r-1", "2026-10-10", today)
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 100_000)
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 120_000)
        assertEquals(2, c.reservations.listAll().size, "التاني بيستبدل")
        // «بمرتب» بييجي من مصادر الدخل (§65): وظيفة شغالة ⇒ المحسوب قبل المرتب الجاي بس
        c.incomeSources.saveMany(listOf(IncomeSource("src-1", "شركة وهمية", "شركه وهميه", IncomeSourceKind.JOB, Currency.SAR, "2025-01-01", createdAt = "c")))
        val left = LoadLeftover(LoadLeftoverDeps(calendar, c.wallets, c.transactions, c.reservations, c.profile, Currency.SAR, c.incomeSources)).load(today)
        assertEquals(500_000L - 5_000 - 120_000, left.leftoverMinor)
        counting.uncount(CalendarItemType.RECURRING, "r-1", "2026-10-10")
        assertEquals(listOf(120_000L), c.reservations.listAll().map { it.amountMinor })

        val ids = SequentialIdGenerator()
        val prep = ManageEventPrep(ManageEventPrepDeps(c.lifeEvents, c.eventLinks, c.eventPrep, c.transactions, PassthroughUnitOfWork(), ids, clock))
        val saved = prep.addMany("ev-1", prep.suggestions("ev-1", today))
        assertTrue(saved.isNotEmpty() && c.eventPrep.listByEvent("ev-1").all { it.plannedMinor == null })
        c.transactions.saveMany(
            listOf(
                Transaction(
                    id = "t-hall", occurredAt = "2026-10-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
                    economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 1_000_000, currency = Currency.SAR, categoryConfirmed = false,
                    excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
                ),
            ),
        )
        val gifts = EventGifts(EventGiftsDeps(c.lifeEvents, c.eventLinks, c.transactions, c.wallets, c.people, PassthroughUnitOfWork(), ids, clock, c.categories))
        gifts.link("ev-1", "t-hall", EventRole.SPEND, sharePercent = 30)
        prep.assignSpend("ev-1", "t-hall", saved.first().id)
        assertEquals(300_000L, prep.summary("ev-1", Currency.SAR).items.first().spentMinor)
        prep.remove(saved.first().id)
        assertNull(c.eventLinks.listByEvent("ev-1").single().prepItemId, "الحقل بيتمسح من المستند")
    }
}
