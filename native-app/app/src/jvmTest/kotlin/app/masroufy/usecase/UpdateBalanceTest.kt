package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EGYPT_TIME_ZONE
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.periodForDate
import app.masroufy.core.uiText
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «حدّث الرصيد» / «حدّث الكاش» (`UpdateBalance` — قايمة المالك 2026-10-15 البنود 4 و5) على مستودعات الذاكرة: التسجيل دلوقتي ويوم فات · القهوة بعد
 * التحديث · الرسالة القديمة اللي وصلت متأخر · التوزيع على تصنيف · «سيبه» · التراجع · المجاميع ما بتتأثرش · مصر بالجنيه ويوم القاهرة.
 * كل البيانات مخترعة، والمبالغ هللات صحيحة.
 */
class UpdateBalanceTest {
    private class StepClock(var now: String) : Clock {
        override fun nowIso(): String = now
    }

    private fun txn(id: String, wallet: String, dir: Direction, minor: Long, day: String, created: String = "2026-10-01T09:00:00Z", kind: EconomicKind = EconomicKind.PURCHASE, to: String? = null) =
        Transaction(
            id = id, occurredAt = day, datePrecision = "day", sourceOrder = 1, economicKind = kind, economicKindConfirmed = true,
            observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = created, updatedAt = created, walletId = wallet, transferToWalletId = to,
        )

    private val bank = Wallet("bank", "بنك وهمي", Currency.SAR, "bank", 400_000, "2026-10-01")
    private val cash = Wallet("cash", "الكاش", Currency.SAR, "cash", 50_000, "2026-10-01")
    private val food = Category("cat-food", null, "مطاعم وهمية", "utensils", "#eee", "#111", true, 1)

    private inner class World(clockAt: String = "2026-10-15T08:00:00Z", zone: String = app.masroufy.core.SAUDI_TIME_ZONE, wallets: List<Wallet> = listOf(bank, cash)) {
        val clock = StepClock(clockAt)
        val walletRepo = MemoryWalletRepository(wallets)
        val txns = MemoryTransactionRepository(
            listOf(
                txn("t-salary", "bank", Direction.IN, 300_000, "2026-10-05", kind = EconomicKind.SALARY),
                txn("t-shop", "bank", Direction.OUT, 45_000, "2026-10-07"),
                txn("t-atm", "bank", Direction.OUT, 20_000, "2026-10-08", kind = EconomicKind.INTERNAL_TRANSFER, to = "cash"),
                txn("t-cafe", "cash", Direction.OUT, 3_500, "2026-10-09"),
            ),
        )
        val update = UpdateBalance(walletRepo, txns, MemoryCategoryRepository(listOf(food)), SequentialIdGenerator(), clock, zone)
        val now = LoadWithYouNow(walletRepo, txns, Currency.SAR)
        val cashSummary = LoadCashSummary(LoadCashSummaryDeps(walletRepo, txns, MemoryAllocationRepository(), MemoryCategoryRepository(listOf(food))))

        suspend fun balances(day: String = "2026-10-15") = now.load(day).wallets.associate { it.wallet.id to it.balanceMinor }

        suspend fun totals(day: String = "2026-10-15") = periodForDate(day, 28).let { p -> computePeriodTotals(txns.listByDateRange(p.start, p.end), emptyList()) }
    }

    @Test fun bankNowAnchorsTheBalanceAndTotalsStayTheSame() = runBlocking<Unit> {
        val w = World()
        assertEquals(mapOf("bank" to 635_000L, "cash" to 66_500L), w.balances())
        val before = w.totals()
        val count = w.txns.listByDateRange("2026-01-01", "2026-12-31").size
        val p = w.update.preview("bank", 615_000)
        assertEquals(635_000L to -20_000L, p.computedMinor to p.differenceMinor)
        assertTrue(p.now)
        val e = w.update.record("bank", 615_000)
        assertEquals(-20_000L, e.adjustmentMinor)
        assertEquals(-20_000L, e.checkpoint.recordedDiffMinor)
        assertEquals("2026-10-15T08:00:00Z", e.checkpoint.atInstant)
        assertEquals(615_000L, w.balances()["bank"])
        assertEquals(681_500L, w.now.load("2026-10-15").totalMinor)
        assertEquals(before, w.totals(), "الفرق مش مصروف ولا دخل")
        assertEquals(count, w.txns.listByDateRange("2026-01-01", "2026-12-31").size, "ولا عملية اتضافت")
        assertEquals(listOf(e.checkpoint.id), w.update.openDifferences().map { it.checkpoint.id })
    }

    @Test fun cashCountThenCoffeeAfterwardsComesOffTheCountedCash() = runBlocking<Unit> {
        val w = World()
        w.update.record("cash", 60_000)
        w.clock.now = "2026-10-15T10:00:00Z"
        w.txns.saveMany(listOf(txn("t-coffee", "cash", Direction.OUT, 1_200, "2026-10-15", created = w.clock.now)))
        assertEquals(58_800L, w.balances()["cash"])
        val s = w.cashSummary.load(periodForDate("2026-10-15", 28), "2026-10-15")!!
        assertEquals(58_800L, s.balanceMinor)
        assertEquals(4_700L, s.spentInPeriodMinor, "المصروف كاش = القهوتين بس، من غير الفرق")
        assertEquals(-6_500L, s.checkpoints.single().adjustmentMinor)
    }

