package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** «استخدم المتوسط» في لوحة السقف = متوسط آخر 3 شهور بالظبط (رد المالك OVERRIDES §82-17). مبالغ مخترعة. */
class LimitAverageTest {
    private fun p(key: String, spent: Long, reliable: Boolean = true, count: Int = 5) = CompletedPeriodSpend(key, spent, reliable, count)

    @Test fun averageOfTheLastThreeOnlyNewestFirst() {
        val periods = listOf(p("2026-08", 60_000), p("2026-07", 30_000), p("2026-06", 30_000), p("2026-05", 900_000))
        assertEquals(40_000, lastPeriodsAverage(periods), "الشهر الرابع برّه")
        assertEquals(20_000, lastPeriodsAverage(listOf(p("a", 60_000), p("b", 0), p("c", 0))), "شهر من غير صرف على التصنيف = صفر")
        // تقريب الهللة زي باقي المتوسطات (100 ÷ 3)
        assertEquals(33, lastPeriodsAverage(listOf(p("a", 100), p("b", 0), p("c", 0))))
    }

    @Test fun notAvailableUnlessAllThreeAreKnown() {
        assertNull(lastPeriodsAverage(listOf(p("a", 1), p("b", 1))), "أقل من 3 شهور")
        assertNull(lastPeriodsAverage(listOf(p("a", 1), p("b", 1, reliable = false), p("c", 1))), "شهر فيه عملية مش محددة")
        assertNull(lastPeriodsAverage(listOf(p("a", 1), p("b", 0, count = 0), p("c", 1))), "شهر فاضي خالص = مش معروف")
        assertEquals(3, LIMIT_AVERAGE_PERIODS)
    }
}
