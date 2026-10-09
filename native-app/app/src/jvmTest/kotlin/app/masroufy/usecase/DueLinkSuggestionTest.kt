package app.masroufy.usecase

import app.masroufy.core.AskKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.DueLinkKind
import app.masroufy.core.DuesCategories
import app.masroufy.core.EconomicKind
import app.masroufy.core.EventLink
import app.masroufy.core.EventRole
import app.masroufy.core.Id
import app.masroufy.core.InstallmentKind
import app.masroufy.core.PendingAsk
import app.masroufy.core.ReviewState
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.SpaceTransfer
import app.masroufy.core.TextKey
import app.masroufy.core.Transaction
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.ZakatPayment
import app.masroufy.core.dueLinkQuestion
import app.masroufy.core.periodForDate
import app.masroufy.core.uiText
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryBudgetRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryEventLinkRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryZakatPaymentRepository
import app.masroufy.memory.PassthroughUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.SpaceTransferLegs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §75-8 (قرار المالك 2026-10-08): **الأقساط والجمعية ⇒ يقترح الربط، والقسط يتحسب صرف** (§56). الاقتراح سؤال على العملية (`DUE_LINK`)،
 * «أيوه» بيعدّي على الربط العادي و«مش ده» بيتفتكر على الخطة أو الجمعية. أسامي ومبالغ وهمية كلها.
 */
class DueLinkSuggestionTest {
    private fun txn(id: String, date: String, minor: Long, dir: Direction = Direction.OUT, kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 0, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = dir, amountMinor = minor, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x", walletId = "w-bank",
    )

    private class Legs(val ids: Set<Id>) : SpaceTransferLegs {
        override suspend fun pairsOf(transactionIds: List<Id>): List<SpaceTransfer> = emptyList()

        override suspend fun legsAmong(transactionIds: List<Id>): Set<Id> = transactionIds.filter { it in ids }.toSet()

        override suspend fun detach(pairs: List<SpaceTransfer>) = Unit
    }

    private class World(seed: List<Transaction>, legs: Set<Id> = emptySet()) {
        val txns = MemoryTransactionRepository(seed)
        val categories = MemoryCategoryRepository()
        val plans = MemoryInstallmentPlanRepository()
        val payments = MemoryInstallmentPaymentRepository()
        val roscas = MemoryRoscaRepository()
        val entries = MemoryRoscaEntryRepository()
        val zakat = MemoryZakatPaymentRepository()
        val events = MemoryEventLinkRepository()
        val spaceLegs = Legs(legs)
        val ids = SequentialIdGenerator()
        val clock = FixedClock("2026-10-12T00:00:00.000Z")
        val installments = ManageInstallments(
            ManageInstallmentsDeps(plans, payments, entries, MemoryDebtTermsRepository(), MemoryObligationRepository(), txns, PassthroughUnitOfWork(), ids, clock, categories, zakat, events, spaceLegs),
        )
        val roscaLinks = ManageRoscas(ManageRoscasDeps(roscas, entries, payments, txns, PassthroughUnitOfWork(), ids, clock, categories, plans, zakat, events, spaceLegs))
        val suggest = SuggestDueLinks(SuggestDueLinksDeps(txns, plans, payments, roscas, entries, installments, roscaLinks, zakat, events, spaceLegs))

        suspend fun financing(firstDue: String = "2026-10-10") = installments.save(
            InstallmentInput(
                name = "تمويل وهمي", provider = "بنك وهمي", kind = InstallmentKind.FINANCING, currency = Currency.SAR,
                principalMinor = 1_200_000, totalMinor = 1_440_000, installmentMinor = 120_000, firstDueAt = firstDue,
            ),
        )

        suspend fun rosca() = roscaLinks.save(RoscaInput(name = "جمعية وهمية", currency = Currency.SAR, contributionMinor = 50_000, firstDueAt = "2026-09-01", cycleCount = 5, myTurns = listOf(2)))

        suspend fun october() = suggest.list("2026-10-01", "2026-10-31")
    }

