package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** قارئ `PRIVATE-FIGURES.md` (OVERRIDES §120-1): بياخد سطور «مفتاح=قيمة» بس ويسيب الجداول والكلام — بأرقام مخترعة. */
class PrivateFiguresTest {
    @Test fun readsOnlyKeyValueLines() {
        val text = "# عنوان\r\n| `[P:debit]` | المدين | 1,234.56 |\r\n```properties\r\ndebit=1234.56\r\nopeningAt=2025-01-01\r\nbank=بنك تجربة\r\n```\r\n"
        assertEquals(mapOf("debit" to "1234.56", "openingAt" to "2025-01-01", "bank" to "بنك تجربة"), parseFigures(text))
    }
}
