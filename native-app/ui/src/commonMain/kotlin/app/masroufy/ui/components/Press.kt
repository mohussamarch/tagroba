package app.masroufy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.motion

/**
 * حالة «مضغوط» (DESIGN-SYSTEM «حالات التفاعل»): `scale(0.97)` + الظل البعيد للنص، بالنابض **السريع**، خلال 100ms من اللمس.
 * «تقليل الحركة» ⇒ من غير تصغير. مفيش تموّج (ripple) — مش جزء من نظام التصميم.
 */
class PressState internal constructor(val source: MutableInteractionSource, pressed: State<Boolean>) {
    val pressed: Boolean by pressed
}

@Composable
fun rememberPress(): PressState {
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState()
    return remember(source) { PressState(source, pressed) }
}

/** معامل الظل البعيد: عادي 1 · مضغوط 0.5 · معطّل 0. */
@Composable
fun farShadowFactor(press: PressState, enabled: Boolean = true): Float {
    val target = when {
        !enabled -> 0f
        press.pressed -> 0.5f
        else -> 1f
    }
    val factor by animateFloatAsState(target, motion(Springs.SNAPPY), label = "far-shadow")
    return factor
}

/** التصغير وقت الضغط. */
@Composable
fun Modifier.pressScale(press: PressState, enabled: Boolean = true): Modifier {
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (press.pressed && enabled && !reduce) 0.97f else 1f, motion(Springs.SNAPPY), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** ضغطة (وضغط مطوّل اختياري) من غير تموّج — [label] لقارئ الشاشة لما الزرار مالوش نص. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(
    press: PressState,
    enabled: Boolean = true,
    role: Role = Role.Button,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = press.source,
    indication = null,
    enabled = enabled,
    onClickLabel = label,
    role = role,
    onLongClick = onLongClick,
    onClick = onClick,
)