    @Test fun anInstallmentIsSuggestedAskedAndCountsAsSpendingOnceAccepted() = runBlocking<Unit> {
        val w = World(listOf(txn("t-ins", "2026-10-09", 120_000)))
        val plan = w.financing()
        val s = w.october().single()
        assertEquals(DueLinkKind.INSTALLMENT to plan.id, s.kind to s.ownerId)
        assertEquals("2026-10-10" to 1, s.dueAt to s.number)
        assertEquals(uiText(TextKey.DUE_LINK_ASK_INSTALLMENT, "تمويل وهمي", "2026-10-10"), dueLinkQuestion(s))
        assertEquals(listOf(PendingAsk(AskKind.DUE_LINK, "sa", "t-ins", date = "2026-10-09")), DueLinkAskSource("sa", w.suggest).pending("2026-10-01", "2026-10-31"))

        w.suggest.accept(s)
        val linked = w.txns.findByIds(listOf("t-ins")).single()
        assertEquals(EconomicKind.INSTALLMENT_PAID, linked.economicKind)
        assertTrue(linked.economicKindConfirmed)
        assertEquals(DuesCategories.FINANCING, linked.categoryId, "تحت «المستحقات ← تمويل»")
        assertEquals(ReviewState.CONFIRMED, linked.reviewState)
        assertEquals(120_000L, w.payments.listByPlan(plan.id).single().amountMinor)
        assertTrue(w.october().isEmpty(), "اتربطت ⇒ مفيش سؤال")
        // §56: القسط كله مصروف شخصي
        val home = LoadHomeScreen(LoadHomeScreenDeps(w.txns, w.categories, MemoryAllocationRepository(), MemoryBudgetRepository()))
            .load(LoadHomeScreenRequest(periodForDate("2026-10-12", 28), "2026-10-12", 28, includeHistory = false))
        assertEquals(120_000L, home.expenseMinor)
    }

    @Test fun declineIsRememberedOnThePlanAndSurvivesEditingIt() = runBlocking<Unit> {
        val w = World(listOf(txn("t-ins", "2026-10-09", 120_000)))
        val plan = w.financing()
        w.suggest.decline(w.october().single())
        assertTrue(w.october().isEmpty())
        assertEquals(listOf("t-ins"), w.plans.listAll().single().dismissedTxnIds)
        w.suggest.decline(app.masroufy.core.DueLinkSuggestion("t-ins", "2026-10-09", DueLinkKind.INSTALLMENT, plan.id, plan.name, 1, "2026-10-10", 120_000, Currency.SAR))
        assertEquals(listOf("t-ins"), w.plans.listAll().single().dismissedTxnIds, "مرة تانية = ولا حاجة")
        // تعديل الخطة ما بيمسحش «مش ده»
        w.installments.save(InstallmentInput(plan.id, "تمويل وهمي معدّل", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_200_000, 1_440_000, 120_000, firstDueAt = "2026-10-10"))
        assertEquals(listOf("t-ins"), w.plans.listAll().single().dismissedTxnIds)
        assertTrue(w.october().isEmpty())
    }

    @Test fun roscaContributionAndPayoutAreSuggestedTheSameWay() = runBlocking<Unit> {
        val w = World(listOf(txn("t-c", "2026-10-02", 50_000), txn("t-p", "2026-10-01", 250_000, Direction.IN)))
        val rosca = w.rosca()
        val got = w.october().associateBy { it.transactionId }
        assertEquals(DueLinkKind.ROSCA_CONTRIBUTION to "2026-10-01", got.getValue("t-c").let { it.kind to it.dueAt })
        assertEquals(DueLinkKind.ROSCA_PAYOUT to 2, got.getValue("t-p").let { it.kind to it.number })
        assertEquals(uiText(TextKey.DUE_LINK_ASK_ROSCA_PAYOUT, "جمعية وهمية", "2026-10-01"), dueLinkQuestion(got.getValue("t-p")))
        for (s in got.values) w.suggest.accept(s)
        assertEquals(setOf(RoscaEntryKind.CONTRIBUTION, RoscaEntryKind.PAYOUT), w.entries.listByRosca(rosca.id).map { it.kind }.toSet())
        assertEquals(EconomicKind.ROSCA_PAYOUT, w.txns.findByIds(listOf("t-p")).single().economicKind)
        assertTrue(w.october().isEmpty())
        // «مش ده» على الجمعية
        val w2 = World(listOf(txn("t-c", "2026-10-02", 50_000)))
        val circle = w2.rosca()
        w2.suggest.decline(w2.october().single())
        assertEquals(listOf("t-c"), w2.roscas.listAll().single().dismissedTxnIds)
        assertTrue(w2.october().isEmpty())
        // تعديل الجمعية ما بيمسحش «مش ده»
        w2.roscaLinks.save(RoscaInput(circle.id, "جمعية وهمية معدّلة", Currency.SAR, 50_000, firstDueAt = "2026-09-01", cycleCount = 5, myTurns = listOf(2)))
        assertEquals(listOf("t-c"), w2.roscas.listAll().single().dismissedTxnIds)
    }

