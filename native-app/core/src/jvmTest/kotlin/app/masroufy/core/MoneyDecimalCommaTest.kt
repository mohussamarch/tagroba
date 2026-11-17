package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** OVERRIDES §121-15: الفاصلة العشرية مقبولة بس في رقم من غير فواصل آلاف. */
class MoneyDecimalCommaTest {
    @Test
    fun spaceThousandsWithDecimalCommaIsRejected() {
        assertFailsWith<MoneyError> { parseMoney("1 234,00") }
        assertFailsWith<MoneyError> { parseMoney("1 234,5") }
        assertNull(tryParseMoney("1 234,00"))
    }

    @Test
    fun commaThousandsWithDecimalCommaIsRejected() {
        assertFailsWith<MoneyError> { parseMoney("1,234,50") }
        assertNull(tryParseMoney("1,234,50"))
    }

    @Test
    fun plainDecimalCommaIsAccepted() {
        assertEquals(1250L, parseMoney("12,50"))
        assertEquals(1250L, parseMoney(" 12,50 "))
        assertEquals(-1250L, parseMoney("- 12,50"))
    }

    @Test
    fun thousandsCommaAloneStaysThousands() {
        assertEquals(123400L, parseMoney("1,234"))
        assertEquals(123400L, parseMoney("1 234"))
        assertEquals(123450L, parseMoney("1 234.50"))
    }
}
