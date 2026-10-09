package app.masroufy.ui.nav

import app.masroufy.ui.screens.buildRegistry
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * جدول الشاشات اللي المناطق بتسجّل فيه من ملفاتها (ARCHITECTURE §31.31): القطع المشتركة بين المناطق ([Slots]) · اللوحات · الشاشات —
 * والتسجيل مرتين بيقع وقت البناء (مش في صمت)، والمش متسجّل بيبان «قيد البناء» بدل ما التطبيق يقع.
 */
class RouteRegistryTest {
    private object AddPerson : SheetRoute {
        override val name = "AddPersonSheet"
    }

    @Test fun slotsAreRegisteredOnceByTheirOwningArea() {
        val r = RouteRegistry()
        assertFalse(r.hasSlot(Slots.DUES), "قطعة مش متسجلة ⇒ «قيد البناء» مكانها")
        r.slot(Slots.DUES) { }
        assertTrue(r.hasSlot(Slots.DUES))
        assertFailsWith<IllegalStateException> { r.slot(Slots.DUES) { } }
    }

    @Test fun sheetsRegisterOnceAndAreKnownByType() {
        val r = RouteRegistry()
        assertFalse(r.has(AddPerson))
        r.sheet<AddPerson> { _, _ -> }
        assertTrue(r.has(AddPerson))
        assertFailsWith<IllegalStateException> { r.sheet<AddPerson> { _, _ -> } }
    }

    @Test fun tabRootsCannotBeRegisteredTwice() {
        val r = buildRegistry()
        assertFailsWith<IllegalStateException> { r.tabRoot(Tab.HOME) { } }
    }

    @Test fun pushingASheetRouteOpensItAsASheet() {
        val nav = Navigator()
        nav.push(AddPerson)
        assertTrue(nav.atTabRoot, "اللوحة مش شاشة داخلية — الشريطين بيفضلوا")
        assertTrue(nav.sheets.single().route == AddPerson)
        assertTrue(nav.back())
        assertTrue(nav.sheets.isEmpty())
    }
}
