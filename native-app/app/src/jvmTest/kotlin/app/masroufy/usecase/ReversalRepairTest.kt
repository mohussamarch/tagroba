package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §77-D: الزوج «العملية اللي رجعت + الأصلية» بيفضل سليم مهما حصل — الوقوع بعد الحفظ (`RepairReversals` بيكمّل، ومرتين = نفس النتيجة) ·
 * التراجع عن الدفعة (`ReversalUndo` بيرجّع الأصلية) · عملية من الزوج اتمسحت أو المالك غيّر نوعها. كل الرسايل مخترعة.
 */
class ReversalRepairTest {
    private suspend fun recordedPair(w: ReturnsWorld, failAfterCommit: Boolean = false): Pair<String, String> {
        w.confirmOnScreen("buy" to purchaseWithRef())
        val original = w.all().single().id
        if (failAfterCommit) w.effectTxns.failUpdates = 1
        assertEquals(1, w.confirmOnScreen("back" to reversalWithRef()), "الدفعة اتحفظت حتى لو اللي بعد الحفظ وقع")
        return w.all().single { it.observedDirection == Direction.IN }.id to original
    }

    @Test fun aCrashAfterCommitIsRepairedOnceAndOnlyOnce() = runBlocking<Unit> {
        val w = ReturnsWorld()
        val (ret, original) = recordedPair(w, failAfterCommit = true)
        assertEquals(original, w.one(ret).reversalOfId)
        assertNull(w.one(original).reversedById, "الأصلية لسه ما اتعلّمتش")
        assertEquals(RepairOutcome(finished = 1), w.repair().run())
        val o = w.one(original)
        assertEquals(ret, o.reversedById)
        assertEquals(EconomicKind.INTERNAL_TRANSFER to true, o.economicKind to o.economicKindConfirmed)
        val before = w.all()
        assertEquals(RepairOutcome(), w.repair().run(), "مرة تانية ⇒ ولا حاجة")
        assertEquals(before, w.all())
    }

    /** الأصلية اتأكد نوعها قبل التصليح ⇒ ما تتلغيش لوحدها: الرجوع يرجع «نلغي الاتنين؟». */
    @Test fun aRepairDoesNotCancelAnOriginalTheOwnerConfirmedMeanwhile() = runBlocking<Unit> {
        val w = ReturnsWorld()
        val (ret, original) = recordedPair(w, failAfterCommit = true)
        w.txns.update(original, TransactionPatch(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true))
        assertEquals(RepairOutcome(returnsReopened = 1), w.repair().run())
        val r = w.one(ret)
        assertEquals(EconomicKind.INTERNAL_TRANSFER, r.suggestedKind)
        assertNull(r.reversalOfId)
        assertEquals(EconomicKind.PURCHASE, w.one(original).economicKind)
    }

    @Test fun revertingTheReturnsBatchRestoresTheOriginal() = runBlocking<Unit> {
        val w = ReturnsWorld()
        val (ret, original) = recordedPair(w)
        val batch = w.sources.listByTransactionIds(listOf(ret)).single().batchId
        val plan = w.revert().execute(batch)
        assertEquals(listOf(ret), plan.toDelete)
        assertEquals(listOf(original), w.all().map { it.id })
        val o = w.one(original)
        assertEquals(EconomicKind.UNCLASSIFIED, o.economicKind)
        assertEquals(false, o.economicKindConfirmed)
        assertEquals(ReviewState.NEEDS_REVIEW, o.reviewState)
        assertNull(o.reversedById)
        assertEquals(RepairOutcome(), w.repair().run())
    }

    /** التراجع عن دفعة **الأصلية** ⇒ الرجوع يرجع «استرداد» مقترح (ولا ربط بيشاور على عملية مش موجودة). */
    @Test fun revertingTheOriginalsBatchReopensTheReturn() = runBlocking<Unit> {
        val w = ReturnsWorld()
        val (ret, original) = recordedPair(w)
        val batch = w.sources.listByTransactionIds(listOf(original)).single().batchId
        w.revert().execute(batch)
        val r = w.one(ret)
        assertEquals(EconomicKind.UNCLASSIFIED to EconomicKind.REFUND_RECEIVED, r.economicKind to r.suggestedKind)
        assertNull(r.reversalOfId)
    }

