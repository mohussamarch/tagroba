package app.masroufy.ui.shell.ask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * المحادثة مع المساعد (آخر §76): بتفضل لحد «محادثة جديدة» · السجل بالأحدث الأول وعنوانه أول سؤال · فتح قديمة بيرجّع الحالية للسجل ·
 * المسح بتراجع. (حالة الشاشة بس — مفيش فهم للرسالة ولا أرقام.)
 */
class AskStateTest {
    @Test fun aNewChatMovesTheCurrentOneToHistory() {
        val s = AskState()
        assertFalse(s.startNew(), "فاضية ⇒ الزرار معطّل ومفيش حاجة تتسجل")
        assertTrue(s.history.isEmpty())
        s.add(mine = true, text = "سؤال أول")
        s.add(mine = false, text = "رد أول")
        s.draft = "لسه بكتب"
        assertEquals(listOf("سؤال أول"), s.history.map { it.title }, "الحالية بتظهر في السجل لو فيها كلام")
        assertTrue(s.startNew())
        assertTrue(s.current.isEmpty)
        assertEquals("", s.draft)
        s.add(mine = true, text = "سؤال تاني")
        assertEquals(listOf("سؤال تاني", "سؤال أول"), s.history.map { it.title }, "الأحدث الأول")
        assertEquals("رد أول", s.history.last().firstReply)
    }

    @Test fun openingAnOldChatPutsTheCurrentBack() {
        val s = AskState()
        s.add(true, "أ")
        s.startNew()
        s.add(true, "ب")
        val old = s.history.first { it.title == "أ" }
        s.open(old.id)
        assertEquals("أ", s.current.title)
        assertEquals(listOf("أ", "ب"), s.history.map { it.title })
    }

    @Test fun deleteAndUndo() {
        val s = AskState()
        s.add(true, "أ")
        s.startNew()
        s.add(true, "ب")
        val old = s.history.first { it.title == "أ" }
        val gone = assertNotNull(s.delete(old.id))
        assertEquals(listOf("ب"), s.history.map { it.title })
        s.restore(gone)
        assertEquals(setOf("أ", "ب"), s.history.map { it.title }.toSet())
        s.restore(gone)
        assertEquals(2, s.history.size, "التراجع مرتين ما بيكررش")
        // مسح الحالية ⇒ تبدأ واحدة فاضية
        val current = s.current.id
        assertNotNull(s.delete(current))
        assertTrue(s.current.isEmpty)
        assertNull(s.delete(current), "اتمسحت خلاص")
    }
}
