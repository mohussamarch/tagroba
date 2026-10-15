package app.masroufy.ui.overlay

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.Springs
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * عطل HANDOVER §6.5-2 (المالك 2026-10-15): «لما برفعها لفوق البرنامج بيقفل كل مرة».
 * السبب: بعد الشد لفوق اللوحة بترجع بالنابض **المرن** (`Springs.BOUNCY` — تخميد 0.75) اللي بيعدّي الصفر شوية (≈ 3% تحت الصفر)،
 * والقيمة دي كانت بتروح `Modifier.padding(bottom = …)` على طول ⇒ Compose بيرمي `IllegalArgumentException: Padding must be non-negative`
 * وقت الرسم ⇒ البرنامج بيقفل. الاختبار بيشغّل **نفس النابض** من أقصى شد (60) لحد الصفر فريم بفريم ويبني المسافة بنفس دالة اللوحة.
 */
class SheetStretchTest {
    private val density = Density(2.75f) // كثافة جوال عادي (المحاكي masroufy-fbtrial)
    private val maxPx = with(density) { 60.dp.toPx() }
    private val scalePx = with(density) { 140.dp.toPx() }

    /** كل فريم (16ms) في رجوع النابض من [from] لحد الصفر — نفس `stretch.animateTo(0f, bounce)` في اللوحة. */
    private fun bounceBack(from: Float): List<Float> {
        val anim = TargetBasedAnimation(Springs.BOUNCY.spec<Float>(), Float.VectorConverter, from, 0f)
        val frames = (anim.durationNanos / 16_000_000L).toInt() + 1
        return (0..frames).map { anim.getValueFromNanos(it * 16_000_000L) }
    }

    @Test fun theBouncyReturnReallyGoesBelowZero() {
        // الشرط اللي العطل بيعتمد عليه: لو النابض اتغير ومابقاش بيعدّي الصفر، الاختبار ده بيقول
        val lowest = bounceBack(rubberStretch(-400f, maxPx, scalePx)).min()
        assertTrue(lowest < 0f, "BOUNCY spring should overshoot below 0 (lowest=$lowest)")
        // وComposeبيرفض المسافة السالبة — ده اللي كان بيقفل البرنامج
        assertFailsWith<IllegalArgumentException> { Modifier.padding(bottom = with(density) { lowest.toDp() }) }
    }

    @Test fun releasingAnUpwardDragNeverBuildsANegativePadding() {
        // شد قليل · متوسط · لآخره (الصباع 20 · 120 · 600 بكسل لفوق)
        for (pull in listOf(-20f, -120f, -600f)) {
            val start = rubberStretch(pull, maxPx, scalePx)
            assertTrue(start in 0f..maxPx)
            for (frame in bounceBack(start)) Modifier.sheetStretchPadding(frame, density) // كان بيرمي هنا
        }
    }

    @Test fun stretchStaysWithinItsLimit() {
        assertTrue(rubberStretch(0f, maxPx, scalePx) == 0f)
        assertTrue(rubberStretch(-10_000f, maxPx, scalePx) <= maxPx)
    }
}
