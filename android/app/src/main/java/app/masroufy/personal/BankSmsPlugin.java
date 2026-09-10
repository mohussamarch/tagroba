package app.masroufy.personal;

import android.Manifest;
import android.database.Cursor;
import android.provider.Telephony;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@CapacitorPlugin(name = "BankSms", permissions = {
    @Permission(alias = "sms", strings = { Manifest.permission.READ_SMS })
})
public class BankSmsPlugin extends Plugin {
    private static final int MAX_MESSAGES = 500;
    private static final Pattern SENSITIVE = Pattern.compile(
        "\\bOTP\\b|verification\\s*code|one.time\\s*(password|code)|رمز\\s*(التحقق|التوثيق|التفعيل|الدخول)|كلمة\\s*(المرور|السر)",
        Pattern.CASE_INSENSITIVE
    );

    @PluginMethod
    public void read(PluginCall call) {
        try {
            validate(call);
        } catch (Exception error) {
            call.reject("اختار فترة لا تتجاوز سنة واسم مرسل البنك");
            return;
        }
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            requestPermissionForAlias("sms", call, "permissionResult");
            return;
        }
        readGranted(call);
    }

    @PermissionCallback
    private void permissionResult(PluginCall call) {
        if (getPermissionState("sms") != PermissionState.GRANTED) {
            call.reject("إذن قراءة الرسائل مش متاح. تقدر تلصق رسالة واحدة بدل القراءة.");
            return;
        }
        readGranted(call);
    }

    private void validate(PluginCall call) throws Exception {
        LocalDate from = LocalDate.parse(call.getString("from", ""));
        LocalDate to = LocalDate.parse(call.getString("to", ""));
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (days < 0 || days > 366) throw new IllegalArgumentException();
        JSArray senders = call.getArray("senders", new JSArray());
        if (senders.length() == 0 || senders.length() > 10) throw new IllegalArgumentException();
        for (int i = 0; i < senders.length(); i++) {
            String sender = senders.getString(i).trim();
            if (sender.isEmpty() || sender.length() > 50) throw new IllegalArgumentException();
        }
    }

    private void readGranted(PluginCall call) {
        getBridge().execute(() -> {
            try {
                ZoneId zone = ZoneId.of("Asia/Riyadh");
                long from = LocalDate.parse(call.getString("from")).atStartOfDay(zone).toInstant().toEpochMilli();
                long until = LocalDate.parse(call.getString("to")).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                JSArray senders = call.getArray("senders");
                List<String> args = new ArrayList<>();
                args.add(Long.toString(from)); args.add(Long.toString(until));
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < senders.length(); i++) {
                    conditions.add("address = ? COLLATE NOCASE");
                    args.add(senders.getString(i).trim());
                }
                String selection = "date >= ? AND date < ? AND (" + String.join(" OR ", conditions) + ")";
                JSArray messages = new JSArray();
                boolean truncated = false;
                try (Cursor cursor = getContext().getContentResolver().query(
                    Telephony.Sms.Inbox.CONTENT_URI, new String[]{"address", "body", "date"},
                    selection, args.toArray(new String[0]), "date DESC"
                )) {
                    if (cursor != null) while (cursor.moveToNext()) {
                        String body = cursor.getString(1);
                        if (body == null || SENSITIVE.matcher(body).find()) continue;
                        if (messages.length() >= MAX_MESSAGES) { truncated = true; break; }
                        JSObject message = new JSObject();
                        message.put("sender", cursor.getString(0));
                        message.put("body", body);
                        message.put("receivedAt", Instant.ofEpochMilli(cursor.getLong(2)).toString());
                        messages.put(message);
                    }
                }
                JSObject result = new JSObject();
                result.put("messages", messages); result.put("truncated", truncated);
                call.resolve(result);
            } catch (SecurityException error) {
                call.reject("إذن الرسائل اتسحب. راجع أذونات التطبيق أو استخدم اللصق.");
            } catch (Exception error) {
                call.reject("تعذر قراءة الرسائل. جرّب فترة أصغر أو استخدم اللصق.");
            }
        });
    }
}
