package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.MatchingState
import app.masroufy.core.SaudiBankSmsReader
import app.masroufy.core.SourceRecord
import app.masroufy.core.Wallet
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «صلّح رصيدي» (2026-10-16): «طبّق» الواحدة = نفس نتيجة الأدوات واحدة واحدة بالظبط، و«تراجع» واحد بيرجّع كل حاجة. كل المبالغ والأسامي مخترعة.
 * المحفظة: رصيد بداية 5,000.00 · رسالة «حوالة بين حساباتك» اتسجلت طالعة 500.00 (الصح داخلة) · رسالة وسطر كشف لنفس الـ200.00.
 */
class FixMyBalanceTest {
    private val today = "2026-10-15"
    private val bToA = "حوالة بين حساباتك\nمن:1111\nالى:2222\nمبلغ:SAR 500.00"

    private class Tools(val w: LedgerWorld, val repair: RepairOldSms, val merge: MergeDuplicates, val explain: ExplainBalance, val balance: UpdateBalance) {
        val guided = FixMyBalance(FixMyBalanceDeps(repair, merge, explain, balance))
        suspend fun computed() = balance.preview(w.bank.id, null).computedMinor
        suspend fun direction(id: String) = w.txns.findByIds(listOf(id)).single().observedDirection
    }

    private suspend fun world(): Tools {
        val w = LedgerWorld(bank = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2025-01-01", "2222", null))
        w.add(w.txn("s1", "2026-03-05", 20_000), ImportSourceType.SMS)
        w.add(w.txn("c1", "2026-03-05", 20_000, createdAt = "2026-10-02T00:00:00.000Z"), ImportSourceType.CSV_PREVIEW)
        w.txns.saveMany(listOf(w.txn("t1", "2026-03-03", 50_000)))
        w.sources.saveMany(listOf(SourceRecord("r1", "b-sms", "بنك وهمي", "SMS:r1", "hash-r1", 1, bToA, "t1", MatchingState.NEW, "")))
        val clock = FixedClock("2026-10-15T00:00:00Z")
        val repair = RepairOldSms(w.txns, w.sources, w.batches, w.wallets, listOf(SaudiBankSmsReader), w.backup, clock, PassthroughUnitOfWork())
        val balance = UpdateBalance(w.wallets, w.txns, MemoryCategoryRepository(), SequentialIdGenerator(), clock)
        return Tools(w, repair, w.merger(), w.explain(), balance)
    }

    private fun all(p: FixPreview, stated: Long?) = FixChoice(p.moves.map { it.key }.toSet(), p.unrecorded.map { it.key }.toSet(), p.pairs.map { it.key }.toSet(), stated)

    @Test fun oneApplyEqualsTheToolsOneByOne() = runBlocking<Unit> {
        val a = world()
        val p = a.guided.preview(a.w.bank.id, today)
        assertEquals(410_000L, p.beforeMinor)
        assertEquals(listOf(100_000L), p.moves.map { it.effectMinor }, "طالعة 500 بقت داخلة 500")
        assertEquals(listOf(20_000L), p.pairs.map { it.effectMinor }, "نسخة الرسالة بتتشال")
        val estimate = fixSummary(p, all(p, 600_000))
        assertEquals(FixSummary(410_000, 530_000, 70_000), estimate)
        val r = a.guided.apply(p, all(p, 600_000))!!
        assertEquals(Triple(1, 1, 0), Triple(r.repaired, r.merged, r.refused))

        val b = world()
        b.repair.applyAll(setOf("t1"), emptySet())
        val pair = b.explain.explain(b.w.bank.id, today)!!.review.duplicates.single()
        assertTrue(b.merge.merge(pair.sms.transactionId, pair.statement.transactionId) is MergeResult.Done)
        val bAfter = b.computed()
        val cp = b.balance.record(b.w.bank.id, 600_000)

        assertEquals(bAfter, r.afterMinor)
        assertEquals(b.computed(), a.computed())
        assertEquals(cp.adjustmentMinor, r.unknownMinor)
        assertEquals(b.w.shown(today), a.w.shown(today))
        assertEquals(estimate.afterMinor, r.afterMinor, "التقدير طلع زي الحقيقة هنا")
        assertEquals(b.w.txns.listByDateRange("2025-01-01", today).map { it.id to it.observedDirection }, a.w.txns.listByDateRange("2025-01-01", today).map { it.id to it.observedDirection })
    }

    @Test fun oneUndoRestoresEverything() = runBlocking<Unit> {
        val t = world()
        val before = t.computed()
        val p = t.guided.preview(t.w.bank.id, today)
        val r = t.guided.apply(p, all(p, 600_000))!!
        assertEquals(0, t.guided.undo(r.undo))
        assertEquals(before, t.computed())
        assertEquals(Direction.OUT, t.direction("t1"))
        assertTrue(t.w.wallets.findById(t.w.bank.id)!!.checkpoints.isEmpty())
        assertEquals(setOf("s1", "c1", "t1"), t.w.txns.listByDateRange("2025-01-01", today).map { it.id }.toSet())
    }

    @Test fun skippedLinesStayAndNothingChosenWritesNothing() = runBlocking<Unit> {
        val t = world()
        val p = t.guided.preview(t.w.bank.id, today)
        assertNull(t.guided.apply(p, FixChoice(emptySet(), emptySet(), emptySet(), null)))
        val r = t.guided.apply(p, FixChoice(emptySet(), emptySet(), p.pairs.map { it.key }.toSet(), null))!!
        assertEquals(0 to 1, r.repaired to r.merged)
        assertEquals(Direction.OUT, t.direction("t1"), "السطر المتخطّي ما اتلمسش")
        assertNull(r.unknownMinor)
    }
}
