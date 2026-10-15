package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.TransactionTag
import app.masroufy.memory.MemoryRepairBackup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «راجع المكرر» — «دول عملية واحدة» بآلية الدمج الموجودة (§75-10): اللي اتسجل الأول بيفضل، الكشف بيكسب التاريخ والرصيد، نسخة مؤكدة قبل
 * أي كتابة، ورفض بسببه لما مفيش نسخة أو فيه ربط أو مش زوج. **ولا حاجة بتتدمج لوحدها** — كل دمج هنا بنداء صريح.
 */
class MergeDuplicatesTest {
    private suspend fun pair(w: LedgerWorld, smsFirst: Boolean) {
        val early = "2026-09-01T00:00:00.000Z"
        val late = "2026-10-01T00:00:00.000Z"
        w.add(w.txn("sms", "2026-08-10", 4_250, createdAt = if (smsFirst) early else late).copy(categoryId = "cat-food", categoryConfirmed = true), ImportSourceType.SMS)
        w.add(w.txn("stmt", "2026-08-11", 4_250, createdAt = if (smsFirst) late else early).copy(statedBalanceMinor = 123_400), ImportSourceType.CSV_LEGACY)
    }

    @Test fun statementRecordedFirstKeepsItsTransactionAndTheMessageBecomesItsSecondSource() = runBlocking<Unit> {
        val w = LedgerWorld()
        pair(w, smsFirst = false)
        val before = w.shown("2026-09-01")!!
        val done = assertIs<MergeResult.Done>(w.merger().merge("sms", "stmt"))
        assertEquals("stmt" to "sms", done.keptId to done.removedId)
        assertEquals(listOf("stmt"), w.txns.listByDateRange("2026-01-01", "2026-12-31").map { it.id })
        val records = w.sources.listByTransactionIds(listOf("stmt"))
        assertEquals(setOf("src-sms", "src-stmt"), records.map { it.id }.toSet())
        assertEquals(MatchingState.DUPLICATE, records.single { it.id == "src-sms" }.matchingState)
        val kept = w.txns.findByIds(listOf("stmt")).single()
        assertEquals("cat-food" to true, kept.categoryId to kept.categoryConfirmed, "the owner's category moves over")
        assertEquals(before + 4_250, w.shown("2026-09-01"), "the doubled purchase is gone from the balance")
        assertTrue(w.backup.files.keys.single().startsWith("masroufy-merge-duplicates-"))
        assertTrue(w.backup.files.values.single().contains("\"removed\""))
    }

    @Test fun messageRecordedFirstStaysButTakesTheStatementDateAndBalanceWithUndoKept() = runBlocking<Unit> {
        val w = LedgerWorld()
        pair(w, smsFirst = true)
        val done = assertIs<MergeResult.Done>(w.merger().merge("sms", "stmt"))
        assertEquals("sms" to "stmt", done.keptId to done.removedId)
        val kept = w.txns.findByIds(listOf("sms")).single()
        assertEquals("2026-08-11" to 123_400L, kept.occurredAt to kept.statedBalanceMinor, "the statement wins date and stated balance")
        val moved = w.sources.listByTransactionIds(listOf("sms")).single { it.id == "src-stmt" }
        assertEquals(MergeRestore("2026-08-10", done.ticket.keptBefore.sourceOrder, null, "2026-08-11", 123_400), moved.mergeUndo)

        assertIs<UndoResult.Done>(w.merger().undo(done.ticket))
        assertEquals(setOf("sms", "stmt"), w.txns.listByDateRange("2026-01-01", "2026-12-31").map { it.id }.toSet())
        assertEquals(done.ticket.keptBefore, w.txns.findByIds(listOf("sms")).single())
        assertNull(w.sources.listByTransactionIds(listOf("stmt")).single().mergeUndo)
        assertEquals(2, w.backup.files.size, "a verified backup before the merge and before the undo")
    }

    @Test fun refusesWithoutABackupPlaceWhenLinkedOrWhenItIsNotAPair() = runBlocking<Unit> {
        val w = LedgerWorld()
        pair(w, smsFirst = false)
        assertEquals(MergeResult.Refused(MergeRefusal.NO_BACKUP), w.merger(port = null).merge("sms", "stmt"))
        assertEquals(MergeResult.Refused(MergeRefusal.GONE), w.merger().merge("sms", "nope"))
        assertEquals(MergeResult.Refused(MergeRefusal.NOT_A_PAIR), w.merger().merge("stmt", "sms"), "sides swapped")
        w.tags.saveMany(listOf(TransactionTag("tag-1", "sms", "t-trip")))
        assertEquals(MergeResult.Refused(MergeRefusal.LINKED, setOf(MergeLink.TAG)), w.merger().merge("sms", "stmt"))
        assertTrue(w.backup.files.isEmpty(), "nothing written, not even a backup")

        val far = LedgerWorld()
        far.add(far.txn("sms", "2026-08-01", 4_250), ImportSourceType.SMS)
        far.add(far.txn("stmt", "2026-08-05", 4_250), ImportSourceType.CSV_LEGACY)
        far.add(far.txn("sms-in", "2026-08-01", 4_250, Direction.IN), ImportSourceType.SMS)
        far.add(far.txn("stmt-2", "2026-08-01", 4_300), ImportSourceType.CSV_LEGACY)
        assertEquals(MergeResult.Refused(MergeRefusal.NOT_A_PAIR), far.merger().merge("sms", "stmt"), "4 days apart")
        assertEquals(MergeResult.Refused(MergeRefusal.NOT_A_PAIR), far.merger().merge("sms-in", "stmt"), "other direction")
        assertEquals(MergeResult.Refused(MergeRefusal.NOT_A_PAIR), far.merger().merge("sms", "stmt-2"), "other amount")
        assertEquals(4, far.txns.listByDateRange("2026-01-01", "2026-12-31").size)
    }

    @Test fun aFailedBackupWriteStopsBeforeAnyChange() = runBlocking<Unit> {
        val w = LedgerWorld()
        pair(w, smsFirst = false)
        val result = runCatching { w.merger(port = MemoryRepairBackup(truncate = true)).merge("sms", "stmt") }
        assertTrue(result.isFailure, "a short backup file throws")
        assertEquals(2, w.txns.listByDateRange("2026-01-01", "2026-12-31").size)
    }
}
