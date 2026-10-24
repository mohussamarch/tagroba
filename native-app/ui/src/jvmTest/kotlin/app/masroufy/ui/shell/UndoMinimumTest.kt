package app.masroufy.ui.shell

import app.masroufy.ui.screens.operations.UNDO_WINDOW_MS
import app.masroufy.ui.screens.operations.UndoWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * fix055c (§118) — شريط «تراجع» بعد أي مسح بيفضل 6 ثواني على الأقل: الشريط اللي مكان الزرار (`UndoWindow`) وشريط الشل (`Toaster`)
 * — ورسالة عادية بتيجي في النص بتستنى وراه بدل ما تقصّره. ساعة مزيفة.
 */
class UndoMinimumTest {
    @Test fun bothUndoBarsLastAtLeastSixSeconds() {
        assertTrue(UNDO_WINDOW_MS >= UNDO_MIN_MS)
        assertTrue(TOAST_ACTION_MS >= UNDO_MIN_MS)
        var now = 0L
        val w = UndoWindow({ now })
        now = UNDO_MIN_MS - 1
        assertFalse(w.expired())
    }

    @Test fun aPlainToastWaitsBehindAFreshUndoBar() {
        var now = 1_000L
        val toaster = Toaster { now }
        toaster.showUndo("حُذف «سداد وهمي»", "تراجع") { null }
        val undoId = toaster.current!!.id
        now += 2_000
        toaster.show("سُجّلت: عملية وهمية")
        assertEquals("تراجع", toaster.current?.action, "الشريط ما اتقصّرش")
        assertEquals("سُجّلت: عملية وهمية", toaster.pending?.text)
        toaster.endAction(undoId)
        assertEquals("سُجّلت: عملية وهمية", toaster.current?.text, "الرسالة ظهرت بعد ما الشريط خلص")
        assertNull(toaster.pending)
    }

    @Test fun afterTheMinimumAPlainToastReplacesTheBar() {
        var now = 0L
        val toaster = Toaster { now }
        toaster.showUndo("حُذف «تصنيف وهمي»", "تراجع") { null }
        now += UNDO_MIN_MS
        toaster.show("رسالة وهمية")
        assertEquals("رسالة وهمية", toaster.current?.text)
    }
}
