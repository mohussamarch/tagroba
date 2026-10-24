package app.masroufy.android

import android.content.Context
import app.masroufy.port.SignOutCleanup

/**
 * تسجيل الخروج على أندرويد (AUTH-4 — PLAN-0.6 §1): قبل الخروج ⇒ قراية رسايل البنك بتقف ورسايل الحساب المحفوظة بتتمسح · نسخ
 * «قبل الإصلاح» · مسودة أول تشغيل ومسودة «شخصيتك» للحساب ده. بعد الخروج ⇒ نسخة فايربيز على الجهاز بتتمسح ([FirebaseHandles.wipeLocalCopy]).
 * مفيش uid ولا عدد في أي سجل.
 */
class AndroidSignOutCleanup(private val context: Context, private val firebase: FirebaseHandles?) : SignOutCleanup {
    override suspend fun beforeSignOut(uid: String) {
        app.masroufy.device.DeviceSignOut.wipe(context, uid)
        runCatching { AndroidOnboardingDraft(context, uid).write(null) }
        runCatching { AndroidCharacterDraft(context, uid).write(null) }
    }

    override suspend fun afterSignOut() {
        firebase?.wipeLocalCopy()
    }
}
