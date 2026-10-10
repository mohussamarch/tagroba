package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** «أضف محفظة» (الحساب الجديد بيوصل هنا بعد أسئلة البداية): التأكد قبل الكتابة، وآخر 4 أرقام بس. أسماء وأرقام مخترعة. */
class ManageWalletsTest {
    private val repo = MemoryWalletRepository(listOf(Wallet("wallet-cash", "كاش", Currency.SAR, "cash", 0, "2026-10-10")))
    private val manage = ManageWallets(repo, SequentialIdGenerator(), FixedClock("2026-10-10T09:00:00.000Z"), Currency.SAR)

    @Test fun addsABankWalletWithItsOpeningAndLastFourOnly() = runBlocking<Unit> {
        val w = manage.add(AddWalletInput("bank", "  بنك   وهمي ", "٤٢١٧", 125_050, "2026-10-01"))
        assertEquals("بنك وهمي", w.name)
        assertEquals("4217", w.accountLast4, "الأرقام العربية بتتحول، وآخر 4 بس")
        assertEquals(125_050, w.openingBalanceMinor)
        assertEquals(Currency.SAR, w.currency)
        assertEquals(w, repo.findById(w.id))
    }

    @Test fun noAmountMeansZeroFromToday() = runBlocking<Unit> {
        val w = manage.add(AddWalletInput("digital_wallet", "محفظة رقمية", null, null, "2026-01-01"))
        assertEquals(0, w.openingBalanceMinor)
        assertEquals("2026-10-10", w.openingAt)
        assertNull(manage.add(AddWalletInput("cash", "كاش السفر", "1234", null, null)).accountLast4, "الكاش من غير أرقام حساب")
    }

    @Test fun refusesBeforeWriting() = runBlocking<Unit> {
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "  ", null, null, null)) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("cash", "كاش", null, null, null)) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك", "123", null, null)) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك", null, -1, "2026-10-01")) }
        assertFailsWith<WalletError> { manage.add(AddWalletInput("bank", "بنك", null, 100, "2026-10-11")) }
        assertEquals(1, repo.listAll().size, "ولا محفظة اتكتبت")
    }

    @Test fun openingChangesOnlyThatWallet() = runBlocking<Unit> {
        val updated = manage.setOpening("wallet-cash", 100_000, "2026-09-06")
        assertEquals(100_000, updated.openingBalanceMinor)
        assertEquals("2026-09-06", repo.findById("wallet-cash")!!.openingAt)
        assertFailsWith<WalletError> { manage.setOpening("wallet-cash", 5, "2027-01-01") }
    }
}
