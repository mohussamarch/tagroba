package app.masroufy.ui.nav

import app.masroufy.ui.nav.ScrollMemory.ListPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * الرجوع بيرجّعك لنفس المكان بالظبط (OVERRIDES §85): مكان كل خانة في سجل الرجوع في الملّاح نفسه. الاختبار بيعمل اللي الشاشة بتعمله
 * (`EntryScroll.kt`): وهي خارجة بتكتب مكانها بمفتاح خانتها، ولما ترجع بتقراه — قوايم (`LazyColumn`) وعمود عادي («الأشخاص»)، على أكتر من عمق.
 */
class ScrollMemoryTest {
    private data class Screen(override val name: String) : Route

    /** الشاشة الظاهرة بتتقفل (دخلت جوه حاجة أو بدّلت التبويب) ⇒ بتكتب مكانها. */
    private fun Navigator.leaveList(pos: ListPos, slot: String = "main") = scrolls.saveList(visibleKey, slot, pos)

    private fun Navigator.shownList(slot: String = "main") = scrolls.list(visibleKey, slot)

    @Test fun listScreensComeBackToTheExactSpotAtEveryDepth() {
        val nav = Navigator()
        nav.switchTab(Tab.OPERATIONS)
        nav.leaveList(ListPos(42, 17)) // نزلت تحت خالص في العمليات
        nav.push(Screen("OperationDetail"))
        nav.leaveList(ListPos(3, 0))
        nav.push(Screen("MerchantProfile"))
        nav.leaveList(ListPos(8, 120))
        nav.push(Screen("CategoryBudget"))
        assertNull(nav.shownList(), "شاشة جديدة بتبدأ من فوق")

        nav.pop()
        assertEquals(ListPos(8, 120), nav.shownList())
        nav.pop()
        assertEquals(ListPos(3, 0), nav.shownList())
        nav.pop()
        assertEquals(ListPos(42, 17), nav.shownList(), "العمليات نفسها في نفس المكان")
    }

    @Test fun aPoppedScreenForgetsItsSpotAndCannotWriteItBack() {
        val nav = Navigator()
        nav.push(Screen("Notifications"))
        nav.leaveList(ListPos(5, 5))
        nav.push(Screen("OperationDetail"))
        val gone = nav.visibleKey
        nav.pop()
        nav.pop()
        assertEquals(0, nav.scrolls.size, "اللي اتشال من السجل مكانه اتمسح")
        // الشاشة اللي خرجت بتتقفل **بعد** الرجوع — كتابتها المتأخرة ما بتترجعش
        nav.scrolls.saveList(gone, "main", ListPos(9, 9))
        assertNull(nav.scrolls.list(gone, "main"))
        nav.push(Screen("Notifications"))
        assertNull(nav.shownList(), "نفس الشاشة من جديد = خانة جديدة بتبدأ من فوق")
    }

    @Test fun eachOfTheFourTabsKeepsItsOwnPlace() {
        val nav = Navigator()
        nav.leaveList(ListPos(6, 0)) // الرئيسية
        nav.switchTab(Tab.OPERATIONS)
        nav.leaveList(ListPos(30, 4))
        nav.switchTab(Tab.PEOPLE)
        nav.scrolls.savePlain(nav.visibleKey, "main", 560) // المسرح مطوي والأسطوانة مفرودة
        nav.switchTab(Tab.INVESTMENT)
        nav.leaveList(ListPos(2, 40))

        nav.switchTab(Tab.HOME)
        assertEquals(ListPos(6, 0), nav.shownList())
        nav.switchTab(Tab.PEOPLE)
        assertEquals(560, nav.scrolls.plain(nav.visibleKey, "main"))
        nav.switchTab(Tab.INVESTMENT)
        assertEquals(ListPos(2, 40), nav.shownList())
        nav.back() // من تبويب غير الرئيسية ⇒ الرئيسية
        assertEquals(ListPos(6, 0), nav.shownList())
    }

    @Test fun tabSwitchDropsInnerScreensButKeepsTheTabRoot() {
        val nav = Navigator()
        nav.switchTab(Tab.PEOPLE)
        nav.scrolls.savePlain(nav.visibleKey, "main", 420)
        nav.push(Screen("PersonProfile"))
        nav.leaveList(ListPos(4, 0))
        nav.switchTab(Tab.HOME)
        assertEquals(1, nav.scrolls.size, "الشاشات الداخلية اتمسحت ومكانها معاها")
        nav.switchTab(Tab.PEOPLE)
        assertEquals(420, nav.scrolls.plain(nav.visibleKey, "main"))
    }

    @Test fun replacePopToAndResetForgetWhatLeftTheStack() {
        val nav = Navigator()
        nav.push(Screen("A"))
        nav.leaveList(ListPos(1, 0))
        nav.push(Screen("B"))
        nav.leaveList(ListPos(2, 0))
        nav.replace(Screen("B2"))
        nav.leaveList(ListPos(3, 0))
        nav.push(Screen("C"))
        nav.popTo("A")
        assertEquals(ListPos(1, 0), nav.shownList())
        assertEquals(1, nav.scrolls.size)
        nav.reset()
        assertEquals(0, nav.scrolls.size, "الدخول أو تبديل البلد ⇒ كله من الأول")
    }

    @Test fun twoScrollablesInOneScreenAreKeptApart() {
        val nav = Navigator()
        nav.push(Screen("Calendar"))
        nav.leaveList(ListPos(10, 0), slot = "days")
        nav.leaveList(ListPos(1, 8), slot = "main")
        nav.push(Screen("OperationDetail"))
        nav.pop()
        assertEquals(ListPos(10, 0), nav.shownList("days"))
        assertEquals(ListPos(1, 8), nav.shownList("main"))
    }

    @Test fun whileRestoringTheWantedSpotIsWhatGetsSaved() {
        // القايمة لسه فاضية (المحتوى بيحمّل) ووقفت فوق — لو خرجت ساعتها، المحفوظ هو المكان المطلوب مش أول الصفحة
        val r = ScrollRestore(ListPos(40, 12))
        assertTrue(r.pending)
        assertEquals(ListPos(40, 12), r.toSave(ListPos(0, 0)))
        r.finish()
        assertFalse(r.pending)
        assertEquals(ListPos(5, 0), r.toSave(ListPos(5, 0)))
        // أول ما تلمس الشاشة بيقف — واللي تحت صباعك هو اللي بيتحفظ
        val touched = ScrollRestore(300)
        touched.touched()
        assertEquals(120, touched.toSave(120))
        assertEquals(7, ScrollRestore<Int>(null).toSave(7), "مفيش مكان محفوظ ⇒ المكان الحالي")
    }
}
