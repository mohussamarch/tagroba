package app.masroufy.device

import app.masroufy.memory.FixedClock
import app.masroufy.port.LockResult
import app.masroufy.usecase.PdfReadError
import app.masroufy.usecase.saveVerifiedBackup
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.stringWithContentsOfFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** منافذ الجهاز على **محاكي الآيفون** (GitHub — مفيش ماك هنا). نفس حالات اختبارات أندرويد، ببيانات وهمية. */
@OptIn(ExperimentalForeignApi::class)
class IosDeviceTest {
    @Test fun pdfKitReadsTheSyntheticStatementLikeTheWordsItWasWrittenWith() = runBlocking<Unit> {
        val progress = mutableListOf<Pair<Int, Int>>()
        val read = PdfKitPages().read(SyntheticStatement.pdf()) { p, t -> progress += p to t }
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        SyntheticStatement.assertReadsLikeTheWords(read)
    }

    @Test fun notAPdfIsAClearError() = runBlocking<Unit> {
        assertFailsWith<PdfReadError> { PdfKitPages().read("مش PDF".encodeToByteArray()) }
        assertFailsWith<PdfReadError> { PdfKitPages().read(ByteArray(0)) }
    }

    @Test fun backupIsWrittenAndItsSizeReadBackFromDisk() = runBlocking<Unit> {
        val port = IosRepairBackup()
        val content = "{\"اسم\":\"نسخة وهمية\",\"عمليات\":[1,2,3]}"
        val saved = port.save("masroufy-test.json", content)
        assertEquals(content.encodeToByteArray().size.toLong(), saved.bytes, "الحجم بالبايت من القرص")
        assertEquals(content, NSString.stringWithContentsOfFile(saved.location, NSUTF8StringEncoding, null))
        assertTrue("/Documents/backups/" in saved.location, saved.location)
        assertFailsWith<IllegalArgumentException> { port.save("../خارج.json", content) }
        val verified = saveVerifiedBackup(port, FixedClock("2026-10-01T10:00:00.000Z"), "repair", listOf(mapOf("id" to "t-1")))
        assertEquals(verified.expectedBytes, verified.bytes)
        NSFileManager.defaultManager.removeItemAtPath(saved.location, null)
    }

    @Test fun appLockSettingSurvives() {
        val settings = IosAppLockSettings()
        settings.write(true)
        assertTrue(IosAppLockSettings().read())
        settings.write(false)
        assertFalse(IosAppLockSettings().read())
    }

    @Test fun syncCursorIsPerAccountAndIgnoresGarbage() {
        val a = IosSyncCursor("kt-a-${kotlin.random.Random.nextLong()}")
        val b = IosSyncCursor("kt-b-${kotlin.random.Random.nextLong()}")
        assertNull(a.read(), "حساب جديد: مفيش مزامنة قبل كده")
        a.write("2026-10-01T10:00:00.123Z")
        assertEquals("2026-10-01T10:00:00.123Z", a.read())
        assertNull(b.read(), "كل حساب ليه مؤشره")
        for (bad in listOf("مش وقت", "2026-02-30T10:00:00Z", "2026-10-01T25:00:00Z", "2026-10-01 10:00:00Z")) {
            a.write(bad)
            assertNull(a.read(), "قيمة بايظة ($bad) = مزامنة كاملة، مش وقع")
        }
        NSUserDefaults.standardUserDefaults.dictionaryRepresentation().keys.filterIsInstance<String>().filter { it.startsWith("masroufy-sync-v1:kt-") }
            .forEach { NSUserDefaults.standardUserDefaults.removeObjectForKey(it) }
    }

    @Test fun deviceLockOnASimulatorWithoutPasscodeIsUnavailableNotStuck() = runBlocking<Unit> {
        val lock = IosDeviceLock()
        val availability = lock.availability()
        println("محاكي الآيفون: القفل متاح=${availability.available} الكود=${availability.code}")
        if (!availability.available) {
            // من غير رمز: النتيجة بترجع فورًا من غير نافذة
            assertEquals(LockResult.UNAVAILABLE, withTimeout(10_000) { lock.authenticate("مصروفي") })
        }
        assertEquals("NONE_ENROLLED", IosDeviceLock.availabilityCode(-5))
        assertEquals(LockResult.CANCELLED, IosDeviceLock.resultOf(-2))
        assertEquals(LockResult.FAILED, IosDeviceLock.resultOf(-1))
    }
}
