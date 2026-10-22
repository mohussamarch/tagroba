package app.masroufy.usecase

import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplProvider
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DuesCategories
import app.masroufy.core.EconomicKind
import app.masroufy.memory.FixedClock
import app.masroufy.core.ReviewState
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.core.Transaction
import app.masroufy.core.bnplProviderOf
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r05 — «اشتري دلوقتي وادفع بعدين» من رسايل البنك: أول خصم من تابي ⇒ سؤال «اشتريت إيه؟ قسمتها على كام؟» ⇒ خطة (أول قسط مدفوع) ⇒ الخصومات
 * الجاية بنفس القسط بتتربط لوحدها · مبلغ تاني ⇒ «أنهي مشترى؟» · «تخطّي» ⇒ قسط عادي. الحساب = قواعد الأقساط (القسط مصروف، المشترى مالوش عملية).
 * كل الأسامي والأرقام مخترعة.
 */
class BnplChargesTest {
    private fun charge(id: String, minor: Long, date: String, merchant: String = "Tabby") = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = false,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = merchant,
    )

    private class World(seed: List<Transaction>, today: String = "2026-10-16") {
        val txns = MemoryTransactionRepository(seed)
        val payments = MemoryInstallmentPaymentRepository()
        val plans = MemoryInstallmentPlanRepository()
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                plans, payments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns)), SequentialIdGenerator(), FixedClock("2026-10-16T09:00:00.000Z"), MemoryCategoryRepository(),
            ),
        )
        val bnpl = BnplCharges(BnplChargesDeps(txns, plans, payments, installments, FixedClock("2026-10-16T09:00:00.000Z"), { today }))
    }

    @Test fun providersAreRecognisedByNameOnly() {
        assertEquals(BnplProvider.TABBY, bnplProviderOf("Tabby"))
        assertEquals(BnplProvider.TABBY, bnplProviderOf("TABBY.AI RIYADH"))
        assertEquals(BnplProvider.TAMARA, bnplProviderOf("تمارا"))
        assertEquals(BnplProvider.MISPAY, bnplProviderOf("MIS PAY"))
        assertEquals(BnplProvider.VALU, bnplProviderOf("valU"))
        assertNull(bnplProviderOf("كتابي للقرطاسية"), "a word that only contains the brand letters")
        assertNull(bnplProviderOf("TEST GROCER"))
        assertNull(bnplProviderOf("مدفوع"), "plain Arabic word, not the brand")
    }

    @Test fun firstChargeAsksThenBecomesThePlanWithTheFirstInstallmentPaid() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-10")))
        val first = w.bnpl.settle()
        assertTrue(first.linked.isEmpty())
        val ask = first.asks.single()
        assertEquals(BnplMatch.NewPurchase, ask.match)
        assertEquals(BnplProvider.TABBY, ask.provider)

        val plan = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", "cat-test", 4))
        assertEquals(100_000L, plan.totalMinor, "total = installment × count")
        assertEquals(25_000L, plan.installmentMinor)
        assertEquals("2026-09-10", plan.firstDueAt, "first due = the purchase day (paid at purchase)")
        assertEquals("Tabby", plan.provider)
        assertEquals(25_000L, w.payments.listByPlan(plan.id).single().amountMinor, "first installment paid")
        // القسط مصروف بتصنيف المشترى، ومفيش عملية تانية للمشترى نفسه
        val t1 = w.txns.findByIds(listOf("t1")).single()
        assertEquals(EconomicKind.PURCHASE, t1.economicKind)
        assertEquals("cat-test", t1.categoryId)
        assertEquals(1, w.txns.listByDateRange("2026-01-01", "2026-12-31").size)
        assertTrue(w.bnpl.settle().asks.isEmpty(), "answered ⇒ not asked again")
    }

    @Test fun nextChargesWithTheSameInstallmentLinkByThemselves() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-08-10"), charge("t2", 25_000, "2026-09-10"), charge("t3", 24_999, "2026-10-10")))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("", null, 4))
        assertEquals("Tabby", plan.name, "blank answer ⇒ the provider name")
        val settled = w.bnpl.settle()
        assertEquals(mapOf("t2" to plan.id, "t3" to plan.id), settled.linked, "same amount ±1 halala ⇒ next due installment")
        assertTrue(settled.asks.isEmpty())
        assertEquals(74_999L, w.payments.listByPlan(plan.id).sumOf { it.amountMinor })
        // بلا خطة: قسط عادي تحت «المستحقات ← تقسيط مشتريات»
        assertEquals(DuesCategories.PURCHASE_PLANS, w.txns.findByIds(listOf("t2")).single().categoryId)
    }

    @Test fun aDifferentAmountAsksWhichPurchase() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-10"), charge("t2", 13_000, "2026-09-20")))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("حذاء وهمي", null, 4, BnplCycle.TWO_WEEKS))
        assertEquals(2, plan.cycleWeeks)
        val ask = w.bnpl.settle().asks.single()
        val which = assertIs<BnplMatch.AskWhich>(ask.match)
        assertEquals(listOf(plan.id), which.planIds)
        assertEquals(listOf(plan.id), ask.plans.map { it.id })
        w.bnpl.installmentOf("t2", plan.id)
        assertTrue(w.bnpl.settle().asks.isEmpty())
    }

    @Test fun anotherProviderIsANewPurchaseAndNotAnInstallmentIsNotAskedAgain() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-10"), charge("t2", 25_000, "2026-09-12", merchant = "Tamara")))
        w.bnpl.newPurchase("t1", BnplPurchase("جوال وهمي", null, 4))
        val ask = w.bnpl.settle().asks.single()
        assertEquals("t2", ask.transaction.id)
        assertEquals(BnplMatch.NewPurchase, ask.match, "a Tabby plan never takes a Tamara charge")
        w.bnpl.notInstallment("t2")
        val t2 = w.txns.findByIds(listOf("t2")).single()
        assertEquals(true, t2.bnplDecided)
        assertNull(t2.categoryId, "the operation stays as it was")
        assertTrue(w.bnpl.settle().asks.isEmpty())
    }

    @Test fun incomingAndOldChargesAreNotAsked() = runBlocking<Unit> {
        val refund = charge("r1", 25_000, "2026-09-10").copy(observedDirection = Direction.IN)
        val old = charge("o1", 25_000, "2025-01-10")
        assertTrue(World(listOf(refund, old)).bnpl.settle().asks.isEmpty())
    }
}
