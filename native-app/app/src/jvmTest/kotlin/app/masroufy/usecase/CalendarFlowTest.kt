package app.masroufy.usecase

import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.LeftoverMode
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Occasion
import app.masroufy.core.OccasionKind
import app.masroufy.core.Person
import app.masroufy.core.PrepItem
import app.masroufy.core.Project
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReservationError
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatYear
import app.masroufy.core.buildPeriod
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryLifeEventRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryOccasionRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryPrepItemRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryProjectRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryReservationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.MemoryZakatYearRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** التقويم والحساب من الفلوس و«فاضلك تقريبًا» على مستودعات الذاكرة (OVERRIDES §65) — كل الأسامي والمبالغ مخترعة. */
class CalendarFlowTest {
    private val today = "2026-10-04"
    private val out = Transaction(
        id = "t-out", occurredAt = "2026-09-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = 200_000, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
    )
    private val txns = MemoryTransactionRepository(listOf(out))
    private val wallets = MemoryWalletRepository(
        listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 1_000_000, "2026-01-01"), Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 50_000, "2026-01-01")),
    )
    private val plans = MemoryInstallmentPlanRepository(listOf(InstallmentPlan("ip-1", "تقسيط وهمي", "جهة وهمية", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 500_000, 500_000, 100_000, 1, "2026-10-25")))
    private val payments = MemoryInstallmentPaymentRepository()
    private val people = MemoryPersonRepository(listOf(Person("p-1", "سامي الوهمي")))
    private val obligations = MemoryObligationRepository(listOf(Obligation("o-1", "p-1", null, ObligationKind.LOAN_PAYABLE, 30_000, Currency.SAR)))
    private val dues = LoadDues(
        LoadDuesDeps(
            MemoryRoscaRepository(), MemoryRoscaEntryRepository(), plans, payments, MemoryDebtTermsRepository(listOf(DebtTerms("o-1", "p-1", "2026-10-26"))),
            people, obligations, MemorySettlementRepository(),
            MemoryRecurringRepository(listOf(RecurringItem("r-1", "اشتراك وهمي", "manual:x", "subscription", 1, 5_000, Currency.SAR, "2026-10-10", true, true))), txns,
        ),
    )
    private val events = MemoryLifeEventRepository(listOf(LifeEvent("ev-1", "فرح صاحب وهمي", "x", LifeEventKind.WEDDING, "2026-10-15", mine = false, createdAt = "c")))
    private val prep = MemoryPrepItemRepository()
    private val profile = MemoryProfileRepository(emptyProfile().copy(payday = 28))
    private val reservations = MemoryReservationRepository()
    private val calendar = LoadCalendar(
        LoadCalendarDeps(
            dues, MemoryProjectRepository(listOf(Project("pr-1", "تجديد وهمي", "x", false, "c", deadline = "2026-10-20"))), events, prep,
            MemoryOccasionRepository(listOf(Occasion("occ-1", "p-1", OccasionKind.BIRTHDAY, month = 10, day = 12, yearly = true, createdAt = "c"))),
            people, profile, reservations, "SA", Currency.SAR,
            zakatYears = MemoryZakatYearRepository(listOf(ZakatYear("2026-10-22", "2025-10-30", "2026-10-22", Currency.SAR, "c"))),
        ),
    )
    private val counting = ManageReservations(calendar, reservations, FixedClock("2026-10-04T09:00:00.000Z"))
    private val leftover = LoadLeftover(LoadLeftoverDeps(calendar, wallets, txns, reservations, profile, Currency.SAR))

    @Test fun monthShowsEverySourceAndTheSummaryCountsUnknownsApart() = runBlocking<Unit> {
        val month = calendar.month(2026, 10, today)
        assertEquals(
            listOf("recurring", "occasion", "event", "project", "zakat", "installment", "debt", "payday"),
            month.items.map { it.type.wire },
        )
        val outlook = month.summary.untilPayday!!
        assertEquals(135_000L, outlook.knownTotalMinor)
        assertEquals(2, outlook.unknownAmountCount)
        profile.save(emptyProfile().copy(payday = 28, islamicContentVisible = false))
        assertTrue(calendar.month(2026, 10, today).items.none { it.type == CalendarItemType.ZAKAT })
        val ramadan = calendar.items("2027-02-01", "2027-02-28", today).filter { it.type == CalendarItemType.PUBLIC_OCCASION }
        assertTrue(ramadan.isEmpty(), "المحتوى الإسلامي مقفول ⇒ مفيش رمضان")
    }

    @Test fun eventAmountAppearsOnceEveryPrepItemIsPriced() = runBlocking<Unit> {
        prep.saveMany(listOf(PrepItem("a", "ev-1", "هدية", 40_000, 1, false, "c"), PrepItem("b", "ev-1", "لبس", null, 2, false, "c")))
        assertNull(calendar.items("2026-10-15", "2026-10-15", today).single { it.type == CalendarItemType.EVENT }.amountMinor)
        prep.saveMany(listOf(PrepItem("b", "ev-1", "لبس", 10_000, 2, false, "c")))
        assertEquals(50_000L, calendar.items("2026-10-15", "2026-10-15", today).single { it.type == CalendarItemType.EVENT }.amountMinor)
    }

    @Test fun countingWithOneActionDefaultsToTheKnownAmountAndReplaces() = runBlocking<Unit> {
        val first = counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", today)
        assertEquals(100_000L, first.amountMinor)
        counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", today, 60_000)
        assertEquals(listOf(60_000L), reservations.listAll().map { it.amountMinor }, "حجز واحد للمرة دي — التاني بيستبدل")
        assertEquals(first.createdAt, reservations.listAll().single().createdAt)
        assertFailsWith<ReservationError>("حدث من غير مبلغ") { counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today) }
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 25_000)
        assertFailsWith<ReservationError> { counting.countUpcomingItem(CalendarItemType.PAYDAY, "payday", "2026-10-28", today, 1_000) }
        assertFailsWith<ReservationError> { counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-24", today) }
        val line = calendar.items("2026-10-25", "2026-10-25", today).single()
        assertEquals(100_000L to 60_000L, line.amountMinor to line.reservedMinor)
        counting.uncount(CalendarItemType.EVENT, "ev-1", "2026-10-15")
        assertFailsWith<ReservationError> { counting.uncount(CalendarItemType.EVENT, "ev-1", "2026-10-15") }
    }

    @Test fun countingNeverChangesDuesBudgetOrPeriodTotals() = runBlocking<Unit> {
        val period = buildPeriod(2026, 9, 28)
        val duesBefore = dues.load(today, period, Currency.SAR, 400_000)
        val totalsBefore = computePeriodTotals(txns.listByDateRange(period.start, period.end), emptyList())
        val summaryBefore = calendar.summary(today)
        val itemsBefore = calendar.items("2026-10-01", "2026-11-30", today)
        counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", today)
        counting.countUpcomingItem(CalendarItemType.DEBT, "o-1", "2026-10-26", today)
        assertEquals(duesBefore, dues.load(today, period, Currency.SAR, 400_000))
        assertEquals(totalsBefore, computePeriodTotals(txns.listByDateRange(period.start, period.end), emptyList()))
        val summaryAfter = calendar.summary(today)
        assertEquals(summaryBefore.untilPayday, summaryAfter.untilPayday)
        assertEquals(summaryBefore.heaviestWeek, summaryAfter.heaviestWeek)
        assertEquals(summaryBefore.beforePayday, summaryAfter.beforePayday.map { it.copy(reservedMinor = null) }, "السطر عليه العلامة بس")
        assertEquals(itemsBefore, calendar.items("2026-10-01", "2026-11-30", today).map { it.copy(reservedMinor = null) })
    }

    @Test fun leftoverForSalariedAndNotSalaried() = runBlocking<Unit> {
        counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", today)
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 25_000)
        counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-11-25", today)
        val salaried = leftover.load(today, salaried = true)
        assertEquals(LeftoverMode.UNTIL_MONTH_END, salaried.mode)
        assertEquals(850_000L, salaried.onHandMinor, "1,000,000 − 200,000 + 50,000")
        assertEquals(725_000L, salaried.leftoverMinor, "قسط نوفمبر بعد المرتب ما بيتطرحش")
        val free = leftover.load(today, salaried = false)
        assertEquals(LeftoverMode.FROM_WHAT_YOU_HAVE, free.mode)
        assertEquals(625_000L, free.leftoverMinor)
        assertTrue(leftover.load(today, salaried = true, unreconciledWalletIds = setOf("w-cash")).approximate)
        // قسط أكتوبر اتدفع ⇒ مش في التقويم ⇒ حجزه ما بيتطرحش تاني (الفلوس خرجت خلاص)
        payments.saveMany(listOf(InstallmentPayment("pay-1", "ip-1", "t-out", 100_000)))
        assertEquals(25_000L, leftover.load(today, salaried = true).countedMinor)
    }

    @Test fun walletNotOpenedYetMakesItNotAvailable() = runBlocking<Unit> {
        wallets.save(Wallet("w-new", "محفظة جاية", Currency.SAR, "digital_wallet", 0, "2026-12-01"))
        assertNull(leftover.load(today, salaried = true).leftoverMinor)
    }
}
