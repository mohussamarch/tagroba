package app.masroufy.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.smsSenderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * صندوق رسايل البنك لحساب واحد ([uid]) — نقل `SmsInboxPlugin.java`. التفعيل محتاج إذن القراية والاستقبال، والشاشة هي اللي
 * بتطلبهم (نافذة أندرويد) — هنا لو مش موجودين بيرمي [SmsPermissionError]. المزامنة بتكمّل من آخر مؤشر (500 رسالة في المرة).
 */
class AndroidSmsInbox(private val context: Context, private val uid: String) : SmsInboxPort {
    override val available: Boolean = true

    private fun granted() = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun <T> withStore(block: (SmsInboxStore) -> T): T = withContext(Dispatchers.IO) {
        try {
            synchronized(SmsInboxStore.LOCK) { SmsInboxStore(context).use(block) }
        } catch (_: SecurityException) {
            throw SmsPermissionError(uiText(TextKey.SMS_INBOX_PERMISSION_REVOKED))
        }
    }

    override suspend fun enable(senders: List<String>): SmsInboxState {
        val names = senders.map { it.trim() }.toSet()
        if (names.isEmpty() || names.size > 10 || names.any { it.isEmpty() || it.length > 50 }) throw IllegalArgumentException(uiText(TextKey.SMS_SENDERS_REQUIRED))
        if (!granted()) throw SmsPermissionError(uiText(TextKey.SMS_INBOX_PERMISSION_MISSING))
        return withStore {
            it.configure(uid, names)
            it.snapshot(uid, true, false)
        }
    }

    override suspend fun disable(): SmsInboxState = withStore {
        it.disable(uid)
        it.snapshot(uid, granted(), false)
    }

    override suspend fun acknowledge(ids: List<String>): SmsInboxState = withStore {
        it.acknowledge(uid, ids)
        it.snapshot(uid, granted(), false)
    }

    override suspend fun senderWallets(spaceId: String): Map<String, String> = withStore { it.senderWallets(uid, spaceId) }

    override suspend fun setSenderWallet(spaceId: String, sender: String, walletId: String?) =
        withStore { it.setSenderWallet(uid, spaceId, smsSenderKey(sender), walletId) }

    override suspend fun sync(): SmsInboxState = withStore { store ->
        // التحقق من الإذن ما بيفتحش نافذة أندرويد لوحده أبدًا
        val more = store.enabled(uid) && granted() && catchUp(store)
        store.snapshot(uid, granted(), more)
    }

    /** الرسايل اللي وصلت والتطبيق مقفول (أو الاستقبال فاتها) — من آخر مؤشر، بالترتيب. */
    private fun catchUp(store: SmsInboxStore): Boolean {
        val senders = store.senders()
        if (senders.isEmpty()) return false
        val args = arrayOf(store.cursorDate().toString(), store.cursorDate().toString(), store.cursorId().toString()) + senders
        val selection = "(date > ? OR (date = ? AND _id > ?)) AND (" + senders.joinToString(" OR ") { "address = ? COLLATE NOCASE" } + ")"
        val projection = arrayOf("_id", "address", "body", "date", "date_sent")
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args, "date ASC, _id ASC")?.use { rows ->
            var processed = 0
            while (rows.moveToNext()) {
                if (processed++ >= 500) return true
                val date = rows.getLong(3)
                val sent = rows.getLong(4)
                store.enqueue(uid, rows.getString(1), if (sent > 0) sent else date, rows.getString(2) ?: "")
                // المؤشر بيتحرك بعد الحفظ (أو الاستبعاد المقصود) بس
                store.checkpoint(date, rows.getLong(0))
            }
        }
        return false
    }
}
