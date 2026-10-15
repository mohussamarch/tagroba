package app.masroufy.ui.screens.operations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** وضع «تحديد» في «العمليات» (قايمة المالك §6.5 بند 15) — الحالة بس. */
class OpsSelectionTest {
    private val visible = listOf("a", "b", "c")

    @Test fun togglingOutsideTheModeDoesNothing() {
        val idle = OpsSelection()
        assertEquals(idle, idle.toggle("a"))
        assertEquals(idle, idle.toggleAll(visible))
        assertFalse(idle.active)
    }

    @Test fun startWithRowFromMenuThenToggle() {
        val s = OpsSelection().start("b")
        assertTrue(s.active)
        assertEquals(listOf("b"), s.ids)
        val more = s.toggle("a").toggle("c")
        assertEquals(listOf("b", "a", "c"), more.ids)
        assertEquals(listOf("a", "c"), more.toggle("b").ids)
        assertTrue(OpsSelection().start().active)
        assertEquals(0, OpsSelection().start().count)
    }

    /** «تحديد» من قايمة الصف (جولة 0.4): الوضع بيبدأ والصف ده محسوب (1)، وبيفضل بعد إعادة القراية طالما ظاهر. */
    @Test fun menuSelectStartsAtOneAndSurvivesTheReload() {
        val s = OpsSelection().start("b")
        assertEquals(1, s.count)
        assertTrue(s.isSelected("b"))
        assertEquals(1, s.keepOnly(visible).count)
        assertFalse(s.allSelected(visible))
    }

    @Test fun selectAllVisibleAndBack() {
        val s = OpsSelection().start("x").toggle("a")
        val all = s.toggleAll(visible)
        assertEquals(listOf("x", "a", "b", "c"), all.ids)
        assertTrue(all.allSelected(visible))
        val none = all.toggleAll(visible)
        assertEquals(listOf("x"), none.ids)
        assertFalse(OpsSelection().start().allSelected(emptyList()))
    }

    @Test fun reloadDropsVanishedAndCancelLeaves() {
        val s = OpsSelection().start("a").toggle("gone")
        assertEquals(listOf("a"), s.keepOnly(visible).ids)
        val same = OpsSelection().start("a")
        assertTrue(same.keepOnly(visible) === same)
        assertEquals(OpsSelection(), s.cancel())
    }
}
