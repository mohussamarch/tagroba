package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals

/** صف «أهم 5» (OVERRIDES §84): الافتراضي · ترتيبك · السحب · «×» و«تراجع» · «+». معرّفات مخترعة. */
class PeopleRowTest {
    private fun c(id: String, dep: Boolean = false, n: Int = 0) = PeopleRowCandidate(id, dep, n)

    @Test fun defaultIsDependentsFirstThenMostInteractedAndStableOnTies() {
        val all = listOf(c("a", n = 3), c("b", dep = true, n = 1), c("c", n = 9), c("d", dep = true, n = 4), c("e", n = 3), c("f"), c("g", n = 3))
        assertEquals(listOf("d", "b", "c", "a", "e"), defaultPeopleRow(all))
        assertEquals(listOf("d", "b", "c", "a", "e", "g", "f"), rankForPeopleRow(all))
    }

    @Test fun manualOrderWinsAndDropsGoneOrArchivedPeople() {
        val live = listOf(c("a"), c("b"), c("c"), c("d", dep = true))
        assertEquals(PeopleRow(listOf("d", "a", "b", "c"), custom = false), peopleRow(null, live))
        assertEquals(PeopleRow(listOf("c", "a"), custom = true), peopleRow(listOf("c", "x-gone", "a", "c"), live))
        // شال الكل = ترتيب بإيده فاضي، مش رجوع للافتراضي
        assertEquals(PeopleRow(emptyList(), custom = true), peopleRow(emptyList(), live))
    }

    @Test fun dragTakesTheSpotAndTheRestSlide() {
        val row = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "c", "d", "a", "e"), rowMove(row, "a", 3))
        assertEquals(listOf("e", "a", "b", "c", "d"), rowMove(row, "e", 0))
        assertEquals(row, rowMove(row, "c", 2))
        assertEquals(row, rowMove(row, "zz", 1))
        assertEquals(listOf("b", "c", "d", "e", "a"), rowMove(row, "a", 99))
    }

    @Test fun removeThenUndoRestoresTheSameIndex() {
        val row = listOf("a", "b", "c", "d")
        val gone = rowRemove(row, "b")
        assertEquals(listOf("a", "c", "d"), gone)
        assertEquals(row, rowRestore(gone, "b", 1))
        assertEquals(gone, rowRestore(gone, "c", 0), "موجود ⇒ زي ما هو")
        assertEquals(listOf("a", "c", "d", "b"), rowRestore(gone, "b", 9), "الصف قصر ⇒ آخره")
    }

    @Test fun appendGoesToTheEndAndNeverPassesFive() {
        assertEquals(listOf("a", "b", "z"), rowAppend(listOf("a", "b"), "z"))
        val full = listOf("a", "b", "c", "d", "e")
        assertEquals(5, PEOPLE_ROW_MAX)
        assertEquals(full, rowAppend(full, "z"))
        assertEquals(full, rowRestore(full, "z", 0))
        assertEquals(listOf("a"), rowAppend(listOf("a"), "a"))
    }
}