    /** عملية من الزوج اتمسحت من برّه (التطبيق القديم مثلًا) ⇒ التصليح بيفك الربط من الناحية التانية. */
    @Test fun aDanglingLinkIsRepaired() = runBlocking<Unit> {
        val w = ReturnsWorld()
        val (ret, original) = recordedPair(w)
        w.txns.deleteMany(listOf(original))
        assertEquals(RepairOutcome(returnsReopened = 1), w.repair().run())
        assertTrue(w.one(ret).let { it.reversalOfId == null && it.suggestedKind == EconomicKind.REFUND_RECEIVED })

        val w2 = ReturnsWorld()
        val (ret2, original2) = recordedPair(w2)
        w2.txns.deleteMany(listOf(ret2))
        assertEquals(RepairOutcome(originalsRestored = 1), w2.repair().run())
        assertTrue(w2.one(original2).let { it.reversedById == null && it.economicKind == EconomicKind.UNCLASSIFIED })
    }

    /** المالك غيّر نوع طرف من الزوج بنفسه ⇒ الزوج اتفك والطرف التاني بيرجع يتسأل — نوع المالك ما بيتلمسش. */
    @Test fun anOwnerEditOfOneLegUnlinksThePair() = runBlocking<Unit> {
        val w = ReturnsWorld()
        val (ret, original) = recordedPair(w)
        w.txns.update(ret, TransactionPatch(economicKind = EconomicKind.SALARY, economicKindConfirmed = true))
        assertEquals(RepairOutcome(originalsRestored = 1), w.repair().run())
        assertEquals(EconomicKind.SALARY to null, w.one(ret).let { it.economicKind to it.reversalOfId })
        assertEquals(EconomicKind.UNCLASSIFIED to null, w.one(original).let { it.economicKind to it.reversedById })

        val w2 = ReturnsWorld()
        val (ret2, original2) = recordedPair(w2)
        w2.txns.update(original2, TransactionPatch(economicKind = EconomicKind.PURCHASE))
        assertEquals(RepairOutcome(returnsReopened = 1), w2.repair().run())
        assertEquals(EconomicKind.PURCHASE to null, w2.one(original2).let { it.economicKind to it.reversedById })
        assertTrue(w2.one(ret2).let { it.reversalOfId == null && it.suggestedKind == EconomicKind.REFUND_RECEIVED })
        assertEquals(RepairOutcome(), w2.repair().run())
    }

    /** بيعدّ القرايات والكتابات اللي التصليح بيعملها (دورة الخلفية على فايربيز = قرايات بفلوس على باقة Spark). */
    private class CountingTxns(private val real: TransactionRepository) : TransactionRepository by real {
        var ranges = 0
        var finds = 0
        var updates = 0

        override suspend fun listByDateRange(fromIso: String, toIso: String) = real.listByDateRange(fromIso, toIso).also { ranges++ }

        override suspend fun findByIds(ids: List<String>) = real.findByIds(ids).also { finds++ }

        override suspend fun update(id: String, patch: TransactionPatch) = real.update(id, patch).also { updates++ }
    }

    /** دورة الخلفية بتقرا العمليات المربوطة بس (مش فترة) — والزوج السليم ما بيتقراش تاني ولا بيتكتب. */
    @Test fun theRepairReadsOnlyLinkedTransactionsAndSkipsHealthyPairs() = runBlocking<Unit> {
        val w = ReturnsWorld()
        recordedPair(w)
        w.confirmOnScreen("other" to purchaseWithRef(day = "03", amount = "99.00", ref = "771234567"))
        assertEquals(3, w.all().size)
        assertEquals(2, w.txns.listReversalLinked().size, "المربوطين بس")
        val counting = CountingTxns(w.txns)
        assertEquals(RepairOutcome(), RepairReversals(RepairReversalsDeps(counting, w.clock)).run())
        assertEquals(Triple(0, 0, 0), Triple(counting.ranges, counting.finds, counting.updates))
    }
}
