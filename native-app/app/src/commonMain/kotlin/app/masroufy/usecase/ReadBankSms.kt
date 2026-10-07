package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey
import app.masroufy.core.daysBetween
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.smsRowsJson
import app.masroufy.core.uiText
import app.masroufy.port.BankSmsParser
import app.masroufy.port.BankSmsPort

/** ReadBankSms — نقل `readBankSms.ts`: قراية رسايل فترة من الجوال، أو نص ملصوق، لصفوف جاهزة للاستيراد. */

data class SmsSkip(val line: Int, val reason: String)

data class SmsBatch(
    val rows: List<SmsRow>,
    val skipped: List<SmsSkip>,
    /** نص الصفوف زي `JSON.stringify` — محتوى «الملف» اللي خط الاستيراد بيبصمه. */
    val content: String,
    val truncated: Boolean,
)

class ReadBankSms(private val port: BankSmsPort, private val parse: BankSmsParser) {
    val available: Boolean get() = port.available

    private fun prepare(messages: List<BankSmsMessage>, truncated: Boolean): SmsBatch {
        val rows = mutableListOf<SmsRow>()
        val skipped = mutableListOf<SmsSkip>()
        messages.forEachIndexed { index, message ->
            when (val parsed = parse(message, index + 1)) {
                is SmsParseResult.Ok -> rows += parsed.row
                is SmsParseResult.Rejected -> skipped += SmsSkip(index + 1, parsed.reason)
            }
        }
        return SmsBatch(rows, skipped, smsRowsJson(rows), truncated)
    }

    suspend fun read(from: String, to: String, senders: List<String>): SmsBatch {
        if (!isValidIsoDate(from) || !isValidIsoDate(to) || from > to) throw IllegalArgumentException(uiText(TextKey.SMS_PERIOD_INVALID))
        if (daysBetween(from, to) > 366) throw IllegalArgumentException(uiText(TextKey.SMS_PERIOD_TOO_LONG))
        if (senders.isEmpty() || senders.size > 10 || senders.any { jsTrim(it).isEmpty() || it.length > 50 }) {
            throw IllegalArgumentException(uiText(TextKey.SMS_SENDER_REQUIRED))
        }
        val result = port.read(from, to, senders)
        return prepare(result.messages, result.truncated)
    }

    fun paste(body: String): SmsBatch = prepare(listOf(BankSmsMessage("نص ملصق", "", body)), truncated = false)
}
