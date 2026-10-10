package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.SmsParseResult
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * التجميع (الملاحظة 14 في المراجعة): خط رسايل البنك اتبنى **من غير** آثار الشريحة S3 صريحة — زي `TestBackgroundGraph` اللي هو نموذج
 * `Application.onCreate` — ⇒ `SmsLane.of` بيضيفهم لوحده: «التحويل رجع» ما بيتسجلش داخل عادي، وإجابة الأجنبي بتاخد المبلغ الأجنبي.
 */
class ReturnsWiringTest {
    private class PlainGraph(wallet: Wallet, private val parse: (BankSmsMessage, Int) -> SmsParseResult) {
        val txns = MemoryTransactionRepository()
        private val sources = MemorySourceRecordRepository()
        private val batches = MemoryImportBatchRepository()
        private val parties = MemoryTransferPartyRepository()
        private val importDeps = ImportStatementDeps(
            txns = txns, sources = sources, batches = batches, merchants = MemoryMerchantRepository(), categories = MemoryCategoryRepository(),
            rules = MemoryRuleRepository(), uow = MemoryUnitOfWork(listOf(txns, sources, batches, parties)), ids = SequentialIdGenerator(),
            clock = FixedClock("2026-10-08T00:00:00.000Z"), transferParties = parties,
        )
        val memory = MemorySmsInbox(emptyList(), available = true)
        private val walletRepo = MemoryWalletRepository(listOf(wallet))

        /** S3 آثارها مش هنا — الخط بيضيفها لوحده؛ آثار S1 لازمة (`SmsLane.of`). */
        val lane = SmsLane.of("x", importDeps.copy(effects = s1Effects(walletRepo, memory, "x")), ManageSmsInbox(memory, parse), walletRepo)
        private val autoRecord = AutoRecordSms(AutoRecordSmsDeps(memory, listOf(lane)))

        /** S1 (§77-A): شكل كل رسالة في الصندوق اتأكد قبل كده. */
        val auto: AutoRecordSms get() = autoRecord.also { runBlocking { memory.learnQueued("x", parse) } }
    }

    @Test fun theSmsLaneAddsTheReturnEffectsByItself() = runBlocking<Unit> {
        val g = PlainGraph(EG_BANK, ::parseEgyptBankSms)
        g.memory.enable(listOf("TESTBANK"))
        g.memory.receive(sms("buy", kfhOut(), at = "2026-10-02T09:00:00Z"))
        assertEquals(1, g.auto.run().recorded)
        g.memory.receive(sms("back", kfhReturned(), at = "2026-10-05T10:00:00Z"))
        assertEquals(1, g.auto.run().recorded)
        val ret = g.txns.listByDateRange("2026-01-01", "2026-12-31").single { it.observedDirection == Direction.IN }
        // الروابط مش معروفة للخط ده ⇒ ما بيلغيش لوحده — بيسأل «نلغي الاتنين؟» (ومش «داخل» عادي بيتقدّر راتب)
        assertEquals(EconomicKind.UNCLASSIFIED to EconomicKind.INTERNAL_TRANSFER, ret.economicKind to ret.suggestedKind)
        assertNull(g.txns.listByDateRange("2026-01-01", "2026-12-31").single { it.observedDirection == Direction.OUT }.reversedById)
    }

    @Test fun theForeignAnswerKeepsTheForeignAmountWithoutExplicitEffects() = runBlocking<Unit> {
        val g = PlainGraph(BANK, ::parseBankSms)
        g.memory.enable(listOf("TESTBANK"))
        g.memory.receive(sms("f1", "شراء انترنت\nبطاقة:6604;مدى\nمبلغ:USD 23.40 (SAR 87.75)\nلدى:TEST SHOP\nفي:26-10-07 10:00"))
        ForeignSmsAsks(AutoRecordSmsDeps(g.memory, listOf(g.lane))).answerForeign("f1", 8_775)
        val t = g.txns.listByDateRange("2026-01-01", "2026-12-31").single()
        assertEquals("USD" to 2_340L, t.foreignCurrency to t.foreignAmountMinor)
    }

    /** التجميع الكامل (`ReturnsWiring` بالروابط): بيلغي لوحده · التراجع بيرجّع · التصليح والأسئلة من نفس المكان. */
    @Test fun theFullWiringCancelsAndUndoes() = runBlocking<Unit> {
        val w = ReturnsWorld(wallets = listOf(EG_BANK), parse = ::parseEgyptBankSms)
        val wiring = ReturnsWiring(w.txns, w.sources, w.links, w.clock, MemoryUnitOfWork(listOf(w.txns)))
        val memory = w.memory.also { it.enable(listOf("TESTBANK")) }
        val deps = w.importDeps(withEffects = false).copy(effects = s1Effects(w.wallets, memory, "eg") + wiring.effects)
        val auto = AutoRecordSms(AutoRecordSmsDeps(memory, listOf(SmsLane.of("eg", deps, ManageSmsInbox(memory, ::parseEgyptBankSms), w.wallets))))
        memory.receive(sms("buy", kfhOut(), at = "2026-10-02T09:00:00Z"))
        memory.learnQueued("eg", ::parseEgyptBankSms)
        auto.run()
        memory.receive(sms("back", kfhReturned(), at = "2026-10-05T10:00:00Z"))
        memory.learnQueued("eg", ::parseEgyptBankSms)
        val ret = auto.run().recordedTransactionIds.single()
        assertEquals(EconomicKind.INTERNAL_TRANSFER, w.one(ret).economicKind)
        val revert = RevertImportBatch(RevertDeps(w.txns, w.sources, w.batches, w.settlements, w.allocations, w.obligations, MemoryUnitOfWork(listOf(w.txns, w.sources, w.batches)), w.revertLinks, wiring.undoers))
        revert.execute(w.sources.listByTransactionIds(listOf(ret)).single().batchId)
        val original = w.all().single()
        assertEquals(EconomicKind.UNCLASSIFIED to null, original.economicKind to original.reversedById)
        assertEquals(RepairOutcome(), wiring.repair.run())
        assertEquals(emptyList(), wiring.asks("eg").pending("2026-09-01", "2026-10-31"))
    }
}
