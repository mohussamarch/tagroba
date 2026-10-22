package app.masroufy.ui.screens.common

import app.masroufy.core.R06Key
import kotlin.test.Test
import kotlin.test.assertEquals

class PullMathTest {
    private val th = PullMetrics.THRESHOLD_DP

    @Test
    fun oneCoinPerThirdOfTheDistance() {
        assertEquals(0, PullMetrics.coinCount(0f))
        assertEquals(0, PullMetrics.coinCount(21f))
        assertEquals(1, PullMetrics.coinCount(22f))
        assertEquals(1, PullMetrics.coinCount(42f))
        assertEquals(2, PullMetrics.coinCount(43f))
        assertEquals(2, PullMetrics.coinCount(63f))
        assertEquals(3, PullMetrics.coinCount(th))
        assertEquals(3, PullMetrics.coinCount(PullMetrics.MAX_DP))
    }

    @Test
    fun negativeOrBadInputGivesNoCoins() {
        assertEquals(0, PullMetrics.coinCount(-5f))
        assertEquals(0, PullMetrics.coinCount(30f, threshold = 0f))
    }

    @Test
    fun draggingPhaseSwitchesAtTheThreshold() {
        assertEquals(PullPhase.IDLE, PullMetrics.draggingPhase(0f))
        assertEquals(PullPhase.PULL, PullMetrics.draggingPhase(th - 0.5f))
        assertEquals(PullPhase.READY, PullMetrics.draggingPhase(th))
        assertEquals(PullPhase.READY, PullMetrics.draggingPhase(PullMetrics.MAX_DP))
    }

    @Test
    fun labelsFollowThePhase() {
        assertEquals(R06Key.LOADER_PULL, PullMetrics.labelKey(PullPhase.PULL))
        assertEquals(R06Key.LOADER_PULL, PullMetrics.labelKey(PullPhase.IDLE))
        assertEquals(R06Key.LOADER_RELEASE, PullMetrics.labelKey(PullPhase.READY))
        assertEquals(R06Key.LOADER_UPDATING, PullMetrics.labelKey(PullPhase.BUSY))
        assertEquals(R06Key.LOADER_DONE, PullMetrics.labelKey(PullPhase.DONE))
    }

    @Test
    fun busyAnimatesAndDoneShowsThreeStaticCoins() {
        assertEquals(-1, PullMetrics.coinsFor(PullPhase.BUSY, PullMetrics.BUSY_DP))
        assertEquals(3, PullMetrics.coinsFor(PullPhase.DONE, PullMetrics.BUSY_DP))
        assertEquals(1, PullMetrics.coinsFor(PullPhase.PULL, 30f))
    }

    @Test
    fun designNumbers() {
        assertEquals(64f, PullMetrics.THRESHOLD_DP)
        assertEquals(120f, PullMetrics.MAX_DP)
        assertEquals(60f, PullMetrics.BUSY_DP)
        assertEquals(900, PullMetrics.DONE_HOLD_MS)
        assertEquals(320, PullMetrics.RETURN_MS)
    }

    @Test
    fun pullHasHalfResistanceAndCapsAtMax() {
        assertEquals(40f, pullAfterDrag(0f, 80f, PullMetrics.MAX_DP))
        assertEquals(PullMetrics.MAX_DP, pullAfterDrag(100f, 500f, PullMetrics.MAX_DP))
    }
}
