package app.masroufy.ui.screens.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «اسحب لتحت عشان تحدّث» (قايمة المالك بند 9): حساب مكان الدايرة وإمتى السيب بيحدّث — من غير واجهة. */
class PullRefreshTest {
    private val max = 120f
    private val threshold = 72f

    @Test
    fun theCircleMovesHalfTheFingerDistance() {
        assertEquals(20f, pullAfterDrag(0f, 40f, max))
        assertEquals(50f, pullAfterDrag(30f, 40f, max))
    }

    @Test
    fun theCircleNeverGoesAboveTheTopOrBelowTheMax() {
        // لفوق أكتر من اللي نزل ⇒ صفر، عمره ما يبقى سالب
        assertEquals(0f, pullAfterDrag(10f, -100f, max))
        assertEquals(0f, pullAfterDrag(0f, -1f, max))
        // لتحت جامد ⇒ يقف عند الحد
        assertEquals(max, pullAfterDrag(100f, 1_000f, max))
    }

    @Test
    fun releasingPastTheThresholdRefreshes() {
        assertTrue(releaseRefreshes(threshold, threshold, refreshing = false))
        assertTrue(releaseRefreshes(threshold + 1f, threshold, refreshing = false))
    }

    @Test
    fun releasingShortOfTheThresholdDoesNot() {
        assertFalse(releaseRefreshes(threshold - 0.5f, threshold, refreshing = false))
        assertFalse(releaseRefreshes(0f, threshold, refreshing = false))
    }

    @Test
    fun noSecondRefreshWhileOneIsRunning() {
        assertFalse(releaseRefreshes(max, threshold, refreshing = true))
    }
}
