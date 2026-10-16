package app.masroufy.usecase

import app.masroufy.core.BalanceCheckpoint
import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.core.periodForDate
import app.masroufy.core.walletBalancesOn
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r041b P10 + P4: «أيوه، ده حسابي» وكل محافظ البنك عليها أرقام ⇒ «محفظة جديدة لهذا الحساب» (بنك محفظة الرسالة · الأرقام دي · رصيدها
 * «غير متاح») أو نقل الأرقام لمحفظة موجودة بإذن صريح. ومحفظة بنك من غير رصيد بداية رصيدها «غير متاح» مش صفر. كله مخترع.
 */
class AccountAnswerNoDeadEndTest {
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 50_000, "2026-01-01", accountLast4 = "9999", bankId = "sa-al-rajhi-bank")
    private val cash = Wallet("w-cash", "كاش وهمي", Currency.SAR, "cash", 10_000, "2026-01-01")
    private val repo = MemoryWalletRepository(listOf(bank, cash.copy(notMineLast4 = listOf("1111"))))
    private val manage = ManageWallets(repo, SequentialIdGenerator(), FixedClock("2026-10-10T09:00:00.000Z"), Currency.SAR)
    private val answer = AnswerSmsAccount(repo, manage)

    @Test fun aNewWalletForTheAccountWhenEveryBankWalletHasDigits() = runBlocking<Unit> {
        val created = answer.mineNewWallet(bank.id, "١١١١")
        assertEquals("1111", created.accountLast4)
        assertEquals("bank" to "sa-al-rajhi-bank", created.kind to created.bankId, "same bank and kind as the message's wallet")
        assertEquals(Currency.SAR, created.currency)
        assertEquals("بنك وهمي 1111", created.name)
        assertTrue(created.openingUnknown, "no opening balance ⇒ unknown, not a known zero")
        assertEquals(created, repo.findById(created.id))
        assertEquals("9999", repo.findById(bank.id)!!.accountLast4, "the old wallet keeps its own digits")
        assertEquals(emptyList(), repo.findById(cash.id)!!.notMineLast4, "an old «no» on the same digits is cleared")
        val now = LoadWithYouNow(repo, MemoryTransactionRepository(), Currency.SAR).load("2026-10-10")
        assertNull(now.wallets.single { it.wallet.id == created.id }.balanceMinor)
        assertNull(now.totalMinor, "the total is «not available», never counted as a known 0")
    }

    @Test fun aNewWalletRefusesDigitsAlreadyOnAWallet() = runBlocking<Unit> {
        assertFailsWith<WalletError> { answer.mineNewWallet(bank.id, "9999") }
        assertFailsWith<WalletError> { answer.mineNewWallet(bank.id, "12") }
        assertFailsWith<WalletError> { AnswerSmsAccount(repo).mineNewWallet(bank.id, "1111") }
        assertEquals(2, repo.listAll().size)
    }

    @Test fun movingTheDigitsNeedsTheExplicitReplace() = runBlocking<Unit> {
        assertFailsWith<WalletError> { answer.mine(bank.id, "1111") }
        val moved = answer.mine(bank.id, "1111", replace = true)
        assertEquals("1111", moved.accountLast4)
        assertEquals("1111", repo.findById(bank.id)!!.accountLast4)
        assertFailsWith<WalletError> { answer.mine(cash.id, "2222", replace = true) }
    }

    @Test fun noTransactionsInThePeriodMeansNoForecastNotZero() = runBlocking<Unit> {
        val deps = LoadHomeScreenDeps(MemoryTransactionRepository(), MemoryCategoryRepository(), MemoryAllocationRepository())
        val data = LoadHomeScreen(deps).load(LoadHomeScreenRequest(periodForDate("2026-10-16", 28), "2026-10-16", 28, includeHistory = false))
        assertTrue(data.forecast.elapsedDays >= 5, "past the too-early window")
        assertNull(data.forecast.projectedMinor, "rule 10: a sentence, never «you'll spend about 0.00»")
    }

    @Test fun anAddedBankWithoutOpeningIsUnknownUntilABalanceUpdate() = runBlocking<Unit> {
        val w = manage.add(AddWalletInput("bank", "بنك تجريبي", "4321", null, null))
        assertTrue(w.openingUnknown)
        assertFalse(manage.add(AddWalletInput("cash", "كاش تجريبي", null, null, null)).openingUnknown, "cash unchanged (owner question)")
        assertNull(walletBalancesOn(listOf(w), emptyList(), "2026-10-12")[w.id])
        val checked = w.copy(checkpoints = listOf(BalanceCheckpoint("cp-1", "2026-10-11", null, 77_000, 0L, "2026-10-11T12:00:00.000Z")))
        assertNull(walletBalancesOn(listOf(checked), emptyList(), "2026-10-10")[w.id], "before the update it is still unknown")
        assertEquals(77_000L, walletBalancesOn(listOf(checked), emptyList(), "2026-10-12")[w.id])
        val set = manage.setOpening(w.id, 30_000, "2026-10-10")
        assertFalse(set.openingUnknown)
        assertEquals(30_000L, walletBalancesOn(listOf(set), emptyList(), "2026-10-10")[w.id])
    }
}
