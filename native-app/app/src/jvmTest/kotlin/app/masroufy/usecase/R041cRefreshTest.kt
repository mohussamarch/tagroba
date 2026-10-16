package app.masroufy.usecase

import app.masroufy.core.Transaction
import app.masroufy.core.parseBankSms
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r041c — «اسحب لتحت» كان بيعلّق البرنامج ٤–٥ ثواني على جوال فيه آلاف الرسايل: بيعيد فهم ومعاينة كل الرسايل المستنية في كل سحبة وعلى خيط
 * الواجهة. هنا: السحبة بتقرا **الجديد بس** · بتسجّل نفس اللي التشغيلة الكاملة بتسجله بالظبط · عدد «إعداد القراءة» زي الكامل · وقوايم
 * «العمليات» برّه خيط الواجهة. **كل الرسايل والمبالغ مخترعة.**
 */
class R041cRefreshTest {
    /** نص كل رسالة القارئ فهمها. */
    private val parsed: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val counted = SmsSpace(parse = { m, i -> parsed += m.body; parseBankSms(m, i) })

    @Test fun refreshReadsOnlyNewMessages() = runBlocking<Unit> {
        val w = SmsWorld(listOf(counted), learnOnReceive = false).enable()
        w.learn(sms("shape", CAFE)) // المالك أكّد شكل الرسالة دي قبل كده
        w.receive(sms("old1", KEYWORD_ONLY), sms("old2", UNCLEAR)) // مستنيين: شكل مش معروف · مش مفهومة
        val memo = SmsRunMemo()
        assertEquals(0, w.auto().runNew(memo).recorded, "أول سحبة: لفة كاملة ومفيش حاجة تتسجل")
        parsed.clear()
        assertEquals(0, w.auto().runNew(memo).recorded)
        assertTrue(parsed.isEmpty(), "مفيش جديد ⇒ ولا رسالة اتفهمت تاني: $parsed")
        w.receive(sms("new1", CAFE))
        val r = w.auto().runNew(memo)
        assertEquals(1, r.recorded)
        assertEquals(1, r.recordedTransactionIds.size, "اللمعة على اللي اتسجل")
        assertTrue(parsed.isNotEmpty() && parsed.all { it == CAFE }, "الجديدة بس اتفهمت: $parsed")
        assertEquals(listOf("old1", "old2"), w.queued().sorted(), "المستنيين زي ما هم")
    }

    @Test fun aChangeSinceTheLastRefreshSweepsTheWaitingOnesToo() = runBlocking<Unit> {
        val w = SmsWorld(listOf(counted), learnOnReceive = false).enable()
        w.receive(sms("m1", CAFE)) // شكل جديد (§77-A) ⇒ مستنية
        val memo = SmsRunMemo()
        assertEquals(0, w.auto().runNew(memo).recorded)
        assertEquals(0, w.auto().runNew(memo).recorded, "مفيش جديد ولا تغيير")
        w.learn(sms("shape", CAFE)) // المالك أكّد الشكل ⇒ العلامة اتغيرت
        assertEquals(1, w.auto().runNew(memo).recorded, "لفة كاملة: المستنية اللي بقت تتسجل اتسجلت")
        assertTrue(w.queued().isEmpty())
    }

    @Test fun refreshRecordsExactlyWhatTheFullRunRecords() = runBlocking<Unit> {
        val messages = arrayOf(sms("m1", CAFE), sms("m2", MART), sms("m3", TO_PERSON), sms("m4", FROM_PERSON), sms("m5", KEYWORD_ONLY), sms("m6", UNCLEAR))
        val full = SmsWorld().enable().apply { receive(*messages) }
        val pull = SmsWorld().enable().apply { receive(*messages) }
        val a = full.auto().run()
        val b = pull.auto().runNew(SmsRunMemo())
        assertEquals(a.recorded to a.duplicates, b.recorded to b.duplicates)
        assertEquals(a.recordedTransactionIds.size, b.recordedTransactionIds.size)
        assertEquals(money(full.spaces.first().all()), money(pull.spaces.first().all()), "نفس العمليات بنفس المبالغ والاتجاه والنوع")
        assertEquals(full.queued(), pull.queued())
    }

    @Test fun settingsCountsMatchTheFullWaitingComputation() = runBlocking<Unit> {
        val w = SmsWorld(listOf(SmsSpace(wallets = listOf(CASH, BANK, BANK2)))).enable() // بنكين ⇒ المرسل مستني محفظة
        w.receive(sms("m1", CAFE), sms("m2", MART))
        val auto = w.auto()
        val quick = auto.unmappedQuick()
        assertEquals(auto.unmappedSenders(), quick)
        assertEquals(listOf(2), quick.map { it.messages })
        assertEquals(listOf("m1", "m2"), w.queued().sorted(), "قراية بس — مفيش تسجيل ولا شيل")
    }

    private fun money(all: List<Transaction>) =
        all.map { listOf(it.amountMinor, it.observedDirection, it.economicKind, it.occurredAt, it.rawMerchantName, it.reviewState, it.categoryConfirmed) }
            .sortedBy { it.toString() }

    // ── قوايم «العمليات» (القايمة وعدد أسئلة التحويلات) برّه خيط الواجهة
    private class ThreadTxns(private val inner: MemoryTransactionRepository) : TransactionRepository by inner {
        val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
            threads += Thread.currentThread().name
            return inner.listByDateRange(fromIso, toIso)
        }
    }

    private suspend fun readOperations(txns: ThreadTxns, work: kotlin.coroutines.CoroutineContext?) {
        val load = if (work == null) LoadTransactionsScreenDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository())
        else LoadTransactionsScreenDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository(), work = work)
        LoadTransactionsScreen(load).load(LoadTransactionsScreenRequest(today = "2026-10-07", payday = 28))
        val parties = MemoryTransferPartyRepository()
        val zone = ManageTransfersDeps(txns, parties, MemoryPersonRepository(), MemoryUnitOfWork(emptyList()), FixedClock("2026-10-07T11:00:00.000Z"))
        ManageTransfers(if (work == null) zone else zone.copy(work = work)).zone()
    }

    @Test fun operationsListAndTransferZoneRunOnTheWorkContext() = runBlocking<Unit> {
        val txns = ThreadTxns(MemoryTransactionRepository())
        readOperations(txns, null)
        assertTrue(txns.threads.isNotEmpty() && txns.threads.all { it.startsWith("DefaultDispatcher-worker") }, "${txns.threads}")
        val caller = Thread.currentThread().name
        val injected = ThreadTxns(MemoryTransactionRepository())
        readOperations(injected, EmptyCoroutineContext)
        assertTrue(injected.threads.isNotEmpty() && injected.threads.all { it == caller }, "${injected.threads} vs $caller")
    }
}
