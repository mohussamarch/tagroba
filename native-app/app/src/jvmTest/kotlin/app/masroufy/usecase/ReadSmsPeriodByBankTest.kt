package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.toParsedRow
import app.masroufy.port.BankSmsPort
import app.masroufy.port.BankSmsRead
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §82-15 (رد المالك 2026-10-10): **«اقرأ فترة» ⇒ كل بنك على محفظته المربوطة** — كانت الرسايل كلها بتروح محفظة واحدة يختارها المالك.
 * بنكين مخترعين (TESTBANK · OTHERBANK) ومحفظتين، وبنك تالت مش مربوط بياخد المحفظة اللي المالك اختارها. كل الرسايل مخترعة.
 */
class ReadSmsPeriodByBankTest {
    private val messages = listOf(
        BankSmsMessage("TESTBANK", "2026-10-07T10:00:00Z", "شراء\nبـSR 25\nلدى:TEST CAFE\n26/10/07"),
        BankSmsMessage("OtherBank", "2026-10-07T11:00:00Z", "شراء\nبـSR 40\nلدى:TEST MART\n26/10/07"),
        BankSmsMessage("NEWBANK", "2026-10-07T12:00:00Z", "شراء\nبـSR 12\nلدى:TEST SHOP\n26/10/07"),
        BankSmsMessage("testbank", "2026-10-07T13:00:00Z", "رسالة بلا عملية"),
    )
    private val reader = ReadBankSms(
        object : BankSmsPort {
            override val available = true
            override suspend fun read(from: String, to: String, senders: List<String>) = BankSmsRead(messages, false)
        },
        ::parseBankSms,
    )
    private val linked = mapOf("testbank" to BANK.id, "otherbank" to BANK2.id)

    @Test fun eachBankGoesToItsOwnLinkedWallet() = runBlocking<Unit> {
        val batch = reader.read("2026-10-01", "2026-10-07", listOf("TESTBANK", "OtherBank", "NEWBANK"))
        assertEquals(3, batch.rows.size)
        val parts = reader.byWallet(batch, { linked[it] }, fallback = null)
        assertEquals(listOf(BANK.id, BANK2.id, null), parts.map { it.walletId })
        assertEquals(listOf(listOf(2_500L), listOf(4_000L), listOf(1_200L)), parts.map { p -> p.batch.rows.map { it.amountMinor } })
        assertEquals(listOf(listOf("TESTBANK"), listOf("OtherBank"), listOf("NEWBANK")), parts.map { it.senders })
        // أرقام السطور نفسها (حالة كل سطر بتتجمع من معاينة محفظته) · ومحتوى كل جزء لوحده (بصمة دفعة لكل محفظة)
        assertEquals(batch.rows.map { it.lineNumber }, parts.flatMap { p -> p.batch.rows.map { it.lineNumber } })
        assertEquals(3, parts.map { it.batch.content }.distinct().size)
        assertTrue(parts.all { it.batch.skipped.isEmpty() }, "the skipped message is in no part")
        // البنك اللي مش مربوط ⇒ المحفظة اللي المالك اختارها
        assertEquals(listOf(BANK.id, BANK2.id, BANK2.id), reader.byWallet(batch, { linked[it] }, fallback = BANK2.id).flatMap { p -> p.batch.rows.map { p.walletId } })
    }

    /** آخر الطريق: كل جزء بيتسجل في محفظته — رسالة البنك التاني عمرها ما بتتسجل في محفظة البنك الأول. */
    @Test fun committingThePartsWritesEachBankIntoItsWallet() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, BANK, BANK2))
        val importer = ImportStatement(space.importDeps())
        val batch = reader.read("2026-10-01", "2026-10-07", listOf("TESTBANK", "OtherBank", "NEWBANK"))
        val wallets = listOf(BANK, BANK2).associateBy { it.id }
        for (part in reader.byWallet(batch, { linked[it] }, fallback = BANK.id)) {
            val w = wallets.getValue(part.walletId!!)
            val request = request(part.batch, w)
            val preview = importer.preview(request)
            assertTrue(preview.lines.all { it.state == MatchingState.NEW })
            importer.commit(request, preview)
        }
        val byWallet = space.all().groupBy({ it.walletId }, { it.amountMinor })
        assertEquals(mapOf<String?, List<Long>>(BANK.id to listOf(2_500L, 1_200L), BANK2.id to listOf(4_000L)), byWallet.mapValues { it.value.sorted().reversed() })
    }

    /** نفس طلب الشاشة (`smsRequest` في «الاستيراد»). */
    private fun request(batch: SmsBatch, wallet: Wallet) = ImportRequest(
        fileName = "bank-sms.json", content = batch.content, accountIdentity = wallet.name, sourceType = ImportSourceType.SMS,
        walletId = wallet.id, schema = SchemaId.SMS, parsedRows = batch.rows.map { it.toParsedRow() }, currency = wallet.currency,
    )
}
