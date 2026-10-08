package app.masroufy.data

import app.masroufy.core.ALERT_SETTINGS_GROUP
import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertGroupSetting
import app.masroufy.core.Person
import app.masroufy.core.emptyBackupData
import app.masroufy.memory.MemoryFullBackup
import app.masroufy.usecase.FullBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * مجموعة «رسايل البنك» الجديدة (OVERRIDES §72 — رد المالك ٣) في التخزين والنسخة الشاملة: الإعدادات المتخزنة قبلها (من غير المجموعة)
 * معناها إنها **شغالة**، والمقفولة بتتخزن وبتسافر في النسخة وبترجع. بيانات مخترعة.
 */
class BankSmsAlertGroupTest {
    private val codec = AlertCodecs.alertSettings

    /** نفس قراية `FirestoreAlertSettings.disabledGroups` بالظبط: المقفول بس، والمستند اللي ما يتقريش بيتخطّى. */
    private fun disabled(docs: List<Map<String, Any?>>) = docs.mapNotNull { codec.skippingUnreadable().decode(it) }.filter { !it.enabled }.map { it.group }.toSet()

    @Test
    fun `إعدادات قديمة من غير رسايل البنك يبقى شغالة`() {
        // المستخدم كان قافل «الأسئلة» قبل ما المجموعة الجديدة تتعمل
        val old = listOf(mapOf("group" to "questions", "enabled" to false), mapOf("group" to "budget", "enabled" to true))
        val off = disabled(old)
        assertEquals(setOf(AlertGroup.QUESTIONS), off)
        assertFalse(AlertGroup.BANK_SMS in off, "مش متخزنة ⇒ شغالة افتراضيًا")
    }

    @Test
    fun `قفل رسايل البنك بيتخزن ومستقل عن الأسئلة`() {
        val stored = codec.toStore(AlertGroupSetting(AlertGroup.BANK_SMS, false))
        assertEquals("bank_sms", stored["group"])
        assertEquals(setOf(AlertGroup.BANK_SMS), disabled(listOf(stored, codec.toStore(AlertGroupSetting(AlertGroup.QUESTIONS, true)))))
    }

    @Test
    fun `رسايل البنك المقفولة بتسافر في النسخة الشاملة وبترجع`() = runBlocking<Unit> {
        val source = emptyBackupData().also {
            it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص وهمي"))
            it.getValue(ALERT_SETTINGS_GROUP) += codec.toStore(AlertGroupSetting(AlertGroup.BANK_SMS, false))
        }
        val text = FullBackup(MemoryFullBackup(source)).create("2026-10-08T12:00:00.000Z").toJsonText()
        assertTrue("\"bank_sms\"" in text)
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        assertEquals(1, restore.apply(restore.plan(text).file).added[ALERT_SETTINGS_GROUP])
        assertEquals(setOf(AlertGroup.BANK_SMS), disabled(target.read().getValue(ALERT_SETTINGS_GROUP)))
    }
}
