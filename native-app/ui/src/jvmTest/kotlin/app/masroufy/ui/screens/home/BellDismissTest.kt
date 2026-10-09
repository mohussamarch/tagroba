package app.masroufy.ui.screens.home

import app.masroufy.ui.app.BellItem
import app.masroufy.ui.app.BellState
import app.masroufy.ui.app.BellTone
import app.masroufy.ui.nav.Tab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * «×» على الإشعار (قرار المالك 2026-10-09): بيختفي من الجرس، وما بيتعدّش في «جديد»، وبيشيل نقطة تبويبه لو مفيش غيره،
 * و«تراجع» ٤ ثواني بيرجّعه (نفس الحالة في نافذة الجرس وصفحة الإشعارات).
 */
class BellDismissTest {
    private fun item(key: String, tab: Tab?, unread: Boolean = true) = BellItem(key, "عنوان $key", "مجموعة", BellTone.ALERT, unread, tab)

    private val state = BellState(
        listOf(item("sms", Tab.OPERATIONS), item("party", Tab.OPERATIONS), item("fahd", Tab.PEOPLE), item("budget", null), item("old", Tab.PEOPLE, unread = false)),
        unread = 4,
        dots = setOf(Tab.OPERATIONS, Tab.PEOPLE),
    )

    @Test fun nothingDeletedKeepsTheStateAsIs() {
        assertSame(state, state.without(emptySet()))
    }

    @Test fun deletingTheOnlyPeopleNoticeClearsThePeopleDot() {
        val s = state.without(setOf("fahd"))
        assertEquals(setOf(Tab.OPERATIONS), s.dots, "الأشخاص كان ليه «فهد» بس — المقروء ما بيعملش نقطة")
        assertEquals(3, s.unread)
        assertTrue(s.items.none { it.threadKey == "fahd" })
    }

    @Test fun anOperationsDotStaysWhileAnotherOperationsNoticeIsLeft() {
        assertEquals(setOf(Tab.OPERATIONS, Tab.PEOPLE), state.without(setOf("sms")).dots)
        assertEquals(setOf(Tab.PEOPLE), state.without(setOf("sms", "party")).dots)
    }

    @Test fun undoBringsTheNoticeBackOnlyWithinTheWindow() {
        val d = Dismissals()
        d.drop("fahd")
        assertTrue(d.isGone("fahd"))
        assertEquals("fahd", d.undo)
        d.restore()
        assertTrue(!d.isGone("fahd"), "«تراجع» ⇒ رجع")
        d.drop("sms")
        d.expire("sms")
        assertNull(d.undo, "عدّت الأربع ثواني ⇒ مفيش تراجع")
        d.restore()
        assertTrue(d.isGone("sms"), "بعد المدة الممسوح بيفضل ممسوح")
    }
}
