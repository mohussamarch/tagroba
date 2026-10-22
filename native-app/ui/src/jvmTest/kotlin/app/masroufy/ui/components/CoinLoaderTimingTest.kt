package app.masroufy.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinLoaderTimingTest {
    private fun near(expected: Float, actual: Float, eps: Float = 0.001f) = assertTrue(kotlin.math.abs(expected - actual) <= eps, "expected $expected got $actual")
    private fun at(percent: Float) = CoinLoaderTiming.frameAtPhase(percent / 100f)

    @Test
    fun startsInvisibleFortyFourAbove() {
        val f = at(0f)
        near(-44f, f.offsetY)
        near(0f, f.alpha)
        near(1f, f.scaleX)
    }

    @Test
    fun squashesOnLandingAtTwentyTwoPercent() {
        val f = at(22f)
        near(0f, f.offsetY, 0.01f)
        near(1.07f, f.scaleX, 0.001f)
        near(0.8f, f.scaleY, 0.001f)
        near(1f, f.alpha)
    }

    @Test
    fun bouncesFiveUnitsAtTwentyNinePercent() {
        val f = at(29f)
        near(-5f, f.offsetY, 0.01f)
        near(0.98f, f.scaleX, 0.001f)
        near(1.03f, f.scaleY, 0.001f)
    }

    @Test
    fun settlesAtThirtyFivePercentAndHolds() {
        assertEquals(CoinFrame(0f, 1f, 1f, 1f), at(35f))
        assertEquals(CoinFrame(0f, 1f, 1f, 1f), at(60f))
        assertEquals(CoinFrame(0f, 1f, 1f, 1f), at(78f))
    }

    @Test
    fun endsDroppedEightAndFaded() {
        val end = at(92f)
        near(8f, end.offsetY, 0.01f)
        near(0f, end.alpha, 0.001f)
        near(0.96f, end.scaleX, 0.001f)
        near(8f, at(99f).offsetY)
        near(0f, at(99f).alpha)
    }

    @Test
    fun fallAcceleratesAndNeverMovesBackwardsBeforeLanding() {
        var prev = -44f
        for (i in 0..220) {
            val y = at(i / 10f).offsetY
            assertTrue(y >= prev - 0.0001f, "y went back at ${i / 10f}: $prev -> $y")
            prev = y
        }
        // التسارع: في أول نص الوقت بتقطع أقل من ربع المسافة (منحنى ease-in)
        assertTrue(at(11f).offsetY < -44f * 0.5f, "falls slowly first: ${at(11f).offsetY}")
    }

    @Test
    fun alphaStaysInRange() {
        for (i in 0..1000) assertTrue(at(i / 10f).alpha in 0f..1f)
    }

    @Test
    fun coinsAreStaggeredBy280Ms() {
        assertEquals(CoinLoaderTiming.frame(0f, 0), CoinLoaderTiming.frame(280f, 1))
        assertEquals(CoinLoaderTiming.frame(100f, 0), CoinLoaderTiming.frame(660f, 2))
    }

    @Test
    fun cycleIsTwoPointFourSecondsAndRepeats() {
        assertEquals(2400, CoinLoaderTiming.CYCLE_MS)
        near(0.25f, CoinLoaderTiming.phase(600f, 0))
        assertEquals(CoinLoaderTiming.frame(120f, 1), CoinLoaderTiming.frame(120f + 2400f * 3, 1))
    }

    @Test
    fun glintSweepsFromMinusThirtyToThirtyBetweenHalfAndSeventyTwoPercentOfItsCycle() {
        // اللمعة بتبدأ بعد 0.9 ثانية للعملة الأولى ⇒ نص دورتها = 900 + 1200
        near(-30f, CoinLoaderTiming.shineX(900f, 0))
        near(-30f, CoinLoaderTiming.shineX(900f + 1199f, 0))
        near(30f, CoinLoaderTiming.shineX(900f + 0.72f * 2400f, 0), 0.01f)
        near(30f, CoinLoaderTiming.shineX(900f + 2300f, 0), 0.01f)
        // العملة الثانية متأخرة 0.12 ثانية
        near(-30f, CoinLoaderTiming.shineX(1020f + 1199f, 1))
    }

    @Test
    fun floorShadowNarrowsAtEndsAndWidensInTheMiddle() {
        near(0.55f, CoinLoaderTiming.floor(0f).scaleX)
        near(0.4f, CoinLoaderTiming.floor(0f).alpha)
        near(1f, CoinLoaderTiming.floor(0.5f * 2400f).scaleX)
        near(1f, CoinLoaderTiming.floor(0.5f * 2400f).alpha)
    }
}
