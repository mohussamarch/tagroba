package app.masroufy.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * رسالة جديدة وصلت ⇒ لو من مرسل مفعّل، بتعدّي على [SmsSafety] وتدخل الصندوق — نقل `BankSmsReceiver.java`.
 * **§72:** لو دخلت الصندوق فعلًا ⇒ بيطلب دورة في الخلفية ([MasroufyBackground.requestRun]) تسجّلها لوحدها.
 * ⚠️ عمره ما بيكتب نص رسالة في السجل. لو فشل، المزامنة (الدورية أو لما التطبيق يفتح) بتكمّل من آخر مؤشر.
 */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        val app = context.applicationContext
        Thread({
            try {
                val queued = synchronized(SmsInboxStore.LOCK) {
                    SmsInboxStore(context).use { store -> receive(store, intent) }
                }
                if (queued) MasroufyBackground.requestRun(app)
            } catch (_: Exception) {
                // مفيش تسجيل لنص رسالة أبدًا
            } finally {
                pending.finish()
            }
        }, "masroufy-sms").start()
    }

    /** `true` = رسالة جديدة دخلت الصندوق. */
    private fun receive(store: SmsInboxStore, intent: Intent): Boolean {
        if (!store.enabled(store.owner())) return false
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return false
        val sender = parts[0].originatingAddress ?: return false
        if (!store.accepts(sender)) return false
        // الرسالة الطويلة بتيجي حتت — كلها لازم من نفس المرسل
        if (parts.any { it.originatingAddress != sender }) return false
        return store.enqueue(store.owner(), sender, parts[0].timestampMillis, parts.joinToString("") { it.messageBody ?: "" })
    }
}
