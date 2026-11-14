package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertTrue

/** OVERRIDES §120-13: سطر تنبيه تقارير الأعطال موجود في السعودي والمصري والإنجليزي، سطر واحد قصير، وبيقول إزاي تتقفل. */
class CrashNoticeTextsTest {
    @Test fun noticeIsOneShortLineInEveryLanguage() {
        for (texts in listOf(MSA_MORE_PROBLEM_TEXTS, EGYPTIAN_MORE_PROBLEM_TEXTS, ENGLISH_MORE_PROBLEM_TEXTS)) {
            val line = texts.getValue(UiKey.PLOG_CRASH_NOTICE)
            assertTrue('\n' !in line && line.length <= 90, line)
        }
        assertTrue("تقفلها" in EGYPTIAN_MORE_PROBLEM_TEXTS.getValue(UiKey.PLOG_CRASH_NOTICE))
        assertTrue("إيقافها" in MSA_MORE_PROBLEM_TEXTS.getValue(UiKey.PLOG_CRASH_NOTICE))
    }
}
