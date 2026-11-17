package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.applyKnownPayerSalary
import app.masroufy.core.asksPayerKind
import app.masroufy.core.nearPayday
import app.masroufy.core.personalShareOf
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PLAN-0.6 §7 — الراتب والتحويلات التانية من نفس حساب جهة الشغل. كل الأسامي والمبالغ مخترعة: جهة «الندى» بمرتب 12,000.00 يوم 27،
 * وحسابها المحوِّل آخره 4321.
 */
class PayerKindsFlowTest {
    private val txns = MemoryTransactionRepository()
    private val sources = MemoryIncomeSourceRepository()
    private val clock = FixedClock("2026-10-20T08:00:00.000Z")
    private val uow = MemoryUnitOfWork(listOf(txns, sources))
    private val employers = ManageEmployers(ManageEmployersDeps(sources, txns, SequentialIdGenerator(), clock, Currency.SAR))
    private val retro = EmployerRetroApply(EmployerRetroDeps(sources, txns, uow, clock, null))
    private val answers = IncomeAnswers(IncomeAnswersDeps(txns, MemoryCategoryRepository(), employers, retro, uow, clock))
    private val allocations = MemoryAllocationRepository()
    private val links = LinkInflows(LinkInflowsDeps(txns, allocations, MemorySettlementRepository(), MemoryObligationRepository(), uow, clock))
    private val payer = PayerKindAnswers(PayerKindDeps(txns, sources, answers, employers, links, clock, payday = { 28 }))

    private var seq = 0

    private fun deposit(date: String, amount: Long, op: String = "حوالة واردة داخل البنك") = Transaction(
        id = "in-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "W-/FRACCT/00000000000004321FRNADACO:ملاحظة", sourceOperationType = op,
    )

    private fun purchase(date: String, amount: Long) = Transaction(
        id = "out-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawMerchantName = "مكتبة الوادي",
    )

    /** الجهة بحسابها ومرتبها ويوم الراتب. */
    private fun nada(day: Int? = 27) = runBlocking {
        val e = employers.save(null, EmployerInput("جهة الندى", "2025-01-01"))
        employers.attach(e.id, transferPartyOf(deposit("2026-09-27", 1))!!)
        val linked = sources.listAll().single()
        sources.saveMany(listOf(linked.copy(expectedMinor = 1_200_000, expectedDayOfMonth = day)))
        sources.listAll()
    }

