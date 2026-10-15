package app.masroufy.device

import app.masroufy.core.ErrorKind
import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * «سجل المشاكل» في الملف + تنضيف الخطأ قبل تقارير الأعطال (OVERRIDES §90) — نفس كود أندرويد (`src/jvmShared`).
 */
class ProblemLogDeviceTest {
    private val dir: File = Files.createTempDirectory("plog").toFile()
    private val file = File(dir, "problems/problems.log")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun entry(n: Int, code: ProblemCode = ProblemCode.SYNC_WRITE_FAILED) =
        ProblemEntry("2026-10-15T01:00:%02dZ".format(n), code, ErrorKind.FIREBASE, emptyMap(), "kotlin-0.3", "SA")

    @Test
    fun theFileKeepsOnlyTheLastLinesAndSurvivesReopening() {
        val log = FileProblemLog(file)
        repeat(8) { log.append(entry(it), keepLast = 5) }
        assertEquals((3..7).map { entry(it) }, log.entries())
        assertEquals(5, file.readLines().size)
        assertEquals((3..7).map { entry(it) }, FileProblemLog(file).entries(), "بعد ما البرنامج يتقفل ويتفتح")
        assertFalse(File(file.parentFile, "problems.log.tmp").exists(), "الملف المؤقت اتنقل")
    }

    @Test
    fun brokenLinesAreSkippedAndClearRemovesTheFile() {
        val log = FileProblemLog(file)
        log.append(entry(1), keepLast = 300)
        file.appendText("half a line from a crash\n")
        log.append(entry(2, ProblemCode.CRASH_MAIN), keepLast = 300)
        assertEquals(listOf(entry(1), entry(2, ProblemCode.CRASH_MAIN)), log.entries())
        log.clear()
        assertFalse(file.exists())
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun theWrittenFileHasNoPersonalData() {
        val log = FileProblemLog(file)
        log.append(ProblemEntry("2026-10-15T01:00:00Z", ProblemCode.SMS_RECEIVE_FAILED, null, emptyMap(), "v 0501234567 أحمد", "مصر"), 300)
        val text = file.readText()
        assertFalse(Regex("[؀-ۿ]").containsMatchIn(text), text)
        assertFalse(Regex("""\d{7,}""").containsMatchIn(text), text)
    }

    @Test
    fun theCrashKeepsTypeAndPlaceButNotTheMessage() {
        val inner = java.io.IOException("account 1234567890 balance 4,837.83")
        val outer = IllegalStateException("SMS body: you paid 250.00 SAR at Some Shop", inner)
        outer.addSuppressed(RuntimeException("secret suppressed"))
        val clean = CrashSanitizer.sanitize(outer, "SYNC_WRITE_FAILED")
        assertEquals("[SYNC_WRITE_FAILED] java.lang.IllegalStateException", clean.message)
        assertEquals(outer.stackTrace.toList(), clean.stackTrace.toList())
        val cause = clean.cause as SanitizedCrash
        assertEquals("java.io.IOException", cause.message)
        assertEquals(inner.stackTrace.toList(), cause.stackTrace.toList())
        assertNull(cause.cause)
        assertTrue(clean.suppressed.isEmpty())
        val all = generateSequence(clean as Throwable) { it.cause }.joinToString("\n") { it.stackTraceToString() }
        for (secret in listOf("SAR", "1234567890", "4,837", "Shop", "secret")) assertFalse(secret in all, "«$secret» وصل للتقرير")
    }

    @Test
    fun unsafeCodesAndCauseLoopsAreHandled() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        val clean = CrashSanitizer.sanitize(a, "not a code; 0501234567")
        assertEquals("java.lang.RuntimeException", clean.message)
        assertEquals("java.lang.RuntimeException", clean.cause?.message)
        assertNull(clean.cause?.cause, "الحلقة اتقطعت")
        var deep: Throwable = RuntimeException("0")
        repeat(20) { deep = RuntimeException("$it", deep) }
        assertEquals(CrashSanitizer.MAX_DEPTH, generateSequence(CrashSanitizer.sanitize(deep) as Throwable) { it.cause }.count())
        val again = CrashSanitizer.sanitize(CrashSanitizer.sanitize(IllegalStateException("x")))
        assertEquals("java.lang.IllegalStateException", again.message)
        assertSame(SanitizedCrash::class.java, again.javaClass)
    }
}
