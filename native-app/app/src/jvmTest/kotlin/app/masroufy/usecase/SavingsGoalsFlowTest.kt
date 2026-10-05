package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.GoalState
import app.masroufy.core.ReviewState
import app.masroufy.core.SavingsGoalError
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryGoalContributionRepository
import app.masroufy.memory.MemorySavingsGoalRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** «خطة الادخار» من الإنشاء للملخص (OVERRIDES §68) على مستودعات الذاكرة — أرقام وأسامي مخترعة. */
class SavingsGoalsFlowTest {
    private val goals = MemorySavingsGoalRepository()
    private val contributions = MemoryGoalContributionRepository()
    private val wallets = MemoryWalletRepository(
        listOf(Wallet("w-save", "ادخار وهمي", Currency.SAR, "bank", 100_000, "2026-01-01"), Wallet("w-egp", "جنيه وهمي", Currency.EGP, "bank", 0, "2026-01-01")),
    )
    private val txns = MemoryTransactionRepository(
        listOf(
            Transaction(
                id = "t-in", occurredAt = "2026-04-01", datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.INTERNAL_TRANSFER,
                economicKindConfirmed = true, observedDirection = Direction.IN, amountMinor = 250_000, currency = Currency.SAR, categoryConfirmed = false,
                excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-save",
            ),
        ),
    )
    private val ledger = GoalLedger("default", wallets, txns)
    private val manage = ManageSavingsGoals(ManageSavingsGoalsDeps(goals, contributions, SequentialIdGenerator(), FixedClock("2026-10-05T08:00:00.000Z"), listOf(ledger)))
    private val overview = LoadGoalsOverview(LoadGoalsOverviewDeps(goals, contributions, listOf(ledger)))

    private val manual = GoalInput("سفر وهمي", 1_200_000, Currency.SAR, "2026-01-01", "2026-12-31")

    @Test fun manualGoalFromCreateToOverview() = runBlocking<Unit> {
        val g = manage.create(manual)
        manage.recordContribution(g.id, "2026-01-01", 100_000)
        manage.recordContribution(g.id, "2026-05-01", 400_000)
        val p = overview.load("2026-07-01").single()
        assertEquals(500_000L, p.savedMinor)
        assertEquals(100_000L, p.startSavedMinor, "إيداع يوم البداية = نقطة بداية الخط")
        assertEquals(GoalState.BEHIND, p.state)
        assertEquals(6, p.monthsLeft)
        assertEquals(116_667L, p.requiredPerMonthMinor)
        assertFailsWith<SavingsGoalError> { manage.recordContribution(g.id, "2026-06-01", 0) }
        assertFailsWith<SavingsGoalError> { manage.recordContribution(g.id, "2026-02-30", 1_000) }
        val c = manage.recordContribution(g.id, "2026-06-01", 1_000, "  ")
        assertNull(c.note)
        manage.removeContribution(c.id)
        assertFailsWith<SavingsGoalError> { manage.removeContribution(c.id) }
    }

    @Test fun editKeepsCreationAndArchiveBlocksDeposits() = runBlocking<Unit> {
        val g = manage.create(manual)
        val edited = manage.edit(g.id, manual.copy(name = "سفر وهمي أكبر", targetMinor = 1_500_000))
        assertEquals(g.createdAt, edited.createdAt)
        assertEquals(1_500_000L, goals.listAll().single().targetMinor)
        manage.archive(g.id)
        assertEquals(emptyList(), overview.load("2026-07-01"), "المؤرشف برا الملخص")
        assertEquals(1, overview.load("2026-07-01", includeArchived = true).size)
        assertFailsWith<SavingsGoalError> { manage.recordContribution(g.id, "2026-07-01", 1_000) }
        manage.archive(g.id, archived = false)
        manage.recordContribution(g.id, "2026-07-01", 1_000)
        assertFailsWith<SavingsGoalError> { manage.edit("g-404", manual) }
        assertFailsWith<SavingsGoalError> { manage.create(manual.copy(targetDate = "2025-12-31")) }
    }

    @Test fun linkedGoalReadsTheWalletBalance() = runBlocking<Unit> {
        val g = manage.create(manual.copy(linkedWalletId = "w-save", linkedSpaceId = "default"))
        val p = overview.load("2026-07-01").single()
        assertEquals(350_000L, p.savedMinor, "رصيد البداية 1,000 + تحويل 2,500")
        assertEquals(100_000L, p.startSavedMinor)
        assertFailsWith<SavingsGoalError>("المربوطة ما بتاخدش إيداع يدوي") { manage.recordContribution(g.id, "2026-07-01", 1_000) }
        assertFailsWith<SavingsGoalError>("عملة تانية") { manage.create(manual.copy(linkedWalletId = "w-egp", linkedSpaceId = "default")) }
        assertFailsWith<SavingsGoalError>("محفظة مش موجودة") { manage.create(manual.copy(linkedWalletId = "w-404", linkedSpaceId = "default")) }
        assertFailsWith<SavingsGoalError>("بلد مش موجودة") { manage.create(manual.copy(linkedWalletId = "w-save", linkedSpaceId = "eg")) }
    }

    @Test fun onlyOneGoalCarriesTheStar() = runBlocking<Unit> {
        val a = manage.create(manual)
        val b = manage.create(manual.copy(name = "سيارة وهمية"))
        val c = manage.create(manual.copy(name = "بيت وهمي"))
        assertEquals(emptyList(), goals.listAll().filter { it.starred }, "الجديدة من غير نجمة")
        manage.star(a.id)
        assertEquals(listOf(a.id), goals.listAll().filter { it.starred }.map { it.id })
        manage.star(b.id)
        assertEquals(listOf(b.id), goals.listAll().filter { it.starred }.map { it.id }, "النجمة اتنقلت — مش اتنين")
        manage.star(b.id)
        assertEquals(listOf(b.id), goals.listAll().filter { it.starred }.map { it.id }, "نفس الخطة تاني ⇒ زي ما هي")
        manage.star(c.id, starred = false)
        assertEquals(listOf(b.id), goals.listAll().filter { it.starred }.map { it.id }, "شيل النجمة من خطة مش عليها ⇒ ولا حاجة اتغيرت")
        manage.star(b.id, starred = false)
        assertEquals(emptyList(), goals.listAll().filter { it.starred }, "شيلها ⇒ ولا خطة")
        // التعديل والأرشفة بيسيبوا النجمة؛ المؤرشفة ما تاخدش نجمة جديدة
        manage.star(a.id)
        assertEquals(true, manage.edit(a.id, manual.copy(targetMinor = 1_300_000)).starred)
        assertEquals(true, manage.archive(a.id).starred)
        assertFailsWith<SavingsGoalError> { manage.star(a.id) }
        assertFailsWith<SavingsGoalError> { manage.star("g-404") }
        manage.star(c.id)
        assertEquals(listOf(c.id), goals.listAll().filter { it.starred }.map { it.id }, "النجمة اتشالت من المؤرشفة كمان")
    }

    @Test fun unknownBalanceIsUnavailableNotZero() = runBlocking<Unit> {
        manage.create(manual.copy(linkedWalletId = "w-save", linkedSpaceId = "default"))
        val noLedger = LoadGoalsOverview(LoadGoalsOverviewDeps(goals, contributions))
        val p = noLedger.load("2026-07-01").single()
        assertEquals(GoalState.UNKNOWN, p.state)
        assertNull(p.savedMinor)
        assertNull(p.requiredPerMonthMinor)
    }
}
