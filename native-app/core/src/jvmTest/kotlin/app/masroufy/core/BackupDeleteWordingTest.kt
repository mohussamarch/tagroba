package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** fix055c — تأكيد مسح النسخة بفعل واحد في كل لهجة: الفصحى «احذف» (العنوان والزرار) والمصري «امسح» — من غير خلط. */
class BackupDeleteWordingTest {
    private val keys = listOf(R06Key.L2_BK_CONFIRM_TITLE, R06Key.L2_BK_CONFIRM_BODY, R06Key.L2_BK_CONFIRM_DO, R06Key.L2_BK_DONE, R06Key.L2_BK_FAILED)

    @Test fun saudiUsesOnlyHadhf() {
        assertEquals("احذف", MSA_R06_LINKS2_TEXTS[R06Key.L2_BK_CONFIRM_DO])
        assertTrue(MSA_R06_LINKS2_TEXTS.getValue(R06Key.L2_BK_CONFIRM_TITLE).startsWith("احذف"))
        for (k in keys) assertFalse("مسح" in MSA_R06_LINKS2_TEXTS.getValue(k) || "مُسح" in MSA_R06_LINKS2_TEXTS.getValue(k), "$k")
    }

    @Test fun egyptianUsesOnlyImsah() {
        assertEquals("امسح", EGYPTIAN_R06_LINKS2_TEXTS[R06Key.L2_BK_CONFIRM_DO])
        for (k in keys) assertFalse("حذف" in EGYPTIAN_R06_LINKS2_TEXTS.getValue(k), "$k")
    }
}
