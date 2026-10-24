package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.ParsedRow
import app.masroufy.core.ReviewState
import app.masroufy.core.SmsKind
import app.masroufy.core.SmsRow
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.walletBalancesOn
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * r06-links2 فجوة ١: التحويل اليدوي (سطر واحد) ورسايل البنك (رِجلين) لنفس التحويل ⇒ تحويل واحد، أيًّا كان اللي اتسجل الأول، ورصيد كل
 * محفظة (و«معك الآن») زي ما هو. كل الأسامي والأرقام مخترعة · المبالغ بالوحدة الصغرى.
 */
class OwnTransferManualMergeTest {
    private val a = Wallet("w-a", "بنك وهمي أ", Currency.SAR, "bank", 100_000, "2026-01-01", accountLast4 = "1111")
    private val b = Wallet("w-b", "بنك وهمي ب", Currency.SAR, "bank", 20_000, "2026-01-01", accountLast4 = "2222")

    private fun txn(id: String, wallet: String, dir: Direction, day: String) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.UNCLASSIFIED, economicKindConfirmed = false,
        observedDirection = dir, amountMinor = 50_000, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = wallet,
    )

    /** رسالة بنك على [wallet] اتسجلت بأثر التحويل (نفس طريق التسجيل). */
    private suspend fun sms(txns: MemoryTransactionRepository, wallets: List<Wallet>, wallet: Wallet, t: Transaction, ref: String) {
        val body = "رسالة وهمية $ref"
        val row = ParsedRow(1, t.occurredAt, t.amountMinor, t.observedDirection, "", ref, "SMS", body, body)
        val smsRow = SmsRow(1, t.occurredAt, t.amountMinor, t.observedDirection, "", ref, "SMS", body, body, SmsKind.OTHER, wallet.accountLast4)
        val request = ImportRequest(fileName = "sms", content = body, accountIdentity = wallet.name, sourceType = ImportSourceType.SMS, walletId = wallet.id, parsedRows = listOf(row))
        val line = ImportPreviewLine(row = row, state = MatchingState.NEW, reason = "", categoryReason = "", selectedByDefault = true)
        val ids = object : app.masroufy.port.IdGenerator {
            var n = 0
            override fun next(prefix: String) = "$prefix-$ref-${n++}"
        }
        val ctx = RecordContext(request, "batch-$ref", "2026-10-08T00:00:00Z", false, emptyMap(), mutableListOf(RecordedLine(line, smsRow, t)), mutableListOf(), ids)
        OwnTransferLegEffect(MemoryWalletRepository(wallets), txns).prepare(ctx)
        txns.saveMany(ctx.lines.map { it.transaction } + ctx.extra.map { it.first })
    }

    private suspend fun manual(txns: MemoryTransactionRepository, wallets: List<Wallet>, day: String = "2026-10-07"): Transaction =
        AddTransaction(AddTransactionDeps(txns, MemoryWalletRepository(wallets), SequentialIdGenerator(), FixedClock("2026-10-08T00:00:00Z"))).add(
            NewTransactionInput(50_000, occurredAt = day, walletId = a.id, transferToWalletId = b.id, economicKind = EconomicKind.INTERNAL_TRANSFER, merchantName = "تحويل وهمي"),
        )

    private suspend fun rows(txns: MemoryTransactionRepository) = txns.listByDateRange("0000-01-01", "9999-12-31")

    /** «معك الآن» = مجموع المحفظتين، ورصيد كل واحدة: أ ناقص 500 وب زايد 500 — مرة واحدة. */
    private suspend fun assertOneTransfer(txns: MemoryTransactionRepository, wallets: List<Wallet>) {
        val bal = walletBalancesOn(wallets, rows(txns), "2026-12-31")
        val base = walletBalancesOn(wallets, emptyList(), "2026-12-31")
        assertEquals(base[a.id]!! - 50_000, bal[a.id], "المحفظة أ نقصت مرة واحدة")
        assertEquals(base[b.id]!! + 50_000, bal[b.id], "المحفظة ب زادت مرة واحدة")
        assertEquals(base.values.sumOf { it!! }, bal.values.sumOf { it!! }, "«معك الآن» ما اتغيرش")
        assertTrue(rows(txns).all { it.economicKind == EconomicKind.INTERNAL_TRANSFER })
    }

    @Test fun manualThenSmsIsOneTransfer() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val w = listOf(a, b)
        manual(txns, w)
        sms(txns, w, a, txn("t-out", a.id, Direction.OUT, "2026-10-08"), "r-out")
        assertOneTransfer(txns, w)
        assertTrue(rows(txns).none { it.sourceOrder == MANUAL_SOURCE_ORDER }, "اليدوي اندمج في الرسالة")
        // رسالة البنك التاني بعدها بتندمج في الرِجل التلقائية (§112-3)
        sms(txns, w, b, txn("t-in", b.id, Direction.IN, "2026-10-08"), "r-in")
        assertOneTransfer(txns, w)
        assertEquals(setOf("t-out", "t-in"), rows(txns).map { it.id }.toSet())
    }

    @Test fun smsThenManualIsOneTransfer() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val w = listOf(a, b)
        sms(txns, w, a, txn("t-out", a.id, Direction.OUT, "2026-10-07"), "r-out")
        val kept = manual(txns, w, day = "2026-10-08")
        assertEquals("t-out", kept.id, "اليدوي رجّع الرِجل الموجودة")
        assertOneTransfer(txns, w)
        assertTrue(rows(txns).none { it.sourceOrder == MANUAL_SOURCE_ORDER })
    }

    @Test fun incomingSmsThenManualThenOutgoingSmsIsOneTransfer() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val w = listOf(a.copy(accountLast4 = null), b.copy(accountLast4 = null))
        sms(txns, w, b, txn("t-in", b.id, Direction.IN, "2026-10-07"), "r-in")
        manual(txns, w)
        assertOneTransfer(txns, w)
        sms(txns, w, a, txn("t-out", a.id, Direction.OUT, "2026-10-07"), "r-out")
        assertOneTransfer(txns, w)
        assertEquals(setOf("t-out", "t-in"), rows(txns).map { it.id }.toSet(), "الرِجل الصادرة التلقائية اندمجت")
    }

    @Test fun differentAmountStaysSeparate() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository()
        val w = listOf(a, b)
        sms(txns, w, a, txn("t-out", a.id, Direction.OUT, "2026-10-07").copy(amountMinor = 40_000), "r-out")
        val m = manual(txns, w)
        assertEquals(MANUAL_SOURCE_ORDER, m.sourceOrder)
        assertEquals(2, rows(txns).size)
    }
}
