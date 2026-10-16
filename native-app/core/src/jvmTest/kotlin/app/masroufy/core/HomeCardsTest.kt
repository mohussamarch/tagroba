package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** كروت الرئيسية (اختيار المالك 2026-10-15): الترتيب والمخفي و«تقدر تصرف النهارده» — أرقام مخترعة بالهللة. */
class HomeCardsTest {
    @Test
    fun firstTimeShowsTheSevenPickedAndHidesTheOtherTwo() {
        val layout = homeCardsLayout(null, null)
        assertFalse(layout.custom)
        assertEquals(
            listOf(HomeCardKind.DAILY, HomeCardKind.BUDDY, HomeCardKind.UPCOMING, HomeCardKind.TOP, HomeCardKind.GOALS, HomeCardKind.REVIEW, HomeCardKind.RECENT),
            layout.visible,
        )
        assertEquals(setOf(HomeCardKind.PEOPLE, HomeCardKind.INVEST), layout.hidden)
    }

    @Test
    fun storedOrderDropsUnknownAndDuplicatesAndAppendsNewCards() {
        val layout = homeCardsLayout(listOf("goals", "daily", "someFutureCard", "goals", "people"), listOf("daily"))
        assertTrue(layout.custom)
        // goals · daily · people من المحفوظ، وبعدهم الباقي بترتيبه الأول — والجديد المخفي أول مرة (invest) بيفضل مخفي
        assertEquals(
            listOf(HomeCardKind.GOALS, HomeCardKind.DAILY, HomeCardKind.PEOPLE, HomeCardKind.BUDDY, HomeCardKind.UPCOMING, HomeCardKind.TOP, HomeCardKind.REVIEW, HomeCardKind.RECENT, HomeCardKind.INVEST),
            layout.order,
        )
        assertEquals(setOf(HomeCardKind.DAILY, HomeCardKind.INVEST), layout.hidden)
        assertEquals(HomeCardKind.GOALS, layout.visible.first())
    }

    @Test
    fun arrowsMoveOneStepAndStopAtTheEnds() {
        val first = defaultHomeCards()
        assertEquals(first, moveHomeCard(first, HomeCardKind.DAILY, -1))
        assertEquals(first, moveHomeCard(first, HomeCardKind.INVEST, 1))
        val moved = moveHomeCard(first, HomeCardKind.GOALS, -1)
        assertEquals(listOf(HomeCardKind.DAILY, HomeCardKind.BUDDY, HomeCardKind.UPCOMING, HomeCardKind.GOALS, HomeCardKind.TOP), moved.order.take(5))
        assertTrue(moved.custom)
    }

    @Test
    fun switchHidesAndShows() {
        val hidden = toggleHomeCard(defaultHomeCards(), HomeCardKind.REVIEW)
        assertFalse(HomeCardKind.REVIEW in hidden.visible)
        val shown = toggleHomeCard(toggleHomeCard(hidden, HomeCardKind.REVIEW), HomeCardKind.PEOPLE)
        assertTrue(HomeCardKind.REVIEW in shown.visible)
        assertTrue(HomeCardKind.PEOPLE in shown.visible)
    }

    @Test
    fun dailySpendIsLeftoverOverDaysRoundedDownToWholeUnit() {
        // معاك 7,350.00 ناقص 2,200.00 = 5,150.00 على 21 يوم = 245.238… ⇒ 245.00
        val d = dailySpend(735_000, 220_000, 21, approximate = false, basis = DailySpendBasis.LEFTOVER_UNTIL_PAYDAY)
        assertEquals(24_500L, d.perDayMinor)
        assertEquals(515_000L, d.baseMinor)
        assertFalse(d.short)
        // نفس الأرقام من غير طرح الجاي: 7,350.00 ÷ 21 = 350.00
        assertEquals(35_000L, dailySpend(735_000, 220_000, 21, approximate = false, basis = DailySpendBasis.ON_HAND_UNTIL_PAYDAY).perDayMinor)
    }

    @Test
    fun dailySpendUnknownIsNotZero() {
        assertNull(dailySpend(null, 0, 10, approximate = false).perDayMinor)
        assertNull(dailySpend(100_000, 0, null, approximate = false).perDayMinor)
        assertNull(dailySpend(100_000, 0, 0, approximate = false).perDayMinor)
    }

    @Test
    fun comingMoreThanYouHaveIsZeroAndSaidSo() {
        val d = dailySpend(100_000, 150_000, 5, approximate = true, basis = DailySpendBasis.LEFTOVER_UNTIL_PAYDAY)
        assertEquals(0L, d.perDayMinor)
        assertTrue(d.short)
        assertTrue(d.approximate)
    }

    @Test
    fun barsAreShareOfLargestAndGoalPercentIsFloored() {
        assertEquals(listOf(100, 50, 34), sharesOfLargest(listOf(184_000, 92_000, 64_000)))
        assertEquals(emptyList(), sharesOfLargest(emptyList()))
        assertEquals(62, goalPercent(620_000, 1_000_000))
        assertEquals(18, goalPercent(900_000, 5_000_000))
        assertNull(goalPercent(null, 1_000_000))
        assertNull(goalPercent(100, 0))
    }
}
