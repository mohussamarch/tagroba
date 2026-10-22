package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LastReadTest {
    private val min = 60_000L
    private val hour = 60 * min

    // 2026-01-15T10:00:00Z — قيمة اختبار مخترعة
    private val base = 1_768_471_200_000L

    @Test
    fun relativeBuckets() {
        assertEquals(LastReadWhen.JustNow, lastReadWhen(base, base + 59_000, 0))
        assertEquals(LastReadWhen.MinutesAgo(5), lastReadWhen(base, base + 5 * min + 1, 0))
        assertEquals(LastReadWhen.HoursAgo(3), lastReadWhen(base, base + 3 * hour + min, 0))
    }

    @Test
    fun futureTimeIsJustNowNotNegative() {
        assertEquals(LastReadWhen.JustNow, lastReadWhen(base + hour, base, 0))
    }

    @Test
    fun oldReadingShowsDateAndTimeWithOffsetAndWesternDigits() {
        assertEquals(LastReadWhen.At("2026-01-15 10:00"), lastReadWhen(base, base + 7 * hour, 0))
        assertEquals(LastReadWhen.At("2026-01-15 13:00"), lastReadWhen(base, base + 7 * hour, 3 * hour))
        // الفرق بيعدّي نص الليل ⇒ اليوم اللي بعده
        assertEquals(LastReadWhen.At("2026-01-16 01:05"), lastReadWhen(base, base + 9 * hour, 15 * hour + 5 * min))
    }
}
