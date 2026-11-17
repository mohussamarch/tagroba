package app.masroufy.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OVERRIDES §121-1: لوحة الشهر والسنة — الشهور اللي لسه ما جتش باهتة ومش بتتختار، وسهم السنة الجاية مقفول عند سنة النهارده. */
class MonthYearSheetTest {
    private val nov2026 = MonthLimit.of("2026-11-17")

    @Test fun limitComesFromToday() = assertEquals(MonthLimit(2026, 11), nov2026)

    @Test fun twelveMonthsWithFutureDimmedInCurrentYear() {
        val cells = monthCells(2026, 11, nov2026)
        assertEquals((1..12).toList(), cells.map { it.month })
        assertEquals((1..11).toList(), cells.filter { it.enabled }.map { it.month })
        assertFalse(cells.single { it.month == 12 }.enabled)
        assertEquals(listOf(11), cells.filter { it.selected }.map { it.month })
    }

    @Test fun pastYearIsFullyOpenAndFutureYearFullyDimmed() {
        assertTrue(monthCells(2025, null, nov2026).all { it.enabled })
        assertTrue(monthCells(2027, null, nov2026).none { it.enabled })
    }

    @Test fun noLimitMeansEveryMonthOpen() {
        assertTrue(monthCells(2030, 5, null).all { it.enabled })
        assertTrue(canGoNextYear(2030, null))
    }

    @Test fun nextYearArrowStopsAtTodaysYear() {
        assertTrue(canGoNextYear(2025, nov2026))
        assertFalse(canGoNextYear(2026, nov2026))
    }

    @Test fun movingIntoCurrentYearClampsAFutureMonth() {
        assertEquals(11, clampMonth(2026, 12, nov2026))
        assertEquals(12, clampMonth(2025, 12, nov2026))
        assertEquals(3, clampMonth(2026, 3, nov2026))
        assertEquals(12, clampMonth(2026, 12, null))
    }
}
