package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** r06-darahim (PLAN-0.6 §12-3): «برّه الميزانية» و«برّه التقارير» علامتين منفصلين — والعلامة القديمة الواحدة = برّه الاتنين. */
class ExclusionFlagsTest {
    private fun tx(id: String, amount: Long, budget: Boolean = false, reports: Boolean? = null) = Transaction(
        id = id, occurredAt = "2026-10-07", datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = budget,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "2026-10-07T08:00:00.000Z", updatedAt = "2026-10-07T08:00:00.000Z",
        categoryId = "cat-a", excludedFromReports = reports,
    )

    private val rows = listOf(
        tx("normal", 10_000),
        tx("old-flag", 2_000, budget = true),
        tx("budget-only", 300, budget = true, reports = false),
        tx("reports-only", 40, reports = true),
    )

    @Test fun oldSingleFlagMapsToBoth() {
        val old = tx("x", 1, budget = true)
        assertTrue(old.isExcludedFrom(ExclusionScope.BUDGET))
        assertTrue(old.isExcludedFrom(ExclusionScope.REPORTS))
        assertFalse(tx("y", 1).isExcludedFrom(ExclusionScope.REPORTS))
    }

    @Test fun reportTotalsIgnoreOnlyReportExcluded() {
        val t = computePeriodTotals(rows, emptyList())
        assertEquals(10_300, t.personalExpenseMinor)
        assertEquals(2_040, t.excludedExpenseMinor)
        assertEquals(10_300, categoryDistribution(rows).totalMinor)
    }

    @Test fun budgetTotalsIgnoreOnlyBudgetExcluded() {
        val t = computePeriodTotals(rows, emptyList(), ExclusionScope.BUDGET)
        assertEquals(10_040, t.personalExpenseMinor)
        assertEquals(2_300, t.excludedExpenseMinor)
        assertEquals(10_040, categoryDistribution(rows, emptyList(), ExclusionScope.BUDGET).totalMinor)
    }
}
