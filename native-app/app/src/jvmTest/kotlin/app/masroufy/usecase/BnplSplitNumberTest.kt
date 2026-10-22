package app.masroufy.usecase

import app.masroufy.core.BnplMatch
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.bnplPreviewAt
import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplStepState
import app.masroufy.core.bnplSplitReady
import app.masroufy.core.bnplSumSuggestion
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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r06-1b4 — خصم تابي المجمّع («غالبًا: «أ» + «ب»» · «وزّع» بالمجموع المطابق بس) و«الدفعة دي رقم كام؟» (اللي قبلها اتدفعت من غير عمليات).
 * كل الأسامي والأرقام مخترعة.
 */
class BnplSplitNumberTest {
    private fun charge(id: String, minor: Long, date: String) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = "Tabby",
    )

    private class World(seed: List<Transaction>) {
        val txns = MemoryTransactionRepository(seed)
        val payments = MemoryInstallmentPaymentRepository()
        val plans = MemoryInstallmentPlanRepository()
        val clock = FixedClock("2026-10-16T09:00:00.000Z")
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                plans, payments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns, plans)), SequentialIdGenerator(), clock, MemoryCategoryRepository(),
            ),
        )
        val deps = BnplChargesDeps(txns, plans, payments, installments, clock, { "2026-10-16" })
        val bnpl = BnplCharges(deps)
        val split = BnplSplitCharge(deps)
        suspend fun opCount() = txns.listByDateRange("2000-01-01", "2100-01-01").size
    }

    @Test fun subsetSumFindsTheExactPair() {
        val dues = listOf("a" to 25_000L, "b" to 18_000L, "c" to 40_000L)
        assertEquals(listOf("a", "b"), bnplSumSuggestion(43_000, dues))
        assertEquals(listOf("a", "c"), bnplSumSuggestion(65_000, dues))
        assertEquals(listOf("a", "b", "c"), bnplSumSuggestion(83_000, dues))
        // مفيش مجموع مطابق · قسط واحد لوحده مش «مجموع» · مجموعتين بنفس العدد ⇒ مش واضح ⇒ مفيش اقتراح
        assertEquals(emptyList(), bnplSumSuggestion(43_001, dues))
        assertEquals(emptyList(), bnplSumSuggestion(25_000, dues))
        assertEquals(emptyList(), bnplSumSuggestion(20_000, listOf("x" to 10_000L, "y" to 10_000L, "z" to 10_000L)))
        // الأقل عدد بيكسب: 300 = 100+200 (اتنين) قبل 50+50+200 (تلاتة)
        assertEquals(listOf("p", "q"), bnplSumSuggestion(30_000, listOf("p" to 10_000L, "q" to 20_000L, "r" to 5_000L, "s" to 5_000L)))
    }

    @Test fun distributeOnlyOnExactSumWithTwoPartsAndNamedPurchases() {
        assertTrue(bnplSplitReady(43_000, listOf(25_000, 18_000), newNamed = true))
        assertFalse(bnplSplitReady(43_000, listOf(25_000, 17_999), newNamed = true), "ناقص هللة")
        assertFalse(bnplSplitReady(43_000, listOf(25_000, 18_001), newNamed = true), "زيادة هللة")
        assertFalse(bnplSplitReady(43_000, listOf(43_000), newNamed = true), "جزء واحد")
        assertFalse(bnplSplitReady(43_000, listOf(25_000, null), newNamed = true), "مبلغ مش مكتوب")
        assertFalse(bnplSplitReady(43_000, listOf(25_000, 18_000), newNamed = false), "مشترى من غير اسم")
    }

    @Test fun paymentThreeOfFourMarksTwoEarlierPaidWithoutOperations() = runBlocking<Unit> {
        val w = World(listOf(charge("m", 25_000, "2026-10-16")))
        val ops = w.opCount()
        val done = w.bnpl.answer(BnplAnswer.New("m", BnplPurchase("مكنسة وهمية", null, 4, at = 3)))
        assertEquals(25_000L, done.remainingMinor, "الباقي = القسط × (4 − 3)")
        val plan = w.plans.listAll().single()
        assertEquals(2, plan.paidBeforeCount)
        assertEquals("2026-08-16", plan.firstDueAt, "الدفعة 3 النهارده ⇒ الأولى من شهرين")
        assertEquals(listOf("m"), w.payments.listByPlan(plan.id).map { it.transactionId }, "دفعة واحدة بس مربوطة بعملية")
        assertEquals(ops, w.opCount(), "ولا عملية جديدة")
        val view = w.installments.list("2026-10-16").single()
        assertEquals(3, view.progress.paidCount)
        assertEquals(25_000L, view.progress.remainingMinor)
        assertEquals("2026-11-16", view.progress.nextDueAt)
        // المعاينة قبل الحفظ بنفس الأرقام
        val p = bnplPreviewAt(25_000, Currency.SAR, "2026-10-16", 4, BnplCycle.MONTHLY, 3)
        assertEquals(25_000L, p.remainingMinor)
        assertEquals(listOf(BnplStepState.PAID, BnplStepState.PAID, BnplStepState.PAID, BnplStepState.NEXT), p.steps.map { it.state })
        assertEquals("2026-08-16", p.steps.first().dueAt)
    }

    @Test fun askWhichCarriesTheSumHintAndSplitViewPreChecksIt() = runBlocking<Unit> {
        val w = World(listOf(charge("a1", 25_000, "2026-08-10"), charge("b1", 18_000, "2026-08-12"), charge("m", 43_000, "2026-09-11")))
        val a = w.bnpl.newPurchase("a1", BnplPurchase("سماعة وهمية", null, 4)).id
        val b = w.bnpl.newPurchase("b1", BnplPurchase("أواني وهمية", null, 3)).id
        val ask = w.bnpl.settle().asks.single { it.transaction.id == "m" }
        assertTrue(ask.match is BnplMatch.AskWhich)
        assertEquals(listOf(a, b), ask.splitHint)
        assertEquals(listOf(a, b), w.split.view("m").suggested)
    }

    @Test fun splitWithNewPurchaseAtNumberTwoThenUndo() = runBlocking<Unit> {
        val w = World(listOf(charge("a1", 25_000, "2026-08-10"), charge("m", 45_000, "2026-10-16")))
        val a = w.bnpl.newPurchase("a1", BnplPurchase("سماعة وهمية", null, 4)).id
        val ops = w.opCount()
        val parts = listOf(BnplSplitPart(25_000, planId = a), BnplSplitPart(20_000, newPurchase = BnplPurchase("مكنسة وهمية", null, 3, at = 2)))
        assertTrue(w.split.ready(45_000, parts.map { it.amountMinor }, newNamed = true))
        val undo = w.split.save("m", parts)
        val fresh = w.plans.listAll().single { it.id in undo.newPlanIds }
        assertEquals(1, fresh.paidBeforeCount)
        assertEquals("2026-09-16", fresh.firstDueAt)
        assertEquals(20_000L, w.installments.list("2026-10-16").single { it.plan.id == fresh.id }.progress.remainingMinor, "20 × (3 − 2)")
        assertEquals(ops, w.opCount())
        w.split.undo(undo)
        assertNull(w.plans.listAll().firstOrNull { it.id == fresh.id }, "الخطة الجديدة اتمسحت")
        assertEquals(listOf("a1"), w.payments.listByPlan(a).map { it.transactionId }, "دفعة الخصم اتفكت")
        assertEquals(EconomicKind.PURCHASE, w.txns.findByIds(listOf("m")).single().economicKind)
    }
}
