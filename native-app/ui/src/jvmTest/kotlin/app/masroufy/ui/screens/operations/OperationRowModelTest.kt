package app.masroufy.ui.screens.operations

import app.masroufy.core.EconomicKind
import app.masroufy.core.UiKey
import app.masroufy.ui.screens.home.transactionTitle
import app.masroufy.ui.text.t
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** r041 A4 — تحويل رسالة بنك بعد «لأ»: العنوان اسم الطرف (مش نص الرسالة) والسطر التاني ما يكررهوش. بيانات مخترعة. */
class OperationRowModelTest {
    private val smsOut = "حوالة داخلية صادرة\nمن:9988\nإلى:TEST FRIEND\nبـSR 500\n26/10/07 09:35"
    private val ctx = RowContext(listOf(Fx.food), wallets = listOf(Fx.bank))

    @Test fun theTitleIsThePartyAndTheSubtitleIsNotARepeat() {
        val tx = Fx.tx("t-sms", kind = EconomicKind.UNCLASSIFIED, category = null, merchant = null, description = smsOut, confirmedKind = false)
        assertEquals("TEST FRIEND", titleOf(tx, ctx))
        assertFalse('\n' in titleOf(tx, ctx))
        assertEquals(t(UiKey.OPERATIONS_ROW_UNCLASSIFIED), subtitleOf(tx, ctx))
        assertEquals("TEST FRIEND", transactionTitle(tx), "the cash card and details agree with the row")
    }

    @Test fun aNamedMerchantKeepsThePartyInTheSubtitle() {
        val tx = Fx.tx("t-named", kind = EconomicKind.UNCLASSIFIED, category = null, merchant = "متجر تجربة", description = smsOut)
        assertEquals("متجر تجربة", titleOf(tx, ctx))
        assertEquals(t(UiKey.OPERATIONS_ROW_TO, "TEST FRIEND"), subtitleOf(tx, ctx))
    }
}
