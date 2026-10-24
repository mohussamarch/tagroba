package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.RecurringItem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-delete (§118-1) — مسح الاشتراك: «أرشفه» = «وقّف» · سيب العمليات · هو وعملياته. كل الأسامي والمبالغ مخترعة. */
class RemoveSubscriptionTest {
    private val sub = RecurringItem("s-1", "بث تجربة", "id:m-1", "subscription", 1, 40_00, Currency.SAR, "2026-11-05", active = true, confirmed = true)

    private suspend fun kit(charges: Boolean = true) = RemovalKit().apply {
        recurring.save(sub)
        if (charges) txns.saveMany(listOf(op("c-1", 40_00, merchantId = "m-1", day = "2026-09-05"), op("c-2", 40_00, merchantId = "m-1"), op("x", 15_00, merchantId = "m-2")))
    }

    @Test
    fun noCharges_deleteThenUndo() = runBlocking {
        val k = kit(charges = false)
        assertEquals(listOf(RemoveChoice.DELETE), k.removals.subscription.ask("s-1").choices)
        val done = k.removals.subscription.remove("s-1", RemoveChoice.DELETE)
        assertTrue(k.recurring.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(sub), k.recurring.listAll())
    }

    @Test
    fun archive_isStop_thenUndo() = runBlocking {
        val k = kit()
        val ask = k.removals.subscription.ask("s-1")
        assertEquals(2, ask.linkedCount)
        assertEquals(RemoveChoice.ARCHIVE, ask.choices.first())
        val done = k.removals.subscription.remove("s-1", RemoveChoice.ARCHIVE)
        assertEquals(false, k.recurring.listAll().single().active)
        done.undo()
        assertEquals(listOf(sub), k.recurring.listAll())
    }

    @Test
    fun keep_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.subscription.remove("s-1", RemoveChoice.KEEP_OPERATIONS)
        assertTrue(k.recurring.listAll().isEmpty())
        assertEquals(totals, k.totals())
        assertEquals(setOf("c-1", "c-2"), k.all().filter { it.sourceRemoved != null }.map { it.id }.toSet())
        done.undo()
        assertEquals(before, k.picture())
    }

    @Test
    fun withOperations_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val done = k.removals.subscription.remove("s-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("x"), k.all().map { it.id })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(sub), k.recurring.listAll())
    }
}
