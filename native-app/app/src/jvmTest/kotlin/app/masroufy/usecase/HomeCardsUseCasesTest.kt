package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.HOME_CARDS_KEY
import app.masroufy.core.HomeCardKind
import app.masroufy.core.ReviewState
import app.masroufy.core.SavingsGoal
import app.masroufy.core.Transaction
import app.masroufy.core.UserSetting
import app.masroufy.core.goalProgress
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUserSettingsStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** حالات استخدام كروت الرئيسية على مستودعات الذاكرة (اختيار المالك 2026-10-15) — أسماء ومبالغ مخترعة. */
class HomeCardsUseCasesTest {
    private val clock = FixedClock("2026-10-07T10:00:00.000Z")

    @Test
    fun layoutIsSavedOnTheAccountAndResetRemovesIt() = runBlocking {
        val settings = MemoryUserSettingsStore()
        val cards = ManageHomeCards(settings, clock)
        assertFalse(cards.layout().custom)
        val order = listOf(HomeCardKind.GOALS) + HomeCardKind.entries.filter { it != HomeCardKind.GOALS }
        val saved = cards.save(order, setOf(HomeCardKind.REVIEW, HomeCardKind.INVEST))
        assertEquals(HomeCardKind.GOALS, saved.visible.first())
        val stored = settings.listAll().filterIsInstance<UserSetting.HomeCards>().single()
        assertEquals(HOME_CARDS_KEY, stored.key)
        assertEquals("goals", stored.order.first())
        assertEquals(listOf("review", "invest"), stored.hidden)
        // قراية تانية (جوال تاني) بترجّع نفس الترتيب
        val again = ManageHomeCards(settings, clock).layout()
        assertEquals(saved.order, again.order)
        assertEquals(setOf(HomeCardKind.REVIEW, HomeCardKind.INVEST), again.hidden)
        assertFalse(cards.reset().custom)
        assertTrue(settings.listAll().none { it is UserSetting.HomeCards })
    }

    @Test
    fun closedTipsAreSavedOnTheAccountTogether() = runBlocking {
        val settings = MemoryUserSettingsStore()
        val cards = ManageHomeCards(settings, clock)
        assertNull(cards.tipsClosed().advisorKey)
        cards.closeAdvisor("advisor-key")
        cards.closeProfile("profile:CAR", "2026-10-07")
        val again = ManageHomeCards(settings, clock).tipsClosed()
        assertEquals("advisor-key", again.advisorKey)
        assertEquals("profile:CAR", again.profileKey)
        assertEquals("2026-10-07", again.profileClosedOn)
        assertEquals(1, settings.listAll().filterIsInstance<UserSetting.HomeTips>().size)
        // «الترتيب الأصلي» ما بيرجّعش الكروت المقفولة
        cards.reset()
        assertEquals("advisor-key", cards.tipsClosed().advisorKey)
    }

    private var seq = 0

    private fun txn(date: String, amount: Long, category: String?) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category,
    )

    private fun category(id: String, name: String, order: Int) = Category(id, null, name, "tag", "#000000", "#FFFFFF", true, order)

    @Test
    fun topCategoriesAreTheThreeLargestThisMonthWithBarsFromTheLargest() = runBlocking {
        val txns = MemoryTransactionRepository(
            listOf(
                txn("2026-10-01", 100_000, "c-food"), txn("2026-10-03", 84_000, "c-food"), txn("2026-10-02", 92_000, "c-ride"),
                txn("2026-10-04", 64_000, "c-coffee"), txn("2026-10-05", 10_000, "c-books"),
                // قبل بداية الشهر المالي (28) ⇒ برا
                txn("2026-09-20", 900_000, "c-books"),
            ),
        )
        val cats = MemoryCategoryRepository(listOf(category("c-food", "أكل وهمي", 1), category("c-ride", "مواصلات وهمية", 2), category("c-coffee", "قهوة وهمية", 3), category("c-books", "كتب وهمية", 4)))
        val top = LoadTopCategories(LoadHomeScreen(LoadHomeScreenDeps(txns, cats, MemoryAllocationRepository()))).load("2026-10-07", 28)
        assertEquals(listOf("c-food", "c-ride", "c-coffee"), top.rows.map { it.categoryId })
        assertEquals(listOf(184_000L, 92_000L, 64_000L), top.rows.map { it.amountMinor })
        assertEquals(listOf(100, 50, 34), top.rows.map { it.barPercent })
        assertEquals("أكل وهمي", top.rows.first().name)
    }

    private fun goal(id: String, target: Long, date: String, starred: Boolean = false) =
        SavingsGoal(id, "خطة $id", target, Currency.SAR, "2026-01-01", date, createdAt = "x", updatedAt = "x", starred = starred)

    @Test
    fun goalsShowTheStarredFirstThenTheNearestAndUnknownStaysUnknown() {
        val today = "2026-10-07"
        val rows = LoadGoalsProgress(LoadGoalsOverview(LoadGoalsOverviewDeps(app.masroufy.memory.MemorySavingsGoalRepository(), app.masroufy.memory.MemoryGoalContributionRepository()))).of(
            listOf(
                goalProgress(goal("g-far", 5_000_000, "2028-01-01", starred = true), 900_000, 0, today),
                goalProgress(goal("g-near", 1_000_000, "2027-01-01"), 620_000, 0, today),
                goalProgress(goal("g-later", 2_000_000, "2027-06-01"), null, null, today),
            ),
        )
        assertEquals(listOf("g-far", "g-near"), rows.map { it.goalId })
        assertEquals(listOf(18, 62), rows.map { it.percent })
        val unknown = LoadGoalsProgress(LoadGoalsOverview(LoadGoalsOverviewDeps(app.masroufy.memory.MemorySavingsGoalRepository(), app.masroufy.memory.MemoryGoalContributionRepository())))
            .of(listOf(goalProgress(goal("g-x", 1_000_000, "2027-01-01"), null, null, today))).single()
        assertNull(unknown.percent)
        assertNull(unknown.savedMinor)
    }
}
