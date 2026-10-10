package app.masroufy.usecase

import app.masroufy.core.AlertCandidate
import app.masroufy.core.AlertKind
import app.masroufy.core.LocalMoment
import app.masroufy.memory.MemoryAlertDismissalStore
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryUsualHours
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «×» على إشعار (رد المالك ٣ · §79.2-1): بيتمسح على الحساب بدرجته، والمحرك ما بيرجّعوش بنفس الدرجة — **بيرجع بالدرجة الجديدة بس** (من «قرّبت
 * على السقف» لـ«عدّيت السقف»)، وكارته في «أمور لم تُنجزها بعد» بيرجع معاه. بيانات مخترعة.
 */
class AssistantNotificationsTest {
    private val w = AssistantWorld()
    private val inbox = MemoryAlertInbox()
    private val dismissals = MemoryAlertDismissalStore()
    private val engine = RunAlertEngine(
        AlertEngineDeps(MemoryAlertSettings(), MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), inbox, w.clock, dismissals),
    )
    private val manage = ManageAlertDismissals(dismissals, inbox, w.clock)

    @Test fun aDeletedNotificationComesBackOnlyAtANewLevel() = runBlocking<Unit> {
        val near = AlertCandidate(AlertKind.BUDGET_THRESHOLD, "budget|c-coffee", "قرّبت على سقف القهوة", "تفاصيل")
        engine.run(listOf(near), LocalMoment("2026-10-10", 14))
        manage.dismiss("budget|c-coffee")
        assertEquals(near.eventKey, dismissals.listAll().single().eventKey, "الدرجة اللي اتمسحت متخزنة")

        engine.run(listOf(near), LocalMoment("2026-10-10", 18))
        assertTrue(inbox.listAll().isEmpty(), "نفس الدرجة ⇒ ما بيرجعش")

        val over = AlertCandidate(AlertKind.BUDGET_EXCEEDED, "budget|c-coffee", "عدّيت سقف القهوة", "تفاصيل")
        engine.run(listOf(over), LocalMoment("2026-10-11", 10))
        assertEquals(listOf(over.eventKey), inbox.listAll().map { it.eventKey }, "صعّد ⇒ رجع بالدرجة الجديدة")
        assertTrue(dismissals.listAll().isEmpty(), "العلامة القديمة اتشالت")
    }

    @Test fun deletingTheBellNoticeHidesItsStartCardAndTheTabDot() = runBlocking<Unit> {
        val deps = w.deps.copy(stores = w.stores.copy(alertDismissals = dismissals))
        val chat = AssistantChat(deps)
        val start = chat.open(w.ctx()).start!!
        val bill = start.items.first { it.alertThreadKey != null }
        inbox.save(
            app.masroufy.port.AlertInboxEntry(
                bill.alertThreadKey!!, bill.alertThreadKey + "|due_overdue", AlertKind.DUE_OVERDUE, app.masroufy.core.DueFlow.PAY, "فاتورة", "تفاصيل",
                app.masroufy.core.AlertDecision(AlertKind.DUE_OVERDUE, app.masroufy.core.AlertDelivery.INBOX_ONLY, null, false, emptyList()), w.clock.nowIso(),
            ),
        )
        assertTrue(manage.hasUnread())
        val gone = manage.dismiss(bill.alertThreadKey!!)
        assertTrue(!manage.hasUnread(), "النقطة الحمرا راحت")
        assertTrue(chat.open(w.ctx()).start!!.items.none { it.key == bill.key }, "كارت البداية اختفى معاه")

        manage.undo(gone)
        assertTrue(chat.open(w.ctx()).start!!.items.any { it.key == bill.key }, "«تراجع» ⇒ رجع")
    }
}
