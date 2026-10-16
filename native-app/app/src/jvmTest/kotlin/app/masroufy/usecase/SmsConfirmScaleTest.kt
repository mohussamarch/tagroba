package app.masroufy.usecase

import app.masroufy.core.BankSmsMessage
import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.SmsParseResult
import app.masroufy.core.parseBankSms
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.port.QueuedSms
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * r051 — «أكّد الأشكال» على صندوق كبير (فحص 0.5 على المحاكي: ~3,600 رسالة و3,500 عملية ⇒ الشغل كمّل أكتر من 5 دقايق وكتب 16 ألف سجل مصدر).
 * **كل الرسايل والمحلات والمبالغ مخترعة.** 3,600 رسالة: 4 من كل 5 شراء بشكل واحد (بيتسجل لوحده بعد التأكيد) وواحدة «Purchase» بكلمات عامة
 * (بتفضل مستنية) — زي الصفحة اللي ~20% منها بيستنى. الكشف قبلها 3,500 عملية بمبالغ تانية (ولا دمج).
 * - **العدّ** ([confirmWorkStaysLinear]): الفهم والمزامنة والشيل والكتابة والتقدم بيكبروا مع عدد الرسايل/الصفحات، مش مع (رسايل × لفات).
 * - **الوقت** ([confirmFinishesInTime]): التأكيد كله في حد معقول على الجهاز الافتراضي للاختبار.
 */
class SmsConfirmScaleTest {
    private class CountingInbox(val real: MemorySmsInbox) : SmsInboxPort by real {
        var syncs = 0
        var acks = 0

        override suspend fun sync(): SmsInboxState {
            syncs++
            return real.sync()
        }

        override suspend fun acknowledge(ids: List<String>): SmsInboxState {
            acks++
            return real.acknowledge(ids)
        }
    }

    private class Big {
        var parses = 0
        private val countingParse: (BankSmsMessage, Int) -> SmsParseResult = { m, n -> parses++; parseBankSms(m, n) }
        val space = SmsSpace(wallets = listOf(CASH, HISTORY_BANK.copy(accountLast4 = "1111")), parse = countingParse, window = CROSS_SOURCE_WINDOW_DAYS)
        val world = HistoryWorld(space = space)
        val inbox = CountingInbox(world.memory)
        val history = SmsHistory(
            SmsHistoryDeps(
                AutoRecordSmsDeps(inbox, listOf(SmsLane.of(space.spaceId, space.importDeps(space.smsEffects(inbox)), ManageSmsInbox(inbox, space.parse), space.wallets))),
                world.settings, world.clock,
            ),
        )

        suspend fun records(): Int = space.batchStore.listRecent(100_000).sumOf { space.sources.listByBatch(it.id).size }

        suspend fun batches(): Int = space.batchStore.listRecent(100_000).size
    }

    private fun messages(): List<QueuedSms> = (0 until MESSAGES).map { i ->
        val date = day("2025-01-01", i * 545 / MESSAGES)
        val at = "${date}T${(8 + i % 12).toString().padStart(2, '0')}:${(i % 60).toString().padStart(2, '0')}:00Z"
        val minor = 1_000L + i * 7L
        val body = if (i % 5 == 4) "Purchase\nبـSR ${money(minor)}\nلدى:TEST SHOP\n${smsDay(date)}"
        else "شراء\nبـSR ${money(minor)}\nلدى:${SHOPS[i % SHOPS.size]}\n${smsDay(date)}"
        QueuedSms("m$i", "TESTBANK", at, body)
    }

    private fun ledger(): List<HistoryTxn> = (0 until LEDGER).map { i -> HistoryTxn("L$i", day("2025-01-01", i * 545 / LEDGER), 70_000L + i * 3L, false, "TEST LEDGER", 0) }

