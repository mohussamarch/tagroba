package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import kotlin.test.Test
import app.masroufy.core.Period
import app.masroufy.usecase.SpendRankRow
import app.masroufy.usecase.TopSpendView
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «أكثر ما صرفت عليه» (OVERRIDES §100): الضغط على سطر ⇒ شاشة التصفية مفتوحة على التصنيف ده في نفس الفترة — و«بلا تصنيف» = [NO_CATEGORY].
 * بيانات مخترعة بس.
 */
class TopSpendModelTest {
    private val lunch = Fx.tx("lunch", amount = 4_200)
    private val groceries = Fx.tx("groceries", date = "2026-10-08", amount = 31_840, category = Fx.shop.id, merchant = "بقالة وهمية")
    private val loose = Fx.tx("loose", amount = 6_000, category = null, merchant = null)
    private val salary = Fx.tx("salary", date = "2026-10-01", amount = 1_250_000, direction = Direction.IN, kind = EconomicKind.SALARY, category = null, merchant = null)
    private val data = listOf(Fx.screen(listOf(lunch, groceries, loose, salary)))

    private fun ids(route: CategoryOperationsRoute) = applyFilters(data, route.filters(), Currency.SAR).map { it.id }

    @Test fun tappingACategoryRowFiltersByThatCategoryAndPeriod() {
        val route = categoryOperationsRoute(Fx.shop.id, FilterPeriod.THIS)
        assertEquals("OperationFilters", route.name, "نفس شاشة التصفية")
        assertEquals(setOf(Fx.shop.id), route.filters().categories)
        assertEquals(FilterPeriod.THIS, route.filters().period)
        assertEquals(listOf("groceries"), ids(route))
        assertEquals(listOf("lunch"), ids(categoryOperationsRoute(Fx.food.id, FilterPeriod.THIS)))
    }

    @Test fun tappingTheUncategorizedRowFiltersByNoCategory() {
        val route = categoryOperationsRoute(null, FilterPeriod.LAST)
        assertEquals(NO_CATEGORY, route.categoryKey)
        assertEquals(FilterPeriod.LAST, route.filters().period, "الشهر اللي فات ⇒ نفس الفترة في التصفية")
        assertEquals(listOf("loose"), ids(route), "الراتب من غير تصنيف بس مش صرف ⇒ برّه")
        assertEquals(setOf(KindGroup.OUT), route.filters().kinds)
    }

    @Test fun thePeriodSwitcherUsesTheFilterPeriodsThisLastAndThreeMonths() {
        assertEquals(listOf(FilterPeriod.THIS, FilterPeriod.LAST, FilterPeriod.THREE), TOP_SPEND_PERIODS, "نفس شرايح التصميم")
        assertEquals(listOf(0, 1, 0), TOP_SPEND_PERIODS.map { it.skip }, "عدد الشهور لورا = اللي حالة الاستخدام بتقراه")
        assertEquals(listOf(1, 1, 3), TOP_SPEND_PERIODS.map { it.months })
    }

    @Test fun tappingARowInThreeMonthsKeepsTheThreeMonthPeriod() {
        assertEquals(FilterPeriod.THREE, categoryOperationsRoute(Fx.shop.id, FilterPeriod.THREE).filters().period)
    }

    private fun row(change: Int?) = SpendRankRow("c", "تصنيف وهمي", null, null, 10_000, 3, 500, 50, 100, change)

    @Test fun theChangeShowsAnArrowOnlyWhenKnownAndNotZero() {
        val up = topSpendChange(row(14))!!
        assertTrue(up.second, "زاد ⇒ أحمر")
        assertTrue(up.first.startsWith("↑") && up.first.contains("14%"), up.first)
        val down = topSpendChange(row(-6))!!
        assertTrue(!down.second && down.first.startsWith("↓") && down.first.contains("6%") && !down.first.contains("-"), down.first)
        assertNull(topSpendChange(row(null)), "مش معروف ⇒ مفيش سهم (مش 0%)")
        assertNull(topSpendChange(row(0)), "صفر ⇒ مفيش سهم زي التصميم")
    }

    @Test fun theRangeLineNamesTheCurrentFinancialMonth() {
        val p = Period("2026-09", "2026-09-28", "2026-10-27", 30)
        val now = TopSpendView(p, "", listOf(row(null)), 10_000, false, current = true)
        val range = topSpendRange(now)
        assertTrue(range.contains("28") && range.contains("27") && range.contains("("), range)
        assertTrue(!topSpendRange(now.copy(current = false)).contains("("), "الشهر اللي فات من غير «(الشهر المالي ده)»")
        assertTrue(topSpendSummary(now).contains("1") && topSpendSummary(now).contains("3"), topSpendSummary(now))
    }
}
