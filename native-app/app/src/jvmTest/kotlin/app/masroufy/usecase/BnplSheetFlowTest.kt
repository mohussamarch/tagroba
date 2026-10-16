package app.masroufy.usecase

import app.masroufy.core.BnplCycle
import app.masroufy.core.BnplMatch
import app.masroufy.core.BnplStepState
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DueFlow
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.DueItem
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.bnplPreview
import app.masroufy.core.dueAlertCandidates
import app.masroufy.core.installmentDueItems
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryWalletRepository
import app.masroufy.memory.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * r051 — لوحة «ادفع بعدين» المعتمدة (`BnplSheet.dc.html`): «خطتك» قبل الحفظ · «أنهي مشترى؟» بالأرقام والفرق · «مش قسط» · «تراجع» الربط
 * اللي لوحده · العملية اليدوية · التذكير قبل الدفعة بيوم · قسم «مشتريات بالتقسيط». كل الأسامي والأرقام مخترعة.
 */
class BnplSheetFlowTest {
    private fun charge(id: String, minor: Long, date: String, merchant: String = "Tabby", confirmed: Boolean = false, created: String = "2026-10-16T08:00:00.000Z") =
        Transaction(
            id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = EconomicKind.PURCHASE, economicKindConfirmed = confirmed,
            observedDirection = Direction.OUT, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
            reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = created, updatedAt = created, rawMerchantName = merchant, walletId = "w1",
        )

    private class World(seed: List<Transaction>) {
        val txns = MemoryTransactionRepository(seed)
        val payments = MemoryInstallmentPaymentRepository()
        val plans = MemoryInstallmentPlanRepository()
        val cats = MemoryCategoryRepository(listOf(Category("cat-gadgets", null, "أجهزة وهمية", "box", "#111111", "#222222", true, 1)))
        val clock = FixedClock("2026-10-16T09:00:00.000Z")
        val installments = ManageInstallments(
            ManageInstallmentsDeps(
                plans, payments, MemoryRoscaEntryRepository(), MemoryDebtTermsRepository(), MemoryObligationRepository(), txns,
                MemoryUnitOfWork(listOf(payments, txns)), SequentialIdGenerator(), clock, cats,
            ),
        )
        val wallets = MemoryWalletRepository(listOf(Wallet("w1", "بنك وهمي", Currency.SAR, "bank", 0, "2026-01-01")))
        val bnpl = BnplCharges(BnplChargesDeps(txns, plans, payments, installments, clock, { "2026-10-16" }, wallets, cats))
        val load = LoadBnplPlans(plans, payments, cats)
    }

    @Test fun previewIsPaymentTimesCountWithTheFirstPaidToday() {
        val monthly = bnplPreview(25_000, Currency.SAR, "2026-10-07", 4, BnplCycle.MONTHLY)
        assertEquals(100_000L, monthly.totalMinor)
        assertEquals(75_000L, monthly.remainingMinor)
        assertEquals(listOf("2026-10-07", "2026-11-07", "2026-12-07", "2027-01-07"), monthly.steps.map { it.dueAt })
        assertEquals(listOf(BnplStepState.PAID, BnplStepState.NEXT, BnplStepState.LATER, BnplStepState.LATER), monthly.steps.map { it.state })
        val twoWeeks = bnplPreview(25_000, Currency.SAR, "2026-10-07", 2, BnplCycle.TWO_WEEKS)
        assertEquals(listOf("2026-10-07", "2026-10-21"), twoWeeks.steps.map { it.dueAt }, "every 14 days")
        assertEquals("2026-02-28", bnplPreview(1_000, Currency.SAR, "2026-01-31", 2, BnplCycle.MONTHLY).steps[1].dueAt, "short month ⇒ its last day")
    }

