package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DueScheduleError
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.computePeriodTotals
import app.masroufy.core.installmentDueItems
import app.masroufy.core.installmentSchedule
import app.masroufy.core.dueProgress
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
import kotlin.test.assertNull

/**
 * §82-20 بمثال المالك: مرتبة بـ1,000 على تابي على 4 ⇒ 250 اتدفعت وقت الشراء وفاضل 750 على 3. «عدد الأقساط × القسط ⇒ الإجمالي» في
 * حالة الاستخدام (مش الشاشة)، وأول قسط مصروف بعمليته المربوطة (§56). كل الأسامي والأرقام مخترعة.
 */
class InstallmentCountTest {
    private fun out(id: String, minor: Long, date: String, kind: EconomicKind = EconomicKind.UNCLASSIFIED) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = kind != EconomicKind.UNCLASSIFIED,
        observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", rawMerchantName = "TEST BNPL",
    )

    private inner class World(seed: List<Transaction>) {
        val txns = MemoryTransactionRepository(seed)
        val payments = MemoryInstallmentPaymentRepository()
        val plans = MemoryInstallmentPlanRepository()
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                plans, payments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns)), SequentialIdGenerator(), FixedClock("2026-10-10T09:00:00.000Z"), MemoryCategoryRepository(),
            ),
        )
    }

    private fun mattress(firstDueAt: String = "2026-10-10") = InstallmentInput(
        name = "مرتبة وهمية", provider = "خدمة تقسيط وهمية", kind = InstallmentKind.PURCHASE_PLAN, currency = Currency.SAR,
        principalMinor = 0, totalMinor = 0, installmentMinor = 25_000, firstDueAt = firstDueAt,
    )

    @Test fun countTimesInstallmentIsTheTotal() = runBlocking<Unit> {
        val w = World(emptyList())
        val p = w.installments.countPreview(25_000, 4, payLater = true)
        assertEquals(100_000L, p.totalMinor)
        assertEquals(Triple(25_000L, 75_000L, 3), Triple(p.paidAtPurchaseMinor, p.leftMinor, p.upcomingCount))
        val plain = w.installments.countPreview(150_050, 12, payLater = false)
        assertEquals(1_800_600L, plain.totalMinor, "بالهللة من غير تقريب")
        assertNull(plain.paidAtPurchaseMinor)
        assertFailsWith<DueScheduleError> { w.installments.countPreview(25_000, 0, payLater = false) }
        assertFailsWith<DueScheduleError> { w.installments.countPreview(25_000, 361, payLater = false) }
    }

    /** المرتبة: عملية أول قسط (250 يوم الشراء) بتتربط ⇒ مدفوع 250 · فاضل 750 · 3 أقساط جاية · والـ250 مصروف. */
    @Test fun buyNowPayLaterPaysTheFirstAtPurchase() = runBlocking<Unit> {
        val w = World(listOf(out("t-first", 25_000, "2026-10-10"), out("t-other", 30_000, "2026-10-10")))
        assertEquals(listOf("t-first"), w.installments.firstPaymentCandidates(Currency.SAR, 25_000, "2026-10-10").map { it.id })
        val plan = w.installments.saveCounted(mattress(), 4, firstPaymentTxnId = "t-first")
        assertEquals(100_000L to 100_000L, plan.totalMinor to plan.principalMinor, "تابي من غير زيادة ⇒ السعر = الإجمالي")
        val progress = dueProgress(installmentSchedule(plan), w.payments.listByPlan(plan.id).sumOf { it.amountMinor }, "2026-10-10")
        assertEquals(listOf(25_000L, 75_000L), listOf(progress.paidMinor, progress.remainingMinor))
        assertEquals(1 to 4, progress.paidCount to progress.count)
        assertEquals("2026-11-10", progress.nextDueAt)
        val upcoming = installmentDueItems(plan, w.payments.listByPlan(plan.id), "2026-10-10", "2027-12-31")
        assertEquals(listOf("2026-11-10", "2026-12-10", "2027-01-10"), upcoming.map { it.dueAt })
        assertEquals(listOf(25_000L, 25_000L, 25_000L), upcoming.map { it.amountMinor })
        val first = w.txns.findByIds(listOf("t-first")).single()
        assertEquals(EconomicKind.PURCHASE, first.economicKind, "القسط مصروف بعمليته (§56)")
        assertEquals(25_000L, computePeriodTotals(listOf(first), emptyList()).personalExpenseMinor)
        assertEquals(emptyList(), w.installments.firstPaymentCandidates(Currency.SAR, 25_000, "2026-10-10"), "اتربطت ⇒ مش مرشحة تاني")
    }

    /** من غير عملية لسه: أول قسط **مستحق يوم الشراء** (مش مدفوع من غير مصدر) والأربعة في الجدول. */
    @Test fun withoutItsTransactionTheFirstStaysDueOnThePurchaseDay() = runBlocking<Unit> {
        val w = World(emptyList())
        val plan = w.installments.saveCounted(mattress(), 4)
        val items = installmentDueItems(plan, emptyList(), "2026-10-10", "2027-12-31")
        assertEquals(listOf("2026-10-10", "2026-11-10", "2026-12-10", "2027-01-10"), items.map { it.dueAt })
    }

    @Test fun aWrongFirstPaymentIsRefusedBeforeSaving() = runBlocking<Unit> {
        val w = World(listOf(out("t-small", 10_000, "2026-10-10")))
        assertFailsWith<DueLinkError> { w.installments.saveCounted(mattress(), 4, firstPaymentTxnId = "t-small") }
        assertEquals(emptyList(), w.plans.listAll(), "الفحص قبل الحفظ ⇒ ولا خطة")
    }
}
