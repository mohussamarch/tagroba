package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SmsStatementPair
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «راجع المكرر» + «صلّح رسايل البنك القديمة» (دمج r04-balance-why مع r04-sms-fix): زوج رسالة⇄كشف بعكس الاتجاه (تحويل بين حساباتك اتقرا
 * غلط من الرسالة) بيظهر في المكرر لو القاعدة طلّعته، والدمج بيقبله بس منها وبياخد اتجاه الكشف. كل المبالغ مخترعة.
 */
class BalanceWhyRematchTest {
    @Test fun anOppositeDirectionPairShowsInReviewAndMergesToTheStatementDirection() = runBlocking<Unit> {
        val w = LedgerWorld()
        w.add(w.txn("s1", "2025-03-02", 30_000, Direction.IN, createdAt = "2026-10-01T00:00:00.000Z"), ImportSourceType.SMS)
        w.add(w.txn("t1", "2025-03-02", 30_000, Direction.OUT, createdAt = "2026-10-02T00:00:00.000Z"), ImportSourceType.CSV_PREVIEW)
        val pair = SmsStatementPair("s1", "t1", oppositeDirection = true)

        val plain = w.explain().explain(w.bank.id, "2025-06-01")!!
        assertTrue(plain.review.duplicates.isEmpty(), "opposite direction alone is never a likely duplicate")
        val why = ExplainBalance(ExplainBalanceDeps(w.txns, w.sources, w.batches, w.wallets, rematch = { listOf(pair) })).explain(w.bank.id, "2025-06-01")!!
        val p = why.review.duplicates.single()
        assertTrue(p.oppositeDirection)
        assertEquals("s1", p.sms.transactionId)
        assertEquals("t1", p.statement.transactionId)

        val refused = w.merger().merge("s1", "t1")
        assertEquals(MergeRefusal.NOT_A_PAIR, (refused as MergeResult.Refused).reason, "without the re-match rule it stays refused")
        val done = mergerOf(w.txns, w.sources, w.batches, w.backup, rematch = { listOf(pair) }).merge("s1", "t1") as MergeResult.Done
        val kept = w.txns.findByIds(listOf(done.keptId)).single()
        assertEquals(Direction.OUT, kept.observedDirection, "the statement direction wins")
        assertEquals(500_000L - 30_000L, w.shown("2025-06-01"))
    }

    @Test fun aPairTheRuleDoesNotReturnIsStillRefused() = runBlocking<Unit> {
        val w = LedgerWorld()
        w.add(w.txn("s1", "2025-03-02", 30_000, Direction.IN), ImportSourceType.SMS)
        w.add(w.txn("t1", "2025-03-02", 30_000, Direction.OUT), ImportSourceType.CSV_PREVIEW)
        val merger = mergerOf(w.txns, w.sources, w.batches, w.backup, rematch = { emptyList() })
        assertEquals(MergeRefusal.NOT_A_PAIR, (merger.merge("s1", "t1") as MergeResult.Refused).reason)
    }
}
