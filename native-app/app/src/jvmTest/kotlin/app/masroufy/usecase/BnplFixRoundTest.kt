package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DueFlow
import app.masroufy.core.DueItem
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.EconomicKind
import app.masroufy.core.Id
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.alertReasonText
import app.masroufy.core.dueAlertCandidates
import app.masroufy.core.mutedAlertDecision
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.InstallmentPaymentRepository
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * جولة إصلاح «ادفع بعدين» (r051-f): خصم مربوط بحاجة تانية ما بيوقفش الباقي (F2) · الربط والرد ورا بعض (F4) · «تراجع» بيرجّع العملية زي
 * ما كانت (F6) · «تخطّي» ما بيكتبش (F7) · «ليه اتبعت» للتذكير قبل بيوم (F9). كل الأسامي والأرقام مخترعة.
 */
class BnplFixRoundTest {
    private fun charge(id: String, minor: Long, date: String, merchant: String = "Tabby") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "2026-10-16T08:00:00.000Z", updatedAt = "x", rawMerchantName = merchant,
    )

    /** نفس المستودعات بس بتسيب الدور (`yield`) بعد كل قراية (النتيجة ممكن تبقى قديمة) ⇒ أي فحص وكتابة من غير قفل بيتداخلوا زي الجوال الحقيقي. */
    private class YieldingTxns(private val d: MemoryTransactionRepository) : TransactionRepository by d {
        override suspend fun findByIds(ids: List<Id>): List<Transaction> = d.findByIds(ids).also { yield() }
    }

    private class YieldingPayments(private val d: MemoryInstallmentPaymentRepository) : InstallmentPaymentRepository by d {
        override suspend fun listByTransactionIds(transactionIds: List<Id>): List<InstallmentPayment> = d.listByTransactionIds(transactionIds).also { yield() }
    }

    private class World(seed: List<Transaction>, rosca: List<RoscaEntry> = emptyList()) {
        val memTxns = MemoryTransactionRepository(seed)
        val memPayments = MemoryInstallmentPaymentRepository()
        val txns = YieldingTxns(memTxns)
        val payments = YieldingPayments(memPayments)
        val plans = MemoryInstallmentPlanRepository()
        val clock = FixedClock("2026-10-16T09:00:00.000Z")
        val cats = MemoryCategoryRepository(listOf(Category("cat-gadgets", null, "أجهزة وهمية", "box", "#111111", "#222222", true, 1)))
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                plans, payments, MemoryRoscaEntryRepository(rosca), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(memPayments, memTxns)), SequentialIdGenerator(), clock, cats,
            ),
        )
        val bnpl = BnplCharges(BnplChargesDeps(txns, plans, payments, installments, clock, { "2026-10-16" }, categories = cats))
    }

    @Test fun aChargeLinkedToSomethingElseIsLeftAloneAndTheRestStillLink() = runBlocking<Unit> {
        // خطة تابي بقسط 250 · خصم 250 اتسجل في جمعية · وخصم 250 تاني عادي ⇒ قبل الإصلاح الأولاني كان بيوقّع `settle` كله
        val gift = RoscaEntry("re1", "rosca-x", "t-rosca", RoscaEntryKind.CONTRIBUTION, 25_000)
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), charge("t-rosca", 25_000, "2026-10-01"), charge("t2", 25_000, "2026-10-07")), listOf(gift))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", null, 4))
        val r = w.bnpl.settle()
        assertEquals(listOf("t2"), r.autoLinked.map { it.transactionId }, "the other charge still links by itself")
        assertTrue(r.asks.none { it.transaction.id == "t-rosca" }, "a charge already linked elsewhere is never asked")
        assertEquals(listOf(25_000L, 25_000L), w.memPayments.listByPlan(plan.id).map { it.amountMinor })
        assertTrue(w.memPayments.listByTransactionIds(listOf("t-rosca")).isEmpty())
        // ولو اتبعت رد عليه برضه ⇒ غلط مكتوب للمالك (مش سكوت)
        val e = assertFailsWith<DueLinkError> { w.bnpl.answer(BnplAnswer.Of("t-rosca", plan.id)) }
        assertEquals(uiText(TextKey.DUE_TXN_ALREADY_LINKED), e.message)
    }

    @Test fun settleAndAnswerNeverGiveOneChargeTwoPayments() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), charge("t2", 25_000, "2026-10-07")))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", null, 4))
        // اللوحة لسه بتعرض t2 والمالك داس «تأكيد» في نفس اللحظة اللي القراية بتربطه لوحدها
        val settle = async { w.bnpl.settle() }
        val answer = async { runCatching { w.bnpl.answer(BnplAnswer.Of("t2", plan.id)) } }
        settle.await()
        answer.await()
        assertEquals(1, w.memPayments.listByTransactionIds(listOf("t2")).size, "one payment for one charge")
        assertEquals(50_000L, w.memPayments.listByPlan(plan.id).sumOf { it.amountMinor })
    }

    @Test fun undoPutsTheOperationBackExactlyAsItWas() = runBlocking<Unit> {
        // «+» بإيدك تحت «أجهزة وهمية» ونوعه متأكد ⇒ اتربط لوحده ⇒ «تراجع»
        val manual = charge("m1", 25_000, "2026-10-07").copy(
            economicKindConfirmed = true, categoryId = "cat-gadgets", categoryConfirmed = true, reviewState = ReviewState.CONFIRMED,
        )
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), manual))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", null, 4))
        val linked = w.bnpl.settle().autoLinked.single()
        assertEquals("m1", linked.transactionId)
        w.bnpl.undoAutoLink(linked)
        val back = w.memTxns.findByIds(listOf("m1")).single()
        assertEquals("cat-gadgets", back.categoryId, "the owner's category is kept")
        assertTrue(back.categoryConfirmed)
        assertEquals(EconomicKind.PURCHASE, back.economicKind)
        assertTrue(back.economicKindConfirmed)
        assertEquals(ReviewState.CONFIRMED, back.reviewState, "not sent back to review")
        assertEquals(listOf(25_000L), w.memPayments.listByPlan(plan.id).map { it.amountMinor })
    }

    @Test fun anUnansweredChargeKeepsItsCategoryAndIsAskedAgainLater() = runBlocking<Unit> {
        // «تخطّي» = قفل اللوحة بس (النموذج) ⇒ العملية زي ما هي، والسؤال بيرجع لما التطبيق يتفتح تاني
        val manual = charge("m1", 40_000, "2026-10-16").copy(economicKindConfirmed = true, categoryId = "cat-gadgets", categoryConfirmed = true)
        val w = World(listOf(manual))
        assertEquals(listOf("m1"), w.bnpl.settle().asks.map { it.transaction.id })
        val again = w.bnpl.settle()
        assertEquals(listOf("m1"), again.asks.map { it.transaction.id })
        val t = w.memTxns.findByIds(listOf("m1")).single()
        assertEquals("cat-gadgets", t.categoryId)
        assertFalse(t.bnplDecided == true)
    }

    @Test fun oneDayReminderSaysTomorrowNotThreeDays() {
        val item = DueItem(DueSource.INSTALLMENT, "p1", "سماعة وهمية", "2026-11-07", 25_000, Currency.SAR, DueFlow.PAY, DueStatus.UPCOMING, alertDays = 1)
        val c = dueAlertCandidates(listOf(item), "2026-11-06") { null }.single()
        assertEquals(1, c.soonDays)
        val why = alertReasonText(mutedAlertDecision(c.kind), c.soonDays)
        assertTrue(why.startsWith(uiText(TextKey.ALERT_WHY_DUE_TOMORROW)), why)
        assertFalse(why.contains("3"), why)
        val legacy = dueAlertCandidates(listOf(item.copy(alertDays = 3)), "2026-11-04") { null }.single()
        assertTrue(alertReasonText(mutedAlertDecision(legacy.kind), legacy.soonDays).startsWith(uiText(TextKey.ALERT_WHY_DUE_SOON, "3")))
    }
}
