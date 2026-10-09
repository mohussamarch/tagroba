package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** «معك الآن» (OVERRIDES §73) بمحافظ وعمليات مخترعة: المجموع بالهللة، والمحفظة اللي ما اتفتحتش ⇒ «غير متاح» مش صفر. */
class LoadWithYouNowTest {
    private fun txn(id: String, wallet: String, dir: Direction, minor: Long, day: String, kind: EconomicKind = EconomicKind.PURCHASE, to: String? = null) = Transaction(
        id = id, occurredAt = day, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = wallet, transferToWalletId = to,
    )

    private val bank = Wallet("bank", "بنك وهمي", Currency.SAR, "bank", 100_000, "2026-01-01")
    private val bank2 = Wallet("bank2", "بنك تاني", Currency.SAR, "bank", 0, "2026-01-01")
    private val cash = Wallet("cash", "الكاش", Currency.SAR, "cash", 50_000, "2026-01-01")

    @Test fun sumsAllWalletsToTheHalala() = runBlocking<Unit> {
        val txns = MemoryTransactionRepository(
            listOf(
                txn("t1", "bank", Direction.IN, 600_001, "2026-02-01", EconomicKind.SALARY),
                txn("t2", "bank", Direction.OUT, 12_345, "2026-02-02"),
                // سحب من البنك للكاش: صادر من البنك وداخل على الكاش
                txn("t3", "bank", Direction.OUT, 20_000, "2026-02-03", EconomicKind.INTERNAL_TRANSFER, to = "cash"),
                txn("t4", "cash", Direction.OUT, 1_050, "2026-02-04"),
                // بعد النهارده ما بيدخلش
                txn("t5", "bank", Direction.OUT, 99_999, "2026-03-01"),
            ),
        )
        val now = LoadWithYouNow(MemoryWalletRepository(listOf(cash, bank, bank2)), txns, Currency.SAR).load("2026-02-10")
        assertEquals(listOf("bank", "bank2", "cash"), now.wallets.map { it.wallet.id }, "البنوك الأول والكاش آخر حاجة")
        assertEquals(667_656L, now.banks.first().balanceMinor) // 100000 + 600001 − 12345 − 20000
        assertEquals(68_950L, now.cashMinor) // 50000 + 20000 − 1050
        assertEquals(736_606L, now.totalMinor)
        assertEquals(listOf("bank", "bank2"), now.banks.map { it.wallet.id })
    }

    @Test fun walletNotOpenedYetMakesTheTotalNotAvailable() = runBlocking<Unit> {
        val late = Wallet("late", "محفظة جديدة", Currency.SAR, "bank", 0, "2026-05-01")
        val now = LoadWithYouNow(MemoryWalletRepository(listOf(bank, late)), MemoryTransactionRepository(), Currency.SAR).load("2026-02-10")
        assertNull(now.totalMinor, "غير متاح — مش صفر")
        assertNull(now.cashMinor, "مفيش كاش ⇒ غير متاح")
        assertNull(now.wallets.single { it.wallet.id == "late" }.balanceMinor)
    }

    @Test fun noWalletsAtAll() = runBlocking<Unit> {
        val now = LoadWithYouNow(MemoryWalletRepository(), MemoryTransactionRepository(), Currency.EGP).load("2026-02-10")
        assertNull(now.totalMinor)
        assertEquals(Currency.EGP, now.currency)
    }
}
