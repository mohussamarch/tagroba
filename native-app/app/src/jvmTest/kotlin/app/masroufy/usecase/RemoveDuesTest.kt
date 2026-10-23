package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** r06-links: مسح خطة التقسيط مع «تراجع» — بس لو مالهاش دفعات مربوطة بعمليات (الدفتر ما يتكسرش). بيانات مخترعة · وحدة صغرى. */
class RemoveDuesTest {
    private fun plan(id: String) = InstallmentPlan(id, "جهاز وهمي", "TEST", InstallmentKind.PURCHASE_PLAN, Currency.SAR, 120_000, 120_000, 30_000, 1, "2026-10-01")

    @Test fun aPlanWithoutPaymentsIsDeletedAndCanBeRestored() = runBlocking<Unit> {
        val plans = MemoryInstallmentPlanRepository(listOf(plan("p1")))
        val remove = RemoveDues(plans, MemoryInstallmentPaymentRepository())
        val removed = remove.deletePlan("p1")
        assertTrue(plans.listAll().isEmpty())
        remove.restorePlan(removed)
        assertEquals(listOf(plan("p1")), plans.listAll(), "التراجع بيرجّعها زي ما كانت")
    }

    @Test fun aPlanWithLinkedPaymentsIsRefused() = runBlocking<Unit> {
        val plans = MemoryInstallmentPlanRepository(listOf(plan("p1")))
        val remove = RemoveDues(plans, MemoryInstallmentPaymentRepository(listOf(InstallmentPayment("pay-1", "p1", "t-1", 30_000))))
        assertFailsWith<DuesRemovalRefused> { remove.deletePlan("p1") }
        assertEquals(1, plans.listAll().size, "ما اتمسحتش")
    }
}
