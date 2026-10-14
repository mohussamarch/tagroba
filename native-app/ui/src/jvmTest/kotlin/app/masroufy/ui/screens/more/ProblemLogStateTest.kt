package app.masroufy.ui.screens.more

import app.masroufy.core.ArabicVariant
import app.masroufy.core.Language
import app.masroufy.core.ProblemCode
import app.masroufy.core.ProblemEntry
import app.masroufy.core.TextKey
import app.masroufy.core.Texts
import app.masroufy.core.UiKey
import app.masroufy.core.uiText
import app.masroufy.ui.components.BadgeKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** «سجل المشاكل» للعرض (OVERRIDES §90): نص الكود · الدرجة · الوقت بوقت الجوال — والوقت المش مفهوم «غير متاح». */
class ProblemLogStateTest {
    @AfterTest
    fun reset() {
        Texts.language = Language.AR
        Texts.arabicVariant = ArabicVariant.MSA
    }

    @Test
    fun rowShowsTheCodeLabelLevelAndLocalTime() {
        Texts.arabicVariant = ArabicVariant.EGYPTIAN
        val row = problemRow(ProblemEntry("2026-10-15T04:20:03+03:00", ProblemCode.SMS_READ_FAILED))
        assertEquals(UiKey.PLOG_C_SMS_READ, row.label)
        assertEquals(UiKey.PLOG_LEVEL_ERROR, row.level)
        assertEquals(BadgeKind.APPROX, row.badge)
        assertEquals("15 أكتوبر، 04:20", row.time)
        assertEquals("قراية رسايل البنك ما نجحتش", uiText(row.label))
        Texts.arabicVariant = ArabicVariant.MSA
        assertEquals("تعذّرت قراءة رسائل البنك", uiText(row.label))
    }

    @Test
    fun infoIsCalmAndBrokenTimeIsNotInvented() {
        val row = problemRow(ProblemEntry("-", ProblemCode.IMPORT_PDF_LOCKED))
        assertEquals(BadgeKind.INFO, row.badge)
        assertEquals(UiKey.PLOG_LEVEL_INFO, row.level)
        assertEquals(uiText(TextKey.NOT_AVAILABLE), row.time)
    }
}
