package app.masroufy.ui.screens.home

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Texts
import app.masroufy.core.periodForDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * «اختر الشهر»: الشهر المالي **باسم الشهر اللي بيخلص فيه** (٢٨ سبتمبر–٢٧ أكتوبر = «أكتوبر» — رد المالك آخر §76) ومفتاحه في كوتلن زي ما هو ·
 * اللي قبله واللي بعده · لوحة السنة (المستقبل مقفول · قبل بياناتك مقفول) · اليوم كام من كام.
 */
class PeriodModelTest {
    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    private val current = periodForDate("2026-10-07", 28)

    @Test fun theFiscalMonthIsNamedByTheMonthItEndsIn() {
        assertEquals("2026-09", current.key, "مفتاح كوتلن بشهر البداية — ما اتغيرش")
        assertEquals("أكتوبر ٢٠٢٦", fiscalName(current))
        assertEquals("٢٨ سبتمبر – ٢٧ أكتوبر ٢٠٢٦", fiscalRange(current, withYear = true))
        assertEquals("٢٨ سبتمبر – ٢٧ أكتوبر", fiscalRange(current, withYear = false))
        val january = periodEndingIn(2026, 1, 28)
        assertEquals("٢٨ ديسمبر ٢٠٢٥ – ٢٧ يناير", fiscalRange(january, withYear = false), "السنة جنب البداية لو مختلفة")
        assertEquals("أكتوبر ٢٠٢٦", fiscalName(periodEndingIn(2026, 10, 1)), "يوم الراتب ١ ⇒ الشهر نفسه")
        assertEquals("2026-10-31", periodEndingIn(2026, 10, 1).end)
    }

    @Test fun stepsAndBoardStates() {
        val prev = previousPeriod(current, 28)
        assertEquals("2026-08-28", prev.start)
        assertEquals(current, nextPeriod(prev, 28))
        assertEquals(TileState.FUTURE, tileState(nextPeriod(current, 28), current, null), "ما بيروحش للمستقبل")
        assertEquals(TileState.BEFORE_DATA, tileState(periodEndingIn(2024, 12, 28), current, "2025-01-01"))
        assertEquals(TileState.OPEN, tileState(periodEndingIn(2025, 1, 28), current, "2025-01-01"), "فيه أيام من بياناتك ⇒ مفتوح")
        val tiles = yearTiles(2026, 28, current, current, "2025-01-01")
        assertEquals(12, tiles.size)
        assertTrue(tiles[9].isNow && tiles[9].selected, "أكتوبر هو الحالي والمختار")
        assertEquals(TileState.FUTURE, tiles[10].state)
        assertEquals("لم يبدأ بعد", tiles[10].range)
        assertFalse(tiles[8].isNow)
    }

    @Test fun progressAndNote() {
        val pr = progressOf(current, "2026-10-07")
        assertEquals(10, pr.day)
        assertEquals(30, pr.days)
        assertEquals(21, pr.left)
        assertEquals("اليوم ١٠ من ٣٠، بقي ٢١ يومًا على الراتب", periodNote(current, current, "2026-10-07"))
        assertEquals("كان ٣١ يومًا", periodNote(previousPeriod(current, 28), current, "2026-10-07"))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("النهارده يوم ١٠ من ٣٠، فاضل ٢١ يوم على المرتب", periodNote(current, current, "2026-10-07"))
        assertEquals("يومين", daysWord(2))
    }
}
