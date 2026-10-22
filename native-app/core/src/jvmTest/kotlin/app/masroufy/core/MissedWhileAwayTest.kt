package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MissedWhileAwayTest {
    @Test fun neverReadAndUnreadIsMissed() = assertEquals(2, missedWhileAway(null, listOf(10L, 20L)))

    @Test fun neverReadAndNothingUnreadIsOk() = assertEquals(0, missedWhileAway(null, emptyList()))

    @Test fun onlyNewerThanLastReadCount() = assertEquals(2, missedWhileAway(100L, listOf(50L, 101L, 500L)))

    @Test fun olderThanLastReadIsOk() = assertEquals(0, missedWhileAway(100L, listOf(50L, 100L)))

    @Test fun unknownTimeIsNotGuessedOnceReadBefore() = assertEquals(1, missedWhileAway(100L, listOf(null, 200L)))
}
