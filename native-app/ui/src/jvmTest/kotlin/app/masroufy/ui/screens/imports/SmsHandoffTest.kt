package app.masroufy.ui.screens.imports

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * تسليم «رسائل البنك» بعد أول تشغيل (قرار المالك 2026-10-15 مساءً — OVERRIDES §91): الشرح ثم الإذن، والكارت الهادي لو اترفض ·
 * «أوقفتَ القراءة» بس لما المالك وقّفها بإيده — غير كده سطر محايد يطلب الإذن.
 */
class SmsHandoffTest {
    private fun overview(enabled: Boolean, permission: Boolean, senders: List<String>) =
        SmsOverview(Fx.inbox(enabled = enabled, permission = permission, senders = senders), emptyList(), emptyList(), emptyMap())

    @Test fun explanationFirstThenTheGentleCardOnlyWhilePending() {
        val pending = HandoffState(pending = true)
        assertEquals(HandoffView.EXPLAIN, handoffView(pending, granted = false, readable = true), "أول ما الرحلة تخلص: الشرح")
        assertEquals(HandoffView.CARD, handoffView(pending.copy(asked = true), granted = false, readable = true), "اترفض أو اتقفل ⇒ الكارت")
        assertEquals(HandoffView.NONE, handoffView(pending, granted = true, readable = true), "الإذن موجود ⇒ التسجيل على طول من غير شرح")
        assertEquals(HandoffView.NONE, handoffView(pending, granted = false, readable = false), "الآيفون ⇒ ولا حاجة")
        assertEquals(HandoffView.NONE, handoffView(HandoffState(), granted = false, readable = true), "ما اختارش رسائل البنك ⇒ ولا حاجة")
    }

    @Test fun theSessionHolderMovesThroughTheSteps() {
        SmsHandoff.start()
        assertEquals(HandoffState(pending = true), SmsHandoff.state)
        SmsHandoff.asked()
        assertEquals(HandoffState(pending = true, asked = true), SmsHandoff.state)
        SmsHandoff.askAgain()
        assertEquals(HandoffState(pending = true, asked = false), SmsHandoff.state)
        SmsHandoff.done()
        assertEquals(HandoffState(), SmsHandoff.state)
    }

    @Test fun permissionLineShowsTheRealSystemState() {
        assertEquals(app.masroufy.core.UiKey.SMS_SETTINGS_PERM_LINE_ON, permissionLine(granted = true))
        assertEquals(app.masroufy.core.UiKey.SMS_SETTINGS_PERM_LINE_OFF, permissionLine(granted = false), "«ممنوح» عمره ما يظهر والإذن مرفوض")
    }

    @Test fun stoppedOnlyWhenTheOwnerReallyTurnedReadingOff() {
        assertEquals(SmsStatus.SETUP, bankSmsUi(overview(false, false, emptyList()), emptyList(), emptySet()).status, "عمرها ما اشتغلت ⇒ سطر محايد")
        assertEquals(SmsStatus.SETUP, bankSmsUi(overview(false, true, emptyList()), emptyList(), emptySet()).status)
        assertEquals(SmsStatus.OFF, bankSmsUi(overview(false, true, listOf("BANK-A")), emptyList(), emptySet()).status, "وقّفها بإيده ⇒ «أوقفتَ»")
        assertEquals(SmsStatus.PERMISSION, bankSmsUi(overview(true, false, listOf("BANK-A")), emptyList(), emptySet()).status)
        assertEquals(SmsStatus.READING, bankSmsUi(overview(true, true, listOf("BANK-A")), emptyList(), emptySet()).status)
    }
}
