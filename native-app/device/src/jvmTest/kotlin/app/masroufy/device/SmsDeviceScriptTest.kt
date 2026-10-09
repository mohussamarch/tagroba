package app.masroufy.device

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Currency
import app.masroufy.core.SmsParseResult
import app.masroufy.core.SmsShape
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryImportBatchRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemorySourceRecordRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.QueuedSms
import app.masroufy.usecase.AutoRecordSms
import app.masroufy.usecase.AutoRecordSmsDeps
import app.masroufy.usecase.ImportStatementDeps
import app.masroufy.usecase.ManageSmsInbox
import app.masroufy.usecase.SmsLane
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * رسايل `scripts/device/sendTestSms.sh` لاختبار المحاكي `SmsAutoRecordOnDeviceTest` (الجولة الرابعة) — **على الكمبيوتر**: نفس النصوص
 * (والسكربت لازم فيه النص ده بالحرف) ⇒ «\n» سطر جديد زي ما المحاكي بيعمل ⇒ `SmsSafety` ⇒ التسجيل التلقائي بمستودعات الذاكرة.
 * المتوقع زي المحاكي: الشراء بالشكل المعروف بيتسجل لوحده، والشراء اللي من كلمات عامة والرسالة الملتبسة بيستنوا.
 */
class SmsDeviceScriptTest {
    private val sender = "5550003"
    private val today = "2026-10-09"
    private val known = "PoS Purchase\\nAmount: SAR 25.00\\nAt: TEST CAFE\\nOn: \$today"
    private val keywordOnly = "TEST-FALLBACK Purchase SAR 12.00 at TEST SHOP on \$today"
    private val unclear = "TEST-WAIT transfer SAR 10.00 \$today"

    private fun script(): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, "scripts/device/sendTestSms.sh").takeIf { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        fail("scripts/device/sendTestSms.sh not found")
    }

    /**
     * من غير مكتبة الكوروتين (مش في اختبارات `device` على الكمبيوتر): مستودعات الذاكرة والقفل من غير زحمة ما بيوقفوش — لو حاجة وقفت
     * الاختبار بيقع بوضوح.
     */
    private fun <T> blocking(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
        return (result ?: fail("suspended — needs a real coroutine runner")).getOrThrow()
    }

    /** المحاكي بيحوّل «\n» لسطر جديد (`sms send`)، والسكربت بيحط التاريخ. */
    private fun asReceived(template: String) = template.replace("\\n", "\n").replace("\$today", today)

    @Test fun theScriptSendsExactlyTheseMessages() {
        val text = script()
        for (t in listOf(known, keywordOnly, unclear)) assertTrue("emu sms send $sender \"$t\"" in text, "script must send: $t")
    }

    @Test fun storedMessagesAreReadAndOnlyTheKnownShapeIsRecordedAutomatically() = blocking {
        val stored = listOf(known, keywordOnly, unclear).map { t -> assertNotNull(SmsSafety.sanitize(asReceived(t)), "dropped before storage: $t") }
        val receivedAt = "${today}T10:00:00Z"
        val shapes = stored.map { (parseBankSms(BankSmsMessage(sender, receivedAt, it), 1) as? SmsParseResult.Ok)?.row?.shape }
        assertEquals(SmsShape.SamaTitle, shapes[0], "«PoS Purchase» عنوان موحّد")
        assertEquals(SmsShape.KeywordFallback, shapes[1], "شراء من كلمات عامة بس")
        assertIs<SmsParseResult.Rejected>(parseBankSms(BankSmsMessage(sender, receivedAt, stored[2]), 1), "الاتجاه مش واضح")

        val inbox = MemorySmsInbox(emptyList(), available = true)
        inbox.enable(listOf(sender))
        stored.forEachIndexed { i, body -> inbox.receive(QueuedSms("m$i", sender, receivedAt, body)) }
        val txns = MemoryTransactionRepository()
        val sources = MemorySourceRecordRepository()
        val batches = MemoryImportBatchRepository()
        val parties = MemoryTransferPartyRepository()
        val importDeps = ImportStatementDeps(
            txns = txns, sources = sources, batches = batches, merchants = MemoryMerchantRepository(), categories = MemoryCategoryRepository(),
            rules = MemoryRuleRepository(), uow = MemoryUnitOfWork(listOf(txns, sources, batches, parties)), ids = SequentialIdGenerator(),
            clock = FixedClock("${today}T11:00:00.000Z"), transferParties = parties,
        )
        val wallets = MemoryWalletRepository(listOf(Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")))
        val auto = AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(SmsLane.of("sa", importDeps, ManageSmsInbox(inbox, ::parseBankSms), wallets))))
        val r = auto.run()
        assertEquals(1, r.recorded)
        val txn = txns.listByDateRange("0000-01-01", "9999-12-31").single()
        assertEquals(2_500L to "TEST CAFE", txn.amountMinor to txn.rawMerchantName)
        assertEquals(listOf("m1", "m2"), r.waiting)
        assertEquals(listOf("m1"), r.unknownShape)
    }
}
