package app.masroufy.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinLoaderTimingTest {
    private fun near(expected: Float, actual: Float, eps: Float = 0.001f) = assertTrue(kotlin.math.abs(expected - actual) <= eps, "expected $expected got $actual")

    @Test
    fun startsInvisibleThirtyUnitsAbove() {
        val f = CoinLoaderTiming.frame(0f, 0)
        near(-30f, f.offsetY)
        near(0f, f.alpha)
    }

    @Test
    fun holdsInPlaceBetweenAppearAndFade() {
        // 50% من الدورة
        val f = CoinLoaderTiming.frame(900f, 0)
        assertEquals(CoinFrame(0f, 1f), f)
    }

    @Test
    fun appearEndsAtTwentyTwoPercentAtRest() {
        val f = CoinLoaderTiming.frame(0.22f * 1800f, 0)
        near(0f, f.offsetY, 0.01f)
        near(1f, f.alpha, 0.001f)
    }

    @Test
    fun fadesOutAtTheEnd() {
        val late = CoinLoaderTiming.frame(1799f, 0)
        assertTrue(late.alpha < 0.05f, "alpha ${late.alpha}")
        near(0f, late.offsetY)
    }

    @Test
    fun coinsAreStaggeredByQuarterSecond() {
        // الأولى بدأت من 250ms ⇒ الثانية لسه في الصفر لحد 250
        assertEquals(CoinLoaderTiming.frame(0f, 0), CoinLoaderTiming.frame(250f, 1))
        assertEquals(CoinLoaderTiming.frame(100f, 0), CoinLoaderTiming.frame(600f, 2))
    }

    @Test
    fun cycleRepeats() {
        assertEquals(CoinLoaderTiming.frame(120f, 1), CoinLoaderTiming.frame(120f + 1800f * 3, 1))
    }

    @Test
    fun overshootGoesPastRestButNeverBeyondOpacityRange() {
        var sawOvershoot = false
        for (ms in 0..400 step 5) {
            val f = CoinLoaderTiming.frame(ms.toFloat(), 0)
            assertTrue(f.alpha in 0f..1f)
            if (f.offsetY > 0.5f) sawOvershoot = true
        }
        assertTrue(sawOvershoot, "الزيادة (overshoot) لازم تعدّي مكان العملة شوية")
    }

    @Test
    fun staticFrameShowsAllCoins() {
        assertEquals(CoinFrame(0f, 1f), CoinLoaderTiming.STATIC)
    }
}
