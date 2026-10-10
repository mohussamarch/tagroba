package app.masroufy.firestore

import kotlin.test.Test
import kotlin.test.assertEquals

/** مهلة الفتح الواحدة على جهاز عنده نسخة كاملة (OVERRIDES §86 — اختيار المالك «نص ثانية» بدل 2.5 ثانية). */
class OpeningGraceTest {
    @Test
    fun graceIsHalfASecond() {
        assertEquals(500L, OFFLINE_GRACE_MS)
    }

    @Test
    fun leftShrinksWithElapsedAndNeverGoesNegative() {
        assertEquals(500L, openingWaitLeft(0))
        assertEquals(200L, openingWaitLeft(300))
        assertEquals(0L, openingWaitLeft(500))
        // حساب الأول خد وقت أكتر من المهلة ⇒ البلاد ما تستناش خالص (مش مهلة تانية جديدة)
        assertEquals(0L, openingWaitLeft(2_400))
    }
}
