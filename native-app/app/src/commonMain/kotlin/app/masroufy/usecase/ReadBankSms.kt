package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.IsoDate
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsRow
import app.masroufy.core.TextKey
import app.masroufy.core.UiKey
import app.masroufy.core.daysBetween
import app.masroufy.core.isValidIsoDate
import app.masroufy.core.jsTrim
import app.masroufy.core.smsRowsJson
import app.masroufy.core.uiText
import app.masroufy.core.Id
import app.masroufy.port.BankSmsParser
import app.masroufy.port.BankSmsPort
import app.masroufy.port.smsSenderKey

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

    /**
     * الرسالة الملصوقة — **§82-14 (رد المالك 2026-10-10 ✱): لازم تاريخ** ([date] — المالك بيكتبه أو بيختار «النهاردة؟»). التاريخ ده هو «يوم
     * وصول» الرسالة: الرسالة اللي مكتوب فيها تاريخها بتاخد تاريخها هي، واللي مفيهاش بتاخد [date] (§77-C · §82-7). من غير تاريخ صالح ⇒ مرفوض.
     */
    fun paste(body: String, date: IsoDate): SmsBatch {
        if (!isValidIsoDate(date)) throw IllegalArgumentException(uiText(UiKey.SMS_PASTE_DATE_NEEDED))
        return prepare(listOf(BankSmsMessage(PASTED_SENDER, date, body)), truncated = false)
    }

    /** زي التطبيق القديم بالظبط (من غير وقت وصول) — لملف المرجع بس (`SmsFlowGoldenTest`)؛ الشاشة بتنادي [paste] بالتاريخ. */
    internal fun pasteWithoutDate(body: String): SmsBatch = prepare(listOf(BankSmsMessage(PASTED_SENDER, "", body)), truncated = false)

    /**
     * **§82-15 (رد المالك 2026-10-10): «اقرأ فترة» ⇒ كل بنك على محفظته المربوطة** — كانت الرسايل كلها بتروح محفظة واحدة يختارها المالك.
     * [walletOf] = محفظة البنك (المرسل بعد [smsSenderKey]) زي التسجيل لوحده (`AutoRecordSms.walletOf`)؛ البنك اللي مالوش ⇒ [fallback]
     * (المحفظة اللي المالك اختارها في الشاشة، أو null لسه). جزء لكل محفظة بصفوفه (نفس أرقام السطور — الحالة بتتجمع منهم) ومحتواه لوحده
     * (بصمة دفعته)؛ جزء البنوك اللي مالهاش محفظة ولسه ما اختارش ⇒ `walletId = null` (ما بيتسجلش). الرسايل اللي اتعدّت مش في أي جزء.
     */
    fun byWallet(batch: SmsBatch, walletOf: (String) -> Id?, fallback: Id?): List<SmsWalletPart> {
        val groups = LinkedHashMap<Id?, MutableList<SmsRow>>()
        val senders = LinkedHashMap<Id?, MutableSet<String>>()
        for (row in batch.rows) {
            val wallet = walletOf(smsSenderKey(row.sourceName)) ?: fallback
            groups.getOrPut(wallet) { mutableListOf() } += row
            senders.getOrPut(wallet) { linkedSetOf() } += row.sourceName
        }
        return groups.map { (wallet, rows) ->
            SmsWalletPart(wallet, senders.getValue(wallet).toList(), SmsBatch(rows, emptyList(), smsRowsJson(rows), batch.truncated))
        }
    }

    private companion object {
        const val PASTED_SENDER = "نص ملصق"
    }
}

/** §82-15: رسايل بنك (أو أكتر) رايحة محفظة واحدة — [walletId] null = البنوك دي مالهاش محفظة والمالك لسه ما اختارش. */
data class SmsWalletPart(val walletId: Id?, val senders: List<String>, val batch: SmsBatch)
