package app.masroufy.personal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class BankSmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                synchronized (SmsInboxStore.LOCK) {
                    try (SmsInboxStore store = new SmsInboxStore(context)) {
                        if (!store.enabled(store.owner())) return;
                        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                        if (parts == null || parts.length == 0) return;
                        String sender = parts[0].getOriginatingAddress();
                        if (!store.accepts(sender)) return;
                        StringBuilder body = new StringBuilder();
                        for (SmsMessage part : parts) {
                            if (!java.util.Objects.equals(sender, part.getOriginatingAddress())) return;
                            body.append(part.getMessageBody());
                        }
                        store.enqueue(store.owner(), sender, parts[0].getTimestampMillis(), body.toString());
                    }
                }
            } catch (Exception ignored) {
                // Foreground READ_SMS catch-up retries from its persisted checkpoint; never log SMS.
            } finally { pending.finish(); }
        }, "masroufy-sms").start();
    }
}
