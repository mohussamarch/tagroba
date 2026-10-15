package app.masroufy.usecase

import app.masroufy.core.BalanceSource
import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.memory.MemorySmsInbox
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * البند 3 (رصيد البنك ظاهر بالسالب ~20 ألف): **كل سبب محتمل متكرر على بيانات مخترعة** — إيه اللي الكود يقدر يعمله فعلًا، وإزاي
 * «افهم رصيدك» بيبيّنه. (أ) تكرار رسالة وكشف في 0.1/0.2 · (ب) تاريخ الرسايل قبل رصيد البداية · (ج) رصيد البداية وتاريخه · (د) التحويلات
 * والرسوم والعملة والاسترداد · (هـ) الراتب المستني «ده راتبك؟» والمشتريات بتتسجل.
 */
class BalanceCauseTest {
    private val march = eighteenMonths(twins = false).filter { it.date.startsWith("2026-03") && !it.incoming }

    /** (أ) نفس الرسايل فوق كشف متستورد: من غير دمج (0.1/0.2) كل شراء اتحسب مرتين ⇒ الرصيد أقل بمجموعهم؛ بالدمج (0.3) زي ما هو. */
    @Test fun oldBuildsCountedEverySmsAgainOverAnImportedStatement() = runBlocking<Unit> {
        suspend fun run(window: Int?): Pair<Long, List<Long>> {
            val space = SmsSpace(wallets = listOf(CASH, HISTORY_BANK), window = window)
            val w = HistoryWorld(space = space)
            w.importStatement(w.statement(march))
            val inbox = MemorySmsInbox(available = true, now = HISTORY_NOW)
            inbox.enable(listOf("TESTBANK"))
            march.forEach { inbox.receive(it.sms); inbox.preLearn(space.spaceId, space.parse, it.sms) }
            AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(space.lane(inbox)))).run()
            val world = LedgerWorld(HISTORY_BANK, CASH, space.txnStore, space.sources, space.batchStore, space.wallets)
            val why = world.explain().explain(HISTORY_BANK.id, "2026-04-05")!!
            return why.computedMinor!! to why.review.duplicates.map { it.sms.amountMinor }.sorted()
        }
        val truth = HISTORY_BANK.openingBalanceMinor - march.sumOf { it.minor }
        val (old, oldPairs) = run(window = null)
        val (now, nowPairs) = run(window = CROSS_SOURCE_WINDOW_DAYS)
        // 0.1/0.2: الرسالة اللي يوم سطر كشفها بالظبط بتستنى «شبه عملية» (منع التكرار العادي) — واللي بعدها بيوم أو يومين بتتسجل تاني
        val doubled = march.filter { it.statementLag > 0 }
        assertTrue(doubled.size > march.size / 2)
        assertEquals(truth - doubled.sumOf { it.minor }, old, "0.1/0.2: every message one or two days off its statement line counted twice")
        assertEquals(doubled.map { it.minor }.sorted(), oldPairs, "and exactly those are listed as likely duplicates")
        assertEquals(truth to emptyList(), now to nowPairs, "0.3: merged — nothing doubled")
    }

    /** (ب) «اقرأ اللي فات؟» من أقدم رسالة: الرسايل اللي قبل رصيد البداية بتتسجل بس **مش بتتحسب** — وبتتعرض في «قبل رصيد البداية». */
    @Test fun historyOlderThanTheOpeningBalanceIsShownButNeverCounted() = runBlocking<Unit> {
        val space = SmsSpace(wallets = listOf(CASH, HISTORY_BANK), window = CROSS_SOURCE_WINDOW_DAYS)
        val december = (1..5).map { HistoryTxn("old$it", "2024-12-2$it", 4_000L + it, false, "TEST CAFE", 0) }
        val inbox = MemorySmsInbox(available = true, now = HISTORY_NOW)
        inbox.enable(listOf("TESTBANK"))
        december.forEach { inbox.receive(it.sms); inbox.preLearn(space.spaceId, space.parse, it.sms) }
        AutoRecordSms(AutoRecordSmsDeps(inbox, listOf(space.lane(inbox)))).run()
        assertEquals(5, space.all().size, "recorded")
        val world = LedgerWorld(HISTORY_BANK, CASH, space.txnStore, space.sources, space.batchStore, space.wallets)
        val why = world.explain().explain(HISTORY_BANK.id, "2026-01-01")!!
        assertEquals(HISTORY_BANK.openingBalanceMinor, world.shown("2026-01-01"))
        assertEquals(HISTORY_BANK.openingBalanceMinor, why.computedMinor, "nothing before the opening day moves the balance")
        assertEquals(december.map { it.minor }.toSet(), why.review.beforeOpening.map { it.amountMinor }.toSet())
    }

    /** (ج) رصيد البداية اتنقل لتاريخ متأخر ⇒ كل اللي قبله خرج من الحساب (بيتعرض) · عملية من غير محفظة مش محسوبة في أي رصيد. */
    @Test fun aLaterOpeningDateHidesEverythingBeforeItAndWalletlessItemsCountNowhere() = runBlocking<Unit> {
        val w = LedgerWorld()
        w.add(w.txn("s1", "2025-01-10", 900_000, Direction.IN, kind = EconomicKind.SALARY), ImportSourceType.CSV_LEGACY)
        w.add(w.txn("s2", "2025-02-10", 50_000), ImportSourceType.CSV_LEGACY)
        w.add(w.txn("s3", "2025-03-10", 20_000), ImportSourceType.CSV_LEGACY)
        w.add(w.txn("loose", "2025-03-11", 7_000, wallet = null), ImportSourceType.CSV_LEGACY)
        assertEquals(500_000L + 900_000 - 50_000 - 20_000, w.shown("2025-04-01"))
        w.wallets.save(w.bank.copy(openingBalanceMinor = 0, openingAt = "2025-03-01"))
        val why = w.explain().explain(w.bank.id, "2025-04-01")!!
        assertEquals(-20_000L, why.computedMinor, "only March counts after the move — the salary vanished from the balance")
        assertEquals(w.shown("2025-04-01"), why.computedMinor)
        assertEquals(listOf("s1", "s2"), why.review.beforeOpening.map { it.transactionId })
        assertEquals(listOf("loose"), why.review.unassigned.map { it.transactionId })
    }

    /** (د) تحويل لمحفظة مش موجودة (طرف واحد) · رسوم من الرسالة ومن الكشف (مرتين) · عملة تانية بتتحسب برقمها · استرداد مستني قرار. */
    @Test fun transfersFeesCurrencyAndRefundsAreFlagged() = runBlocking<Unit> {
        val w = LedgerWorld()
        w.add(w.txn("to-cash", "2025-05-01", 30_000, to = w.cash.id, kind = EconomicKind.INTERNAL_TRANSFER), ImportSourceType.SMS)
        w.add(w.txn("to-gone", "2025-05-02", 40_000, to = "w-archived", kind = EconomicKind.INTERNAL_TRANSFER), ImportSourceType.SMS)
        w.add(w.txn("fee-sms", "2025-05-03", 575, kind = EconomicKind.FEE), ImportSourceType.SMS)
        w.add(w.txn("fee-stmt", "2025-05-04", 575, kind = EconomicKind.FEE), ImportSourceType.CSV_LEGACY)
        w.add(w.txn("egp", "2025-05-05", 100_000, currency = Currency.EGP), ImportSourceType.SMS)
        w.add(w.txn("usd", "2025-05-06", 8_750).copy(foreignCurrency = "USD", foreignAmountMinor = 2_340), ImportSourceType.SMS)
        w.add(w.txn("refund", "2025-05-07", 2_500, Direction.IN).copy(suggestedKind = EconomicKind.REFUND_RECEIVED, economicKind = EconomicKind.UNCLASSIFIED), ImportSourceType.SMS)
        w.add(w.txn("hand", "2025-05-08", 1_000), null)

        val why = w.explain().explain(w.bank.id, "2025-06-01")!!
        val out = 30_000L + 40_000 + 575 + 575 + 100_000 + 8_750 + 1_000
        assertEquals(500_000L - out + 2_500, why.computedMinor)
        assertEquals(w.shown("2025-06-01"), why.computedMinor, "the EGP number is counted as riyals — same as the shown balance")
        assertEquals(listOf("to-gone"), why.review.oneSidedTransfers.map { it.transactionId })
        assertEquals(listOf("fee-sms" to "fee-stmt"), why.review.duplicates.map { it.sms.transactionId to it.statement.transactionId })
        assertEquals(listOf("egp"), why.review.otherCurrency.map { it.transactionId })
        assertEquals(listOf("usd"), why.review.foreignAmount.map { it.transactionId })
        assertEquals(listOf("refund"), why.review.reversals.map { it.transactionId })
        val bySource = why.totals.associateBy { it.source }
        assertEquals(70_000L to 2, bySource.getValue(BalanceSource.TRANSFER).let { it.outMinor to it.count })
        assertEquals(1_000L, bySource.getValue(BalanceSource.MANUAL).outMinor)
        assertEquals(575L, bySource.getValue(BalanceSource.STATEMENT).outMinor)

        // الكاش: الطرف الداخل محسوب «تحويل»
        val cash = w.explain().explain(w.cash.id, "2025-06-01")!!
        assertEquals(100_000L + 30_000, cash.computedMinor)
        assertEquals(30_000L, cash.totals.single { it.source == BalanceSource.TRANSFER }.inMinor)
    }

    /** (هـ) رسالة راتب من غير جهة بتستنى «ده راتبك؟» (§75-2) والمشتريات بتتسجل لوحدها ⇒ الرصيد بينزل بالمشتريات من غير الراتب. */
    @Test fun aSalaryWaitingForItsAnswerLeavesOnlyTheOutflowsInTheBalance() = runBlocking<Unit> {
        val w = SmsWorld(listOf(SmsSpace()), learnOnReceive = false).enable()
        val space = w.spaces.single()
        w.learn(sms("seed-cafe", CAFE), sms("seed-mart", MART))
        w.receive(sms("salary", "راتب\nبـSR 9,500\n26/10/01"))
        w.receive(sms("cafe", CAFE))
        w.receive(sms("mart", MART))
        val run = w.auto().run()
        assertEquals(listOf("salary"), run.waiting, "the salary waits for «is this your salary?»")
        val world = LedgerWorld(BANK, CASH, space.txnStore, space.sources, space.batchStore, space.wallets)
        val why = world.explain().explain(BANK.id, "2026-10-07")!!
        assertEquals(-(2_500L + 4_000), why.computedMinor, "purchases are in, the salary is not")
        assertTrue(why.review.isEmpty, "nothing in the ledger looks wrong — the missing money is a waiting message")
    }
}
