package app.masroufy.ui.screens.imports

import app.masroufy.core.SmsParseResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * r05 (4b — شكوى المالك: «الرسايل اللي بانتظار تأكيدي دايمًا 200»): الصندوق على الجهاز بيعرض أحدث 200، فالعدد كان عدّ الصفحة. دلوقتي العدد
 * = كل اللي في الصندوق ([BankSmsUi.trueWaiting]) و«معروض 200 من 450» بيبان مع «عرض الأقدم». كل البيانات مخترعة.
 */
class BankSmsWaitingCountTest {
    @AfterTest fun reset() = Fx.resetTexts()

    @Test fun theCountIsTheWholeInboxNotThePage() {
        val items = (1..3).map { Fx.item("f$it", "BANK-A", SmsParseResult.Rejected("لا مبلغ فيها")) }
        val messages = (1..3).map { Fx.queued("f$it", "BANK-A", "رسالة مخترعة") }
        val page = Fx.inbox(items = items, messages = messages).copy(count = 450)
        val ui = bankSmsUi(SmsOverview(page, emptyList(), emptyList(), senderWallets = emptyMap()), emptyList(), emptySet())
        assertEquals(3, ui.waitingCount, "the page")
        assertEquals(450 to 3, ui.total to ui.pageSize)
        assertEquals(450, ui.trueWaiting)
    }

    @Test fun neverLessThanWhatIsShown() {
        val items = (1..2).map { Fx.item("f$it", "BANK-A", SmsParseResult.Rejected("لا مبلغ فيها")) }
        val ui = bankSmsUi(SmsOverview(Fx.inbox(items = items).copy(count = 0), emptyList(), emptyList(), senderWallets = emptyMap()), emptyList(), emptySet())
        assertEquals(2, ui.trueWaiting)
    }
}
