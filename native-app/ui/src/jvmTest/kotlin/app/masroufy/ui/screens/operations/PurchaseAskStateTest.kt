package app.masroufy.ui.screens.operations

import app.masroufy.core.Category
import app.masroufy.core.EconomicKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** «إيه الصنف؟» بعد «شراء» (2026-10-16): بتتفتح للشراء بس · الاختيار بيكتب على كل العمليات · «تخطّي» ما بيكتبش حاجة. */
class PurchaseAskStateTest {
    private val food = Category("c-food", null, "أكل تجريبي", "tag", "#ABCDEF", "#123456", true, 1)

    @Test fun onlyPurchaseOpensTheSheet() {
        assertNull(purchaseAskAfter(EconomicKind.FEE, listOf("t-1")))
        assertNull(purchaseAskAfter(EconomicKind.SUPPORT_GIFT, listOf("t-1")))
        assertNull(purchaseAskAfter(EconomicKind.PURCHASE, emptyList()), "مفيش عمليات ⇒ مفيش لوحة")
        assertEquals(PurchaseAsk(listOf("t-1", "t-2")), purchaseAskAfter(EconomicKind.PURCHASE, listOf("t-1", "t-2", "t-1")))
    }

    @Test fun pickWritesOnAllAndSkipWritesNothing() {
        val ask = purchaseAskAfter(EconomicKind.PURCHASE, listOf("t-1", "t-2"))!!
        assertEquals(PurchaseSave(listOf("t-1", "t-2"), "c-food"), ask.pick(food))
        assertNull(ask.skip(), "تخطّي ⇒ العملية بتفضل من غير تصنيف")
    }
}
