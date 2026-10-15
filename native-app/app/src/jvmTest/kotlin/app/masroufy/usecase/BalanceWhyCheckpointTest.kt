package app.masroufy.usecase

import app.masroufy.core.BalanceCheckpoint
import app.masroufy.core.BalanceSource
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.Wallet
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * «افهم رصيدك» + «حدّث الرصيد» (دمج r04-balance-why مع r04-balance-now): فرق كل نقطة سطر «تعديلات» لوحده، والمجموع = «معك الآن» بالهللة.
 * كل المبالغ مخترعة.
 */
class BalanceWhyCheckpointTest {
    private fun cp(id: String, day: String, stated: Long) =
        BalanceCheckpoint(id = id, day = day, atInstant = null, statedMinor = stated, recordedDiffMinor = 0L, createdAt = "${day}T12:00:00.000Z")

    private val bank = Wallet(
        "w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2025-01-01",
        // قبل الأولى: 500,000 − 20,000 = 480,000 ⇒ فرق −10,000 · قبل التانية: 470,000 + 5,000 = 475,000 ⇒ فرق +5,000
        checkpoints = listOf(cp("cp-1", "2025-03-01", 470_000), cp("cp-2", "2025-05-01", 480_000)),
    )

    @Test fun checkpointDifferencesAreTheirOwnAdjustmentLineAndTheTotalMatchesWithYouNow() = runBlocking<Unit> {
        val w = LedgerWorld(bank = bank)
        w.add(w.txn("a", "2025-02-01", 20_000), ImportSourceType.CSV_PREVIEW)
        w.add(w.txn("b", "2025-04-01", 5_000, Direction.IN), ImportSourceType.CSV_PREVIEW)

        val why = w.explain().explain(bank.id, "2025-06-01")!!
        val adj = why.totals.single { it.source == BalanceSource.ADJUSTMENT }
        assertEquals(2, adj.count)
        assertEquals(5_000L, adj.inMinor)
        assertEquals(10_000L, adj.outMinor)
        assertEquals(480_000L, why.computedMinor)
        assertEquals(w.shown("2025-06-01"), why.computedMinor, "same number as «معك الآن»")
    }

    @Test fun onlyCheckpointsUpToTheDayCount() = runBlocking<Unit> {
        val w = LedgerWorld(bank = bank)
        w.add(w.txn("a", "2025-02-01", 20_000), ImportSourceType.CSV_PREVIEW)
        w.add(w.txn("b", "2025-04-01", 5_000, Direction.IN), ImportSourceType.CSV_PREVIEW)

        val march = w.explain().explain(bank.id, "2025-03-15")!!
        val adj = march.totals.single { it.source == BalanceSource.ADJUSTMENT }
        assertEquals(1, adj.count)
        assertEquals(10_000L, adj.outMinor)
        assertEquals(470_000L, march.computedMinor)
        assertEquals(w.shown("2025-03-15"), march.computedMinor)

        val january = w.explain().explain(bank.id, "2025-01-15")!!
        assertNull(january.totals.firstOrNull { it.source == BalanceSource.ADJUSTMENT }, "no checkpoint yet ⇒ no adjustment line")
    }
}
