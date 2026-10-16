package app.masroufy.ui.screens.operations

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/** شريط «تراجع» بيفضل 8 ثواني (OVERRIDES §91-11). */
const val UNDO_WINDOW_MS = 8_000L

/**
 * r041 A5 — شباك «تراجع» واحد لكل الشاشات (القايمة بعد الحذف أو الفعل الجماعي · تفاصيل العملية بعد الحذف · «فك الربط»).
 * موعد نهاية من ساعة رتيبة (مش من وقت الجهاز)، وبيقف وإيد المالك على الشريط (عشان ما يختفيش وهو بيدوس «تراجع»).
 * [clock] بالمللي ثانية — في الاختبار ساعة مزيفة.
 */
class UndoWindow(private val clock: () -> Long, private val lengthMs: Long = UNDO_WINDOW_MS) {
    private val openedAt = clock()
    private var pausedAt: Long? = null
    private var pausedTotal = 0L

    /** الإيد على الشريط ⇒ الوقت واقف. */
    fun pause() {
        if (pausedAt == null) pausedAt = clock()
    }

    fun resume() {
        pausedAt?.let { pausedTotal += clock() - it }
        pausedAt = null
    }

    /** الباقي بالمللي ثانية (واقف وهو متوقف). */
    fun leftMs(): Long = (openedAt + pausedTotal + lengthMs - (pausedAt ?: clock())).coerceAtLeast(0L)

    /** خلص: الوقت كله عدّى والإيد مش عليه. */
    fun expired(): Boolean = pausedAt == null && leftMs() == 0L

    /** يستنى لحد ما يخلص (الإيقاف بيمد الانتظار). */
    suspend fun await() {
        while (!expired()) delay(leftMs().coerceIn(50L, lengthMs))
    }
}

private val monotonicOrigin = TimeSource.Monotonic.markNow()

/** ساعة رتيبة بالمللي ثانية للشاشات. */
internal fun monotonicMs(): Long = monotonicOrigin.elapsedNow().inWholeMilliseconds

/**
 * شباك لـ[value] (غير null ⇒ الشريط ظاهر): بيبدأ لما القيمة تتغير، ولما يخلص بينادي [onExpire] (الشاشة بتشيل الشريط أو بترجع).
 * بيكتب في سجل الأداء «undo strip shown/expired» عشان الفحص على المحاكي يثبت التوقيت من logcat.
 */
@Composable
internal fun <T : Any> undoWindowFor(value: T?, onExpire: () -> Unit): UndoWindow? {
    val window = remember(value) { value?.let { UndoWindow(::monotonicMs) } }
    val expire by rememberUpdatedState(onExpire)
    LaunchedEffect(window) {
        val w = window ?: return@LaunchedEffect
        app.masroufy.perf.PerfTrace.log("undo strip shown ms=$UNDO_WINDOW_MS")
        w.await()
        app.masroufy.perf.PerfTrace.log("undo strip expired")
        expire()
    }
    return window
}

/** الإيد على الشريط ⇒ [UndoWindow.pause] لحد ما ترفع (حتى لو الضغطة راحت لزرار «تراجع» جواه). */
internal fun Modifier.holdsUndo(window: UndoWindow?): Modifier = if (window == null) this else pointerInput(window) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        window.pause()
        try {
            waitForUpOrCancellation()
        } finally {
            window.resume()
        }
    }
}
