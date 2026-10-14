package app.masroufy.usecase

import app.masroufy.core.ErrorKind
import app.masroufy.core.PROBLEM_LOG_LIMIT
import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemDetail
import app.masroufy.memory.MemoryProblemLog
import app.masroufy.port.CrashReportsPort
import app.masroufy.problems.Problems
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «سجل المشاكل» (OVERRIDES §90): التسجيل · الحلقة (آخر 300) · الأحدث فوق · النص اللي بيتبعت · المسح · المفتاح. */
class ProblemLogTest {
    private var tick = 0
    private val log = MemoryProblemLog()
    private var country: String? = "SA"
    private val clock = { "2026-10-15T01:%02d:%02dZ".format(tick / 60 % 60, tick++ % 60) }
    private val record = RecordProblem(log, clock, "kotlin-0.3", { country })

    @AfterTest
    fun reset() {
        Problems.sink = null
    }

    @Test
    fun timeVersionAndCountryComeFromTheAppNotTheCaller() {
        val e = record.record(ProblemCode.SYNC_LISTEN_FAILED, ErrorKind.NETWORK, mapOf(ProblemDetail.COUNT to 3))
        assertEquals("2026-10-15T01:00:00Z", e.at)
        assertEquals("kotlin-0.3", e.appVersion)
        assertEquals("SA", e.country)
        country = null
        assertEquals(null, record.record(ProblemCode.CRASH_MAIN).country, "قبل الدخول مفيش بلد")
    }

    @Test
    fun theRingKeepsOnlyTheLast300() {
        repeat(PROBLEM_LOG_LIMIT + 10) { record.record(if (it < 10) ProblemCode.SMS_READ_FAILED else ProblemCode.SYNC_WRITE_FAILED) }
        val all = log.entries()
        assertEquals(PROBLEM_LOG_LIMIT, all.size)
        assertTrue(all.all { it.code == ProblemCode.SYNC_WRITE_FAILED }, "أقدم 10 اتشالوا")
        val small = RecordProblem(log, clock, "kotlin-0.3", { "EG" }, keepLast = 2)
        small.record(ProblemCode.CRASH_MAIN)
        assertEquals(2, log.entries().size)
    }

    @Test
    fun listIsNewestFirstAndExportIsOldestFirst() {
        record.record(ProblemCode.IMPORT_PDF_LOCKED)
        record.record(ProblemCode.BACKUP_CREATE_FAILED, ErrorKind.IO)
        assertEquals(listOf(ProblemCode.BACKUP_CREATE_FAILED, ProblemCode.IMPORT_PDF_LOCKED), ListProblems(log).newestFirst().map { it.code })
        val text = ExportProblems(log, { "2026-10-15T09:00:00Z" }, "kotlin-0.3").text()
        assertEquals(
            listOf(
                "Masroufy problem log",
                "exported=2026-10-15T09:00:00Z v=kotlin-0.3 entries=2",
                "---",
                "2026-10-15T01:00:00Z|INFO|import|IMPORT_PDF_LOCKED|v=kotlin-0.3|c=SA",
                "2026-10-15T01:00:01Z|ERROR|backup|BACKUP_CREATE_FAILED|kind=IO|v=kotlin-0.3|c=SA",
            ),
            text.trimEnd().lines(),
        )
        ClearProblems(log).clear()
        assertTrue(ListProblems(log).newestFirst().isEmpty())
        assertTrue(ExportProblems(log, { "2026-10-15T09:00:00Z" }, "kotlin-0.3").text().contains("entries=0"))
    }

    @Test
    fun reportingNeverCrashesTheCaller() {
        Problems.report(ProblemCode.CRASH_MAIN) // مفيش sink ⇒ ولا حاجة
        Problems.sink = { _, _, _ -> error("broken sink") }
        Problems.report(ProblemCode.SYNC_WRITE_FAILED, IllegalStateException("x"))
        val seen = mutableListOf<ProblemCode>()
        Problems.sink = { code, error, _ -> seen += code; record.record(code, app.masroufy.core.errorKindOf(error)) }
        Problems.report(ProblemCode.SMS_RECEIVE_FAILED, SecurityException("text of a bank message 1,250.00"))
        assertEquals(listOf(ProblemCode.SMS_RECEIVE_FAILED), seen)
        assertEquals(ErrorKind.SECURITY, log.entries().single().kind)
    }

    @Test
    fun theSwitchDoesNothingWhenReportsAreNotAvailable() {
        val port = object : CrashReportsPort {
            var state = true
            var screens = mutableListOf<String>()
            override var available = false
            override fun isOn() = state
            override fun setOn(on: Boolean) { state = on }
            override fun screenShown(route: String) { screens += route }
        }
        val manage = ManageCrashReports(port)
        assertFalse(manage.isOn(), "مش متاحة ⇒ مقفولة في العرض")
        manage.setOn(false)
        assertTrue(port.state, "ما اتلمسش")
        port.available = true
        assertTrue(manage.isOn())
        manage.setOn(false)
        assertFalse(manage.isOn())
        manage.screenShown("Backup")
        assertEquals(listOf("Backup"), port.screens)
    }
}
