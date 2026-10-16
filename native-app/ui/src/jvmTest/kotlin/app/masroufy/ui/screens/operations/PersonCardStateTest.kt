package app.masroufy.ui.screens.operations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** مراجعة r05 الجولة التانية (N6 · N7) — كارت «شخص» في تفاصيل العملية من غير رسم. */
class PersonCardStateTest {
    @Test fun n7APurchaseAlreadyLinkedStillOpensTheSplitSheetForAnotherPerson() {
        val linkedPurchase = personCardState(locked = false, purchase = true, meaningFlow = true, taken = true)
        assertFalse(linkedPurchase.blocked, "«حد تاني» ما بيتقفلش على الشراء")
        assertTrue(linkedPurchase.purchaseMore, "بيفتح لوحة التقسيم (نصيب لكل شخص)")
        val linkedTransfer = personCardState(locked = false, purchase = false, meaningFlow = true, taken = true)
        assertTrue(linkedTransfer.blocked, "التحويل رابط واحد بس")
        assertFalse(linkedTransfer.purchaseMore)
        assertTrue(personCardState(locked = true, purchase = true, meaningFlow = true, taken = true).blocked, "المقفولة مقفولة")
        assertEquals(PersonCardState(blocked = false, purchaseMore = false), personCardState(locked = false, purchase = true, meaningFlow = true, taken = false))
    }

    @Test fun n6TheMenuActionOpensTheSheetOnceTheStatusIsLoaded() {
        val fresh = personCardState(locked = false, purchase = true, meaningFlow = true, taken = false)
        assertEquals(InitialPersonAction.NONE, initialPersonAction(wanted = true, meaningFlow = true, statusLoaded = false, card = fresh), "لسه ما اتقريش")
        assertEquals(InitialPersonAction.ASK, initialPersonAction(wanted = true, meaningFlow = true, statusLoaded = true, card = fresh), "«مع مين؟» بتتفتح لوحدها")
        val more = personCardState(locked = false, purchase = true, meaningFlow = true, taken = true)
        assertEquals(InitialPersonAction.SPLIT, initialPersonAction(wanted = true, meaningFlow = true, statusLoaded = true, card = more))
        assertEquals(InitialPersonAction.NONE, initialPersonAction(wanted = false, meaningFlow = true, statusLoaded = true, card = fresh), "من غير طلب ما بيفتحش")
        val locked = personCardState(locked = true, purchase = true, meaningFlow = true, taken = false)
        assertEquals(InitialPersonAction.NONE, initialPersonAction(wanted = true, meaningFlow = true, statusLoaded = true, card = locked))
    }
}