    @Test fun financingReceivedIsSuggestedBeforeTheFirstInstallment() = runBlocking<Unit> {
        val w = World(listOf(txn("t-got", "2026-09-15", 1_200_000, Direction.IN)))
        val plan = w.financing()
        val s = w.suggest.list("2026-09-01", "2026-09-30").single()
        assertEquals(DueLinkKind.FINANCING_RECEIVED, s.kind)
        w.suggest.accept(s)
        assertEquals("t-got", w.plans.listAll().single { it.id == plan.id }.receivedTransactionId)
        assertEquals(EconomicKind.FINANCING_RECEIVED, w.txns.findByIds(listOf("t-got")).single().economicKind)
    }

    @Test fun linkedTransactionsAndSpaceLegsAreNeverSuggested() = runBlocking<Unit> {
        val seed = listOf(
            txn("t-roscalinked", "2026-10-09", 120_000), txn("t-leg", "2026-10-09", 120_000), txn("t-gift", "2026-10-09", 120_000),
            txn("t-zakat", "2026-10-09", 120_000), txn("t-move", "2026-10-09", 120_000).copy(transferToWalletId = "w-cash"),
            txn("t-support", "2026-10-09", 120_000, kind = EconomicKind.SUPPORT_GIFT, confirmed = true),
        )
        val w = World(seed, legs = setOf("t-leg"))
        val rosca = w.roscaLinks.save(RoscaInput(name = "جمعية تانية", currency = Currency.SAR, contributionMinor = 120_000, firstDueAt = "2026-10-09", cycleCount = 3, myTurns = listOf(3)))
        w.roscaLinks.link(rosca.id, "t-roscalinked", RoscaEntryKind.CONTRIBUTION)
        w.events.saveMany(listOf(EventLink("ev-1", "event-1", "t-gift", EventRole.GIFT_OUT, createdAt = "x")))
        w.zakat.saveMany(listOf(ZakatPayment("z-1", "year-1", "t-zakat", 120_000, listOf(ZakatLineKind.CASH), "2026-10-09", "x")))
        w.financing()
        assertTrue(w.october().none { it.kind == DueLinkKind.INSTALLMENT }, w.october().toString())
    }

    @Test fun theNearestTransactionWinsAndOnlyAnExactAmountInTheWeekCounts() = runBlocking<Unit> {
        val w = World(
            listOf(
                txn("t-far", "2026-10-04", 120_000), txn("t-near", "2026-10-09", 120_000), txn("t-cents", "2026-10-10", 119_999),
                txn("t-late", "2026-10-18", 120_000), txn("t-in", "2026-10-10", 120_000, Direction.IN),
            ),
        )
        w.financing()
        val got = w.october()
        assertEquals(listOf("t-near"), got.filter { it.number == 1 }.map { it.transactionId })
        // القسط التاني (2026-11-10) أبعد من أسبوع عن كلهم
        assertTrue(got.none { it.transactionId in setOf("t-cents", "t-late", "t-in", "t-far") }, got.toString())
        // الأسبوع بالظبط: ٧ أيام جوه، ٨ برّه
        for ((date, expected) in listOf("2026-10-17" to 1, "2026-10-03" to 1, "2026-10-18" to 0, "2026-10-02" to 0)) {
            val one = World(listOf(txn("t-1", date, 120_000)))
            one.financing()
            assertEquals(expected, one.october().size, date)
        }
    }
}