    @Test fun salaryWordInTheMessageMakesItSalaryWhateverTheAmount() = runBlocking<Unit> {
        val all = nada()
        val t = deposit("2026-10-12", 345_000, op = "حوالة واردة رواتب")
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(t, all, "now").economicKind)
        assertFalse(asksPayerKind(t, all, null))
    }

    @Test fun nearAmountAndNearPaydayMakeItSalary() = runBlocking<Unit> {
        val all = nada()
        val t = deposit("2026-09-29", 1_150_000) // 12,000 − 4% · يومين بعد 27
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(t, all, "now").economicKind)
        // نفس المبلغ بعيد عن الميعاد ⇒ مش راتب
        assertEquals(EconomicKind.UNCLASSIFIED, applyKnownPayerSalary(deposit("2026-09-15", 1_150_000), all, "now").economicKind)
        // يوم الجهة مش مكتوب ⇒ يوم الملف (28)
        val noDay = all.map { it.copy(expectedDayOfMonth = null) }
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(deposit("2026-09-30", 1_250_000), noDay, "now", 28).economicKind)
        assertTrue(nearPayday("2026-03-01", 28), "آخر فبراير + يوم")
        assertFalse(nearPayday("2026-03-05", 28))
    }

    @Test fun alwaysSalaryFromThisAccountMakesAnyDepositSalaryUntilTurnedOff() = runBlocking<Unit> {
        // جهة اتعملت من رد «راتب» — من غير مبلغ ولا يوم (مراجعة todo-types-4)
        val e = employers.save(null, EmployerInput("جهة الندى", "2025-01-01"))
        val key = transferPartyOf(deposit("2026-09-27", 1))!!.key
        employers.attach(e.id, transferPartyOf(deposit("2026-09-27", 1))!!)
        // §121-8: المستنية من قبل التشغيل (اتسجلت قبل وقت الساعة) تفضل مستنية — والتشغيل ما بيكتبش عمليات
        val waiting = deposit("2026-10-05", 915_000).copy(createdAt = "2026-10-06T09:00:00.000Z")
        txns.saveMany(listOf(waiting))
        assertTrue(asksPayerKind(waiting, sources.listAll(), 28), "من غيره: القرار الأحدث (مبلغ وميعاد) ⇒ بيتسأل")
        assertTrue(employers.setAlwaysSalary(e.id, key, on = true))
        assertEquals(listOf(clock.nowIso()), sources.listAll().single().alwaysSalarySince.mapNotNull { app.masroufy.core.alwaysSalarySinceOf(sources.listAll().single(), key) })
        assertEquals(waiting, txns.findByIds(listOf(waiting.id)).single(), "§121-8: المستنية ما اتلمستش")
        assertTrue(asksPayerKind(waiting, sources.listAll(), 28), "§121-8: لسه بتتسأل")
        assertEquals(waiting, applyKnownPayerSalary(waiting, sources.listAll(), "now"), "§121-8: «طبّق» ما بيسجلهاش مرتب")
        // الجاية بعد التشغيل ⇒ مرتب
        val any = deposit("2026-11-13", 33_000).copy(createdAt = "2026-11-13T10:00:00.000Z")
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(any, sources.listAll(), "now").economicKind)
        assertFalse(asksPayerKind(any, sources.listAll(), 28))
        assertFalse(employers.setAlwaysSalary(e.id, key, on = true), "تشغيل تاني ما بيغيرش الوقت")
        employers.setAlwaysSalary(e.id, key, on = false)
        assertEquals(any, applyKnownPayerSalary(any, sources.listAll(), "now"), "القفل ⇒ يرجع للقرار الأحدث")
        assertEquals(emptyList(), sources.listAll().single().alwaysSalarySince)
        // مفتاح قديم من غير وقت (اتشغل قبل §121) ⇒ على الكل زي ما كان
        val legacy = sources.listAll().single().copy(alwaysSalaryKeys = listOf(key))
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(waiting, listOf(legacy), "now").economicKind)
        // حساب مش من حسابات الجهة ⇒ مرفوض · شيل الحساب بيشيل العلامة ووقتها
        kotlin.test.assertFailsWith<app.masroufy.core.IncomeSourceError> { employers.setAlwaysSalary(e.id, "#9999", on = true) }
        employers.setAlwaysSalary(e.id, key, on = true)
        employers.detach(e.id, key)
        assertEquals(emptyList(), sources.listAll().single().alwaysSalaryKeys)
        assertEquals(emptyList(), sources.listAll().single().alwaysSalarySince)
    }

    @Test fun samePayerOtherAmountAsksAndTheAnswerIsRememberedThenUndone() = runBlocking<Unit> {
        val all = nada()
        val first = deposit("2026-10-05", 85_000)
        txns.saveMany(listOf(first))
        assertEquals(first, applyKnownPayerSalary(first, all, "now"), "مبلغ تاني ⇒ ما بيتسجلش راتب")
        assertTrue(asksPayerKind(first, all, 28))
        val view = payer.view(first.id)!!
        assertTrue(view.asks)
        assertNull(view.ruleKind)

        val undo = payer.answer(first.id, EconomicKind.BONUS, makeRule = true)
        assertEquals(EconomicKind.BONUS, txns.findByIds(listOf(first.id)).single().economicKind)
        val next = deposit("2026-11-06", 60_000)
        val applied = applyKnownPayerSalary(next, sources.listAll(), "now")
        assertEquals(Triple(EconomicKind.BONUS, true, ReviewState.CONFIRMED), Triple(applied.economicKind, applied.economicKindConfirmed, applied.reviewState))
        // الراتب الحقيقي من نفس الحساب بيفضل راتب حتى مع القاعدة
        assertEquals(EconomicKind.SALARY, applyKnownPayerSalary(deposit("2026-10-27", 1_200_000), sources.listAll(), "now").economicKind)

        answers.undo(undo)
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf(first.id)).single().economicKind)
        assertEquals(next, applyKnownPayerSalary(next, sources.listAll(), "now"), "التراجع شال القاعدة")

        payer.answer(first.id, EconomicKind.ADVANCE_RECEIVED, makeRule = true)
        assertEquals(EconomicKind.ADVANCE_RECEIVED, payer.view(first.id)!!.ruleKind)
        payer.forgetRule(first.id)
        assertNull(payer.view(first.id)!!.ruleKind)
    }

    @Test fun reimbursementLinkedToThePurchaseReducesItsPersonalShare() = runBlocking<Unit> {
        nada()
        val bought = purchase("2026-09-20", 300_000)
        val other = purchase("2026-06-01", 300_000) // أقدم من 60 يوم
        val refund = deposit("2026-10-03", 200_000)
        txns.saveMany(listOf(bought, other, refund))
        payer.setAwaitingRefund(bought.id, true)
        val waiting = txns.findByIds(listOf(bought.id)).single()
        assertEquals(0L, personalShareOf(waiting, emptyList()), "مستني استرداد ⇒ مش من مصروفك")

        payer.answer(refund.id, EconomicKind.REFUND_RECEIVED)
        assertEquals(listOf(bought.id), payer.refundCandidates(refund.id).map { it.id })
        assertNull(payer.linkRefund(refund.id, bought.id))
        val after = txns.findByIds(listOf(bought.id)).single()
        assertNull(after.awaitingRefund, "الاسترداد وصل ⇒ العلامة اتشالت")
        assertEquals(100_000L, personalShareOf(after, emptyList()), "300 − 200 = 100")
    }
}
