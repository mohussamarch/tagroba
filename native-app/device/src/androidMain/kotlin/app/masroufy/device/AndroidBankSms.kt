package app.masroufy.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import app.masroufy.core.BankSmsMessage
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.port.BankSmsPort
import app.masroufy.port.BankSmsRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** إذن الرسايل مش متاح — الشاشة بتطلبه (نافذة أندرويد) قبل ما تنادي، والرسالة دي لو اتسحب أو اترفض. */
class SmsPermissionError(message: String) : IllegalStateException(message)

/**
 * قراية رسايل البنك بطلب المستخدم — نقل `BankSmsPlugin.java`: فترة لحد سنة، ولحد 10 مرسلين، ولحد 500 رسالة (الباقي `truncated`).
 * رسايل الرموز (OTP) ما بتطلعش خالص. [zone] = منطقة وقت البلد (`CountryPack.timeZone`) — التطبيق الحالي كان مثبّت الرياض.
 */
class AndroidBankSms(private val context: Context, private val zone: ZoneId) : BankSmsPort {
    override val available: Boolean = true

    override suspend fun read(from: String, to: String, senders: List<String>): BankSmsRead = withContext(Dispatchers.IO) {
        val start = runCatching { LocalDate.parse(from) }.getOrNull()
        val end = runCatching { LocalDate.parse(to) }.getOrNull()
        val names = senders.map { it.trim() }
        val days = if (start != null && end != null) ChronoUnit.DAYS.between(start, end) else -1
        if (days < 0 || days > 366 || names.isEmpty() || names.size > 10 || names.any { it.isEmpty() || it.length > 50 }) {
            throw IllegalArgumentException(uiText(TextKey.SMS_READ_ARGS))
        }
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw SmsPermissionError(uiText(TextKey.SMS_PERMISSION_MISSING))
        }
        val fromMs = start!!.atStartOfDay(zone).toInstant().toEpochMilli()
        val untilMs = end!!.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val selection = "date >= ? AND date < ? AND (" + names.joinToString(" OR ") { "address = ? COLLATE NOCASE" } + ")"
        val args = arrayOf(fromMs.toString(), untilMs.toString()) + names
        val out = mutableListOf<BankSmsMessage>()
        var truncated = false
        try {
            context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), selection, args, "date DESC")?.use { c ->
                while (c.moveToNext()) {
                    val body = c.getString(1) ?: continue
                    if (SENSITIVE.containsMatchIn(body)) continue
                    if (out.size >= MAX_MESSAGES) {
                        truncated = true
                        break
                    }
                    out += BankSmsMessage(c.getString(0), Instant.ofEpochMilli(c.getLong(2)).toString(), body)
                }
            }
        } catch (_: SecurityException) {
            throw SmsPermissionError(uiText(TextKey.SMS_PERMISSION_REVOKED))
        }
        BankSmsRead(out, truncated)
    }

    private companion object {
        const val MAX_MESSAGES = 500
        val SENSITIVE = Regex(
            """\bOTP\b|verification\s*code|one.time\s*(password|code)|رمز\s*(التحقق|التوثيق|التفعيل|الدخول)|كلمة\s*(المرور|السر)|مشاركة\s*الرمز|الرمز\s*[:：]?\s*\d{4,8}""",
            RegexOption.IGNORE_CASE,
        )
    }
}