    @Test fun whichPurchaseCarriesNumbersAndTheDifference() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), charge("t2", 25_000, "2026-10-07"), charge("t3", 30_000, "2026-10-16")))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", "cat-gadgets", 4))
        val settled = w.bnpl.settle()
        assertEquals(listOf(BnplLinked("t2", plan.id, "سماعة وهمية", 2, 4)), settled.autoLinked)
        val ask = settled.asks.single()
        assertIs<BnplMatch.AskWhich>(ask.match)
        assertEquals("بنك وهمي", ask.walletName)
        val o = ask.options.single()
        assertEquals(2, o.leftCount)
        assertEquals(4, o.count)
        assertEquals("2026-11-07", o.nextDueAt)
        assertEquals(5_000L, o.diffMinor, "300 − 250 ⇒ the amber line")
        // اختار الخطة: الباقي من اللي اتدفع فعلًا (250 + 250 + 300 من 1,000)
        assertEquals(20_000L, w.bnpl.answer(BnplAnswer.Of("t3", plan.id)).remainingMinor)
    }

    @Test fun notAnInstallmentIsNeverAskedAgainAndKeepsTheOperation() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), charge("t2", 9_900, "2026-10-10")))
        w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", null, 4))
        assertEquals("t2", w.bnpl.settle().asks.single().transaction.id)
        w.bnpl.answer(BnplAnswer.NotInstallment("t2"))
        assertTrue(w.bnpl.settle().asks.isEmpty())
        val t2 = w.txns.findByIds(listOf("t2")).single()
        assertEquals(true, t2.bnplDecided)
        assertNull(t2.categoryId, "a normal operation: its category is asked like any other")
    }

    @Test fun undoingAnAutoLinkFreesThePaymentAndAsksWhich() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), charge("t2", 25_000, "2026-10-07")))
        val plan = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", null, 4))
        val linked = w.bnpl.settle().autoLinked.single()
        w.bnpl.undoAutoLink(linked.transactionId, linked.planId)
        assertEquals(listOf(25_000L), w.payments.listByPlan(plan.id).map { it.amountMinor }, "back to the first payment only")
        val again = w.bnpl.settle()
        assertTrue(again.autoLinked.isEmpty(), "not linked by itself to the same plan again")
        assertIs<BnplMatch.AskWhich>(again.asks.single().match)
    }

    @Test fun aManualChargeRecordedNowIsAskedButOldConfirmedOnesAreNot() = runBlocking<Unit> {
        val manual = charge("m1", 40_000, "2026-10-16", merchant = "تمارا", confirmed = true)
        val oldConfirmed = charge("m0", 40_000, "2026-08-01", merchant = "تمارا", confirmed = true, created = "2026-08-01T08:00:00.000Z")
        val w = World(listOf(manual, oldConfirmed))
        assertEquals(listOf("m1"), w.bnpl.settle().asks.map { it.transaction.id })
    }

    @Test fun reminderSwitchDrivesTheAlertWindow() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-10-12"), charge("t2", 25_000, "2026-10-12", merchant = "Tamara")))
        val on = w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", null, 2, remind = true))
        val off = w.bnpl.newPurchase("t2", BnplPurchase("ساعة وهمية", null, 2, remind = false))
        assertEquals(true, on.remindDayBefore)
        val onItems = installmentDueItems(on, w.payments.listByPlan(on.id), "2026-11-10", "2026-12-31")
        assertEquals(1, onItems.single().alertDays)
        assertTrue(dueAlertCandidates(onItems, "2026-11-10") { null }.isEmpty(), "two days before ⇒ not yet")
        assertEquals(1, dueAlertCandidates(onItems, "2026-11-11") { null }.size, "one day before ⇒ reminder")
        val offItems = installmentDueItems(off, w.payments.listByPlan(off.id), "2026-11-11", "2026-12-31")
        assertEquals(1, offItems.size, "still in Dues and «القادم»")
        assertTrue(dueAlertCandidates(offItems, "2026-11-12") { null }.isEmpty(), "no reminder at all")
        val legacy = DueItem(DueSource.INSTALLMENT, "x", "x", "2026-11-14", 1, Currency.SAR, DueFlow.PAY, DueStatus.UPCOMING)
        assertEquals(1, dueAlertCandidates(listOf(legacy), "2026-11-11") { null }.size, "older plans keep the 3-day window")
    }

    @Test fun purchasesSectionListsPlansWithCategoryAndSchedule() = runBlocking<Unit> {
        val w = World(listOf(charge("t1", 25_000, "2026-09-07"), charge("t2", 25_000, "2026-10-07")))
        w.bnpl.newPurchase("t1", BnplPurchase("سماعة وهمية", "cat-gadgets", 4))
        w.bnpl.settle()
        val v = w.load.load("2026-10-16").single()
        assertEquals("أجهزة وهمية", v.categoryName)
        assertEquals(2, v.progress.paidCount)
        assertEquals(50_000L, v.progress.remainingMinor)
        assertEquals(listOf(BnplStepState.PAID, BnplStepState.PAID, BnplStepState.NEXT, BnplStepState.LATER), v.steps.map { it.state })
        assertTrue(w.load.isBnpl(v.plan))
    }
}
