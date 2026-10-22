package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MissedWhileAwayTest {
    @Test fun neverReadAndUnreadIsMissed() = assertEquals(2, missedWhileAway(null, listOf(10L, 20L)))

    @Test fun neverReadAndNothingUnreadIsOk() = assertEquals(0, missedWhileAway(null, emptyList()))

    @Test fun onlyNewerThanLastReadCount() = assertEquals(2, missedWhileAway(100L, listOf(50L, 101L, 500L)))

    @Test fun olderThanLastReadIsOk() = assertEquals(0, missedWhileAway(100L, listOf(50L, 100L)))

    /** 0.5.2: بعد «أكّد الأشكال» الرسايل المستنية تأكيد اتعاينت (آخر قراية من الشاشة) ⇒ مش «فاتته». */
    @Test fun waitingForConfirmationAfterAScreenReadIsNotMissed() {
        val waiting = List(618) { 1_000L + it }
        assertEquals(MissedSms(200, more = true), missedSms(null, waiting.takeLast(200), 618), "ولا قراية ⇒ «أكتر من 200» مش 200")
        assertEquals(MissedSms(0), missedSms(5_000L, waiting.takeLast(200), 618))
        assertEquals(MissedSms(1), missedSms(5_000L, waiting.takeLast(199) + 6_000L, 619), "اللي وصلت بعد القراية بس")
        assertEquals(MissedSms(0), missedSms(7_000L, waiting.takeLast(199) + 6_000L, 619), "الخلفية قرت بعدها")
    }

    @Test fun unknownTimeIsNotGuessedOnceReadBefore() = assertEquals(1, missedWhileAway(100L, listOf(null, 200L)))
}
