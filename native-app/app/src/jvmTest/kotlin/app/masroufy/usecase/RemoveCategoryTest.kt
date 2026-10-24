package app.masroufy.usecase

import app.masroufy.core.Category
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** r06-delete (§118-1) — التصنيف: بدل «امسح عملياته» فيه «انقل العمليات لتصنيف تاني». كل الأسامي والمبالغ مخترعة. */
class RemoveCategoryTest {
    private fun cat(id: String, name: String, parent: String? = null) = Category(id, parent, name, "tag", "#111111", "#eeeeee", true, 1)
    private val food = cat("c-food", "أكل تجربة")
    private val cafe = cat("c-cafe", "قهوة تجربة")
    private val other = cat("c-other", "تاني تجربة")

    private suspend fun kit(ops: Boolean = true) = RemovalKit().apply {
        for (c in listOf(food, cafe, other)) categories.save(c)
        if (ops) txns.saveMany(listOf(op("t-1", 18_00, categoryId = "c-cafe"), op("t-2", 22_00, categoryId = "c-cafe"), op("t-3", 60_00, categoryId = "c-food")))
    }

    @Test
    fun noOps_deleteThenUndo() = runBlocking {
        val k = kit(ops = false)
        assertEquals(listOf(RemoveChoice.DELETE), k.removals.category.ask("c-cafe").choices)
        val done = k.removals.category.remove("c-cafe", RemoveChoice.DELETE)
        assertTrue(k.categories.listAll().none { it.id == "c-cafe" })
        done.undo()
        assertEquals(cafe, k.categories.listAll().single { it.id == "c-cafe" })
    }

    @Test
    fun parentWithChildren_refused() = runBlocking<Unit> {
        val k = kit()
        k.categories.save(cat("c-sub", "فرعي تجربة", parent = "c-food"))
        assertNotNull(k.removals.category.ask("c-food").refused)
    }

    @Test
    fun linked_offersArchiveMoveKeep() = runBlocking {
        val ask = kit().removals.category.ask("c-cafe")
        assertEquals(2, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.MOVE_OPERATIONS, RemoveChoice.KEEP_OPERATIONS), ask.choices)
        assertEquals(setOf("c-food", "c-other"), ask.targets.map { it.id }.toSet())
    }

    @Test
    fun archive_hidesThenUndo() = runBlocking {
        val k = kit()
        val done = k.removals.category.remove("c-cafe", RemoveChoice.ARCHIVE)
        assertEquals(false, k.categories.listAll().single { it.id == "c-cafe" }.active)
        done.undo()
        assertEquals(cafe, k.categories.listAll().single { it.id == "c-cafe" })
    }

    @Test
    fun move_operationsToAnother_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.category.remove("c-cafe", RemoveChoice.MOVE_OPERATIONS, moveTo = "c-food")
        assertEquals(totals, k.totals())
        assertTrue(k.all().all { it.categoryId == "c-food" })
        done.undo()
        assertEquals(before, k.picture())
    }

    @Test
    fun keep_uncategorized_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.category.remove("c-cafe", RemoveChoice.KEEP_OPERATIONS)
        assertEquals(totals, k.totals())
        assertEquals(2, k.all().count { it.categoryId == null && it.sourceRemoved == "category:قهوة تجربة" })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(cafe, k.categories.listAll().single { it.id == "c-cafe" })
    }
}
