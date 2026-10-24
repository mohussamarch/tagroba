package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-delete (§118-1) — مسح الجمعية بمدخلاتها. مفيش أرشفة للجمعيات ⇒ اختيارين. كل الأسامي والمبالغ مخترعة. */
class RemoveRoscaTest {
    private val rosca = Rosca("r-1", "جمعية تجربة", Currency.SAR, 500_00, 1, "2026-01-01", 10, listOf(3), 5_000_00)

    private suspend fun kit(entries: Boolean = true) = RemovalKit().apply {
        roscas.save(rosca)
        if (entries) {
            txns.saveMany(listOf(op("c-1", 500_00, EconomicKind.INTERNAL_TRANSFER), op("pay", 5_000_00, EconomicKind.INTERNAL_TRANSFER, Direction.IN), op("x", 30_00)))
            this.entries.saveMany(listOf(RoscaEntry("e-1", "r-1", "c-1", RoscaEntryKind.CONTRIBUTION, 500_00), RoscaEntry("e-2", "r-1", "pay", RoscaEntryKind.PAYOUT, 5_000_00)))
        }
    }

    @Test
    fun noEntries_deleteThenUndo() = runBlocking {
        val k = kit(entries = false)
        assertEquals(0, k.removals.rosca.ask("r-1").linkedCount)
        val done = k.removals.rosca.remove("r-1", RemoveChoice.DELETE)
        assertTrue(k.roscas.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(rosca), k.roscas.listAll())
    }

    @Test
    fun keep_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        assertEquals(listOf(RemoveChoice.KEEP_OPERATIONS, RemoveChoice.WITH_OPERATIONS), k.removals.rosca.ask("r-1").choices)
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.rosca.remove("r-1", RemoveChoice.KEEP_OPERATIONS)
        assertTrue(k.roscas.listAll().isEmpty() && k.entries.listByRosca("r-1").isEmpty())
        assertEquals(totals, k.totals())
        assertEquals(2, k.all().count { it.sourceRemoved == "rosca:جمعية تجربة" })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(2, k.entries.listByRosca("r-1").size)
    }

    @Test
    fun withOperations_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val done = k.removals.rosca.remove("r-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("x"), k.all().map { it.id })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(rosca), k.roscas.listAll())
        assertEquals(2, k.entries.listByRosca("r-1").size)
    }
}
