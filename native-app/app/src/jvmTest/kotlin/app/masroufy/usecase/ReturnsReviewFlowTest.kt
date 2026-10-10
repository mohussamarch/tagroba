package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.LocalMoment
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.SmsKind
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.computePersonBalance
import app.masroufy.core.parseEgyptBankSms
import app.masroufy.core.withEstimatedKinds
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.port.TransactionPatch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** مراجعة الشريحة S3 (§77-D) على خط رسايل البنك نفسه — كل حالة من ملاحظات المراجعة باسمها (P1 …). كل الرسايل مخترعة. */
class ReturnsReviewFlowTest {
    private suspend fun ReturnsWorld.estimatedTotals() = computePeriodTotals(withEstimatedKinds(all(), emptyMap()).transactions, emptyList())

    /** P1: «استرداد» مقترح أو «نلغي الاتنين؟» **مش دخل** في الملخص (اللي الشاشات بتستعمله) — كان بيتقدّر «راتب». */
    @Test fun pendingReturnsAreNotIncomeOnTheScreens() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef(amount = "400.00"), "back" to reversalWithRef(ref = null))
        val summary = LoadMoneySummary(LoadMoneySummaryDeps(w.txns, w.categories, w.allocations)).load("2026-10-01", "2026-10-31")
        assertEquals(0L, summary.incomeMinor, "الاسترداد المقترح مش دخل")
        assertEquals(40_000L, summary.expenseMinor)

        val w2 = ReturnsWorld()
        w2.confirmOnScreen("buy" to purchaseWithRef())
        val original = w2.all().single()
        w2.txns.update(original.id, TransactionPatch(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true))
        w2.confirmOnScreen("back" to reversalWithRef())
        assertEquals(EconomicKind.INTERNAL_TRANSFER, w2.all().single { it.observedDirection == Direction.IN }.suggestedKind, "«نلغي الاتنين؟»")
        assertEquals(0L to 25_000L, w2.estimatedTotals().let { it.incomeMinor to it.personalExpenseMinor })
    }

    /** P10: «Purchase Reversal» (عكس من محل) = استرداد بيستنى تأكيدك (§75-6) — ما بيتسجلش لوحده ولا بيلغي الشراء من غير سؤال. */
    @Test fun aMerchantReversalWaitsAndCancelsNothing() = runBlocking<Unit> {
        val w = ReturnsWorld().enable()
        w.memory.receive(sms("buy", purchaseWithRef(day = "05")))
        assertEquals(1, w.auto().run().recorded)
        w.memory.receive(sms("back", merchantReversal()))
        val run = w.auto().run()
        assertEquals(0 to listOf("back"), run.recorded to run.waiting)
        val buy = w.all().single()
        assertEquals(EconomicKind.UNCLASSIFIED to null, buy.economicKind to buy.reversedById)
    }

    /** P2: «أيوه نلغيهم» على أصلية عليها دين ⇒ مرفوض ومفيش كتابة — الشخص لسه عليه المبلغ كله ومش «اتلغى» من غير ما الدين يتقفل. */
    @Test fun aLoanWithADebtIsNotCancelledUnderIt() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef())
        val original = w.all().single()
        w.txns.update(original.id, TransactionPatch(economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true))
        w.obligations.saveMany(listOf(Obligation("ob-1", "p-1", original.id, ObligationKind.RECEIVABLE, 25_000, Currency.SAR)))
        w.confirmOnScreen("back" to reversalWithRef())
        val ret = w.all().single { it.observedDirection == Direction.IN }
        val answer = assertIs<ReversalAnswer.Linked>(w.refunds().confirmReversal(ret.id, original.id))
        assertEquals(setOf(ReversalLink.OBLIGATION), answer.links)
        assertEquals(EconomicKind.LOAN_GRANTED, w.one(original.id).economicKind)
        assertEquals(25_000L, computePersonBalance("p-1", w.obligations.listByPerson("p-1"), emptyList()).receivableMinor)
    }

    /** P3: سلفة مؤكدة اتلغت مع رجوعها بإيد المالك، والتراجع عن دفعة الرجوع ⇒ ترجع **سلفة مؤكدة** (مش «غير محددة» تتقدّر شراء). */
    @Test fun undoRestoresTheKindTheOwnerConfirmed() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef())
        val original = w.all().single()
        w.txns.update(original.id, TransactionPatch(economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true))
        w.confirmOnScreen("back" to reversalWithRef())
        val ret = w.all().single { it.observedDirection == Direction.IN }
        assertIs<ReversalAnswer.Cancelled>(w.refunds().confirmReversal(ret.id, original.id))
        assertEquals(EconomicKind.LOAN_GRANTED, w.one(original.id).kindBeforeReversal)
        w.revert().execute(w.sources.listByTransactionIds(listOf(ret.id)).single().batchId)
        val back = w.one(original.id)
        assertEquals(EconomicKind.LOAN_GRANTED to true, back.economicKind to back.economicKindConfirmed)
        assertNull(back.kindBeforeReversal)
        assertNull(back.reversedById)
        assertEquals(0L, w.estimatedTotals().personalExpenseMinor, "السلفة مش مصروف")
    }

    /** P5: الرجوع اتسجل الأول («استرداد» مقترح) وبعده الكشف جاب الأصلية بنفس الذيل ⇒ الاتنين بيتلغوا. */
    @Test fun anOriginalRecordedAfterItsReturnPairs() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("back" to reversalWithRef())
        val ret = w.all().single()
        assertEquals(EconomicKind.REFUND_RECEIVED, ret.suggestedKind)
        w.importStatementLine("2026-10-02", 25_000, Direction.OUT, "FT26A$RETURN_REF")
        val original = w.all().single { it.observedDirection == Direction.OUT }
        val r = w.one(ret.id)
        assertEquals(original.id to ret.id, r.reversalOfId to original.reversedById)
        assertTrue(r.economicKind == EconomicKind.INTERNAL_TRANSFER && original.economicKind == EconomicKind.INTERNAL_TRANSFER)
        assertNull(r.suggestedKind)
        assertEquals(emptyList(), w.asks().pending("2026-10-01", "2026-10-31"))
        assertEquals(RepairOutcome(), w.repair().run())

        // وقع بعد الحفظ وقبل ما الرجوع يتعلّم ⇒ التصليح بيكمّله (من ناحية الأصلية)
        val w2 = ReturnsWorld()
        w2.confirmOnScreen("back" to reversalWithRef())
        w2.effectTxns.failUpdates = 1
        w2.importStatementLine("2026-10-02", 25_000, Direction.OUT, "FT26A$RETURN_REF")
        assertNull(w2.all().single { it.observedDirection == Direction.IN }.reversalOfId)
        assertEquals(RepairOutcome(finished = 1), w2.repair().run())
        assertTrue(w2.all().all { it.economicKind == EconomicKind.INTERNAL_TRANSFER })
    }

    /** P12: «أيوه نلغيهم» وقع بين الكتابتين (فايربيز من غير وحدة عمل) ⇒ التصليح بيكمّل الزوج — إجابة المالك ما بتضيعش. */
    @Test fun aCrashBetweenTheTwoWritesKeepsTheOwnersAnswer() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef())
        val original = w.all().single()
        w.txns.update(original.id, TransactionPatch(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true))
        w.confirmOnScreen("back" to reversalWithRef())
        val ret = w.all().single { it.observedDirection == Direction.IN }
        w.effectTxns.passBeforeFail = 1
        w.effectTxns.failUpdates = 1
        runCatching { w.refundsWithoutUnitOfWork().confirmReversal(ret.id, original.id) }
        assertEquals(ret.id, w.one(original.id).reversedById, "الأصلية اتكتبت الأول")
        assertEquals(RepairOutcome(finished = 1), w.repair().run())
        assertEquals(original.id, w.one(ret.id).reversalOfId)
        assertEquals(EconomicKind.PURCHASE, w.one(original.id).kindBeforeReversal)

        // وبوحدة العمل (الذاكرة): الكتابتين مع بعض أو ولا واحدة
        val w2 = ReturnsWorld()
        w2.confirmOnScreen("buy" to purchaseWithRef())
        val o2 = w2.all().single()
        w2.txns.update(o2.id, TransactionPatch(economicKind = EconomicKind.PURCHASE, economicKindConfirmed = true))
        w2.confirmOnScreen("back" to reversalWithRef())
        val r2 = w2.all().single { it.observedDirection == Direction.IN }
        val flaky = FlakyUpdates(w2.txns).apply { passBeforeFail = 1; failUpdates = 1 }
        runCatching { RefundAsks(RefundAsksDeps(flaky, w2.sources, w2.links, w2.clock, MemoryUnitOfWork(listOf(w2.txns)))).confirmReversal(r2.id, o2.id) }
        assertNull(w2.one(o2.id).reversedById, "اترجعت")
    }

    /** P11: تراجع التطبيق القديم بيمسح الرجوع من غير ما يعرف الربط ⇒ دورة الخلفية بتصلّح: الأصلية ترجع بنوع المالك. */
    @Test fun theBackgroundCycleRepairsAnOldAppRevert() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("buy" to purchaseWithRef())
        val original = w.all().single()
        w.txns.update(original.id, TransactionPatch(economicKind = EconomicKind.LOAN_GRANTED, economicKindConfirmed = true))
        w.confirmOnScreen("back" to reversalWithRef())
        val ret = w.all().single { it.observedDirection == Direction.IN }
        w.refunds().confirmReversal(ret.id, original.id)
        w.txns.deleteMany(listOf(ret.id)) // زي `revertImportBatch.ts` في التطبيق القديم
        assertEquals(EconomicKind.INTERNAL_TRANSFER, w.one(original.id).economicKind, "متخبية لحد التصليح")
        RunBackgroundCycle(BackgroundCycleDeps(reversalRepairs = listOf(w.repair()))).run(LocalMoment("2026-10-07", 12))
        val back = w.one(original.id)
        assertEquals(EconomicKind.LOAN_GRANTED to null, back.economicKind to back.reversedById)
    }

    /** P13 (الملاحظة الصغيرة): نوع المالك بيشيل الاقتراح المستني، والتأكيد الجماعي ما بيلمسش عملية عليها سؤال. */
    @Test fun theOwnersKindClearsThePendingSuggestion() = runBlocking<Unit> {
        val w = ReturnsWorld()
        w.confirmOnScreen("back" to reversalWithRef(ref = null))
        val ret = w.all().single()
        val kinds = SetEconomicKind(SetEconomicKindDeps(w.txns, w.categories, MemoryUnitOfWork(listOf(w.txns)), w.clock))
        assertTrue(kinds.summarize(listOf(ret)).confirmable.isEmpty(), "عليها سؤال ⇒ مش في التأكيد الجماعي")
        kinds.setOne(ret.id, EconomicKind.SALARY)
        val t = w.one(ret.id)
        assertEquals(EconomicKind.SALARY to null, t.economicKind to t.suggestedKind)
        assertEquals(emptyList(), w.asks().pending("2026-10-01", "2026-10-31"))

        // نوع من طريق تاني (ربط بالمستحقات) بيشيل الاقتراح برضه — ما يفضلش متخزن ويرجع يسأل لو الربط اتفك
        val w2 = ReturnsWorld()
        w2.confirmOnScreen("back" to reversalWithRef(ref = null))
        val r2 = w2.all().single()
        DueLinks(
            w2.txns, app.masroufy.memory.MemoryRoscaEntryRepository(), app.masroufy.memory.MemoryInstallmentPaymentRepository(),
            app.masroufy.memory.MemoryInstallmentPlanRepository(), w2.categories, w2.clock,
        ).markKind(r2.id, EconomicKind.ROSCA_PAYOUT, "cat-shop")
        assertEquals(EconomicKind.ROSCA_PAYOUT to null, w2.one(r2.id).let { it.economicKind to it.suggestedKind })
    }

    /** P4 على مستوى القارئ: الأجنبي اللي رجع نوعه RETURNED في السؤال (مصر بتعدّي من غير `refineSmsKind` في القارئ). */
    @Test fun aForeignReturnIsAskedAsReturned() = runBlocking<Unit> {
        val w = ReturnsWorld(wallets = listOf(EG_BANK), parse = ::parseEgyptBankSms).enable()
        w.memory.receive(sms("f", "IPN Transfer dated 02/10 10:00 with USD 25.00 returned with Ref# $RETURN_REF. For info call 19533"))
        val asks = ForeignSmsAsks(AutoRecordSmsDeps(w.memory, listOf(w.lane()))).list()
        assertEquals(listOf(SmsKind.RETURNED), asks.map { it.kind })
    }
}
