package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.ProjectLink
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.buildPeriod
import app.masroufy.core.cashMovement
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.withEstimatedKinds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §77-D (قرار المالك 2026-10-09): «تم رد المبلغ» / «التحويل رجع» ⇒ يدوّر على العملية الأصلية برقمها المرجعي ويلغيها، ولو ما لقاهاش ⇒
 * «استرداد» مقترح ويسأل. على مستوى خط رسايل البنك نفسه (الخلفية و«سجّل الكل»). كل الرسايل مخترعة.
 */
class ReturnedSmsTest {
    private fun cancelled(t: Transaction) = t.economicKind == EconomicKind.INTERNAL_TRANSFER && t.economicKindConfirmed

    private fun pendingRefund(t: Transaction) =
        t.economicKind == EconomicKind.UNCLASSIFIED && !t.economicKindConfirmed && t.suggestedKind == EconomicKind.REFUND_RECEIVED && t.reversalOfId == null

    /** الأصلية والرجوع اتلغوا مع بعض لوحدهم في الخلفية (بيت التمويل — أشكال معروفة) — ومش في أي مجموع ولا «حركة فلوس»، وأثر المحفظة صفر. */
    @Test fun aReturnFoundByItsReferenceCancelsTheOriginal() = runBlocking<Unit> {
        val w = ReturnsWorld(wallets = listOf(EG_BANK), parse = ::parseEgyptBankSms).enable()
        w.memory.receive(sms("buy", kfhOut(), at = "2026-10-02T09:00:00Z"))
        assertEquals(1, w.auto().run().recorded, "الحوالة (شكل معروف) اتسجلت لوحدها")
        val original = w.all().single()
        assertTrue(original.rawDescription!!.contains("••••7781"), "الوصف المتخزن محجوب")
        assertEquals(EconomicKind.UNCLASSIFIED, original.economicKind)

        w.memory.receive(sms("back", kfhReturned(), at = "2026-10-05T10:00:00Z"))
        val run = w.auto().run()
        assertEquals(1, run.recorded, "الرجوع (شكل معروف) اتسجل لوحده")
        val ret = w.one(run.recordedTransactionIds.single())
        val o = w.one(original.id)
        assertTrue(cancelled(ret) && cancelled(o))
        assertEquals(original.id, ret.reversalOfId)
        assertEquals(ret.id, o.reversedById)
        assertNull(ret.suggestedKind)
        assertNull(o.kindBeforeReversal, "نوعها ما كانش مؤكد")
        assertEquals(ReviewState.CONFIRMED, o.reviewState)

        for (period in listOf(buildPeriod(2026, 9, 28), buildPeriod(2026, 10, 28), buildPeriod(2026, 10, 1))) {
            val inPeriod = w.txns.listByDateRange(period.start, period.end)
            val totals = computePeriodTotals(withEstimatedKinds(inPeriod, emptyMap()).transactions, emptyList())
            assertEquals(0L to 0L, totals.incomeMinor to totals.personalExpenseMinor, "${period.start}")
            assertEquals(0L to 0L, cashMovement(inPeriod).let { it.inMinor to it.outMinor })
        }
        assertEquals(0L, w.walletNet(EG_BANK.id))
        assertEquals(emptyList(), w.asks().pending("2026-09-01", "2026-10-31"), "مفيش سؤال")
    }

