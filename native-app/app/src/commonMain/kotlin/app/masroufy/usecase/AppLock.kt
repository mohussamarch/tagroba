package app.masroufy.usecase

import app.masroufy.core.TextKey
import app.masroufy.core.shouldLock
import app.masroufy.core.uiText
import app.masroufy.port.AppLockSettingsPort
import app.masroufy.port.DeviceLockPort
import app.masroufy.port.LockResult

/**
 * AppLock — نقل `appLock.ts`: قفل التطبيق (OVERRIDES §21). اختياري، بيتقفل عند الفتح وبعد خمس دقايق في الخلفية،
 * والبصمة ليها بديل رمز الجوال (النظام بيعرضه لوحده). التشغيل والإيقاف **الاتنين محتاجين تأكيد** —
 * عشان حد ماسك الجوال وهو مفتوح ما يقدرش يشيل القفل من الإعدادات.
 */

sealed interface LockChange {
    data object Ok : LockChange

    data class Refused(val message: String) : LockChange
}

data class UnlockOutcome(val result: LockResult, val message: String?)

private fun resultMessage(result: LockResult): String = when (result) {
    LockResult.CANCELLED -> uiText(TextKey.LOCK_CANCELLED)
    LockResult.FAILED -> uiText(TextKey.LOCK_FAILED)
    else -> uiText(TextKey.LOCK_UNAVAILABLE)
}

// دالة مش جدول ثابت: النص بيتبني وقت العرض بنسخة العربي الشغالة (§66)
private fun availabilityMessage(code: String): String? = when (code) {
    "NOT_ANDROID" -> uiText(TextKey.LOCK_NOT_ANDROID)
    "NONE_ENROLLED" -> resultMessage(LockResult.UNAVAILABLE)
    "NO_HARDWARE" -> uiText(TextKey.LOCK_NO_HARDWARE)
    "HW_UNAVAILABLE" -> uiText(TextKey.LOCK_HW_UNAVAILABLE)
    "SECURITY_UPDATE_REQUIRED" -> uiText(TextKey.LOCK_SECURITY_UPDATE)
    else -> null
}

class AppLock(private val device: DeviceLockPort, private val settings: AppLockSettingsPort, val now: () -> Long) {
    val supported: Boolean get() = device.supported

    private suspend fun confirmOwner(title: String): LockChange {
        val availability = device.availability()
        if (!availability.available) return LockChange.Refused(availabilityMessage(availability.code) ?: resultMessage(LockResult.UNAVAILABLE))
        val result = device.authenticate(title)
        return if (result == LockResult.OK) LockChange.Ok else LockChange.Refused(resultMessage(result))
    }

    fun isEnabled(): Boolean = settings.read()

    fun needsUnlock(hiddenAt: Long?): Boolean = shouldLock(settings.read(), hiddenAt, now())

    suspend fun enable(): LockChange = confirmOwner(uiText(TextKey.LOCK_CONFIRM_ENABLE)).also { if (it == LockChange.Ok) settings.write(true) }

    suspend fun disable(): LockChange = confirmOwner(uiText(TextKey.LOCK_CONFIRM_DISABLE)).also { if (it == LockChange.Ok) settings.write(false) }

    /** فتح التطبيق المقفول. */
    suspend fun unlock(): UnlockOutcome {
        val result = device.authenticate(uiText(TextKey.LOCK_UNLOCK_TITLE), uiText(TextKey.LOCK_UNLOCK_SUBTITLE))
        return UnlockOutcome(result, if (result == LockResult.OK) null else resultMessage(result))
    }

    /**
     * القفل متشغّل بس الجوال بقى مالوش قفل خالص: النظام ما يقدرش يأكد حاجة، فالقفل بيتوقف **ويتقال ده للمستخدم**
     * بدل ما يتقفل بره التطبيق. ❓ سلوك مبدئي مستني تأكيد المالك (زي التطبيق الحالي).
     */
    suspend fun releaseIfDeviceHasNoLock(): Boolean {
        val availability = device.availability()
        if (availability.available || availability.code != "NONE_ENROLLED") return false
        settings.write(false)
        return true
    }
}
