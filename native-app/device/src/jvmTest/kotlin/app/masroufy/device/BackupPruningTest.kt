package app.masroufy.device

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupPruningTest {
    private fun f(name: String, at: Long) = BackupFileInfo(name, at)

    @Test
    fun keepsTheNewestTwoIncludingTheOneJustWritten() {
        val files = listOf(f("a", 10), f("b", 20), f("c", 30), f("new", 40))
        assertEquals(listOf("b", "a"), BackupPruning.toDelete(files, "new"))
    }

    @Test
    fun fewerThanTwoDeletesNothing() {
        assertEquals(emptyList(), BackupPruning.toDelete(listOf(f("new", 1)), "new"))
        assertEquals(emptyList(), BackupPruning.toDelete(listOf(f("a", 1), f("new", 2)), "new"))
    }

    @Test
    fun justWrittenIsNeverDeletedEvenWithAnOlderClock() {
        val files = listOf(f("a", 100), f("b", 200), f("new", 1))
        assertEquals(listOf("a"), BackupPruning.toDelete(files, "new"))
    }

    @Test
    fun tiesBreakByName() {
        val files = listOf(f("2026-01-01", 5), f("2026-01-02", 5), f("new", 5))
        assertEquals(listOf("2026-01-01"), BackupPruning.toDelete(files, "new"))
    }
}