    /** الكشف ⇒ الرسايل ⇒ «أيوه من رصيد البداية» ⇒ قايمة الأشكال. بيرجّع قايمة التأكيد. */
    private suspend fun Big.ready(): SmsHistoryReport {
        world.importStatement(world.statement(ledger()))
        assertEquals(LEDGER, space.all().size)
        world.memory.deliver(*messages().toTypedArray())
        world.memory.enable(listOf("TESTBANK"))
        val q = history.question("sa") ?: error("no history question")
        val first = history.start("sa", q.senders, "2025-01-01")
        assertEquals(0, first.recorded, "nothing records before a shape is confirmed")
        assertEquals(MESSAGES, first.queued)
        assertTrue(first.shapes.isNotEmpty(), "one line per new shape")
        return first
    }

    @Test fun confirmWorkStaysLinear() = runBlocking<Unit> {
        val big = Big()
        val first = big.ready()
        val records = big.records()
        val batches = big.batches()
        big.parses = 0
        big.inbox.syncs = 0
        big.inbox.acks = 0
        val windowsBefore = big.world.memory.windows
        val progress = mutableListOf<SmsRunProgress>()
        val report = big.history.confirmShapes(first.shapeMessageIds, progress = { progress += it })
        val pages = MESSAGES / PAGE + 1
        val written = big.records() - records
        val passes = big.batches() - batches
        val windows = big.world.memory.windows - windowsBefore
        println(
            "r051 counts: parses=${big.parses} syncs=${big.inbox.syncs} windows=$windows acks=${big.inbox.acks} records=$written batches=$passes " +
                "progress=${progress.size} recorded=${report.recorded} waiting=${report.waiting}",
        )
        assertEquals(RECORDABLE, big.space.all().size - LEDGER, "every message of the confirmed shape is recorded")
        assertEquals(RECORDABLE, report.recorded)
        assertEquals(MESSAGES - RECORDABLE, big.world.memory.sync().count, "only the keyword-only messages still wait")
        // Spark (20,000 كتابة في اليوم): سجل مصدر للي اتسجل أو اتشال مكرر بس — مش 200 لكل صفحة في كل لفة (كانت 16,332)
        assertEquals(report.recorded + report.duplicates, written, "source records = messages handled, never one per waiting line per pass")
        assertTrue(passes <= pages, "one commit per page at most ($passes)")
        assertTrue(big.parses <= 2 * MESSAGES, "each message is parsed a bounded number of times (${big.parses})")
        assertTrue(big.inbox.syncs <= pages, "phone-inbox catch-ups stay bounded (${big.inbox.syncs})")
        assertTrue(windows <= 3 * pages, "each page is read a bounded number of times ($windows)")
        assertTrue(big.inbox.acks <= pages + 2, "one acknowledge per page at most (${big.inbox.acks})")
        // التقدم بيتحرك صفحة صفحة (مش لكل رسالة) وآخره = اللي اتسجل · «بانتظارك» = العدد الحقيقي في الصندوق كله (مش الـ200)
        assertTrue(progress.size in 2..pages + 2, "progress per page (${progress.size})")
        assertEquals(RECORDABLE, progress.last().recorded)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.recorded >= a.recorded }, "progress only moves forward")
        assertEquals(MESSAGES - RECORDABLE, report.waiting, "the header shows the true waiting total")
    }

    @Test fun confirmFinishesInTime() = runBlocking<Unit> {
        val big = Big()
        val first = big.ready()
        val mark = TimeSource.Monotonic.markNow()
        val report = big.history.confirmShapes(first.shapeMessageIds)
        val ms = mark.elapsedNow().inWholeMilliseconds
        println("r051 timing: confirm ${MESSAGES} messages over $LEDGER transactions took $ms ms (recorded=${report.recorded})")
        assertEquals(RECORDABLE, report.recorded)
        assertTrue(ms < BUDGET_MS, "confirm took $ms ms (budget $BUDGET_MS)")
    }

    private companion object {
        const val MESSAGES = 3_600
        const val LEDGER = 3_500
        const val PAGE = 200
        const val RECORDABLE = MESSAGES - MESSAGES / 5
        const val BUDGET_MS = 30_000L
        val SHOPS = listOf("TEST CAFE", "TEST MART", "TEST BAKERY", "TEST FUEL")
    }
}
