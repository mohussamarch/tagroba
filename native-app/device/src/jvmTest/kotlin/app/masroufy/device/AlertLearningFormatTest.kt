package app.masroufy.device

import app.masroufy.core.AlertKind
import app.masroufy.core.KindStats
import app.masroufy.core.UsualHours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** شكل تعلّم محرك التنبيهات على الجوال (OVERRIDES §61 — على الجوال بس، جلسة 18). القيمة البايظة = من الأول، مش وقوع. */
class AlertLearningFormatTest {
    @Test
    fun statsRoundTripAndAreSorted() {
        val stats = mapOf(AlertKind.ZAKAT_TODAY to KindStats(1, 0), AlertKind.DUE_SOON to KindStats(6, 4))
        val text = AlertLearningFormat.encodeStats(stats)
        assertEquals("due_soon=6/4;zakat_today=1/0", text)
        assertEquals(stats, AlertLearningFormat.decodeStats(text))
    }

    @Test
    fun brokenOrUnknownStatsAreSkippedNotFatal() {
        val text = "due_soon=6/4;kind_from_newer_app=3/1;due_today=2;budget_exceeded=1/5;zakat_soon=x/1;;income_late=-1/0"
        assertEquals(mapOf(AlertKind.DUE_SOON to KindStats(6, 4)), AlertLearningFormat.decodeStats(text))
        assertEquals(emptyMap(), AlertLearningFormat.decodeStats(null))
        assertEquals(emptyMap(), AlertLearningFormat.decodeStats(""))
    }

    @Test
    fun hoursRoundTripAndBrokenMeansStartOver() {
        val hours = UsualHours().recordOpen(9).recordOpen(9).recordOpen(21)
        assertEquals(hours, AlertLearningFormat.decodeHours(AlertLearningFormat.encodeHours(hours)))
        for (bad in listOf(null, "", "1,2,3", List(24) { "1" }.joinToString(",").replaceFirst("1", "-1"), List(25) { "0" }.joinToString(","), "a" + ",0".repeat(23))) {
            assertEquals(UsualHours(), AlertLearningFormat.decodeHours(bad), bad.toString())
        }
    }

    @Test
    fun keysArePerAccount() {
        assertNotEquals(AlertLearningFormat.statsKey("uid-a"), AlertLearningFormat.statsKey("uid-b"))
        assertNotEquals(AlertLearningFormat.statsKey("uid-a"), AlertLearningFormat.hoursKey("uid-a"))
    }
}
