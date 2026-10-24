package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** r06-delete (§118-1) — مسح خطة التقسيط بدفعاتها. الأرشفة أول اختيار (§118-ب-1). كل الأسامي والمبالغ مخترعة. */
class RemovePlanTest {
    private val plan = InstallmentPlan("pl-1", "جوال تجربة", "متجر تجربة", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 3_000_00, 3_000_00, 1_000_00, 1, "2026-08-01")

    private suspend fun kit(paid: Boolean = true) = RemovalKit().apply {
        plans.save(plan)
        if (paid) {
            txns.saveMany(listOf(op("p-1", 1_000_00), op("p-2", 1_000_00), op("x", 70_00)))
            payments.saveMany(listOf(InstallmentPayment("ip-1", "pl-1", "p-1", 1_000_00), InstallmentPayment("ip-2", "pl-1", "p-2", 1_000_00)))
        }
    }

    @Test
    fun noPayments_deleteThenUndo() = runBlocking {
        val k = kit(paid = false)
        assertEquals(listOf(RemoveChoice.DELETE), k.removals.plan.ask("pl-1").choices)
        val done = k.removals.plan.remove("pl-1", RemoveChoice.DELETE)
        assertTrue(k.plans.listAll().isEmpty())
        done.undo()
        assertEquals(listOf(plan), k.plans.listAll())
    }

    @Test
    fun linked_archiveFirstThenTwoChoices() = runBlocking {
        val ask = kit().removals.plan.ask("pl-1")
        assertEquals(2, ask.linkedCount)
        assertEquals(listOf(RemoveChoice.ARCHIVE, RemoveChoice.KEEP_OPERATIONS, RemoveChoice.WITH_OPERATIONS), ask.choices)
    }

    @Test
    fun keep_totalsUnchanged_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val totals = k.totals()
        val done = k.removals.plan.remove("pl-1", RemoveChoice.KEEP_OPERATIONS)
        assertTrue(k.plans.listAll().isEmpty() && k.payments.listByPlan("pl-1").isEmpty())
        assertEquals(totals, k.totals())
        assertEquals(2, k.all().count { it.sourceRemoved == "plan:جوال تجربة" })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(2, k.payments.listByPlan("pl-1").size)
    }

    @Test
    fun withOperations_undoExact() = runBlocking {
        val k = kit()
        val before = k.picture()
        val done = k.removals.plan.remove("pl-1", RemoveChoice.WITH_OPERATIONS)
        assertEquals(listOf("x"), k.all().map { it.id })
        done.undo()
        assertEquals(before, k.picture())
        assertEquals(listOf(plan), k.plans.listAll())
        assertEquals(2, k.payments.listByPlan("pl-1").size)
    }
}
