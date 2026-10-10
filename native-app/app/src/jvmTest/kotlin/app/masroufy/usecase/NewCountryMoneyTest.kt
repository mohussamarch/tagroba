package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.EGYPT_PACK
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * كارتين الفلوس لمصر لما الحساب الجديد يختارها في أول تشغيل (§81 = رحلة الحساب الجديد لأي بلد — جولة 0.3): الكاش والبنك بيتكتبوا في
 * محافظ مصر بالجنيه ويوم القاهرة، كل حاجة بتتأكد قبل الكتابة، والإعادة ما بتكررش. أرقام مخترعة.
 */
class NewCountryMoneyTest {
    // 23:30 بالقاهرة في الشتا = 00:30 بالرياض — يوم مصر لسه 15
    private val clock = FixedClock("2026-01-15T21:30:00.000Z")
    private val wallets = MemoryWalletRepository()
    private val money = NewCountryMoney(wallets, ManageWallets(wallets, SequentialIdGenerator(), clock, Currency.EGP, EGYPT_PACK.timeZone), EGYPT_PACK)
    private val nbe = AddWalletInput("bank", "البنك الأهلي المصري", "0042", 1_250_000, null, "eg-national-bank-of-egypt")

    @Test fun cashAndBankLandInEgyptWithItsCurrencyAndDay() = runBlocking<Unit> {
        assertIs<OnboardingResult.Ok>(money.apply(300_000, nbe))
        val all = wallets.listAll()
        val cash = all.single { it.kind == "cash" }
        assertEquals(CASH_WALLET_ID, cash.id)
        assertEquals(EGYPT_PACK.cashWalletName, cash.name)
        assertEquals(Currency.EGP, cash.currency)
        assertEquals(300_000, cash.openingBalanceMinor)
        assertEquals("2026-01-15", cash.openingAt, "يوم القاهرة مش الرياض ولا جرينتش")
        val bank = all.single { it.kind == "bank" }
        assertEquals("eg-national-bank-of-egypt", bank.bankId)
        assertEquals(Currency.EGP, bank.currency)
        assertEquals("0042", bank.accountLast4)
        assertEquals("2026-01-15", bank.openingAt)
    }

    @Test fun skippingBothStillGivesAZeroCashWallet() = runBlocking<Unit> {
        assertIs<OnboardingResult.Ok>(money.apply(null, null))
        assertEquals(listOf(0L), wallets.listAll().map { it.openingBalanceMinor }, "زي البلد الأساسية: محفظة كاش من صفر")
    }

    @Test fun aRetryDoesNotDuplicate() = runBlocking<Unit> {
        money.apply(300_000, nbe)
        money.apply(300_000, nbe)
        assertEquals(2, wallets.listAll().size, "كاش واحد وبنك واحد")
        money.apply(450_000, null)
        assertEquals(450_000, wallets.findById(CASH_WALLET_ID)!!.openingBalanceMinor, "تصحيح الكاش بيعدّل نفس المحفظة")
    }

    @Test fun nothingIsWrittenWhenSomethingIsWrong() = runBlocking<Unit> {
        assertIs<OnboardingResult.Failed>(money.apply(-1, nbe))
        val bad = money.apply(300_000, nbe.copy(last4 = "12"))
        assertTrue(bad is OnboardingResult.Failed && bad.step == "bank")
        assertTrue(wallets.listAll().isEmpty(), "ولا محفظة اتكتبت")
    }
}
