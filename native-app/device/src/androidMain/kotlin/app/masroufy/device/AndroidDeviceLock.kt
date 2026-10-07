package app.masroufy.device

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.masroufy.port.DeviceLockAvailability
import app.masroufy.port.DeviceLockPort
import app.masroufy.port.LockResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * قفل التطبيق بالبصمة **أو رمز الجوال** (OVERRIDES §21، ARCHITECTURE §27) — نقل `DeviceLockPlugin.java` سطر بسطر:
 * `BiometricPrompt` بيعرض نافذة النظام وبيرجّع نجح/فشل بس — التطبيق ما بيشوفش البصمة ولا الرمز.
 * [activity]: الشاشة اللي النافذة هتطلع فوقها (تطبيق أندرويد هيدّيها لما يتعمل)؛ من غيرها ⇒ `UNAVAILABLE` من غير وقع.
 */
class AndroidDeviceLock(private val context: Context, private val activity: () -> FragmentActivity?) : DeviceLockPort {
    override val supported: Boolean = true

    override suspend fun availability(): DeviceLockAvailability {
        val code = BiometricManager.from(context).canAuthenticate(authenticators())
        return DeviceLockAvailability(code == BiometricManager.BIOMETRIC_SUCCESS, codeName(code))
    }

    override suspend fun authenticate(title: String, subtitle: String?): LockResult {
        val host = activity() ?: return LockResult.UNAVAILABLE
        return suspendCancellableCoroutine { cont ->
            fun finish(result: LockResult) {
                if (cont.isActive) cont.resume(result)
            }
            host.runOnUiThread {
                val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(host), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(LockResult.OK)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finish(resultOf(errorCode))

                    // onAuthenticationFailed = محاولة بصمة غلط واحدة؛ النافذة بتفضل مفتوحة لمحاولة تانية، فمش نهاية
                })
                val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(authenticators())
                if (!subtitle.isNullOrEmpty()) info.setSubtitle(subtitle)
                cont.invokeOnCancellation { host.runOnUiThread { prompt.cancelAuthentication() } }
                prompt.authenticate(info.build())
            }
        }
    }

    internal companion object {
        /** بصمة قوية أو رمز الجوال. على أندرويد 9 و10 الجمع ده مش مدعوم (توثيق androidx.biometric)، فالضعيفة + الرمز. */
        fun authenticators(): Int {
            val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BiometricManager.Authenticators.BIOMETRIC_STRONG or credential
            else BiometricManager.Authenticators.BIOMETRIC_WEAK or credential
        }

        fun resultOf(errorCode: Int): LockResult = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> LockResult.CANCELLED
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_BIOMETRICS -> LockResult.UNAVAILABLE
            else -> LockResult.FAILED
        }

        fun codeName(code: Int): String = when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> "SUCCESS"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NONE_ENROLLED"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "SECURITY_UPDATE_REQUIRED"
            else -> "UNSUPPORTED"
        }
    }
}
