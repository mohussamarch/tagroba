package app.masroufy.ui.screens.people

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.MotionMs

/** مدة الضغطة الطويلة على مكان «شخصيتك» في كارت «الأشخاص» (النموذج `PeopleOrbit` — 480ms). */
const val ME_HOLD_MS = 480L

/**
 * الضغطة الطويلة على مكانك في «الأشخاص» ⇒ «ابنِ/عدّل شخصيتك» (character-spec §4.4). **ما بتاكلش اللمس أبدًا** (مسرح الأشخاص
 * `orbitGestures` تحتها بيفضل شغال) · بتتلغي لو الصباع اتحرك أكتر من 9 أو التمرير خد اللمسة · الضغطة القصيرة ما بتعملش حاجة ·
 * [enabled] = false في وضع ترتيب الصف (الضغط هناك لازم يوصل لـ«الضغط في الفاضي بيخرج»). [label] للقارئ + فعل «ضغط مطوّل».
 */
internal fun Modifier.mePress(enabled: Boolean, label: String, onLongPress: () -> Unit): Modifier {
    if (!enabled) return this
    return semantics {
        contentDescription = label
        onLongClick(label) { onLongPress(); true }
    }.pointerInput(onLongPress) {
        val slop = MotionMs.holdCancelPx.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val ended = withTimeoutOrNull(ME_HOLD_MS) {
                while (true) {
                    val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!ch.pressed || ch.isConsumed || (ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (ended == null) onLongPress()
        }
    }
}
