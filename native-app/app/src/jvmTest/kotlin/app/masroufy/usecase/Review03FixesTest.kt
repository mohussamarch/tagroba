package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryMerchantRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRuleRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.port.SmsInboxPort
import app.masroufy.port.SmsInboxState
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** مراجعة 0.3 — البنود 7 و9 و10 اللي كانت واقفة. كل الأسامي والمبالغ مخترعة. */
class Review03FixesTest {
    /** بيسجّل اسم الخيط اللي الصندوق اتقرا عليه. */
    private class ThreadSpy(private val inner: SmsInboxPort) : SmsInboxPort by inner {
        val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override suspend fun sync(): SmsInboxState {
            threads += Thread.currentThread().name
            return inner.sync()
        }
    }

    private fun onWorker(threads: List<String>) = threads.isNotEmpty() && threads.all { it.startsWith("DefaultDispatcher-worker") }

    // ── بند 7: رسايل البنك واللحاق برّه خيط الواجهة
    @Test fun historyQuestionReadsTheInboxOnAWorkerThread() = runBlocking<Unit> {
        val w = HistoryWorld()
        val spy = ThreadSpy(w.memory)
        SmsHistory(SmsHistoryDeps(w.deps().copy(inbox = spy), w.settings, w.clock)).question("sa")
        assertTrue(onWorker(spy.threads), "${spy.threads}")
    }

    @Test fun backgroundCycleReadsTheInboxOnAWorkerThread() = runBlocking<Unit> {
        val w = HistoryWorld()
        val spy = ThreadSpy(w.memory)
        RunBackgroundCycle(BackgroundCycleDeps(sms = AutoRecordSms(w.deps().copy(inbox = spy)))).run(LocalMoment("2026-07-05", 10))
        assertTrue(onWorker(spy.threads), "${spy.threads}")
    }

    @Test fun testsCanInjectTheirOwnContext() = runBlocking<Unit> {
        val w = HistoryWorld()
        val spy = ThreadSpy(w.memory)
        val caller = Thread.currentThread().name
        SmsHistory(SmsHistoryDeps(w.deps().copy(inbox = spy), w.settings, w.clock, work = EmptyCoroutineContext)).report()
        RunBackgroundCycle(BackgroundCycleDeps(sms = AutoRecordSms(w.deps().copy(inbox = spy)), work = EmptyCoroutineContext)).run(LocalMoment("2026-07-05", 10))
        assertTrue(spy.threads.isNotEmpty() && spy.threads.all { it == caller }, "${spy.threads} vs $caller")
    }

    private fun txn(id: String, date: String, dir: Direction, minor: Long, kind: EconomicKind) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = "TEST SHOP $id",
    )

    /** بيعد استعلامات المدى. */
    private class CountingTxns(private val inner: MemoryTransactionRepository) : TransactionRepository by inner {
        var ranges = 0
        override suspend fun listByDateRange(fromIso: String, toIso: String): List<Transaction> {
            ranges++
            return inner.listByDateRange(fromIso, toIso)
        }
    }

    // ── بند 9: «كل السجل» من أقدم رصيد بداية (أو أقدم عملية) مش من 1970
    @Test fun allHistoryStartsAtTheOldestOpeningNot1970() = runBlocking<Unit> {
        val inner = MemoryTransactionRepository(
            listOf(
                txn("t-new", "2026-03-10", Direction.OUT, 4_500, EconomicKind.PURCHASE),
                txn("t-mid", "2025-02-01", Direction.OUT, 2_000, EconomicKind.PURCHASE),
                // قبل رصيد البداية بشهرين — أقدم عملية، لازم تتقري
                txn("t-old", "2024-11-20", Direction.OUT, 1_000, EconomicKind.PURCHASE),
            ),
        )
        val txns = CountingTxns(inner)
        val deps = CategorizeTransactionsDeps(
            txns, MemoryMerchantRepository(), MemoryCategoryRepository(), MemoryRuleRepository(), MemoryUnitOfWork(listOf(inner)),
            FixedClock("2026-10-15T10:00:00.000Z"),
        )
        val wallets = MemoryWalletRepository(listOf(HISTORY_BANK.copy(openingAt = "2025-01-01")))
        val preview = ReviewHistory(deps, wallets).previewAll("2026-10-15")
        assertEquals(setOf("t-new", "t-mid", "t-old"), preview.rows.map { it.id }.toSet())
        assertTrue(txns.ranges <= 3, "سنة سنة لحد رصيد البداية + استعلام واحد للأقدم — مش 57 سنة: ${txns.ranges}")
    }

    // ── بند 10: الزيادة اللي اتسجلت سلفة جديدة أو أمانة مش «سداد»
    @Test fun overpaySurplusIsNotCalledDebtRepaid() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository(
            listOf(
                txn("repay-loan", "2026-04-02", Direction.OUT, 300_000, EconomicKind.DEBT_REPAID),
                txn("repay-held", "2026-05-02", Direction.OUT, 120_000, EconomicKind.DEBT_REPAID),
                txn("repay-plain", "2026-06-02", Direction.OUT, 80_000, EconomicKind.DEBT_REPAID),
            ),
        )
        val obligations = MemoryObligationRepository(
            listOf(
                Obligation("o-loan", "p-1", "repay-loan", ObligationKind.RECEIVABLE, 50_000, Currency.SAR),
                Obligation("o-held", "p-2", "repay-held", ObligationKind.RECEIVABLE, 20_000, Currency.SAR, custodyHeld = true),
            ),
        )
        val withSurplus = LoadMoneySummary(LoadMoneySummaryDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository(), obligations))
            .load("2026-01-01", "2026-12-31")
        // OVERRIDES §91-10: السلفة الجديدة «سلفة لحد» · الأمانة عنده سطر لوحده «أمانة» · الباقي «سداد» — والمجموع زي ما هو
        assertEquals(mapOf(EconomicKind.DEBT_REPAID to 430_000L, EconomicKind.LOAN_GRANTED to 50_000L), withSurplus.outflowNotExpense)
        assertEquals(20_000L, withSurplus.outflowHeldMinor)
        assertEquals(500_000L, withSurplus.outflowNotExpense.values.sum() + withSurplus.outflowHeldMinor)
        assertEquals(uiText(UiKey.SUMMARY_HELD) to 20_000L, withSurplus.outflowNotExpenseLines().last())
        assertEquals(3, withSurplus.outflowNotExpenseLines().size)
        // من غير مستودع الديون ⇒ زي الأول (المجموع نفسه ما اتغيرش)
        val old = LoadMoneySummary(LoadMoneySummaryDeps(txns, MemoryCategoryRepository(), MemoryAllocationRepository())).load("2026-01-01", "2026-12-31")
        assertEquals(mapOf(EconomicKind.DEBT_REPAID to 500_000L), old.outflowNotExpense)
    }
}
