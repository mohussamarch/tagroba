package app.masroufy.device

import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportPolicyTest {
    @Test
    fun errorsAndWarningsAreSentInfoAndCrashesAreNot() {
        for (code in ProblemCode.entries) {
            val expected = code.level != ProblemLevel.INFO && code != ProblemCode.CRASH_MAIN && code != ProblemCode.CRASH_BACKGROUND
            assertEquals(expected, CrashReportPolicy.shouldSend(code), code.name)
        }
        assertTrue(CrashReportPolicy.shouldSend(ProblemCode.BACKGROUND_GAVE_UP))
        assertFalse(CrashReportPolicy.shouldSend(ProblemCode.IMPORT_PDF_LOCKED))
    }

    @Test
    fun syntheticReportCarriesOnlyTheCodeName() {
        val c = CrashSanitizer.sanitize(ProblemCodeReport(ProblemCode.BACKGROUND_GAVE_UP.name), ProblemCode.BACKGROUND_GAVE_UP.name)
        assertEquals("[BACKGROUND_GAVE_UP] ${ProblemCodeReport::class.java.name}", c.message)
    }

    @Test
    fun realErrorMessageNeverSurvives() {
        val c = CrashSanitizer.sanitize(IllegalStateException("secret 123"), ProblemCode.SYNC_WRITE_FAILED.name)
        assertFalse(c.message.orEmpty().contains("secret"))
    }
}
