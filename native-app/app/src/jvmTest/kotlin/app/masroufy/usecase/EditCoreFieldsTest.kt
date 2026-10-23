package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryTagRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransactionTagRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** r06-links: المبلغ والتاريخ والحساب بيتعدلوا من تفاصيل العملية، و«+» فيه تاريخ (افتراضيه النهارده). بيانات مخترعة · وحدة صغرى. */
class EditCoreFieldsTest {
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01")
    private val usd = Wallet("w-usd", "حساب دولار", Currency.USD, "bank", 0, "2026-01-01")
    private val txns = MemoryTransactionRepository()
    private val wallets = MemoryWalletRepository(listOf(bank, cash, usd))
    private val clock = FixedClock("2026-10-20T09:00:00Z")
    private val ids = SequentialIdGenerator()
    private val quick = QuickAddOperation(
        wallets, MemoryCategoryRepository(), MemoryProfileRepository(), AddTransaction(AddTransactionDeps(txns, wallets, ids, clock)), Currency.SAR, { "2026-10-20" },
    )
    private val edit = EditTransaction(
        EditTransactionDeps(txns, MemoryCategoryRepository(), MemoryTagRepository(), MemoryTransactionTagRepository(), PassthroughUnitOfWork(), ids, clock, wallets = wallets),
    )

    private suspend fun saved(draft: AddOperationDraft) = (quick.save(draft) as AddOperationResult.Saved).transaction

    @Test fun thePlusSheetDateDefaultsToTodayAndTakesAPastDay() = runBlocking<Unit> {
        assertEquals("2026-10-20", saved(AddOperationDraft(AddKind.OUT, "12.50", bank.id)).occurredAt)
        assertEquals("2026-10-03", saved(AddOperationDraft(AddKind.OUT, "12.50", bank.id, date = "2026-10-03")).occurredAt)
        assertTrue(quick.save(AddOperationDraft(AddKind.OUT, "12.50", bank.id, date = "2026-10-25")) is AddOperationResult.Invalid, "بعد النهارده مرفوض")
    }

    @Test fun amountDateAndWalletAreEditable() = runBlocking<Unit> {
        val t = saved(AddOperationDraft(AddKind.OUT, "40.00", bank.id))
        edit.setAmount(t.id, 4_125)
        edit.setDate(t.id, "2026-10-01")
        edit.setWallet(t.id, cash.id)
        val after = edit.load(t.id).transaction
        assertEquals(Triple(4_125L, "2026-10-01", cash.id), Triple(after.amountMinor, after.occurredAt, after.walletId))
        assertEquals(4_000L, after.originalAmountMinor, "الأصلي بيتحفظ (§32)")
    }

    @Test fun badEditsAreRefused() = runBlocking<Unit> {
        val t = saved(AddOperationDraft(AddKind.OUT, "40.00", bank.id))
        assertFailsWith<IllegalStateException> { edit.setDate(t.id, "2026-13-40") }
        assertFailsWith<IllegalStateException> { edit.setDate(t.id, "2026-12-01") }
        assertFailsWith<IllegalStateException> { edit.setWallet(t.id, usd.id) }
        assertFailsWith<IllegalStateException> { edit.setWallet(t.id, "missing") }
        assertEquals(bank.id to "2026-10-20", edit.load(t.id).transaction.let { it.walletId to it.occurredAt })
    }
}
