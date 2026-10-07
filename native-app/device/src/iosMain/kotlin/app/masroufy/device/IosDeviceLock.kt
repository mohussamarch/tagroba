package app.masroufy.device

import app.masroufy.port.DeviceLockAvailability
import app.masroufy.port.DeviceLockPort
import app.masroufy.port.LockResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

/**
 * قفل التطبيق على الآيفون (OVERRIDES §21) — `LAContext` من النظام: Face ID / Touch ID **أو رمز الجوال** (نفس
 * «البصمة أو الرمز» بتاع `DeviceLockPlugin.java` في التطبيق الحالي). التطبيق ما بيشوفش البصمة ولا الرمز — نتيجة بس.
 * الأكواد نفس أكواد أندرويد عشان رسايل `AppLock` تفضل واحدة.
 * ⚠️ تطبيق الآيفون لما يتعمل لازم يحط `NSFaceIDUsageDescription` في Info.plist، وإلا Face ID بيرفض.
 * على محاكي الآيفون في GitHub: `availability` رجّع «مش متاح» بكود `UNSUPPORTED` (مش `NONE_ENROLLED` — المحاكي بيرد بكود
 * غير «مفيش رمز»)، والتأكيد رجّع `UNAVAILABLE` فورًا. على جهاز حقيقي من غير رمز المتوقع `NONE_ENROLLED` (-5) — **ما اتجربش**،
 * ولا نافذة التأكيد نفسها (محتاجة إيد).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosDeviceLock(private val newContext: () -> LAContext = { LAContext() }) : DeviceLockPort {
    override val supported: Boolean = true

    override suspend fun availability(): DeviceLockAvailability = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val ok = newContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error.ptr)
        DeviceLockAvailability(ok, if (ok) "SUCCESS" else availabilityCode(error.value?.code ?: 0))
    }

    override suspend fun authenticate(title: String, subtitle: String?): LockResult {
        if (!availability().available) return LockResult.UNAVAILABLE
        val context = newContext()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, subtitle?.takeIf { it.isNotBlank() } ?: title) { success, error ->
                if (cont.isActive) cont.resume(if (success) LockResult.OK else resultOf(error?.code ?: 0))
            }
        }
    }

    internal companion object {
        // LAError: authenticationFailed -1 · userCancel -2 · userFallback -3 · systemCancel -4 · passcodeNotSet -5
        // biometryNotAvailable -6 · biometryNotEnrolled -7 · biometryLockout -8 · appCancel -9 · notInteractive -1004
        fun availabilityCode(code: Long): String = when (code) {
            -5L, -7L -> "NONE_ENROLLED"
            -6L -> "NO_HARDWARE"
            -8L -> "HW_UNAVAILABLE"
            else -> "UNSUPPORTED"
        }

        fun resultOf(code: Long): LockResult = when (code) {
            -2L, -3L, -4L, -9L -> LockResult.CANCELLED
            -5L, -6L, -7L, -8L, -1004L -> LockResult.UNAVAILABLE
            else -> LockResult.FAILED
        }
    }
}
