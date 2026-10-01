package app.masroufy.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.masroufy.memory.FixedClock
import app.masroufy.usecase.saveVerifiedBackup
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** حفظ النسخة على الجهاز (من غير نافذة) وإعداد القفل — على محاكي أندرويد، بيانات وهمية. */
@RunWith(AndroidJUnit4::class)
class LocalFilesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun backupIsWrittenAndItsSizeReadBackFromDisk() = runBlocking<Unit> {
        val port = AndroidRepairBackup(context)
        val content = "{\"اسم\":\"نسخة وهمية\",\"عمليات\":[1,2,3]}"
        val saved = port.save("masroufy-test.json", content)
        assertEquals(content.encodeToByteArray().size.toLong(), saved.bytes, "الحجم بالبايت (العربي أكتر من حرف) من القرص")
        assertEquals(content, File(saved.location).readText())
        assertTrue("/backups/" in saved.location)
        assertFailsWith<IllegalArgumentException> { port.save("../خارج.json", content) }
        // نفس حالة الاستخدام اللي التطبيق بيستعملها قبل أي إصلاح: بتتأكد إن اللي اتكتب = اللي اتطلب
        val verified = saveVerifiedBackup(port, FixedClock("2026-10-01T10:00:00.000Z"), "repair", listOf(mapOf("id" to "t-1")))
        assertEquals(verified.expectedBytes, verified.bytes)
    }

    @Test fun appLockSettingSurvives() {
        val settings = AndroidAppLockSettings(context)
        settings.write(true)
        assertTrue(AndroidAppLockSettings(context).read())
        settings.write(false)
        assertTrue(!AndroidAppLockSettings(context).read())
    }
}
