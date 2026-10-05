package app.masroufy.data

import app.masroufy.core.ACCOUNT_DATA_GROUPS
import app.masroufy.core.ACCOUNT_GROUPS
import app.masroufy.core.ALERT_INBOX_GROUP
import app.masroufy.core.ALERT_RECEIPTS_GROUP
import app.masroufy.core.ALERT_SETTINGS_GROUP
import app.masroufy.core.AlertDecision
import app.masroufy.core.AlertDelivery
import app.masroufy.core.AlertFactor
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.AlertKind
import app.masroufy.core.BACKUP_GROUPS
import app.masroufy.core.DueFlow
import app.masroufy.core.LocalMoment
import app.masroufy.core.NotificationReceipt
import app.masroufy.core.SPACE_GROUPS
import app.masroufy.port.AlertInboxEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** تخزين محرك التنبيهات (OVERRIDES §61 — جلسة 18): المحوّلات ومكان كل مجموعة. بيشتغل على محاكي الآيفون كمان. كل النصوص مخترعة. */
class AlertCodecsTest {
    private val entry = AlertInboxEntry(
        "eg:due|installment|ip/1|2026-10-10|pay", "eg:due|installment|ip/1|2026-10-10|pay|due_soon", AlertKind.DUE_SOON, DueFlow.RECEIVE,
        "قسط وهمي قرّب", "100.00 ج.م يوم 2026-10-10",
        AlertDecision(AlertKind.DUE_SOON, AlertDelivery.AT_USUAL_TIME, LocalMoment("2026-10-08", 21), false, listOf(AlertFactor.URGENT, AlertFactor.WAIT_FOR_USUAL_HOUR)),
        "2026-10-07T10:00:00.000Z", openedAt = "2026-10-07T11:00:00.000Z", spaceLabel = "مصر",
    )

    private fun <T> roundTrip(codec: DocCodec<T>, value: T): Doc {
        val d = codec.toStore(value)
        assertEquals(value, codec.decode(d), codec.group)
        return d
    }

    @Test
    fun inboxLineRoundTripsWithItsReasonAndEncodedId() {
        val d = roundTrip(AlertCodecs.alertInbox, entry)
        assertEquals(listOf("urgent", "wait_for_usual_hour"), d["factors"])
        assertEquals("at_usual_time", d["delivery"])
        assertEquals(21L, d["deliverAtHour"])
        val id = AlertCodecs.alertInbox.id(entry)
        assertFalse('/' in id, "المعرّف ما فيهوش «/» — مستند واحد مش مسار")
        assertEquals(receiptDocId(entry.threadKey), id, "معرّف السطر = الموضوع ⇒ سطر واحد للموضوع على كل الأجهزة")
        // الاختياري الفاضي ما بيتكتبش، والـmerge بيمسحه صريح
        val bare = entry.copy(openedAt = null, spaceLabel = null, decision = entry.decision.copy(deliverAt = null, factors = emptyList()))
        val stored = roundTrip(AlertCodecs.alertInbox, bare)
        for (f in listOf("openedAt", "spaceLabel", "deliverAtDate", "deliverAtHour")) assertFalse(f in stored, f)
        assertEquals(setOf("openedAt", "spaceLabel", "deliverAtDate", "deliverAtHour"), AlertCodecs.alertInbox.omittedFields(bare))
        // رقم حساب كامل في النص ما يوصلش فايربيز (CLAUDE.md #11) — والمفاتيح زي ما هي
        val withAccount = entry.copy(title = "تحويل لحساب 1234567890123", body = "من حساب ٩٨٧٦٥٤٣٢١")
        val safe = AlertCodecs.alertInbox.toStore(withAccount)
        assertEquals("تحويل لحساب ****0123" to "من حساب ****٤٣٢١", safe["title"] to safe["body"])
        assertEquals(entry.threadKey, safe["threadKey"])
    }

    @Test
    fun receiptAndSettingRoundTrip() {
        val r = NotificationReceipt("due|x|y|due_today", null, "2026-10-10", "2026-10-10T06:00:00.000Z")
        assertEquals(setOf("eventKey", "periodStart", "sentAt"), roundTrip(AlertCodecs.alertReceipts, r).keys)
        assertEquals(receiptDocId(r.eventKey), AlertCodecs.alertReceipts.id(r))
        val off = AlertGroupSetting(AlertGroup.ZAKAT, enabled = false)
        assertEquals(mapOf("group" to "zakat", "enabled" to false), roundTrip(AlertCodecs.alertSettings, off).toMap())
        assertEquals("zakat", AlertCodecs.alertSettings.id(off), "مستند لكل مجموعة ⇒ الجهازين بيكتبوا نفس المستند")
        assertEquals(true, AlertCodecs.alertSettings.decode(AlertCodecs.alertSettings.toStore(off.copy(enabled = true))).enabled)
    }

    @Test
    fun unreadableRowsAreSkippedNotFatal() {
        val newer = AlertCodecs.alertInbox.toStore(entry) + ("kind" to "advisor_card_from_newer_app")
        assertTrue("advisor_card_from_newer_app" in assertFailsWith<DocumentError> { AlertCodecs.alertInbox.decode(newer) }.message!!)
        assertNull(AlertCodecs.alertInbox.skippingUnreadable().decode(newer), "نسخة أقدم من التطبيق بتتخطى السطر بدل ما الصفحة تقع")
        assertNull(AlertCodecs.alertInbox.skippingUnreadable().decode(AlertCodecs.alertInbox.toStore(entry) + ("factors" to listOf("brand_new_factor"))))
        assertNull(AlertCodecs.alertInbox.skippingUnreadable().decode(AlertCodecs.alertInbox.toStore(entry) + ("deliverAtHour" to 24L)))
        assertNull(AlertCodecs.alertSettings.skippingUnreadable().decode(mapOf("group" to "group_from_newer_app", "enabled" to false)))
        assertEquals(entry, AlertCodecs.alertInbox.skippingUnreadable().decode(AlertCodecs.alertInbox.toStore(entry)))
    }

    @Test
    fun syncedOnTheAccountAndOnlySettingsInTheBackupAndNoLearningStored() {
        val alertGroups = listOf(ALERT_SETTINGS_GROUP, ALERT_INBOX_GROUP, ALERT_RECEIPTS_GROUP)
        assertTrue(ACCOUNT_GROUPS.containsAll(alertGroups), "على مستوى الحساب — مش جوه بلد")
        assertTrue(alertGroups.none { it in SPACE_GROUPS })
        assertEquals(listOf(ALERT_SETTINGS_GROUP), alertGroups.filter { it in BACKUP_GROUPS }, "النسخة فيها الإعدادات بس — الصفحة والإيصالات بتتولد تاني")
        assertTrue(ALERT_SETTINGS_GROUP in ACCOUNT_DATA_GROUPS)
        // التعلّم على الجوال بس (قرار المالك): ولا مجموعة متزامنة ولا محوّل بيخزن ساعاتك أو تفاعلك
        val stored = (ACCOUNT_GROUPS + BACKUP_GROUPS + DocumentCodecs.byGroup.keys).map { it.lowercase() }
        assertTrue(stored.none { "learn" in it || "hour" in it || "interaction" in it || "engagement" in it || "stats" in it }, stored.toString())
        assertEquals(alertGroups.toSet(), DocumentCodecs.byGroup.keys.filter { it.startsWith("alert") }.toSet())
    }
}
