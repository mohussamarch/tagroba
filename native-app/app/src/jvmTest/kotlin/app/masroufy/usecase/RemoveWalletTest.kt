package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EconomicKind
import app.masroufy.core.Wallet
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** r06-delete (§118-1) — مسح الحساب: من غير عمليات · أرشفه · سيب العمليات · هو وعملياته · وكل واحدة بـ«تراجع». */
class RemoveWalletTest {
    private val bank = Wallet("w-1", "بنك تجربة", Currency.SAR, "bank", 500_00, "2026-01-01", accountLast4 = "1234")
    private val cash = Wallet("w-2", "كاش تجربة", Currency.SAR, "cash", 100_00, "2026-01-01")

    private suspend fun kit(withOps: Boolean = true) = RemovalKit().apply {
        wallets.save(bank)
        wallets.save(cash)
        inbox.setSenderWallet("sa", "TESTBANK", "w-1")
        if (withOps) txns.saveMany(listOf(op("t-1", 25_00), op("t-2", 90_00, EconomicKind.SALARY, app.masroufy.core.Direction.IN), op("t-3", 10_00, walletId = "w-2")))
    }

    @Test
    fun noOperations_deletesThenUndo() = runBlocking {
        val k = kit(withOps = false)
        val ask = k.removals.wallet.ask("w-1")
        assertEquals(0, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.DELETE), ask.choices)
        val done = k.removals.wallet.remove("w-1", RemoveChoice.DELETE)
        assertNull(k.wallets.findById("w-1"))
        assertTrue(k.inbox.senderWallets("sa").isEmpty(), "المرسل رجع «مالوش حساب»")
        done.undo()
        assertEquals(bank, k.wallets.findById("w-1"))
        assertEquals("w-1", k.inbox.senderWallets("sa").values.single())
    }

    @Test
    fun linked_offersThreeChoices() = runBlocking {
        val ask = kit().removals.wallet.ask("w-1")
        assertEquals(2, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.KEEP_OPERATIONS, RemoveChoice.WITH_OPERATIONS), ask.choices)
    }

    @Test
    fun archive_hidesAndUndo() = runBlocking {
        val k = kit()
        val done = k.removals.wallet.remove("w-1", RemoveChoice.ARCHIVE)
        assertEquals(true, k.wallets.findById("w-1")?.hiddenOnHome)
        assertEquals(3, k.all().size)
        done.undo()
        assertEquals(bank, k.wallets.findById("w-1"))
    }

    @Test
    fun keepOperations_totalsUnchanged_andUndoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.wallet.remove("w-1", RemoveChoice.KEEP_OPERATIONS)
        assertNull(k.wallets.findById("w-1"))
        assertEquals(totals, k.totals(), "المجاميع ما اتغيرتش")
        val kept = k.all().filter { it.id in setOf("t-1", "t-2") }
        assertEquals(2, kept.size)
        assertTrue(kept.all { it.walletId == null && it.sourceRemoved == "wallet:بنك تجربة" })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(bank, k.wallets.findById("w-1"))
        assertEquals("w-1", k.inbox.senderWallets("sa").values.single())
    }

    @Test
    fun withOperations_erasesThenUndoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val done = k.removals.wallet.remove("w-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("t-3"), k.all().map { it.id })
        assertNotNull(done.backup, "نسخة متأكدة قبل مسح العمليات")
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(bank, k.wallets.findById("w-1"))
    }
}
