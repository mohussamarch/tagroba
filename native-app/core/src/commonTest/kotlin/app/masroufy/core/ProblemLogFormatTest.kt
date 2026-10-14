package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «سجل المشاكل» (OVERRIDES §90): شكل السطر والنص اللي بيتبعت — ومفيش فيهم أي حاجة من بيانات المستخدم. */
class ProblemLogFormatTest {
    private val at = "2026-10-15T04:20:03+03:00"

    @Test
    fun lineHasTheFixedShape() {
        val e = ProblemEntry(at, ProblemCode.SYNC_WRITE_FAILED, ErrorKind.PERMISSION, mapOf(ProblemDetail.ATTEMPT to 2), "kotlin-0.3-fast", "SA")
        assertEquals("$at|ERROR|sync|SYNC_WRITE_FAILED|kind=PERMISSION|attempt=2|v=kotlin-0.3-fast|c=SA", ProblemLogFormat.line(e))
        assertEquals("$at|INFO|import|IMPORT_PDF_LOCKED|v=-|c=-", ProblemLogFormat.line(ProblemEntry(at, ProblemCode.IMPORT_PDF_LOCKED)))
    }

    @Test
    fun everyCodeSurvivesWriteAndRead() {
        for (code in ProblemCode.entries) for (kind in listOf(null) + ErrorKind.entries) {
            val e = ProblemEntry("2026-10-15T01:20:03.123Z", code, kind, mapOf(ProblemDetail.COUNT to 7, ProblemDetail.ATTEMPT to 1), "kotlin-0.3", "EG")
            assertEquals(e, ProblemLogFormat.parse(ProblemLogFormat.line(e)), "$code/$kind")
        }
    }

    @Test
    fun unknownOrBrokenLinesAreSkipped() {
        assertNull(ProblemLogFormat.parse(""))
        assertNull(ProblemLogFormat.parse("garbage"))
        assertNull(ProblemLogFormat.parse("$at|ERROR|sync|CODE_FROM_A_NEWER_VERSION|v=x|c=SA"))
    }

    /** حتى لو قيمة غريبة وصلت للسطر (نسخة أو بلد أو وقت)، المكتوب بيتنضف: ولا حرف عربي ولا مسافة ولا @ ولا رقم طويل. */
    @Test
    fun strangeValuesAreCleanedBeforeWriting() {
        val e = ProblemEntry("الوقت 05551234567", ProblemCode.CRASH_MAIN, null, emptyMap(), "kotlin 0.3 محمد@mail.com 1234567890123456", "Saudi")
        val line = ProblemLogFormat.line(e)
        assertTrue(line.startsWith("-|ERROR|ui|CRASH_MAIN|"), line)
        assertTrue(line.endsWith("|c=-"), line)
        assertNoPersonalData(line)
        assertEquals("-", ProblemLogFormat.safeVersion(e.appVersion))
        assertEquals("kotlin-0.3-fast", ProblemLogFormat.safeVersion("kotlin-0.3-fast"))
    }

    @Test
    fun exportIsAHeaderAndTheLinesOldestFirst() {
        val a = ProblemEntry("2026-10-15T01:00:00Z", ProblemCode.SMS_READ_FAILED, ErrorKind.SECURITY, emptyMap(), "kotlin-0.3", "SA")
        val b = ProblemEntry("2026-10-15T02:00:00Z", ProblemCode.BACKUP_RESTORE_FAILED, ErrorKind.PARSE, emptyMap(), "kotlin-0.3", "SA")
        val text = ProblemLogFormat.export(listOf(a, b), "2026-10-15T03:00:00Z", "kotlin-0.3")
        assertEquals(
            "Masroufy problem log\nexported=2026-10-15T03:00:00Z v=kotlin-0.3 entries=2\n---\n" +
                "2026-10-15T01:00:00Z|ERROR|sms|SMS_READ_FAILED|kind=SECURITY|v=kotlin-0.3|c=SA\n" +
                "2026-10-15T02:00:00Z|ERROR|backup|BACKUP_RESTORE_FAILED|kind=PARSE|v=kotlin-0.3|c=SA\n",
            text,
        )
        assertNoPersonalData(text)
    }

    @Test
    fun theKindComesFromTheTypeNameNeverFromTheMessage() {
        assertEquals(ErrorKind.STATE, errorKindOf(IllegalStateException("Permission denied token=abc 1,250.00 SAR")))
        assertEquals(ErrorKind.ARGUMENT, errorKindOf(IllegalArgumentException("Timeout")))
        assertEquals(ErrorKind.PERMISSION, errorKindOf(FakePermissionError()))
        assertEquals(ErrorKind.PARSE, errorKindOf(NumberFormatException("x")))
        assertEquals(ErrorKind.OTHER, errorKindOf(RuntimeException("Firebase")))
        assertNull(errorKindOf(null))
    }

    @Test
    fun everyCodeHasItsOwnReadableLabel() {
        assertEquals(ProblemCode.entries.size, ProblemCode.entries.map { it.label }.toSet().size, "كل كود ليه نص لوحده")
        forEachTextVariant { label ->
            for (code in ProblemCode.entries) assertTrue(uiText(code.label) != code.label.name, "$label: ${code.name} من غير نص")
        }
        assertTrue(ProblemArea.entries.all { area -> area == ProblemArea.OTHER || ProblemCode.entries.any { it.area == area } }, "كل منطقة ليها كود")
    }

    private class FakePermissionError : IllegalStateException("whatever the user typed")

    companion object {
        /** ولا حرف عربي · ولا @ · ولا رقم من 10 خانات أو أكتر (جوال/حساب/كارت) · ولا علامة عملة أو فاصلة عشرية بعد رقم (مبلغ). */
        fun assertNoPersonalData(text: String) {
            assertTrue(Regex("[؀-ۿ]").find(text) == null, "حرف عربي في: $text")
            assertTrue('@' !in text, "@ في: $text")
            assertTrue(Regex("""\d{10,}""").find(text) == null, "رقم طويل في: $text")
            assertTrue(Regex("""\d,\d{3}|SAR|EGP|ر\.س""").find(text) == null, "شكل مبلغ في: $text")
        }
    }
}
