package app.masroufy.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.masroufy.ui.components.Toast
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.motion
import kotlinx.coroutines.delay

/**
 * الرسايل الصغيرة («سُجّلت: …» · «انتقلت إلى حساب مصر») — مستوى 1 من «مستويات الإحساس». واحدة بس في المرة، وبتختفي بعد 2.6 ثانية زي النموذج.
 * عايشة في `AppShell` (مش في الشاشة) عشان ما تضيعش لما الشاشة تتقفل أو البلد يتبدّل.
 */
@Stable
class Toaster {
    internal class Message(val id: Long, val text: String, val dark: Boolean)

    internal var current by mutableStateOf<Message?>(null)
    private var next = 0L

    fun show(text: String, dark: Boolean = false) {
        current = Message(next++, text, dark)
    }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

/** مكان الرسالة (فوق الشريطين في التبويبات، وتحت في الشاشات الداخلية — `AppShell` بيحطه). */
@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val msg = toaster.current ?: return
    val shown = remember(msg.id) { Animatable(0f) }
    val inSpec = motion<Float>(Springs.SNAPPY)
    LaunchedEffect(msg.id) {
        shown.animateTo(1f, inSpec)
        delay(2600)
        shown.animateTo(0f, inSpec)
        if (toaster.current?.id == msg.id) toaster.current = null
    }
    Toast(
        msg.text,
        modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * 8.dp.toPx()
        },
        dark = msg.dark,
    )
}
