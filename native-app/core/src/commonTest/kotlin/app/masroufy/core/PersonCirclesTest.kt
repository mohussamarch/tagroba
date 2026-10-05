package app.masroufy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** دواير الأشخاص والصلات بينهم (جلسة 16) — المنطق النقي، أسماء مخترعة. بيشتغل على محاكي الآيفون كمان (commonTest). */
class PersonCirclesTest {
    private val people = listOf(Person("p-1", "أم وهمية"), Person("p-2", "بنت وهمية"), Person("p-3", "زميل وهمي", archived = true))

    @Test fun relationIdIsTheSameWhicheverOrder() {
        assertEquals(personRelationId("p-1", "p-2"), personRelationId("p-2", "p-1"))
        val ab = newPersonRelation("p-2", "p-1", "أمها", people, "c")
        val ba = newPersonRelation("p-1", "p-2", "أمها", people, "c")
        assertEquals(ab, ba, "الصلة نفسها مهما كان الترتيب")
        assertEquals("p-1", ab.personAId)
        assertEquals("p-2", ab.personBId)
        assertNotEquals(personRelationId("a-b", "c"), personRelationId("a", "b-c"), "معرّفين مختلفين ما يتشابهوش")
    }

    @Test fun aPersonCannotBeRelatedToThemselves() {
        assertFailsWith<PersonCircleError> { personRelationId("p-1", "p-1") }
        assertFailsWith<PersonCircleError> { newPersonRelation("p-1", "p-1", null, people, "c") }
    }

    @Test fun bothPeopleMustExistAndBeActive() {
        assertFailsWith<PersonCircleError> { newPersonRelation("p-1", "p-404", null, people, "c") }
        assertFailsWith<PersonCircleError> { newPersonRelation("p-404", "p-1", null, people, "c") }
        assertFailsWith<PersonCircleError> { newPersonRelation("p-1", "p-3", null, people, "c") }
    }

    @Test fun archivingHidesRelationsWithoutDeletingThem() {
        val stored = listOf(newPersonRelation("p-1", "p-2", null, people, "c"), PersonRelation(personRelationId("p-1", "p-3"), "p-1", "p-3", null, "c"))
        assertEquals(listOf("p-2"), visibleRelations(stored, people).map { it.personBId })
        val back = people.map { it.copy(archived = false) }
        assertEquals(2, visibleRelations(stored, back).size, "رجوعه من الأرشيف بيرجّع صلته")
        val gone = people.filter { it.id != "p-2" }
        assertTrue(visibleRelations(stored, gone).none { it.personBId == "p-2" }, "شخص مش موجود ⇒ صلته ما بتظهرش")
    }

    @Test fun theSamePairShowsOnceEvenIfStoredTwiceInEitherOrder() {
        val first = PersonRelation("prel-x", "p-1", "p-2", "أمها", "2026-01-01")
        val flipped = PersonRelation("prel-y", "p-2", "p-1", "بنتها", "2026-02-01")
        val self = PersonRelation("prel-z", "p-1", "p-1", null, "2026-01-01")
        assertEquals(listOf(first), visibleRelations(listOf(flipped, first, self), people), "الأقدم يكسب، والصلة لنفس الشخص ما بتظهرش")
    }

    @Test fun relationLabelIsCleanedAndLimited() {
        assertEquals("أخي الكبير", cleanRelationLabel("  أخي   الكبير "))
        assertNull(cleanRelationLabel("   "))
        assertEquals(30, cleanRelationLabel("ا".repeat(RELATION_LABEL_MAX))!!.length)
        assertFailsWith<PersonCircleError> { cleanRelationLabel("ا".repeat(RELATION_LABEL_MAX + 1)) }
    }

    @Test fun personWithoutCircleShowsAsOther() {
        assertEquals(PersonCircle.OTHER, circleOf(null))
        assertEquals(PersonCircle.OTHER, circleOf(PersonProfile("p-1", null, "جاري", "c")))
        assertEquals(PersonCircle.FAMILY, circleOf(PersonProfile("p-1", PersonCircle.FAMILY, null, "c")))
        assertEquals(PersonCircle.WORK, PersonCircle.fromWire("work"))
    }
}
