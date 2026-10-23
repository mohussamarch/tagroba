package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Currency
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.parseBankSms
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * fix053 — «اقرأ اللي فات» بقت ~12.5 د على 3,790 رسالة في 0.5.3 (كانت ~4 د في 0.5.2). القياس هنا على 3,000 رسالة مخترعة (تلتهم حوالات
 * واردة) فوق كشف فيه 3,500 عملية مخترعة:
 * - **0.5.2:** بلد واحدة، والآثار القديمة.
 * - **0.5.3:** البلدين (قارئ كل بلد بيشيل رسايل التانية) + آثار التشغيل الحقيقية (`smsRecordEffects` — فيها الرِجل التلقائية §112-3).
 * على الجهاز كل قراية عمليات = استعلام فايربيز ⇒ العدد ([CountingTxns.ranges]) هو اللي بيتحسب، مش الملّي ثانية على الكمبيوتر بس.
 */
class SmsHistoryBenchTest {
    private class CountingTxns(private val real: TransactionRepository) : TransactionRepository by real {
        var ranges = 0

        override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
            ranges++
            return real.listByDateRange(fromIso, toIso)
        }
    }

    private val bank = HISTORY_BANK.copy(accountLast4 = "1111")
    private val egWallet = Wallet("eg-bank", "بنك مصري وهمي", Currency.EGP, "bank", 0, "2025-01-01")

    private class Run(val ms: Long, val ranges: Int, val recorded: Int)

    private fun messages(): List<HistoryTxn> = (0 until MESSAGES).map { i ->
        HistoryTxn("m$i", day("2025-01-01", i * 545 / MESSAGES), 1_000L + i * 7L, i % 3 == 0, if (i % 3 == 0) "TEST PERSON ${i % 9}" else SHOPS[i % SHOPS.size], 0)
    }

    private fun ledger(): List<HistoryTxn> = (0 until LEDGER).map { i -> HistoryTxn("L$i", day("2025-01-01", i * 545 / LEDGER), 70_000L + i * 3L, false, "TEST LEDGER", 0) }

    private suspend fun run(bothCountries: Boolean): Run {
        val sa = SmsSpace(wallets = listOf(CASH, bank), window = CROSS_SOURCE_WINDOW_DAYS, elsewhere = if (bothCountries) listOf(::parseEgyptBankSms) else emptyList())
        val world = HistoryWorld(space = sa)
        val counted = CountingTxns(sa.txns)
        fun effects(s: SmsSpace, inbox: SmsInboxPort) =
            if (bothCountries) smsRecordEffects(s.spaceId, s.wallets, inbox, s.categories, txns = counted, sources = s.sources) else s.smsEffects(inbox)
        val inbox = world.memory
        val lanes = buildList {
            add(SmsLane.of(sa.spaceId, sa.importDeps(effects(sa, inbox)), ManageSmsInbox(inbox, sa.parse, sa.elsewhere), sa.wallets))
            if (bothCountries) {
                val eg = SmsSpace("eg", wallets = listOf(egWallet), parse = ::parseEgyptBankSms, elsewhere = listOf(::parseBankSms))
                add(SmsLane.of(eg.spaceId, eg.importDeps(effects(eg, inbox)), ManageSmsInbox(inbox, eg.parse, eg.elsewhere), eg.wallets))
            }
        }
        val history = SmsHistory(SmsHistoryDeps(AutoRecordSmsDeps(inbox, lanes), world.settings, world.clock))
        world.importStatement(world.statement(ledger()))
        inbox.deliver(*messages().map { it.sms }.toTypedArray())
        inbox.enable(listOf("TESTBANK"))
        counted.ranges = 0
        val mark = TimeSource.Monotonic.markNow()
        val q = history.question("sa") ?: error("no history question")
        val first = history.start("sa", q.senders, "2025-01-01")
        val report = history.confirmShapes(first.shapeMessageIds)
        return Run(mark.elapsedNow().inWholeMilliseconds, counted.ranges, first.recorded + report.recorded)
    }

    @Test fun readPastMessagesBenchmark() = runBlocking<Unit> {
        run(bothCountries = false) // تسخين الـJVM
        val old = run(bothCountries = false)
        val now = run(bothCountries = true)
        println("fix053 bench: 0.5.2-shape ${old.ms} ms (ranges=${old.ranges}, recorded=${old.recorded}) · 0.5.3-shape ${now.ms} ms (ranges=${now.ranges}, recorded=${now.recorded})")
        assertEquals(old.recorded, now.recorded, "the second country changes nothing in Saudi")
        assertTrue(now.recorded > 0)
        // كل رسالة واردة كانت بتعمل استعلام لوحدها (الدمج مع الرِجل التلقائية) ⇒ الحد: استعلام لكل صفحة تقريبًا، مش لكل رسالة
        assertTrue(now.ranges <= MESSAGES / 50, "transaction range reads stay per page, not per message (${now.ranges})")
    }

    private companion object {
        const val MESSAGES = 3_000
        const val LEDGER = 3_500
        val SHOPS = listOf("TEST CAFE", "TEST MART", "TEST BAKERY", "TEST FUEL")
    }
}