    /** الأصلية من **الكشف** (مرجعها في سجل المصدر) والرجوع رسالة ⇒ نفس الإلغاء. */
    @Test fun aStatementOriginalIsFoundThroughItsSourceReference() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.importStatementLine("2026-10-02", 25_000, Direction.OUT, "FT26A$RETURN_REF")
        assertEquals(1, w.confirmOnScreen("back" to reversalWithRef()))
        val (original, ret) = w.all().sortedBy { it.occurredAt }
        assertTrue(cancelled(original) && cancelled(ret))
        assertEquals(original.id, ret.reversalOfId)
    }

    /** الشراء ورجوعه وصلوا مع بعض (نفس الدفعة) ⇒ الاتنين بيتلغوا على طول. */
    @Test fun anOriginalInTheSameBatchIsCancelledToo() = runBlocking<Unit> {
        val w = ReturnsWorld()
        assertEquals(2, w.confirmOnScreen("buy" to purchaseWithRef(), "back" to reversalWithRef()))
        val (o, ret) = w.all().sortedBy { it.occurredAt }
        assertTrue(cancelled(o) && cancelled(ret))
        assertEquals(o.id, ret.reversalOfId)
        assertEquals(ret.id, o.reversedById)
    }

    /** مبلغ تاني · من غير مرجع · أصليتين · أقدم من 60 يوم ⇒ «استرداد» مقترح وسؤال (`REVERSAL_CHECK` — عقد C0)، ومش دخل حتى بالتقدير. */
    @Test fun noSafeMatchMeansASuggestedRefund() = runBlocking<Unit> {
        val cases = listOf(
            "different amount" to listOf(purchaseWithRef(amount = "300.00")),
            "no reference" to listOf(purchaseWithRef()),
            "two candidates" to listOf(purchaseWithRef(day = "01"), purchaseWithRef(day = "03")),
            "older than 60 days" to listOf("شراء\nبطاقة:6604;مدى\nمبلغ:SAR 250.00\nلدى:TEST STORE\nفي:26-08-01 10:00\nمرجع:$RETURN_REF"),
        )
        for ((name, originals) in cases) {
            val w = ReturnsWorld()
            // الرسالة القديمة وصلت يومها (وإلا القارئ بيرفض تاريخ قديم من غير سنة كاملة)
            val at = if (name == "older than 60 days") "2026-08-01T12:00:00Z" else SENT_AT
            assertEquals(originals.size, w.confirmOnScreen(*originals.mapIndexed { i, body -> "o$i" to body }.toTypedArray(), at = at), name)
            val ret = if (name == "no reference") reversalWithRef(ref = null) else reversalWithRef()
            assertEquals(1, w.confirmOnScreen("back" to ret), name)
            val r = w.all().single { it.observedDirection == Direction.IN }
            assertTrue(pendingRefund(r), "$name: $r")
            assertTrue(w.all().filter { it.observedDirection == Direction.OUT }.none { it.reversedById != null || it.economicKind != EconomicKind.UNCLASSIFIED }, name)
            assertEquals(0L, computePeriodTotals(withEstimatedKinds(w.all(), emptyMap()).transactions, emptyList()).incomeMinor, "$name: مش دخل")
            assertEquals(listOf(AskKind.REVERSAL_CHECK), w.asks().pending("2026-07-01", "2026-10-31").map { it.kind }, name)
        }
    }

    /**
     * الأصلية مربوطة (دين · تخصيص · حدث · مشروع) أو نوعها مؤكد ⇒ ما بتتلغيش لوحدها: سؤال «نلغي الاتنين؟». المربوطة: «أيوه» **بيترفض**
     * ([ReversalAnswer.Linked]) لحد ما الربط يتفك — وبعده بتتلغي. المؤكدة: «أيوه» بيلغيهم.
     */
    @Test fun aLinkedOrConfirmedOriginalAsksFirst() = runBlocking<Unit> {
        for (how in listOf("obligation", "allocation", "event", "project", "confirmed")) {
            val w = ReturnsWorld()
            w.confirmOnScreen("buy" to purchaseWithRef())
            val original = w.all().single()
            val detach: suspend () -> Unit = when (how) {
                "obligation" -> {
                    w.obligations.saveMany(listOf(Obligation("ob-1", "p-1", original.id, ObligationKind.RECEIVABLE, 25_000, Currency.SAR)))
                    suspend { w.obligations.deleteMany(listOf("ob-1")) }
                }
                "allocation" -> {
                    w.allocations.saveMany(listOf(app.masroufy.core.PersonAllocation("al-1", original.id, "p-1", app.masroufy.core.AllocationKind.GIFT, 10_000, Currency.SAR)))
                    suspend { w.allocations.deleteMany(listOf("al-1")) }
                }
                "event" -> {
                    w.eventLinks.saveMany(listOf(app.masroufy.core.EventLink("el-1", "ev-1", original.id, app.masroufy.core.EventRole.SPEND, createdAt = "2026-10-03T00:00:00Z")))
                    suspend { w.eventLinks.deleteMany(listOf("el-1")) }
                }
                "project" -> {
                    w.projectLinks.saveMany(listOf(ProjectLink("pl-1", "prj-1", original.id, "manual", "2026-10-03T00:00:00Z")))
                    suspend { w.projectLinks.deleteMany(listOf("pl-1")) }
                }
                else -> {
                    w.txns.update(original.id, app.masroufy.port.TransactionPatch(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true))
                    suspend {}
                }
            }
            w.confirmOnScreen("back" to reversalWithRef())
            val ret = w.all().single { it.observedDirection == Direction.IN }
            assertEquals(EconomicKind.INTERNAL_TRANSFER, ret.suggestedKind, how)
            assertEquals(EconomicKind.UNCLASSIFIED, ret.economicKind, how)
            assertNull(w.one(original.id).reversedById, "$how: ما اتلغتش لوحدها")
            assertEquals(listOf(AskKind.REVERSAL_CHECK), w.asks().pending("2026-10-01", "2026-10-31").map { it.kind }, how)
            assertEquals(listOf(original.id), w.refunds().reversalCandidates(ret.id).map { it.id }, how)

            if (how != "confirmed") {
                val before = w.all()
                val refused = assertIs<ReversalAnswer.Linked>(w.refunds().confirmReversal(ret.id, original.id), how)
                assertEquals(1, refused.links.size, how)
                assertEquals(before, w.all(), "$how: ولا كتابة")
                detach()
            }
            val done = assertIs<ReversalAnswer.Cancelled>(w.refunds().confirmReversal(ret.id, original.id), how)
            assertTrue(cancelled(done.ret) && cancelled(done.original), how)
            assertEquals(original.id, done.ret.reversalOfId)
            assertEquals(ret.id, done.original.reversedById)
            assertEquals(if (how == "confirmed") EconomicKind.PURCHASE else null, done.original.kindBeforeReversal, how)
            assertEquals(emptyList(), w.asks().pending("2026-10-01", "2026-10-31"), how)
        }
    }

    @Test fun decliningTheReversalFallsBackToTheRefundQuestion() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef())
        val original = w.all().single()
        w.projectLinks.saveMany(listOf(ProjectLink("pl-1", "prj-1", original.id, "manual", "2026-10-03T00:00:00Z")))
        w.confirmOnScreen("back" to reversalWithRef())
        val ret = w.all().single { it.observedDirection == Direction.IN }
        assertTrue(pendingRefund(w.refunds().declineReversal(ret.id)))
        assertEquals(listOf(AskKind.REVERSAL_CHECK), w.asks().pending("2026-10-01", "2026-10-31").map { it.kind })
        assertFailsWith<IllegalStateException> { w.refunds().declineReversal(ret.id) }
        // عملية مش مستنية سؤال ⇒ مرفوض
        assertFailsWith<IllegalStateException> { w.refunds().confirmRefund(original.id) }
        assertFailsWith<IllegalStateException> { w.refunds().confirmReversal(ret.id, ret.id) }
    }

    /** «ده استرداد؟»: أيوه ⇒ «استرداد» مؤكد بينقّص المصروف · لأ ⇒ الاقتراح بيتشال والعملية «غير محددة». */
    @Test fun refundAnswers() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef(amount = "400.00"), "back" to reversalWithRef(ref = null))
        val buy = w.all().single { it.observedDirection == Direction.OUT }
        w.txns.update(buy.id, app.masroufy.port.TransactionPatch(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true))
        val ret = w.all().single { it.observedDirection == Direction.IN }
        assertEquals(40_000L, computePeriodTotals(w.all(), emptyList()).personalExpenseMinor, "قبل الإجابة: الاسترداد المقترح ما بينقّصش")
        val yes = w.refunds().confirmRefund(ret.id)
        assertEquals(EconomicKind.REFUND_RECEIVED to true, yes.economicKind to yes.economicKindConfirmed)
        assertNull(yes.suggestedKind)
        assertEquals(15_000L, computePeriodTotals(w.all(), emptyList()).personalExpenseMinor, "400 − 250")
        assertEquals(emptyList(), w.asks().pending("2026-10-01", "2026-10-31"))
        assertFailsWith<IllegalStateException> { w.refunds().rejectRefund(ret.id) }

        val w2 = ReturnsWorld()
        w2.confirmOnScreen("back" to reversalWithRef(ref = null))
        val no = w2.refunds().rejectRefund(w2.all().single().id)
        assertEquals(EconomicKind.UNCLASSIFIED to false, no.economicKind to no.economicKindConfirmed)
        assertNull(no.suggestedKind)
        assertEquals(emptyList(), w2.asks().pending("2026-10-01", "2026-10-31"))
    }

    /** الأثر مش متوصّل في استيراد بُني بإيده (مش `SmsLane`) ⇒ الرجوع بيتسجل «غير محدد» عادي — الخط نفسه بيضيفه (`ReturnsWiringTest`). */
    @Test fun withoutTheEffectNothingIsCancelled() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef())
        w.memory.receive(sms("back", reversalWithRef()))
        val screen = ReviewSmsInbox(ReviewSmsInboxDeps(ManageSmsInbox(w.memory, w.parse), ImportStatement(w.importDeps(withEffects = false)), app.masroufy.memory.MemoryMerchantRepository(), w.categories, w.ids))
        screen.load(SmsReviewTarget(BANK.id, BANK.name))
        screen.recordAll(emptyMap(), emptyList())
        assertTrue(w.all().all { it.economicKind == EconomicKind.UNCLASSIFIED && it.reversalOfId == null && it.reversedById == null && it.suggestedKind == null })
    }

    /** أثر قبله أكد نوع الرجوع (زي «حسابي التاني» بآخر 4) ⇒ الرجوع ما بيتلمسش والأصلية ما بتتلغيش. */
    @Test fun aReturnWhoseKindIsAlreadyDecidedIsLeftAlone() = runBlocking<Unit> {
        val ownAccount = object : RecordEffect {
            override suspend fun prepare(ctx: RecordContext) {
                for (line in ctx.lines) if (line.transaction.observedDirection == Direction.IN) {
                    line.transaction = line.transaction.copy(economicKind = EconomicKind.INTERNAL_TRANSFER, economicKindConfirmed = true)
                }
            }
        }
        val w = ReturnsWorld(before = listOf(ownAccount))
        w.confirmOnScreen("buy" to purchaseWithRef())
        w.confirmOnScreen("back" to reversalWithRef())
        val (o, ret) = w.all().sortedBy { it.occurredAt }
        assertEquals(EconomicKind.UNCLASSIFIED to null, o.economicKind to o.reversedById)
        assertEquals(null to null, ret.reversalOfId to ret.suggestedKind)
    }

    /** الأصلية أقدم من 60 يوم ⇒ «نلغي الاتنين» بإيد المالك نفسه مرفوض (مش بس البحث التلقائي). */
    @Test fun anOriginalOlderThanTheWindowCannotBeCancelledByHand() = runBlocking<Unit> {
        val w = ReturnsWorld()
        assertEquals(1, w.confirmOnScreen("old" to "شراء\nبطاقة:6604;مدى\nمبلغ:SAR 250.00\nلدى:TEST STORE\nفي:26-08-01 10:00\nمرجع:$RETURN_REF", at = "2026-08-01T12:00:00Z"))
        w.confirmOnScreen("back" to reversalWithRef())
        val (old, ret) = w.all().sortedBy { it.occurredAt }
        assertEquals(emptyList(), w.refunds().reversalCandidates(ret.id))
        assertFailsWith<IllegalStateException> { w.refunds().confirmReversal(ret.id, old.id) }
        assertTrue(pendingRefund(w.one(ret.id)))
    }
}
