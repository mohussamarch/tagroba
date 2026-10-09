package app.masroufy.ui.nav

import app.masroufy.ui.screens.buildRegistry
import app.masroufy.ui.screens.home.CalendarRoute
import app.masroufy.ui.screens.home.NotificationsRoute
import app.masroufy.ui.screens.more.AccountRoute
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.screens.more.SpacesRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** التنقل من غير مكتبة — نفس قواعد النموذج (`App.dc.html`): تبويب = تبديل والرجوع بيتمسح · داخلية = دخول · رجوع = خروج · اللوحة الأول. */
class NavigatorTest {
    private data class PersonProfile(val id: String) : Route {
        override val name = "PersonProfile"
    }

    private object Detail : Route {
        override val name = "OperationDetail"
    }

    private object Picker : SheetRoute {
        override val name = "CategoryPicker"
    }

    @Test fun startsAtHomeRoot() {
        val nav = Navigator()
        assertEquals(Tab.HOME, nav.tab)
        assertTrue(nav.atTabRoot)
        assertNull(nav.top)
        assertEquals("tab-HOME", nav.visibleKey)
        assertEquals(NavMotion.NONE, nav.motion)
    }

    @Test fun pushEntersAndPopExits() {
        val nav = Navigator()
        nav.push(PersonProfile("p1"))
        nav.push(Detail)
        assertEquals(listOf("PersonProfile", "OperationDetail"), nav.stack.map { it.route.name })
        assertEquals(NavMotion.PUSH, nav.motion)
        assertFalse(nav.atTabRoot)
        assertTrue(nav.pop())
        assertEquals(NavMotion.POP, nav.motion)
        assertEquals(PersonProfile("p1"), nav.top?.route)
        assertTrue(nav.pop())
        assertFalse(nav.pop(), "على التبويب نفسه مفيش رجوع")
    }

    @Test fun eachEntryHasItsOwnKeySoTheSameScreenTwiceKeepsTwoStates() {
        val nav = Navigator()
        nav.push(PersonProfile("p1"))
        val first = nav.visibleKey
        nav.push(PersonProfile("p2"))
        val second = nav.visibleKey
        assertTrue(first != second)
        nav.pop()
        assertEquals(first, nav.visibleKey, "الرجوع بيرجّع نفس المفتاح ⇒ نفس الحالة المحفوظة")
    }

    @Test fun tabSwitchClearsTheStackLikeThePrototype() {
        val nav = Navigator()
        nav.push(Detail)
        nav.switchTab(Tab.PEOPLE)
        assertEquals(Tab.PEOPLE, nav.tab)
        assertTrue(nav.atTabRoot)
        assertEquals(NavMotion.TAB, nav.motion)
        assertEquals("tab-PEOPLE", nav.visibleKey, "كل تبويب ليه مفتاح ثابت ⇒ حالته بتتحفظ لما ترجعله")
        nav.push(Detail)
        nav.switchTab(Tab.PEOPLE)
        assertTrue(nav.atTabRoot, "نفس التبويب من شاشة داخلية ⇒ يرجع لجذره")
        val before = nav.motion
        nav.switchTab(Tab.PEOPLE)
        assertEquals(before, nav.motion, "نفس التبويب وإنت عليه ⇒ ولا حاجة")
    }

    @Test fun backOrderIsSheetThenScreenThenHomeThenExit() {
        val nav = Navigator(Tab.INVESTMENT)
        nav.push(Detail)
        nav.push(Picker)
        assertEquals(1, nav.sheets.size, "اللوحة بتتفتح فوق مش في الرصة")
        assertEquals(1, nav.stack.size)
        assertTrue(nav.back())
        assertTrue(nav.sheets.isEmpty())
        assertTrue(nav.back())
        assertTrue(nav.atTabRoot)
        assertTrue(nav.back())
        assertEquals(Tab.HOME, nav.tab, "من تبويب تاني ⇒ الرئيسية")
        assertFalse(nav.back(), "الرئيسية ⇒ التطبيق يتقفل")
    }

    @Test fun popToReplaceAndReset() {
        val nav = Navigator()
        nav.push(PersonProfile("p1"))
        nav.push(Detail)
        nav.push(PersonProfile("p2"))
        assertTrue(nav.popTo("OperationDetail"))
        assertEquals(Detail, nav.top?.route)
        assertFalse(nav.popTo("Missing"))
        nav.replace(PersonProfile("p3"))
        assertEquals(listOf(PersonProfile("p1"), PersonProfile("p3")), nav.stack.map { it.route })
        nav.open(Picker)
        nav.reset()
        assertTrue(nav.atTabRoot)
        assertTrue(nav.sheets.isEmpty())
        assertEquals(Tab.HOME, nav.tab)
    }

    @Test fun closeASpecificSheet() {
        val nav = Navigator()
        assertFalse(nav.close())
        nav.open(Picker)
        assertTrue(nav.close(Picker))
        assertFalse(nav.close(Picker))
    }

    @Test fun registryHasTheFourTabRootsAndRejectsDuplicates() {
        val registry = buildRegistry()
        for (tab in Tab.entries) assertTrue(registry.hasRoot(tab), tab.name)
        assertFalse(registry.has(Detail), "شاشة مش متسجلة ⇒ «قيد البناء» بدل الوقوع")
        val r = RouteRegistry()
        r.screen<PersonProfile> { }
        assertFailsWith<IllegalStateException> { r.screen<PersonProfile> { } }
        assertTrue(r.has(PersonProfile("x")))
        r.sheet<Picker> { _, _ -> }
        assertTrue(r.has(Picker))
    }

    @Test fun shellRoutesUseThePrototypeBoardNames() {
        assertEquals(
            listOf("More", "Account", "Spaces", "Notifications", "Calendar"),
            listOf(MoreRoute, AccountRoute, SpacesRoute, NotificationsRoute, CalendarRoute).map { it.name },
        )
        assertEquals(listOf("Main", "Operations", "People", "Investment"), Tab.entries.map { it.boardName })
    }
}
