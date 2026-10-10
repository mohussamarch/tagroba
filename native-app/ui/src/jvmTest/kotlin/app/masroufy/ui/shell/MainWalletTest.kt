package app.masroufy.ui.shell

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.MainWalletSource
import app.masroufy.core.Texts
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.ui.text.t
import app.masroufy.usecase.MainSpendingWallets
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * لوحة «+» والمحفظة الأساسية لكل بلد (قرار المالك 2026-10-09 — §78 ٢) فوق المحرك (`MainSpendingWallets` على الحساب): من غير أساسية ⇒ المحفظة
 * فاضية و«بتصرف عادةً منين؟» والحفظ مقفول لحد ما يختار · بعدها الأساسية مختارة لوحدها و«من أين؟» · كل بلد ليها أساسيتها.
 */
class MainWalletTest {
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01")
    private val settings = MemoryUserSettingsStore()
    private val wallets = MemoryWalletRepository(listOf(bank, cash))
    private val clock = FixedClock("2026-10-10T09:00:00.000Z")
    private val saudi = MainSpendingWallets(settings, wallets, "sa", clock)
    private val egypt = MainSpendingWallets(settings, wallets, "eg", clock)

    @AfterTest fun reset() {
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun withoutAMainWalletNothingIsPickedSaveIsLockedAndTheQuestionShows() = runBlocking<Unit> {
        val d = saudi.defaultForAdd()
        assertTrue(d.needsAsk)
        assertNull(initialFromWallet(d.wallet?.id, listOf(bank, cash)), "مش أول بنك زي الأول — فاضية")
        assertFalse(d.canSave(null), "الحفظ مقفول لحد ما يختار")
        assertTrue(d.canSave(cash.id))
        assertEquals("من أين تصرف عادةً؟", d.prompt)
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("بتصرف عادةً منين؟", saudi.defaultForAdd().prompt)
    }

    @Test fun onceSetTheMainWalletIsTheDefaultPerCountry() = runBlocking<Unit> {
        saudi.set(cash.id, MainWalletSource.ADD_SHEET)
        assertEquals(cash.id, initialFromWallet(saudi.defaultForAdd().wallet?.id, listOf(bank, cash)))
        assertEquals(UiKey.ADD_FROM, fromLabelKey(hasMain = true))
        assertTrue(egypt.needsAsk(), "مصر ليها أساسيتها لوحدها")
    }

    @Test fun aMainWalletThatNoLongerExistsIsNotPicked() {
        assertNull(initialFromWallet("w-gone", listOf(bank, cash)))
    }

    @Test fun theSavedToastSaysWhichWalletBecameTheMain() {
        assertEquals("سُجّلت: 42.00 — وصار «الكاش» الأساسي", t(UiKey.ADD_SAVED_MAIN, t(UiKey.ADD_SAVED, "42.00"), "الكاش"))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("اتسجّلت: 42.00 — و«الكاش» بقى الأساسي", t(UiKey.ADD_SAVED_MAIN, t(UiKey.ADD_SAVED, "42.00"), "الكاش"))
    }
}
