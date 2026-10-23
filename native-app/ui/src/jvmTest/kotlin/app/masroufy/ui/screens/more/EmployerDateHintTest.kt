package app.masroufy.ui.screens.more

import kotlin.test.Test
import kotlin.test.assertEquals

/** fix053 (فحص 0.5.3 ج): خانة «من تاريخ» بتقبل المكتوب زي المثال (يوم-شهر-سنة) وكمان سنة-شهر-يوم. التواريخ مخترعة. */
class EmployerDateHintTest {
    @Test fun dayFirstAsInTheHintBecomesIso() {
        assertEquals("2025-01-01", dateFromHint("01-01-2025"))
        assertEquals("2026-08-31", dateFromHint(" 31-08-2026 "))
        assertEquals("2026-08-03", dateFromHint("3/8/2026"))
    }

    @Test fun isoAndEmptyStayAsTheyAre() {
        assertEquals("2025-01-01", dateFromHint("2025-01-01"))
        assertEquals("", dateFromHint("  "))
        assertEquals("next week", dateFromHint("next week"))
    }

    @Test fun employerInputUsesIt() {
        val input = employerInput(" QA Works ", "01-01-2025", "")
        assertEquals("2025-01-01", input.startedAt)
        assertEquals(null, input.endedAt)
    }
}
