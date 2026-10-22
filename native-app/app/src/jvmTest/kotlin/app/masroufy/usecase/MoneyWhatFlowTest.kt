package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.PayerCustomKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.applyKnownPayerSalary
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.isLinkedInflow
import app.masroufy.core.linkedSharesOf
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-1b3 — تعديلات المالك على «الفلوس دي إيه؟» و«مستني استرداد» (2026-10-22): القاعدة بعلامة بس · «حاجة تانية» لنفس الحساب بس ·
 * الاسترداد لأكتر من مشترى. كل الأسامي والمبالغ مخترعة: جهة «الندى» بمرتب 12,000.00 يوم 27، وحساباها آخرهم 4321 و9876.
 */
class MoneyWhatFlowTest {
    private val txns = MemoryTransactionRepository()
    private val sources = MemoryIncomeSourceRepository()
    private val clock = FixedClock("2026-10-20T08:00:00.000Z")
    private val uow = MemoryUnitOfWork(listOf(txns, sources))
    private val employers = ManageEmployers(ManageEmployersDeps(sources, txns, SequentialIdGenerator(), clock, Currency.SAR))
    private val retro = EmployerRetroApply(EmployerRetroDeps(sources, txns, uow, clock, null))
    private val answers = IncomeAnswers(IncomeAnswersDeps(txns, MemoryCategoryRepository(), employers, retro, uow, clock))
    private val links = LinkInflows(LinkInflowsDeps(txns, MemoryAllocationRepository(), MemorySettlementRepository(), MemoryObligationRepository(), uow, clock))
    private val payer = PayerKindAnswers(PayerKindDeps(txns, sources, answers, employers, links, clock, payday = { 28 }))

    private var seq = 0

    private fun deposit(date: String, amount: Long, account: String = "4321") = Transaction(
        id = "in-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "W-/FRACCT/0000000000000${account}FRNADACO:ملاحظة", sourceOperationType = "حوالة واردة داخل البنك",
    )

    private fun purchase(date: String, amount: Long, waiting: Boolean = false) = Transaction(
        id = "out-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = EconomicKind.PURCHASE,
        economicKindConfirmed = true, observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawMerchantName = "فندق الواحة", awaitingRefund = if (waiting) true else null,
    )

    private fun nada() = runBlocking {
        val e = employers.save(null, EmployerInput("جهة الندى", "2025-01-01"))
        employers.attach(e.id, transferPartyOf(deposit("2026-09-27", 1))!!)
        employers.attach(e.id, transferPartyOf(deposit("2026-09-27", 1, account = "9876"))!!)
        sources.saveMany(listOf(sources.listAll().single().copy(expectedMinor = 1_200_000, expectedDayOfMonth = 27)))
    }

    private suspend fun get(id: String) = txns.findByIds(listOf(id)).single()

    @Test fun noRuleUnlessTheBoxIsTicked() = runBlocking<Unit> {
        nada()
        val first = deposit("2026-10-05", 85_000)
        txns.saveMany(listOf(first))
        val view = payer.view(first.id)!!
        assertTrue(view.asks)
        assertTrue(view.why!!.amountFar && view.why!!.dateFar, "المبلغ والميعاد بعاد")

        payer.answer(first.id, EconomicKind.BONUS)
        assertEquals(EconomicKind.BONUS, get(first.id).economicKind)
        assertNull(payer.view(first.id)!!.ruleKind, "من غير العلامة ⇒ مفيش قاعدة")
        val next = deposit("2026-11-06", 60_000)
        assertEquals(next, applyKnownPayerSalary(next, sources.listAll(), "now"), "المرة الجاية بيتسأل تاني")

        payer.answer(first.id, EconomicKind.BONUS, makeRule = true)
        assertEquals(EconomicKind.BONUS, applyKnownPayerSalary(next, sources.listAll(), "now").economicKind)
    }

