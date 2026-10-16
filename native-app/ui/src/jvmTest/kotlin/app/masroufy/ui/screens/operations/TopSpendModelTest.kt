package app.masroufy.ui.screens.operations

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun thePeriodSwitcherUsesTheFilterPeriodsThisAndLastMonth() {
        assertEquals(listOf(0, 1), TOP_SPEND_PERIODS.map { it.skip }, "عدد الشهور لورا = اللي حالة الاستخدام بتقراه")
        assertEquals(listOf(1, 1), TOP_SPEND_PERIODS.map { it.months })
    }
}
