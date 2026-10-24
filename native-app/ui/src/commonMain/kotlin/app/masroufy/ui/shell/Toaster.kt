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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * الرسايل الصغيرة («سُجّلت: …» · «انتقلت إلى حساب مصر») — مستوى 1 من «مستويات الإحساس». واحدة بس في المرة، وبتختفي بعد 2.6 ثانية زي النموذج.
 * عايشة في `AppShell` (مش في الشاشة) عشان ما تضيعش لما الشاشة تتقفل أو البلد يتبدّل.
 */
@Stable
class Toaster {
    internal class Message(val id: Long, val text: String, val dark: Boolean, val action: String? = null, val onAction: (() -> Unit)? = null)

    internal var current by mutableStateOf<Message?>(null)
    private var next = 0L

    fun show(text: String, dark: Boolean = false) {
        current = Message(next++, text, dark)
    }

    /** فيه رسالة بزرار ظاهرة دلوقتي — اللوحات اللي بتطلع لوحدها (`BnplHost`) بتستناها عشان الزرار ما يتدفنش ورا الستارة. */
    val holdsAction: Boolean get() = current?.action != null

    /** r051: رسالة بزرار واحد («تراجع» بعد الربط اللي لوحده) — بتفضل [TOAST_ACTION_MS] عشان تلحق تدوس. */
    fun show(text: String, action: String, dark: Boolean = true, onAction: () -> Unit) {
        current = Message(next++, text, dark, action, onAction)
    }

    /** نطاق [ToastHost] (عايش في `AppShell`) — الشغل اللي بعد الزرار يكمّل حتى لو الشاشة اللي طلبته اتقفلت. */
    internal var hostScope: CoroutineScope? = null

    /**
     * r06-fix055b: «تراجع» بعد مسح حاجة من صفحتها — الصفحة بتتقفل وبنرجع للقايمة، والشريط بيفضل هنا. [undo] بيرجّع رسالة النتيجة
     * (أو null ⇒ مفيش رسالة) وبيشتغل في [hostScope] مش في نطاق الشاشة اللي اتقفلت.
     */
    fun showUndo(text: String, action: String, undo: suspend () -> String?) {
        show(text, action) { hostScope?.launch { undo()?.let { show(it) } } }
    }
}

/** مدة رسالة فيها زرار (زي «تراجع» في رسايل البنك — 8 ثواني). */
const val TOAST_ACTION_MS = 8_000L

val LocalToaster = staticCompositionLocalOf { Toaster() }

/** مكان الرسالة (فوق الشريطين في التبويبات، وتحت في الشاشات الداخلية — `AppShell` بيحطه). */
@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val hostScope = rememberCoroutineScope()
    SideEffect { toaster.hostScope = hostScope }
    val msg = toaster.current ?: return
    val shown = remember(msg.id) { Animatable(0f) }
    val inSpec = motion<Float>(Springs.SNAPPY)
    LaunchedEffect(msg.id) {
        shown.animateTo(1f, inSpec)
        delay(if (msg.action != null) TOAST_ACTION_MS else 2600)
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
        action = msg.action,
        onAction = msg.onAction?.let { run -> { if (toaster.current?.id == msg.id) toaster.current = null; run() } },
    )
}