    @Test fun aCustomChoiceStaysForItsPayerAccountOnly() = runBlocking<Unit> {
        nada()
        val first = deposit("2026-10-05", 85_000)
        val other = deposit("2026-10-06", 70_000, account = "9876")
        txns.saveMany(listOf(first, other))
        val undo = payer.answerCustom(first.id, PayerCustomKind("🎓", "بدل دورة"), makeRule = true)
        assertEquals(EconomicKind.OTHER_INCOME, get(first.id).economicKind)
        assertEquals("🎓 بدل دورة", get(first.id).note)
        assertEquals(listOf(PayerCustomKind("🎓", "بدل دورة")), payer.view(first.id)!!.customs)
        assertEquals(emptyList(), payer.view(other.id)!!.customs, "الحساب التاني ما بيشوفهوش")
        // القاعدة بالاسم بتتكتب على الجاي من نفس الحساب بس
        val next = applyKnownPayerSalary(deposit("2026-11-06", 60_000), sources.listAll(), "now")
        assertEquals(EconomicKind.OTHER_INCOME to "🎓 بدل دورة", next.economicKind to next.note)
        assertEquals(EconomicKind.UNCLASSIFIED, applyKnownPayerSalary(deposit("2026-11-06", 60_000, account = "9876"), sources.listAll(), "now").economicKind)

        payer.undo(first.id, undo)
        assertNull(get(first.id).note, "التراجع رجّع الملاحظة")
        assertEquals(EconomicKind.UNCLASSIFIED, get(first.id).economicKind)
        assertEquals(1, payer.view(first.id)!!.customs.size, "الاختيار المحفوظ بيفضل (زي التصميم)")
    }

    @Test fun oneRefundForSeveralPurchasesNoneCountsAsSpend() = runBlocking<Unit> {
        nada()
        val hotel = purchase("2026-10-01", 185_000, waiting = true)
        val flight = purchase("2026-09-29", 135_000, waiting = true)
        val coffee = purchase("2026-10-04", 2_300)
        val refund = deposit("2026-10-07", 320_000)
        txns.saveMany(listOf(hotel, flight, coffee, refund))

        val picks = payer.refundPicks(refund.id)
        assertEquals(listOf(hotel.id, flight.id), picks.candidates.take(2).map { it.expense.id }, "مستني استرداد فوق")
        assertEquals(setOf(hotel.id, flight.id), picks.preselected)
        val chosen = picks.candidates.filter { it.expense.id in picks.preselected }
        assertEquals(0L, payer.refundSum(refund.amountMinor, chosen).diffMinor, "مطابق للتحويل بالظبط")
        assertEquals(185_000L, payer.refundSum(refund.amountMinor, chosen.take(1)).diffMinor.let { 320_000L - it }, "فاضل")
        assertTrue(payer.refundSum(refund.amountMinor, picks.candidates).diffMinor < 0, "أكتر من التحويل")

        val done = payer.answerRefund(refund.id, listOf(hotel.id, flight.id))
        assertNull(done.refusal)
        for (id in listOf(hotel.id, flight.id)) {
            val e = get(id)
            assertNull(e.awaitingRefund)
            assertEquals(0L, personalShareOf(e, emptyList()), "اترد ⇒ مش مصروف")
            assertTrue(payer.refundStatus(id) is RefundStatus.Refunded)
        }
        val inflow = get(refund.id)
        assertTrue(isLinkedInflow(inflow))
        assertEquals(mapOf(hotel.id to 185_000L, flight.id to 135_000L), linkedSharesOf(inflow))
        val totals = computePeriodTotals(txns.all(), emptyList())
        assertEquals(0L to 2_300L, totals.incomeMinor to totals.personalExpenseMinor, "التحويل مش دخل · القهوة بس مصروف")

        payer.undo(refund.id, done.undo!!)
        assertEquals(185_000L, personalShareOf(get(hotel.id), emptyList()))
        assertNull(get(refund.id).linkedExpenseId)
        assertNull(get(refund.id).linkedSplit)
        assertEquals(EconomicKind.UNCLASSIFIED, get(refund.id).economicKind)
    }

    @Test fun deletingOnePurchaseKeepsTheRefundOnTheOther() = runBlocking<Unit> {
        nada()
        val a = purchase("2026-10-01", 100_000)
        val b = purchase("2026-10-02", 50_000)
        val refund = deposit("2026-10-07", 150_000)
        txns.saveMany(listOf(a, b, refund))
        assertNull(payer.answerRefund(refund.id, listOf(a.id, b.id)).refusal)
        LinkedUndo(txns, clock).undo("", emptyList(), listOf(a.id))
        val left = get(refund.id)
        assertEquals(mapOf(b.id to 50_000L), linkedSharesOf(left))
        assertEquals(50_000L, get(b.id).linkedInflowMinor)
        // الفك بعد كده بيرجّع كل حاجة
        val undo = links.unlinkWithUndo(refund.id)!!
        assertNull(get(b.id).linkedInflowMinor)
        assertNull(links.relink(undo))
        assertEquals(50_000L, get(b.id).linkedInflowMinor)
    }
}
