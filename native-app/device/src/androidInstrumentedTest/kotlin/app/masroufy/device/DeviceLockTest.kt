package app.masroufy.device

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.masroufy.port.LockResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * القفل على محاكي أندرويد **من غير رمز ولا بصمة** (`masroufy-fbtrial` — Claude ما بيكتبش رموز دخول):
 * الإتاحة بتقول `NONE_ENROLLED`، والتأكيد بيرجع `UNAVAILABLE` فورًا — من غير شاشة، **ومن شاشة حقيقية** (`BiometricPrompt`
 * بيتفتح فعلًا وبيرد بخطأ «مفيش رمز»). نافذة التأكيد بإيد صاحب الجوال **ما اتجربتش** (محتاجة رمز على الجهاز).
 */
@RunWith(AndroidJUnit4::class)
class DeviceLockTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun withoutAScreenLockItIsUnavailableNotStuck() = runBlocking<Unit> {
        val availability = AndroidDeviceLock(context) { null }.availability()
        assertFalse(availability.available)
        assertEquals("NONE_ENROLLED", availability.code)
        assertEquals(LockResult.UNAVAILABLE, AndroidDeviceLock(context) { null }.authenticate("مصروفي"))
    }

    @Test fun thePromptItselfAnswersUnavailableOnADeviceWithoutLock() {
        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            var host: FragmentActivity? = null
            scenario.onActivity { host = it }
            val result = runBlocking { withTimeout(15_000) { AndroidDeviceLock(context) { host }.authenticate("مصروفي", "تأكيد") } }
            assertEquals(LockResult.UNAVAILABLE, result)
        }
    }

    @Test fun errorCodesMapLikeTheCurrentApp() {
        assertEquals(LockResult.CANCELLED, AndroidDeviceLock.resultOf(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(LockResult.CANCELLED, AndroidDeviceLock.resultOf(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(LockResult.UNAVAILABLE, AndroidDeviceLock.resultOf(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
        assertEquals(LockResult.FAILED, AndroidDeviceLock.resultOf(BiometricPrompt.ERROR_LOCKOUT))
    }
}
