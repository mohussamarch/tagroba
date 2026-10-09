package app.masroufy.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.masroufy.port.LockResult
import app.masroufy.usecase.AppLock

/**
 * بوابة القفل (OVERRIDES §21 · `AppLock`): لو القفل متشغّل في الإعدادات ⇒ التطبيق بيتقفل **عند الفتح وبعد ٥ دقايق في الخلفية**
 * (`AppLock.needsUnlock`)، ومحتواه ورا بلور لحد ما نافذة الجهاز (بصمة أو رمز) تأكد. القرار في حالة الاستخدام؛ هنا الحالة بس.
 * التطبيق بيبلّغ بـ[onHidden] (راح الخلفية) و[onVisible] (رجع).
 */
@Stable
class LockGate(private val lock: AppLock?) {
    var locked by mutableStateOf(lock?.needsUnlock(null) == true)
        private set

    /** رسالة جنب زرار «افتح» (فشل · اتلغى · مش متاح) أو null. */
    var message by mutableStateOf<String?>(null)
        private set

    /** نتيجة آخر محاولة (فشل ⇒ زرار «إعادة المحاولة» بدل «فتح» — لوحة `Lock`). null = لسه ما اتجربش أو اتفتح. */
    var lastResult by mutableStateOf<LockResult?>(null)
        private set

    /** القفل اتوقف لأن الجوال بقى مالوش قفل (رسالة لمرة واحدة — §21 سلوك مبدئي). */
    var released by mutableStateOf(false)
        private set

    private var hiddenAt: Long? = null

    fun onHidden() {
        hiddenAt = lock?.now?.invoke()
    }

    fun onVisible() {
        if (!locked && lock?.needsUnlock(hiddenAt) == true) {
            locked = true
            message = null
            lastResult = null
        }
        hiddenAt = null
    }

    /** نافذة الجهاز. */
    suspend fun unlock() {
        val l = lock ?: return
        if (l.releaseIfDeviceHasNoLock()) {
            locked = false
            released = true
            return
        }
        val outcome = l.unlock()
        if (outcome.result == LockResult.OK) {
            locked = false
            message = null
            lastResult = null
        } else {
            message = outcome.message
            lastResult = outcome.result
        }
    }

    fun dismissReleased() {
        released = false
    }
}
