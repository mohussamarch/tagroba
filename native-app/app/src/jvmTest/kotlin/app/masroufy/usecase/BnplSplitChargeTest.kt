package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentError
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * PLAN-0.6 §8 — «مجموع أكتر من قسط»: خصم واحد من تابي بيدفع أقساط كذا خطة (موجودة أو «+ مشترى جديد»)، والمجموع لازم يساوي الخصم بالهللة.
 * كل الأسامي والأرقام مخترعة.
 */
class BnplSplitChargeTest {
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
    }

    /** خطتين مفتوحتين: «سماعة» قسطها 250 و«كرسي» قسطها 400 (أول قسط لكل واحدة مدفوع). */
    private suspend fun World.twoPlans(): Pair<String, String> {
        val a = bnpl.newPurchase("a1", BnplPurchase("سماعة وهمية", null, 4))
        val b = bnpl.newPurchase("b1", BnplPurchase("كرسي وهمي", null, 4))
        return a.id to b.id
    }

    private val seed = listOf(charge("a1", 25_000, "2026-08-10"), charge("b1", 40_000, "2026-08-12"))

    @Test fun oneChargeSplitAcrossTwoPlans() = runBlocking<Unit> {
        val w = World(seed + charge("m", 65_000, "2026-09-11"))
        val (a, b) = w.twoPlans()
        val view = w.split.view("m")
        assertEquals(setOf(a, b), view.options.map { it.planId }.toSet())
        val parts = listOf(BnplSplitPart(25_000, planId = a), BnplSplitPart(40_000, planId = b))
        assertEquals(0L, w.split.diff(65_000, parts))
        w.split.save("m", parts)
        assertEquals(listOf(25_000L, 25_000L), w.payments.listByPlan(a).map { it.amountMinor })
        assertEquals(listOf(40_000L, 40_000L), w.payments.listByPlan(b).map { it.amountMinor })
        assertTrue(w.bnpl.settle().asks.none { it.transaction.id == "m" }, "اتوزّع ⇒ ما بيتسألش تاني")
    }

    @Test fun oneChargeSplitAcrossThreePlansWithANewPurchase() = runBlocking<Unit> {
        val w = World(seed + charge("m", 80_000, "2026-09-11"))
        val (a, b) = w.twoPlans()
        val parts = listOf(
            BnplSplitPart(25_000, planId = a), BnplSplitPart(40_000, planId = b),
            BnplSplitPart(15_000, newPurchase = BnplPurchase("شاحن وهمي", null, 3)),
        )
        val undo = w.split.save("m", parts)
        val fresh = w.plans.listAll().single { it.id == undo.newPlanIds.single() }
        assertEquals("شاحن وهمي", fresh.name)
        assertEquals(45_000L, fresh.totalMinor, "قسط 150 × 3")
        assertEquals("2026-09-11", fresh.firstDueAt, "أول قسط = يوم الخصم")
        assertEquals(80_000L, w.payments.listByTransactionIds(listOf("m")).sumOf { it.amountMinor })
    }

    @Test fun sumMismatchIsRejectedAndNothingIsWritten() = runBlocking<Unit> {
        val w = World(seed + charge("m", 70_000, "2026-09-11"))
        val (a, b) = w.twoPlans()
        val parts = listOf(BnplSplitPart(25_000, planId = a), BnplSplitPart(40_000, planId = b))
        assertEquals(5_000L, w.split.diff(70_000, parts), "الفرق بيبان: ناقص 50")
        assertFailsWith<InstallmentError> { w.split.save("m", parts) }
        assertFailsWith<InstallmentError> { w.split.save("m", listOf(BnplSplitPart(70_000, planId = a))) }
        assertTrue(w.payments.listByTransactionIds(listOf("m")).isEmpty())
        assertEquals(2, w.plans.listAll().size)
    }

    @Test fun undoPutsEverythingBack() = runBlocking<Unit> {
        val w = World(seed + charge("m", 80_000, "2026-09-11"))
        val (a, b) = w.twoPlans()
        val before = w.txns.findByIds(listOf("m")).single()
        val undo = w.split.save(
            "m",
            listOf(BnplSplitPart(25_000, planId = a), BnplSplitPart(40_000, planId = b), BnplSplitPart(15_000, newPurchase = BnplPurchase("", null, 2))),
        )
        w.split.undo(undo)
        assertTrue(w.payments.listByTransactionIds(listOf("m")).isEmpty())
        assertEquals(setOf(a, b), w.plans.listAll().map { it.id }.toSet(), "الخطة الجديدة اتمسحت")
        val after = w.txns.findByIds(listOf("m")).single()
        assertEquals(before.economicKind to before.categoryId, after.economicKind to after.categoryId)
        assertTrue(w.bnpl.settle().asks.any { it.transaction.id == "m" }, "بيتسأل تاني")
    }
}
