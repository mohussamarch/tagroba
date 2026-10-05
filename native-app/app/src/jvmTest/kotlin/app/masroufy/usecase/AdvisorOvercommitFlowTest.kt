package app.masroufy.usecase

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.CalendarItemType
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.LifeEvent
import app.masroufy.core.LifeEventKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.buildPeriod
import app.masroufy.core.emptyProfile
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
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
import app.masroufy.memory.MemoryUsualHours
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * overcommitted من الحجز للصفحة (OVERRIDES §68) على مستودعات الذاكرة: مرة لما يحصل، وتاني بس لو النقص كبر 25% —
 * بإيصالات المحرك نفسها. كل الأسامي والمبالغ مخترعة.
 */
class AdvisorOvercommitFlowTest {
    private val today = "2026-10-04"
    private val period = buildPeriod(2026, 9, 28)
    private val txns = MemoryTransactionRepository()
    private val wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 410_000, "2026-01-01")))
    private val reservations = MemoryReservationRepository()
    private val profile = MemoryProfileRepository(emptyProfile().copy(payday = 28))
    private val incomes = MemoryIncomeSourceRepository(listOf(IncomeSource("s-1", "شركة وهمية", "n", IncomeSourceKind.JOB, Currency.SAR, "2025-01-01")))
    private val events = MemoryLifeEventRepository(
        listOf(
            LifeEvent("ev-1", "فرح صاحب وهمي", "x", LifeEventKind.WEDDING, "2026-10-15", mine = false, createdAt = "c"),
            LifeEvent("ev-2", "سفر وهمي", "y", LifeEventKind.OTHER, "2026-10-20", mine = false, createdAt = "c"),
        ),
    )
    private val people = MemoryPersonRepository()
    private val dues = LoadDues(
        LoadDuesDeps(
            MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(), MemoryDebtTermsRepository(),
            people, MemoryObligationRepository(), MemorySettlementRepository(), MemoryRecurringRepository(), txns,
        ),
    )
    private val calendar = LoadCalendar(
        LoadCalendarDeps(dues, MemoryProjectRepository(), events, MemoryPrepItemRepository(), MemoryOccasionRepository(), people, profile, reservations, "SA", Currency.SAR, incomeSources = incomes),
    )
    private val clock = FixedClock("2026-10-04T09:00:00.000Z")
    private val counting = ManageReservations(calendar, reservations, clock)
    private val receipts = MemoryAlertReceipts()
    private val inbox = MemoryAlertInbox()
    private val engine = RunAlertEngine(AlertEngineDeps(MemoryAlertSettings(), MemoryAlertInteractions(), MemoryUsualHours(), receipts, inbox, clock))

    private fun advisor(leftoverWallets: MemoryWalletRepository = wallets) = AdvisorSignals(
        AdvisorSignalsDeps(
            txns, MemoryAllocationRepository(), MemoryCategoryRepository(), profile,
            leftover = LoadLeftover(LoadLeftoverDeps(calendar, leftoverWallets, txns, reservations, profile, Currency.SAR, incomes)),
            receipts = receipts,
        ),
    )

    private suspend fun runDay(advisor: AdvisorSignals = advisor()) =
        engine.run(advisor.alertCandidates(AlertGatherInput(today, period, Currency.SAR)), LocalMoment(today, 14))

    private suspend fun spend(amount: Long) = txns.saveMany(
        listOf(
            Transaction(
                id = "t-$amount", occurredAt = today, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.PURCHASE,
                economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
                excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
            ),
        ),
    )

    private suspend fun advisorLines() = inbox.listAll().filter { it.kind.group == AlertGroup.ADVISOR }

    @Test fun firesOnceThenOnlyWhenTheGapGrowsAndDisappearsWhenSolved() = runBlocking<Unit> {
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 300_000)
        assertEquals(emptyList(), runDay().posts, "حجز 3,000 ومعاك 4,100 ⇒ مفيش نقص")
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-2", "2026-10-20", today, 220_000)
        val first = runDay()
        assertEquals(1, first.posts.size, "أول مرة يحصل ⇒ يتبعت")
        val line = advisorLines().single()
        assertEquals(AlertKind.OVERCOMMITTED, line.kind)
        assertEquals("حجزت 5,200.00 ر.س لهذا الشهر ومعك 4,100.00 ر.س. ينقصك 1,100.00 ر.س", line.body)

        assertEquals(emptyList(), runDay().posts, "نفس النقص تاني يوم ⇒ ما يتبعتش")
        spend(27_499) // النقص 1,374.99 < 1,100 × 1.25
        assertEquals(emptyList(), runDay().posts, "زاد أقل من 25% ⇒ ساكت")
        assertEquals(1, advisorLines().size)
        spend(1) // 1,375.00 = +25% بالظبط
        val grown = runDay()
        assertEquals(1, grown.posts.size, "كبر بوضوح ⇒ يتبعت تاني")
        assertTrue(advisorLines().single().body.endsWith("ينقصك 1,375.00 ر.س"), "سطر واحد في الصفحة بالنقص الجديد")

        counting.uncount(CalendarItemType.EVENT, "ev-2", "2026-10-20")
        val solved = runDay()
        assertEquals(emptyList(), advisorLines(), "اتحل ⇒ اختفى")
        assertEquals(1, solved.resolved.size)
    }

    @Test fun unknownBalanceGivesNoAlert() = runBlocking<Unit> {
        counting.countUpcomingItem(CalendarItemType.EVENT, "ev-1", "2026-10-15", today, 900_000)
        // المحفظة اتفتحت في التطبيق بعد النهارده ⇒ رصيدها النهارده مش معروف ⇒ «غير متاح» مش نقص
        val later = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 410_000, "2026-11-01")))
        assertEquals(emptyList(), advisor(later).alertCandidates(AlertGatherInput(today, period, Currency.SAR)))
        assertEquals(emptyList(), AdvisorSignals(AdvisorSignalsDeps(txns, MemoryAllocationRepository(), MemoryCategoryRepository())).alertCandidates(AlertGatherInput(today, period, Currency.SAR)))
    }
}
