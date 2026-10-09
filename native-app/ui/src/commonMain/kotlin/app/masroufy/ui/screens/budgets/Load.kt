package app.masroufy.ui.screens.budgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** حالة تحميل شاشة: بيحمّل (هيكل رمادي) · فشل (كارت خطأ + «أعد المحاولة») · جاهز. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Failed(val message: String?) : Load<Nothing>

    data class Ready<T>(val value: T) : Load<T>
}

/**
 * بيحمّل [block] أول مرة ومع كل تغيير في [key] (البلد — `LocalSpace`) أو [reload] (بعد حفظ). وقت إعادة التحميل بعد حفظ البيانات القديمة
 * بتفضل ظاهرة (من غير وميض هيكل) لحد ما الجديدة توصل.
 */
@Composable
fun <T> rememberLoad(key: Any?, reload: Int, block: suspend () -> T): Load<T> {
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(key, reload) {
        state = try {
            Load.Ready(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e.message)
        }
    }
    return state
}

/** رسالة الخطأ اللي حالة الاستخدام رمتها (نص من جداول النصوص — `uiText`) — أو [fallback] لو مالهاش رسالة. */
fun Throwable.userMessage(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback

/** بيشغّل فعل حفظ ويرجّع رسالة الخطأ (أو null لو نجح) — الشاشة بتعرضها جنب الخانة أو في رسالة صغيرة. */
suspend fun attempt(fallback: String, action: suspend () -> Unit): String? = try {
    action()
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    e.userMessage(fallback)
}
