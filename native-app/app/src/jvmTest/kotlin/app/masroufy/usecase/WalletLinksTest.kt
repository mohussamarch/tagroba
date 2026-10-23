package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** r06-links: آخر 4 أرقام بتتعدل (والرقم الكامل ما بيتخزنش) · إخفاء حساب بيشيله من الرئيسية ومن «معك الآن» (§112-1). بيانات مخترعة. */
class WalletLinksTest {
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    private val other = Wallet("w-other", "بنك تاني", Currency.SAR, "bank", 30_000, "2026-01-01", accountLast4 = "5555")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 5_000, "2026-01-01")
    private val repo = MemoryWalletRepository(listOf(bank, other, cash))
    private val manage = ManageWallets(repo, SequentialIdGenerator(), FixedClock("2026-10-10T09:00:00.000Z"), Currency.SAR)
    private suspend fun now() = LoadWithYouNow(repo, MemoryTransactionRepository(), Currency.SAR).load("2026-10-10")

    @Test fun lastFourDigitsAreEditableAndOnlyFourAreKept() = runBlocking<Unit> {
        assertEquals("1234", manage.setLast4(bank.id, "1234").accountLast4)
        assertEquals("9876", manage.setLast4(bank.id, "SA00 0000 0000 9876").accountLast4, "الرقم الكامل ⇒ آخر 4 بس")
        assertFailsWith<WalletError> { manage.setLast4(bank.id, "5555") } // أرقام حساب تاني
        assertFailsWith<WalletError> { manage.setLast4(cash.id, "1111") } // الكاش مالوش أرقام
        assertNull(manage.setLast4(bank.id, "").accountLast4, "فاضي ⇒ بيتشال")
    }

    @Test fun aHiddenAccountLeavesTheHeroTotalAndTheLogos() = runBlocking<Unit> {
        assertEquals(135_000L, now().totalMinor)
        manage.setHidden(other.id, true)
        val after = now()
        assertEquals(105_000L, after.totalMinor, "فلوسه برّه «معك الآن»")
        assertEquals(listOf(bank.id), after.banks.map { it.wallet.id }, "برّه لوجوهات الرئيسية")
        assertEquals(3, after.wallets.size, "لسه في «حساباتي» (تحت «مخفي»)")
        manage.setHidden(other.id, false)
        assertEquals(135_000L, now().totalMinor)
    }

    @Test fun aHiddenAccountWithAnUnknownBalanceDoesNotBlankTheTotal() = runBlocking<Unit> {
        repo.save(other.copy(openingUnknown = true))
        assertNull(now().totalMinor, "غير متاح")
        manage.setHidden(other.id, true)
        assertEquals(105_000L, now().totalMinor)
    }
}
