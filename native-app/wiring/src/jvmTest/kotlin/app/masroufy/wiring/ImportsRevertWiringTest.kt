package app.masroufy.wiring

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ImportBatch
import app.masroufy.core.ImportBatchState
import app.masroufy.core.ImportCounts
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.MergeRestore
import app.masroufy.core.ReviewState
import app.masroufy.core.SourceRecord
import app.masroufy.core.Space
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryAccount
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * مراجعة r06 money-1: التراجع عن دفعة استيراد **من التجميع الحقيقي** (`ImportsGraph.batches`) بيشغّل تراجعات آثار التسجيل —
 * السطر المدموج بيرجع تاريخه وترتيبه، والداخلة التابعة اللي اتمسحت بتفك المصروف. قبل التوصيل الاتنين كانوا بيفضلوا متغيّرين.
 * كل الأرقام والأسامي مخترعة.
 */
class ImportsRevertWiringTest {
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val bank = Wallet("w-1", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")

    private fun txn(id: String, date: String, order: Int, amount: Long, dir: Direction, kind: EconomicKind) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = order, economicKind = kind, economicKindConfirmed = true,
        observedDirection = dir, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "c", updatedAt = "c", walletId = "w-1",
    )

    private fun record(id: String, batch: String, txn: String, row: Int, merge: MergeRestore? = null, state: MatchingState = MatchingState.NEW) =
        SourceRecord(id, batch, "acct-1", null, "h-$id", row, "line $id", txn, state, "r", mergeUndo = merge)

    @Test fun revertingABatchRestoresTheMergedLineAndReleasesTheLinkedExpense() = runBlocking<Unit> {
        // «kept» جت من رسالة، وسطر الكشف (الدفعة b-1) اندمج فيها وغيّر تاريخها من 05 لـ06 وترتيبها لـ7
        val kept = txn("kept", "2026-09-06", 7, 4_500, Direction.OUT, EconomicKind.PURCHASE)
        val meal = txn("meal", "2026-09-10", 1, 12_000, Direction.OUT, EconomicKind.PURCHASE).copy(linkedInflowMinor = 6_000)
        val friend = txn("friend", "2026-09-11", 2, 6_000, Direction.IN, EconomicKind.UNCLASSIFIED).copy(linkedExpenseId = "meal")
        val repos = memorySpaceRepositories(listOf(bank), transactions = listOf(kept, meal, friend))
        repos.importBatches.save(
            ImportBatch("b-1", ImportSourceType.CSV_PREVIEW, "hash-1", "test.csv", "2026-09-12T10:00:00Z", ImportBatchState.COMMITTED, ImportCounts(2, 1, 1, 0, 0, 0)),
        )
        repos.sourceRecords.saveMany(
            listOf(
                record("sms-1", "b-sms", "kept", 0),
                record("csv-7", "b-1", "kept", 7, MergeRestore("2026-09-05", 3, null, mergedOccurredAt = "2026-09-06"), MatchingState.DUPLICATE),
                record("csv-8", "b-1", "friend", 8),
            ),
        )
        val session = object : SessionLinks {
            override val account = MemoryAccount()
            override fun spaces() = listOf(saudi to repos)
            override fun switchSpace(spaceId: String) = false
        }
        val env = memoryEnv()
        val imports = SpaceGraph(saudi, repos, env, session, app.masroufy.usecase.LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis)).imports as ImportsGraph

        val plan = imports.batches.execute("b-1")

        assertEquals(listOf("friend"), plan.toDelete)
        val all = repos.transactions.findByIds(listOf("kept", "meal", "friend")).associateBy { it.id }
        assertEquals("2026-09-05", all.getValue("kept").occurredAt, "الدمج بيرجّع التاريخ")
        assertEquals(3, all.getValue("kept").sourceOrder, "والترتيب")
        assertNull(all.getValue("meal").linkedInflowMinor, "المصروف بيرجع بمبلغه كله بعد مسح الداخلة التابعة")
        assertNull(all["friend"])
    }
}
