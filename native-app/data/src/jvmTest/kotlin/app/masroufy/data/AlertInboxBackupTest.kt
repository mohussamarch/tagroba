package app.masroufy.data

import app.masroufy.core.ALERT_INBOX_GROUP
import app.masroufy.core.ALERT_RECEIPTS_GROUP
import app.masroufy.core.ALERT_SETTINGS_GROUP
import app.masroufy.core.AlertDecision
import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertFactor
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.AlertKind
import app.masroufy.core.BackupRow
import app.masroufy.core.DueFlow
import app.masroufy.core.LocalMoment
import app.masroufy.core.Person
import app.masroufy.core.emptyBackupData
import app.masroufy.core.isRestorableInboxRow
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.port.AlertInboxEntry
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * صفحة الإشعارات في النسخة الشاملة (اختيار المالك §69 «وصفحة الإشعارات كمان»): بتسافر **بقرايتها**، والإيصالات لأ.
 * من غير سطور ⇒ الملف هو هو زي الأول. سطر بنوع مش معروف (من نسخة أحدث) ⇒ بيتخطّى والباقي بيترجع. نصوص مخترعة.
 */
class AlertInboxBackupTest {
    private val at = "2026-10-05T12:00:00.000Z"
    private val opened = AlertInboxEntry(
        "eg:due|installment|ip/1|2026-10-10|pay", "eg:due|installment|ip/1|2026-10-10|pay|due_soon", AlertKind.DUE_SOON, DueFlow.PAY,
        "قسط وهمي قرّب", "100.00 ج.م يوم 2026-10-10",
        AlertDecision(AlertKind.DUE_SOON, AlertDelivery.AT_USUAL_TIME, LocalMoment("2026-10-08", 21), false, listOf(AlertFactor.URGENT)),
        "2026-10-07T10:00:00.000Z", openedAt = "2026-10-07T11:00:00.000Z", spaceLabel = "مصر",
    )
    private val unread = opened.copy(threadKey = "budget|2026-09|total", eventKey = "budget|2026-09|total|80", kind = AlertKind.BUDGET_THRESHOLD,
        title = "ميزانية وهمية", body = "صرفت 80%", openedAt = null, spaceLabel = null,
        decision = AlertDecision(AlertKind.BUDGET_THRESHOLD, AlertDelivery.DIGEST, null, false, emptyList()))

    private fun account(inbox: List<BackupRow> = emptyList(), settings: Boolean = false) = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص وهمي"))
        if (settings) it.getValue(ALERT_SETTINGS_GROUP) += AlertCodecs.alertSettings.toStore(AlertGroupSetting(AlertGroup.BUDGET, false))
        it.getValue(ALERT_INBOX_GROUP) += inbox
    }

    @Test
    fun `الصفحة بقرايتها بتسافر وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(listOf(AlertCodecs.alertInbox.toStore(opened), AlertCodecs.alertInbox.toStore(unread)), settings = true)
        val file = FullBackup(MemoryFullBackup(source)).create(at)
        val text = file.toJsonText()
        assertTrue("\"$ALERT_INBOX_GROUP\"" in text)
        assertFalse("\"$ALERT_RECEIPTS_GROUP\"" in text, "الإيصالات لسه برا النسخة")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        assertEquals(2, restore.apply(restore.plan(text).file).added[ALERT_INBOX_GROUP])
        val back = target.read().getValue(ALERT_INBOX_GROUP).map { AlertCodecs.alertInbox.decode(it) }.sortedBy { it.threadKey }
        assertEquals(listOf(unread, opened), back, "القراية (openedAt) والسبب واسم البلد رجعوا زي ما هم — والموضوع اللي فيه «/» كمان")
        assertEquals(file.checksum, FullBackup(target).create(at).checksum)
        assertEquals(0, restore.apply(restore.plan(text).file).added[ALERT_INBOX_GROUP] ?: 0, "الاسترجاع التاني ما بيكررش")
    }

    @Test
    fun `من غير صفحة الملف هو هو والنسخة القديمة بترجع عادي`() = runBlocking<Unit> {
        val oldText = FullBackup(MemoryFullBackup(account(settings = true))).create(at).toJsonText()
        assertFalse(ALERT_INBOX_GROUP in oldText, "مفيش سطور ⇒ ولا المجموعة ولا عدّها في الملف (الإصدار 2 زي ما هو)")
        // نسخة قديمة (من غير المجموعة خالص) على جهاز عنده صفحة ⇒ بترجع، والصفحة الموجودة ما اتلمستش
        val target = MemoryFullBackup(account(listOf(AlertCodecs.alertInbox.toStore(opened))))
        val restore = FullBackup(target)
        val plan = restore.plan(oldText)
        assertEquals(0, plan.lines.single { it.key == ALERT_INBOX_GROUP }.incoming)
        val out = restore.apply(plan.file)
        assertEquals(0, out.added[ALERT_INBOX_GROUP])
        assertEquals(1, out.added[ALERT_SETTINGS_GROUP])
        assertEquals(listOf(opened), target.read().getValue(ALERT_INBOX_GROUP).map { AlertCodecs.alertInbox.decode(it) })
    }

    @Test
    fun `سطر بنوع مش معروف بيتخطى والباقي بيرجع`() = runBlocking<Unit> {
        val newer = AlertCodecs.alertInbox.toStore(unread.copy(threadKey = "future|1", eventKey = "future|1|x")) + ("kind" to "card_from_newer_app")
        val text = FullBackup(MemoryFullBackup(account(listOf(AlertCodecs.alertInbox.toStore(opened), newer)))).create(at).toJsonText()
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        val plan = restore.plan(text)
        val line = plan.lines.single { it.key == ALERT_INBOX_GROUP }
        assertEquals(2 to 1, line.incoming to line.skipped, "اتنين في الملف وواحد اتخطّى")
        assertEquals(1, restore.apply(plan.file).added[ALERT_INBOX_GROUP])
        assertEquals(listOf(opened), target.read().getValue(ALERT_INBOX_GROUP).map { AlertCodecs.alertInbox.decode(it) })
    }

    /** فحص الاسترجاع في core لازم يوافق قارئ السطر (`skippingUnreadable`) — اللي بيرجع بيتقري، واللي ما بيتقريش ما بيرجعش. */
    @Test
    fun `فحص الاسترجاع موافق لقارئ السطر`() {
        val good = AlertCodecs.alertInbox.toStore(opened)
        val variants: List<BackupRow> = listOf(
            good, AlertCodecs.alertInbox.toStore(unread),
            good + ("kind" to "brand_new"), good + ("flow" to "sideways"), good + ("delivery" to "by_pigeon"),
            good + ("factors" to listOf("urgent", "brand_new_factor")), good + ("deliverAtHour" to 24L), good + ("deliverAtHour" to -1L),
        )
        for (row in variants) {
            val readable = AlertCodecs.alertInbox.skippingUnreadable().decode(row) != null
            assertEquals(readable, isRestorableInboxRow(row), row.toString())
        }
        assertEquals(listOf(true, true, false, false, false, false, false, false), variants.map { isRestorableInboxRow(it) })
    }
}
