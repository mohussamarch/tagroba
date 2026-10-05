package app.masroufy.data

import app.masroufy.core.ALERT_INBOX_GROUP
import app.masroufy.core.ALERT_RECEIPTS_GROUP
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * إعدادات التنبيهات في النسخة الشاملة (جلسة 18، §61): المجموعات المقفولة **بتسافر** (اختيار المستخدم)، والصفحة والإيصالات **لأ**
 * (بتتولد تاني من البيانات). من غير إعدادات ⇒ الملف هو هو زي الأول (ونفس البصمة). بيانات مخترعة.
 */
class AlertSettingsBackupTest {
    private fun account(withSettings: Boolean) = emptyBackupData().also {
        it.getValue("people") += ReferenceCodecs.people.toStore(Person("p-1", "شخص وهمي"))
        if (withSettings) {
            it.getValue(ALERT_SETTINGS_GROUP) += AlertCodecs.alertSettings.toStore(AlertGroupSetting(AlertGroup.BUDGET, false))
            it.getValue(ALERT_SETTINGS_GROUP) += AlertCodecs.alertSettings.toStore(AlertGroupSetting(AlertGroup.ZAKAT, true))
        }
    }

    @Test
    fun `المجموعات المقفولة بتسافر في النسخة وبترجع بنفس البصمة`() = runBlocking<Unit> {
        val source = account(withSettings = true)
        val file = FullBackup(MemoryFullBackup(source)).create("2026-10-05T12:00:00.000Z")
        val text = file.toJsonText()
        assertTrue("\"$ALERT_SETTINGS_GROUP\"" in text)
        for (g in listOf(ALERT_INBOX_GROUP, ALERT_RECEIPTS_GROUP)) assertFalse("\"$g\"" in text, "$g مش في النسخة")
        val target = MemoryFullBackup()
        val restore = FullBackup(target)
        assertEquals(2, restore.apply(restore.plan(text).file).added[ALERT_SETTINGS_GROUP])
        assertEquals(source.getValue(ALERT_SETTINGS_GROUP), target.read().getValue(ALERT_SETTINGS_GROUP))
        assertEquals(file.checksum, FullBackup(target).create("2026-10-05T12:00:00.000Z").checksum)
        assertEquals(0, restore.apply(restore.plan(text).file).added[ALERT_SETTINGS_GROUP] ?: 0, "الاسترجاع التاني ما بيكررش")
    }

    @Test
    fun `من غير إعدادات المجموعة ما بتتكتبش والغلط بيترفض`() = runBlocking<Unit> {
        val text = FullBackup(MemoryFullBackup(account(withSettings = false))).create("2026-10-05T12:00:00.000Z").toJsonText()
        assertFalse("\"$ALERT_SETTINGS_GROUP\"" in text, "ملف الإصدار 2 زي ما هو")
        fun rejects(why: String, change: (Map<String, Any?>) -> Map<String, Any?>) {
            val data = account(withSettings = true).also { it.getValue(ALERT_SETTINGS_GROUP)[0] = change(it.getValue(ALERT_SETTINGS_GROUP)[0]) }
            assertFailsWith<IllegalArgumentException>(why) { runBlocking { FullBackup(MemoryFullBackup(data)).create("2026-10-05T12:00:00.000Z") } }
        }
        rejects("مجموعة مش معروفة") { it + ("group" to "group_from_newer_app") }
        rejects("مفتوحة مش منطقي") { it + ("enabled" to "no") }
        rejects("من غير مفتوحة/مقفولة") { it - "enabled" }
    }
}