    @Test fun pastDayCountsAtTheEndOfThatDayAndBadInputIsRefused() = runBlocking<Unit> {
        val w = World()
        val e = w.update.record("bank", 650_000, "2026-10-07")
        assertNull(e.checkpoint.atInstant)
        assertEquals(-5_000L, e.adjustmentMinor, "400000 + 300000 − 45000 = 655000 محسوب ⇒ كتب 650000")
        assertEquals(650_000L - 655_000L, w.update.history("bank").effects.single().adjustmentMinor)
        assertEquals(630_000L, w.balances()["bank"], "650000 − السحب للكاش 20000")
        assertFailsWith<BalanceError> { w.update.record("bank", 1, "2026-10-16") }.also { assertEquals(uiText(UiKey.BAL_ERR_DATE), it.message) }
        assertFailsWith<BalanceError> { w.update.record("bank", 1, "2026-09-30") }
        assertFailsWith<BalanceError> { w.update.record("bank", -1) }.also { assertEquals(uiText(UiKey.BAL_ERR_AMOUNT), it.message) }
        assertFailsWith<BalanceError> { w.update.record("nope", 1) }
    }

    @Test fun olderSmsArrivingLaterDoesNotBreakTheConfirmedBalance() = runBlocking<Unit> {
        val w = World()
        w.update.record("bank", 615_000)
        w.update.keep("bank", w.update.history("bank").effects.single().checkpoint.id)
        assertTrue(w.update.openDifferences().isEmpty(), "سابه")
        w.clock.now = "2026-10-16T07:00:00Z"
        w.txns.saveMany(listOf(txn("t-old-sms", "bank", Direction.OUT, 8_000, "2026-10-12", created = w.clock.now)))
        assertEquals(615_000L, w.balances("2026-10-16")["bank"], "الرصيد اللي المالك أكده ثابت")
        val e = w.update.history("bank").effects.single()
        assertEquals(-12_000L, e.adjustmentMinor)
        assertEquals(8_000L, e.lateMinor)
        assertEquals(listOf(e.checkpoint.id), w.update.openDifferences().map { it.checkpoint.id }, "اتغيّر ⇒ يرجع للمراجعة")
    }

    @Test fun splitTurnsAPartIntoARegularOperationWithoutMovingTheBalance() = runBlocking<Unit> {
        val w = World()
        val cp = w.update.record("cash", 56_500).checkpoint // ناقص 100.00
        w.clock.now = "2026-10-15T12:00:00Z"
        val totalsBefore = w.totals()
        val part = w.update.split("cash", cp.id, SplitPart(6_000, EconomicKind.PURCHASE, "cat-food"))
        assertEquals(cp.id, part.explainsCheckpointId)
        assertEquals("2026-10-15" to Direction.OUT, part.occurredAt to part.observedDirection)
        assertTrue(part.isCashTagged)
        assertEquals(56_500L, w.balances()["cash"])
        val e = w.update.history("cash").effects.single()
        assertEquals(-4_000L, e.adjustmentMinor)
        assertEquals(-6_000L, e.explainedMinor)
        assertEquals(0L, e.lateMinor)
        assertEquals(totalsBefore.personalExpenseMinor + 6_000, w.totals().personalExpenseMinor, "الجزء بقى مصروف عادي")
        assertEquals(listOf(part.id), w.update.history("cash").parts[cp.id]?.map { it.id })
        // الجانب الغلط والمبلغ الأكبر من الباقي
        assertFailsWith<BalanceError> { w.update.split("cash", cp.id, SplitPart(1_000, EconomicKind.GIFT_RECEIVED, null)) }
        assertFailsWith<BalanceError> { w.update.split("cash", cp.id, SplitPart(4_001, EconomicKind.PURCHASE, null)) }
            .also { assertEquals(uiText(UiKey.BAL_ERR_SPLIT_TOO_BIG), it.message) }
        // «رجّعه للفرق»
        w.update.removePart(part.id)
        assertEquals(-10_000L, w.update.history("cash").effects.single().adjustmentMinor)
        assertEquals(totalsBefore, w.totals())
        assertFailsWith<BalanceError> { w.update.removePart("t-cafe") }
    }

    @Test fun undoBringsBackTheComputedBalanceAndKeepsAssignedParts() = runBlocking<Unit> {
        val w = World()
        val cp = w.update.record("cash", 80_000).checkpoint // زيادة 135.00
        w.clock.now = "2026-10-15T12:00:00Z"
        val part = w.update.split("cash", cp.id, SplitPart(10_000, EconomicKind.GIFT_RECEIVED, null))
        assertEquals(Direction.IN, part.observedDirection)
        assertEquals(80_000L, w.balances()["cash"])
        assertEquals(1, w.update.undo("cash", cp.id))
        assertEquals(76_500L, w.balances()["cash"], "المحسوب + الهدية اللي المالك قال إنها حصلت")
        assertNull(w.txns.findByIds(listOf(part.id)).single().explainsCheckpointId)
        assertTrue(w.walletRepo.findById("cash")!!.checkpoints.isEmpty())
        assertFailsWith<BalanceError> { w.update.undo("cash", cp.id) }
    }

    @Test fun egyptCountsTheCairoDayAndThePound() = runBlocking<Unit> {
        val eg = Wallet("eg-bank", "بنك مصري وهمي", Currency.EGP, "bank", 1_000_000, "2026-12-01")
        // 21:30 جرينتش = 23:30 القاهرة (10 ديسمبر) و00:30 الرياض (11 ديسمبر)
        val w = World(clockAt = "2026-12-10T21:30:00Z", zone = EGYPT_TIME_ZONE, wallets = listOf(eg))
        assertEquals("2026-12-10", w.update.today())
        assertFailsWith<BalanceError> { w.update.record("eg-bank", 900_000, "2026-12-11") }
        val e = w.update.record("eg-bank", 987_650)
        assertEquals("2026-12-10", e.checkpoint.day)
        assertEquals(-12_350L, e.adjustmentMinor)
        assertEquals(987_650L, LoadWithYouNow(w.walletRepo, w.txns, Currency.EGP).load("2026-12-10").totalMinor)
    }
}
