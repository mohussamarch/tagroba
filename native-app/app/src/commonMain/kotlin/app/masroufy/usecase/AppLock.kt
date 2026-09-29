package app.masroufy.usecase

import app.masroufy.core.shouldLock
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
    LockResult.CANCELLED -> "اتلغى التأكيد — ما اتغيّرش حاجة."
    LockResult.FAILED -> "ما اتأكدش إنك صاحب الجوال — ما اتغيّرش حاجة."
    else -> "الجوال مالوش قفل شاشة ولا بصمة متسجلة. اعمل قفل شاشة من إعدادات الجوال الأول."
}

private val AVAILABILITY_MESSAGE = mapOf(
    "NOT_ANDROID" to "القفل بالبصمة متاح في تطبيق أندرويد بس.",
    "NONE_ENROLLED" to resultMessage(LockResult.UNAVAILABLE),
    "NO_HARDWARE" to "الجوال ده مافيهوش بصمة ولا قفل مدعوم.",
    "HW_UNAVAILABLE" to "حساس البصمة مش متاح دلوقتي. جرّب تاني بعد شوية.",
    "SECURITY_UPDATE_REQUIRED" to "الجوال محتاج تحديث أمان قبل ما يسمح بالبصمة.",
)

class AppLock(private val device: DeviceLockPort, private val settings: AppLockSettingsPort, val now: () -> Long) {
    val supported: Boolean get() = device.supported

    private suspend fun confirmOwner(title: String): LockChange {
        val availability = device.availability()
        if (!availability.available) return LockChange.Refused(AVAILABILITY_MESSAGE[availability.code] ?: resultMessage(LockResult.UNAVAILABLE))
        val result = device.authenticate(title)
        return if (result == LockResult.OK) LockChange.Ok else LockChange.Refused(resultMessage(result))
    }

    fun isEnabled(): Boolean = settings.read()

    fun needsUnlock(hiddenAt: Long?): Boolean = shouldLock(settings.read(), hiddenAt, now())

    suspend fun enable(): LockChange = confirmOwner("أكّد علشان تشغّل قفل مصروفي").also { if (it == LockChange.Ok) settings.write(true) }

    suspend fun disable(): LockChange = confirmOwner("أكّد علشان توقف قفل مصروفي").also { if (it == LockChange.Ok) settings.write(false) }

    /** فتح التطبيق المقفول. */
    suspend fun unlock(): UnlockOutcome {
        val result = device.authenticate("افتح مصروفي", "بالبصمة أو برمز الجوال")
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
