package app.masroufy.personal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/** No Android dependencies: filtering happens before local persistence. */
public final class SmsSafety {
    private static final Pattern IGNORE = Pattern.compile(
        "\\bOTP\\b|verification\\s*code|one.time\\s*(password|code)|رمز\\s*(التحقق|التوثيق|التفعيل|الدخول)|كلمة\\s*(المرور|السر)|عرض|سيتم|offer|will be|scheduled|مرفوض|لم تتم|declined|failed",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVEMENT = Pattern.compile(
        "شراء|سحب نقدي|حوالة|تحويل|سداد|إيداع|ايداع|راتب|استرداد|purchase|withdrawal|transfer|deposit|refund",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY = Pattern.compile("SAR|ريال|ر\\.?س\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERS = Pattern.compile("SA[\\d\\s]{20,}|\\b(?:\\d[ -]*){12,34}\\b|\\d{5,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern FINANCIAL = Pattern.compile("(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)\\s*[:：]?\\s*(?:(?:SAR|ريال|ر\\.?س\\.?)\\s*[\\d,٬]+(?:[.٫]\\d{1,2})?|[\\d,٬]+(?:[.٫]\\d{1,2})?\\s*(?:SAR|ريال|ر\\.?س\\.?))", Pattern.CASE_INSENSITIVE);

    public static String sanitize(String body) {
        if (body == null || body.length() > 8000) return null;
        StringBuilder latin = new StringBuilder();
        for (char c : body.toCharArray()) {
            int digit = Character.digit(c, 10);
            latin.append(digit >= 0 ? (char) ('0' + digit) : c);
        }
        String text = latin.toString();
        if (IGNORE.matcher(text).find() || !MOVEMENT.matcher(text).find() || !MONEY.matcher(text).find()) return null;
        var amounts = FINANCIAL.matcher(text);
        StringBuilder safe = new StringBuilder();
        int end = 0;
        while (amounts.find()) {
            safe.append(redactIdentifiers(text.substring(end, amounts.start()))).append(amounts.group());
            end = amounts.end();
        }
        return safe.append(redactIdentifiers(text.substring(end))).toString();
    }
    private static String redactIdentifiers(String text) {
        var matcher = NUMBERS.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            matcher.appendReplacement(result, "••••" + digits.substring(Math.max(0, digits.length() - 4)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String key(String sender, long timestamp, String body) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                (sender.toLowerCase(java.util.Locale.ROOT) + "|" + timestamp + "|" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 255));
            return hex.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
