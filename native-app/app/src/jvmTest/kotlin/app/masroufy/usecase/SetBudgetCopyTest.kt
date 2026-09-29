package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.CategoryBudget
import app.masroufy.core.buildPeriod
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * «انسخ سقوف فترة سابقة» بقرار المالك (OVERRIDES §49): **الموجود ما يتلمسش**، والناقص بس بيتنسخ.
 * مكتوب بالإيد لأن التطبيق الحالي بيعمل غير كده (بيضيف سقف تاني لنفس التصنيف)، فملف المرجع مينفعش يكون الحكم هنا.
 * النسخ لفترة فاضية لسه متغطي في `budgetEdit.json` (السلوكين واحد هناك).
 */
class SetBudgetCopyTest {
    private val august = buildPeriod(2026, 8, 28)
    private val september = buildPeriod(2026, 9, 28)
    private val now = "2026-09-22T10:00:00.000Z"

    private fun budget(period: app.masroufy.core.Period, total: Long?, threshold: Int?) =
        Budget(period.key, period.key, period.start, period.end, total, threshold, "2026-08-28T08:00:00.000Z", "2026-08-28T08:00:00.000Z")

    private val sourceLines = listOf(
        CategoryBudget("cb-food", august.key, "food", 150_000, true, 90),
        CategoryBudget("cb-fuel", august.key, "fuel", 60_000, false, null),
    )

    private fun copy(targetBudget: Budget?, targetLines: List<CategoryBudget>): Pair<Int, MemoryBudgetRepository> {
        val repo = MemoryBudgetRepository(listOfNotNull(budget(august, 800_000, 80), targetBudget), sourceLines + targetLines)
        val set = SetBudget(SetBudgetDeps(repo, MemoryUnitOfWork(listOf(repo)), SequentialIdGenerator(), FixedClock(now)))
        return runBlocking { set.copyFrom(august.key, september) } to repo
    }

    @Test
    fun keepsTheTargetCategoryLimitAndAddsOnlyTheMissingOnes() {
        val mine = CategoryBudget("cb-sep-food", september.key, "food", 90_000, false, null)
        val (added, repo) = copy(budget(september, null, null), listOf(mine))
        assertEquals(1, added)
        val lines = repo.allLines().filter { it.budgetId == september.key }
        assertEquals(listOf(mine, CategoryBudget("catbudget-000001", september.key, "fuel", 60_000, false, null)), lines)
        // مفيش سقف إجمالي في الهدف ⇒ بياخد سقف المصدر وعتبته
        val target = repo.allBudgets().single { it.periodKey == september.key }
        assertEquals(800_000L to 80, target.totalLimitMinor to target.thresholdPercent)
        assertEquals(now, target.updatedAt)
    }

    @Test
    fun keepsTheTargetTotalLimit() {
        val (added, repo) = copy(budget(september, 300_000, 50), emptyList())
        assertEquals(2, added)
        val target = repo.allBudgets().single { it.periodKey == september.key }
        assertEquals(300_000L to 50, target.totalLimitMinor to target.thresholdPercent)
        assertEquals("2026-08-28T08:00:00.000Z", target.updatedAt)
    }

    @Test
    fun copiesNothingWhenEveryCategoryAlreadyHasALimit() {
        val mine = listOf(
            CategoryBudget("cb-sep-food", september.key, "food", 90_000, false, null),
            CategoryBudget("cb-sep-fuel", september.key, "fuel", 40_000, true, 70),
        )
        val (added, repo) = copy(budget(september, 500_000, null), mine)
        assertEquals(0, added)
        assertEquals(mine, repo.allLines().filter { it.budgetId == september.key })
    }
}
