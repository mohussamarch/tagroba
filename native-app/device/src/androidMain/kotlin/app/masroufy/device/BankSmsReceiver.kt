package app.masroufy.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * رسالة جديدة وصلت ⇒ لو من مرسل مفعّل، بتعدّي على [SmsSafety] وتدخل الصندوق — نقل `BankSmsReceiver.java`.
 * ⚠️ عمره ما بيكتب نص رسالة في السجل. لو فشل، المزامنة لما التطبيق يفتح بتكمّل من آخر مؤشر.
 */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        Thread({
            try {
                synchronized(SmsInboxStore.LOCK) {
                    SmsInboxStore(context).use { store -> receive(store, intent) }
                }
            } catch (_: Exception) {
                // مفيش تسجيل لنص رسالة أبدًا
            } finally {
                pending.finish()
            }
        }, "masroufy-sms").start()
    }

    private fun receive(store: SmsInboxStore, intent: Intent) {
        if (!store.enabled(store.owner())) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return
        val sender = parts[0].originatingAddress ?: return
        if (!store.accepts(sender)) return
        // الرسالة الطويلة بتيجي حتت — كلها لازم من نفس المرسل
        if (parts.any { it.originatingAddress != sender }) return
        store.enqueue(store.owner(), sender, parts[0].timestampMillis, parts.joinToString("") { it.messageBody ?: "" })
    }
}
