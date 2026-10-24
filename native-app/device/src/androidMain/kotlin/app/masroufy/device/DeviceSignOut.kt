package app.masroufy.device

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * اللي على الجهاز وبيتمسح مع تسجيل الخروج (AUTH-4 — PLAN-0.6 §1): قراية رسايل البنك بتقف ورسايل الحساب المحفوظة بتتمسح،
 * ونسخ «قبل الإصلاح» بتتمسح. المسودات لكل حساب بتتمسح في `androidApp` (هي اللي عاملاها). كل خطوة لوحدها: فشل واحدة ما بيوقفش الباقي.
 */
object DeviceSignOut {
    data class Wiped(val smsRows: Int, val backups: Int)

    suspend fun wipe(context: Context, uid: String): Wiped = withContext(Dispatchers.IO) {
        val sms = runCatching { synchronized(SmsInboxStore.LOCK) { SmsInboxStore.shared(context).forget(uid) } }.getOrDefault(0)
        Wiped(sms, AndroidRepairBackup.wipeAll(context))
    }
}
