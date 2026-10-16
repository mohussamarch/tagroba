package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.port.SMS_HANDOFF_DONE
import app.masroufy.port.SMS_HANDOFF_PENDING_CARD
import app.masroufy.ui.nav.Route
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r041 A6 + A8 + A10 — ترتيب التسليم بعد أول تشغيل: الشرح ⇒ نافذة الإذن ⇒ «نقرأ اللي فات؟» ⇒ بعدها بس لوحة الشكل · الكارت بيرجع كل
 * تشغيل لحد «اسمح» أو «لاحقًا» · كلام الرئيسية الفاضية من حالة القراءة.
 */
class SmsHandoffOrderTest {
    private val pending = HandoffState(pending = true)

    @AfterTest fun reset() {
        SmsHandoff.done()
        SmsHandoff.next = null
    }

    @Test fun theLookWaitsForTheExplanationTheDialogAndTheHistoryQuestion() {
        assertFalse(lookMayOpen(pending, granted = false, readable = true), "pending + not asked ⇒ the explanation first")
        assertFalse(lookMayOpen(pending.copy(requesting = true), granted = false, readable = true), "the system dialog is up")
        assertFalse(lookMayOpen(pending.copy(asked = true, requesting = true), granted = false, readable = true))
        assertFalse(lookMayOpen(pending, granted = true, readable = true), "allowed ⇒ connecting, the history question may follow")
        assertFalse(lookMayOpen(pending.copy(history = true), granted = true, readable = true), "the history question")
        assertTrue(lookMayOpen(pending.copy(asked = true), granted = false, readable = true), "closed or denied ⇒ the card, the look may open")
        assertTrue(lookMayOpen(HandoffState(), granted = false, readable = true), "nothing pending")
        assertTrue(lookMayOpen(pending, granted = false, readable = false), "a phone that cannot read SMS")
    }

    @Test fun theHistoryPhaseIsItsOwnView() {
        assertEquals(HandoffView.HISTORY, handoffView(pending.copy(history = true), granted = true, readable = true))
        assertEquals(HandoffView.EXPLAIN, handoffView(pending.copy(requesting = true), granted = false, readable = true), "the explanation stays under the dialog")
        assertTrue(handoffNeedsConnect(pending, granted = true, readable = true))
        assertFalse(handoffNeedsConnect(pending.copy(history = true), granted = true, readable = true), "connect once")
        assertFalse(handoffNeedsConnect(pending, granted = false, readable = true))
    }

    @Test fun theHolderQueuesTheNextScreenOnlyWhileAHandoffRuns() {
        val pushed = mutableListOf<Route>()
        val route = object : Route { override val name = "test-look" }
        SmsHandoff.openAfter(route) { pushed += it }
        assertEquals(listOf<Route>(route), pushed, "no handoff ⇒ open now")
        SmsHandoff.start()
        SmsHandoff.openAfter(route) { pushed += it }
        assertEquals(1, pushed.size)
        assertEquals(route, SmsHandoff.next, "a handoff runs ⇒ the host opens it later")
        SmsHandoff.requesting(true)
        assertTrue(SmsHandoff.state.requesting)
        SmsHandoff.history()
        assertEquals(HandoffState(pending = true, history = true), SmsHandoff.state)
    }

    @Test fun theCardComesBackOnEveryStartUntilAllowOrLater() {
        assertEquals(HandoffView.CARD, handoffView(restoredHandoff(SMS_HANDOFF_PENDING_CARD), granted = false, readable = true), "the card, not the explanation again")
        val allowedMeanwhile = restoredHandoff(SMS_HANDOFF_PENDING_CARD)
        assertTrue(handoffNeedsConnect(allowedMeanwhile, granted = true, readable = true), "allowed from the settings ⇒ connect once")
        assertEquals(HandoffState(), restoredHandoff(SMS_HANDOFF_DONE))
        assertEquals(HandoffState(), restoredHandoff(null))
        assertEquals(SMS_HANDOFF_PENDING_CARD, persistedHandoff(pending))
        assertEquals(SMS_HANDOFF_PENDING_CARD, persistedHandoff(pending.copy(asked = true)))
        assertEquals(SMS_HANDOFF_DONE, persistedHandoff(pending.copy(history = true)), "connected ⇒ done")
        assertEquals(SMS_HANDOFF_DONE, persistedHandoff(HandoffState()), "later or connected")
    }

    @Test fun theDeviceKeepsOneValue() = runBlocking {
        val inbox = MemorySmsInbox(emptyList(), available = true)
        assertNull(inbox.handoffCard())
        inbox.setHandoffCard(SMS_HANDOFF_PENDING_CARD)
        assertEquals(SMS_HANDOFF_PENDING_CARD, inbox.handoffCard())
        inbox.setHandoffCard(null)
        assertNull(inbox.handoffCard())
    }

    @Test fun theEmptyHomeTextFollowsTheReadingState() {
        assertEquals(UiKey.HOME_EMPTY_BODY_READING, homeEmptyBodyKey(readable = true, readingOn = true))
        assertEquals(UiKey.HOME_EMPTY_BODY, homeEmptyBodyKey(readable = true, readingOn = false))
        assertEquals(UiKey.HOME_EMPTY_BODY_NO_SMS, homeEmptyBodyKey(readable = false, readingOn = false))
    }
}
