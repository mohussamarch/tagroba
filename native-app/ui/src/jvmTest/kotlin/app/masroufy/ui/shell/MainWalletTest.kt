package app.masroufy.ui.shell

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Currency
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.Wallet
import app.masroufy.ui.text.t
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * لوحة «+» والمحفظة الأساسية لكل بلد (قرار المالك 2026-10-09): من غير أساسية ⇒ المحفظة فاضية و«من أين تصرف عادةً؟» (والحفظ مقفول لحد ما يختار
 * — `ready` بيطلب محفظة) · بعدها الأساسية مختارة لوحدها و«من أين؟» · كل بلد ليها أساسيتها.
 */
class MainWalletTest {
    private val bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")
    private val cash = Wallet("w-cash", "الكاش", Currency.SAR, "cash", 0, "2026-01-01")

    @AfterTest fun reset() {
        MainWalletChoice.clear()
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test fun withoutAMainWalletNothingIsPickedAndTheQuestionShows() {
        assertNull(initialFromWallet(MainWalletChoice.of("sa"), listOf(bank, cash)), "مش أول بنك زي الأول — فاضية")
        assertEquals("من أين تصرف عادةً؟", t(fromLabelKey(hasMain = false)))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("بتصرف عادةً منين؟", t(fromLabelKey(hasMain = false)))
    }

    @Test fun onceSetTheMainWalletIsTheDefaultPerCountry() {
        MainWalletChoice.set("sa", cash.id)
        assertEquals(cash.id, initialFromWallet(MainWalletChoice.of("sa"), listOf(bank, cash)))
        assertEquals(TextKey.ADD_FROM, fromLabelKey(hasMain = true))
        assertNull(initialFromWallet(MainWalletChoice.of("eg"), listOf(bank, cash)), "مصر ليها أساسيتها لوحدها")
    }

    @Test fun aMainWalletThatNoLongerExistsIsNotPicked() {
        MainWalletChoice.set("sa", "w-gone")
        assertNull(initialFromWallet(MainWalletChoice.of("sa"), listOf(bank, cash)))
    }

    @Test fun theSavedToastSaysWhichWalletBecameTheMain() {
        assertEquals("سُجّلت: 42.00 — وصار «الكاش» الأساسي", t(TextKey.ADD_SAVED_MAIN, t(TextKey.ADD_SAVED, "42.00"), "الكاش"))
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        assertEquals("اتسجّلت: 42.00 — و«الكاش» بقى الأساسي", t(TextKey.ADD_SAVED_MAIN, t(TextKey.ADD_SAVED, "42.00"), "الكاش"))
    }
}
