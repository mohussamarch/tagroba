package app.masroufy.usecase

import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
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
import app.masroufy.core.PayFrequency
import app.masroufy.core.Person
import app.masroufy.core.PrepItem
import app.masroufy.core.Project
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReservationError
import app.masroufy.core.ReviewState
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.ZakatYear
import app.masroufy.core.buildPeriod
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.emptyProfile
import app.masroufy.core.leftoverLabel
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryIncomeSourceRepository
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
    private val incomes = MemoryIncomeSourceRepository()
    private val calendar = LoadCalendar(
        LoadCalendarDeps(
            dues, MemoryProjectRepository(listOf(Project("pr-1", "تجديد وهمي", "x", false, "c", deadline = "2026-10-20"))), events, prep,
            MemoryOccasionRepository(listOf(Occasion("occ-1", "p-1", OccasionKind.BIRTHDAY, month = 10, day = 12, yearly = true, createdAt = "c"))),
            people, profile, reservations, "SA", Currency.SAR,
            zakatYears = MemoryZakatYearRepository(listOf(ZakatYear("2026-10-22", "2025-10-30", "2026-10-22", Currency.SAR, "c"))),
            incomeSources = incomes,
        ),
    )
    private val counting = ManageReservations(calendar, reservations, FixedClock("2026-10-04T09:00:00.000Z"))
    private val leftover = LoadLeftover(LoadLeftoverDeps(calendar, wallets, txns, reservations, profile, Currency.SAR, incomes))

    private fun source(id: String, kind: IncomeSourceKind, day: Int? = null, weekly: Int? = null, currency: Currency = Currency.SAR) = IncomeSource(
        id, "مصدر وهمي $id", "n-$id", kind, currency, "2025-01-01", expectedDayOfMonth = day,
        payFrequency = if (weekly != null) PayFrequency.WEEKLY else PayFrequency.MONTHLY, payWeekday = weekly,
    )

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
        // مفيش مصدر دخل ⇒ مش «بمرتب»
        val free = leftover.load(today)
        assertEquals(LeftoverMode.FROM_WHAT_YOU_HAVE, free.mode)
        assertEquals(625_000L, free.leftoverMinor)
        // وظيفة شغالة ⇒ «بمرتب» من مستودع مصادر الدخل، لحد يوم المرتب في الملف (28)
        incomes.saveMany(listOf(source("job", IncomeSourceKind.JOB)))
        val salaried = leftover.load(today)
        assertEquals(LeftoverMode.UNTIL_MONTH_END to "2026-10-28", salaried.mode to salaried.until)
        assertEquals(850_000L, salaried.onHandMinor, "1,000,000 − 200,000 + 50,000")
        assertEquals(725_000L, salaried.leftoverMinor, "قسط نوفمبر بعد المرتب ما بيتطرحش")
        assertEquals(uiText(TextKey.LEFTOVER_MONTH_END), leftoverLabel(salaried))
        assertTrue(leftover.load(today, unreconciledWalletIds = setOf("w-cash")).approximate)
        // قسط أكتوبر اتدفع ⇒ مش في التقويم ⇒ حجزه ما بيتطرحش تاني (الفلوس خرجت خلاص)
        payments.saveMany(listOf(InstallmentPayment("pay-1", "ip-1", "t-out", 100_000)))
        assertEquals(25_000L, leftover.load(today).countedMinor)
    }

    @Test fun weeklyPartTimeMovesTheHorizonToTheNextPay() = runBlocking<Unit> {
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 25_000)
        counting.countUpcomingItem(CalendarItemType.INSTALLMENT, "ip-1", "2026-10-25", today)
        // بارت تايم أسبوعي يوم الخميس (4) — النهارده الحد 2026-10-04 ⇒ القبض الجاي الخميس 2026-10-08، قبل المرتب
        incomes.saveMany(listOf(source("job", IncomeSourceKind.JOB), source("pt", IncomeSourceKind.PART_TIME, weekly = 4)))
        val p = leftover.load(today)
        assertEquals(LeftoverMode.UNTIL_NEXT_PAY to "2026-10-08", p.mode to p.until)
        assertEquals(850_000L, p.leftoverMinor, "مفيش محسوب قبل الخميس")
        assertEquals(uiText(TextKey.LEFTOVER_NEXT_PAY), leftoverLabel(p))
    }

    @Test fun pensionAndMonthlyRentCountAsSalariedButWeeklyRentAndOtherCurrencyDoNot() = runBlocking<Unit> {
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 25_000)
        incomes.saveMany(listOf(source("rent-w", IncomeSourceKind.RENT, weekly = 1), source("job-eg", IncomeSourceKind.JOB, currency = Currency.EGP)))
        assertEquals(LeftoverMode.FROM_WHAT_YOU_HAVE, leftover.load(today).mode, "إيجار أسبوعي · وظيفة بعملة تانية ⇒ مش «بمرتب» هنا")
        incomes.saveMany(listOf(source("pension", IncomeSourceKind.PENSION, day = 10)))
        val pension = leftover.load(today)
        assertEquals(LeftoverMode.UNTIL_NEXT_PAY to "2026-10-10", pension.mode to pension.until)
        assertEquals(850_000L, pension.leftoverMinor)
        incomes.saveMany(listOf(source("pension", IncomeSourceKind.PENSION, day = 20).copy(endedAt = "2026-01-01"), source("rent-m", IncomeSourceKind.RENT, day = 18)))
        val rent = leftover.load(today)
        assertEquals(LeftoverMode.UNTIL_NEXT_PAY to "2026-10-18", rent.mode to rent.until)
        assertEquals(825_000L, rent.leftoverMinor, "الحدث يوم 15 قبل الإيجار")
    }

    @Test fun incomePayDaysComeFromTheIncomeSourcesRepository() = runBlocking<Unit> {
        // رد المالك §64-٣: بارت تايم يوم 10 · معاش يوم 15 · وظيفة شهري (= سطر مرتب الحساب بس) — كل واحد بيومه ومن غير مبلغ
        incomes.saveMany(
            listOf(source("job", IncomeSourceKind.JOB), source("pt", IncomeSourceKind.PART_TIME, day = 10), source("pension", IncomeSourceKind.PENSION, day = 15)),
        )
        val month = calendar.month(2026, 10, today)
        assertEquals(
            listOf("2026-10-10 income_pay pt", "2026-10-15 income_pay pension", "2026-10-28 payday payday"),
            month.items.filter { it.type == CalendarItemType.INCOME_PAY || it.type == CalendarItemType.PAYDAY }.map { "${it.date} ${it.type.wire} ${it.sourceId}" },
        )
        assertTrue(month.items.filter { it.type == CalendarItemType.INCOME_PAY }.all { it.amountMinor == null })
        assertFailsWith<ReservationError>("فلوس جاية ليك") { counting.countUpcomingItem(CalendarItemType.INCOME_PAY, "pt", "2026-10-10", today, 1_000) }
        assertEquals(850_000L, leftover.load(today).onHandMinor, "أيام القبض ما بتغيّرش الفلوس اللي معاك")
    }

    @Test fun walletNotOpenedYetMakesItNotAvailable() = runBlocking<Unit> {
        incomes.saveMany(listOf(source("job", IncomeSourceKind.JOB)))
        wallets.save(Wallet("w-new", "محفظة جاية", Currency.SAR, "digital_wallet", 0, "2026-12-01"))
        assertNull(leftover.load(today).leftoverMinor)
    }
}
