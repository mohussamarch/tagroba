package app.masroufy.ui.screens.auth

import app.masroufy.core.UiKey
import app.masroufy.core.TextKey
import app.masroufy.port.AppLockSettingsPort
import app.masroufy.port.DeviceLockAvailability
import app.masroufy.port.DeviceLockPort
import app.masroufy.port.LockResult
import app.masroufy.ui.app.AppSession
import app.masroufy.ui.app.LockGate
import app.masroufy.ui.app.lockCovers
import app.masroufy.usecase.AppLock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * شاشة القفل (`Lock` في النموذج): الحالات التلاتة — عادي (البصمة اتطابقت ⇒ التطبيق يفتح) · خطأ (ما اتطابقتش ⇒ الرسالة + «إعادة المحاولة») ·
 * غير متاح (الجوال مالوش قفل ⇒ القفل بيتوقف وكارت «أُوقف قفل التطبيق»). القرار في `AppLock` — هنا الربط بحالة الشاشة.
 */
class LockScreenStateTest {
    private class Settings(var on: Boolean) : AppLockSettingsPort {
        override fun read() = on

        override fun write(enabled: Boolean) {
            on = enabled
        }
    }

    private class Device(var results: MutableList<LockResult>, var code: String = "OK") : DeviceLockPort {
        override val supported = true

        override suspend fun availability() = DeviceLockAvailability(code == "OK", code)

        override suspend fun authenticate(title: String, subtitle: String?): LockResult = results.removeAt(0)
    }

    @Test fun buttonSaysRetryOnlyAfterAFailedFingerprint() {
        assertEquals(UiKey.LOCK_SCREEN_OPEN, lockButtonKey(null), "أول مرة ⇒ «فتح»")
        assertEquals(UiKey.LOCK_SCREEN_RETRY, lockButtonKey(LockResult.FAILED), "ما اتطابقتش ⇒ «إعادة المحاولة»")
        assertEquals(UiKey.LOCK_SCREEN_OPEN, lockButtonKey(LockResult.CANCELLED), "اتلغت ⇒ «فتح»")
        assertEquals(UiKey.LOCK_SCREEN_OPEN, lockButtonKey(LockResult.UNAVAILABLE))
    }

    @Test fun failedThenOkUnlocksAndClearsTheMessage() = runBlocking {
        val gate = LockGate(AppLock(Device(mutableListOf(LockResult.FAILED, LockResult.OK)), Settings(true)) { 0L })
        assertTrue(gate.locked, "القفل متشغّل ⇒ مقفول عند الفتح")
        gate.unlock()
        assertTrue(gate.locked)
        assertEquals(LockResult.FAILED, gate.lastResult)
        assertTrue(gate.message != null, "فشل ⇒ رسالة من `AppLock`")
        gate.unlock()
        assertFalse(gate.locked)
        assertNull(gate.message)
        assertNull(gate.lastResult)
    }

    @Test fun phoneWithoutAnyLockReleasesTheLockAndSaysSo() = runBlocking {
        val settings = Settings(true)
        val gate = LockGate(AppLock(Device(mutableListOf(), code = "NONE_ENROLLED"), settings) { 0L })
        gate.unlock()
        assertFalse(gate.locked)
        assertTrue(gate.released, "كارت «أُوقف قفل التطبيق»")
        assertFalse(settings.on, "القفل اتقفل في الإعدادات")
        gate.dismissReleased()
        assertFalse(gate.released)
    }

    /** المهلة بالوقت المنقضي بس (`AppLock.elapsedMs`): ساعة الحيطة مش داخلة — الرجوع بيها لورا ما بيفتحش. */
    @Test fun graceUsesElapsedTimeOnly() = runBlocking {
        var elapsed = 1_000L
        val gate = LockGate(AppLock(Device(mutableListOf(LockResult.OK)), Settings(true)) { elapsed })
        gate.unlock()
        assertFalse(gate.locked)
        gate.onHidden()
        elapsed += 4 * 60_000L
        gate.onVisible()
        assertFalse(gate.locked, "أقل من ٥ دقايق منقضية ⇒ مفتوح")
        gate.onHidden()
        elapsed += 6 * 60_000L
        gate.onVisible()
        assertTrue(gate.locked, "٦ دقايق منقضية ⇒ مقفول، مهما كانت ساعة الجوال")
    }

    /** مراجعة 2026-11-14: زرار الرجوع ما بيتنقلش جوه التطبيق ورا شاشة القفل. */
    @Test fun backDoesNotNavigateBehindTheLock() {
        val gate = LockGate(AppLock(Device(mutableListOf()), Settings(true)) { 0L })
        assertTrue(gate.backNavigates(), "قبل ما الشاشة تترسم")
        gate.covering = lockCovers(gate.locked, AppSession.Starting)
        assertFalse(gate.backNavigates())
        gate.covering = lockCovers(gate.locked, AppSession.SignedOut)
        assertTrue(gate.backNavigates(), "شاشة الدخول مالهاش قفل")
        assertFalse(lockCovers(false, AppSession.Starting))
    }
}
