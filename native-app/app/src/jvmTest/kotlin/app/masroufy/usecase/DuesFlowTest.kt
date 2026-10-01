package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.Direction
import app.masroufy.core.DueFlow
import app.masroufy.core.DueSource
import app.masroufy.core.DueStatus
import app.masroufy.core.DuesTotals
import app.masroufy.core.EconomicKind
import app.masroufy.core.InstallmentError
import app.masroufy.core.InstallmentKind
import app.masroufy.core.InstallmentPayment
import app.masroufy.core.InstallmentPlan
import app.masroufy.core.Obligation
import app.masroufy.core.ObligationKind
import app.masroufy.core.Period
import app.masroufy.core.Person
import app.masroufy.core.RecurringItem
import app.masroufy.core.ReviewState
import app.masroufy.core.Rosca
import app.masroufy.core.RoscaEntry
import app.masroufy.core.RoscaEntryKind
import app.masroufy.core.RoscaError
import app.masroufy.core.RoscaPhase
import app.masroufy.core.Transaction
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.SequentialIdGenerator
import app.masroufy.port.TransactionPatch
import app.masroufy.port.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * «المستحقات» من أولها لآخرها على مستودعات الذاكرة (OVERRIDES §50). مكتوب بالإيد — الميزة في كوتلن بس. الأرقام وهمية.
 */
class DuesFlowTest {
    private fun txn(id: String, date: String, dir: Direction, amount: Long, currency: Currency = Currency.SAR) = Transaction(
        id = id, occurredAt = date, datePrecision = "day", sourceOrder = 1, economicKind = EconomicKind.UNCLASSIFIED,
        economicKindConfirmed = false, observedDirection = dir, amountMinor = amount, currency = currency, categoryConfirmed = false,
        excludedFromBudget = false, reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false,
        createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "2026-01-01T00:00:00.000Z",
    )

    private val txns = MemoryTransactionRepository(
        listOf(
            txn("t-c1", "2026-01-01", Direction.OUT, 100_000), txn("t-c2", "2026-02-01", Direction.OUT, 100_000),
            txn("t-in", "2026-04-01", Direction.IN, 1_000_000), txn("t-egp", "2026-01-02", Direction.OUT, 100_000, Currency.EGP),
            txn("t-loan", "2026-01-30", Direction.OUT, 100_000),
        ),
    )
    private val entries = MemoryRoscaEntryRepository()
    private val categories = app.masroufy.memory.MemoryCategoryRepository()
    private val payments = MemoryInstallmentPaymentRepository()
    private val ids = SequentialIdGenerator()
    private val clock = FixedClock("2026-02-15T09:00:00.000Z")

    private fun roscas(tx: TransactionRepository = txns, uowStores: List<app.masroufy.memory.Snapshotable> = listOf(entries, txns)) =
        ManageRoscas(ManageRoscasDeps(MemoryRoscaRepository(), entries, payments, tx, MemoryUnitOfWork(uowStores), ids, clock, categories))

    private val input = RoscaInput(
        name = "  جمعية   وهمية ", currency = Currency.SAR, contributionMinor = 100_000, firstDueAt = "2026-01-01", cycleCount = 10, myTurns = listOf(4),
    )

    private suspend fun kindOf(id: String) = txns.findByIds(listOf(id)).single().economicKind

    @Test
    fun `الجمعية من الإنشاء للقبض`() = runBlocking<Unit> {
        val manage = roscas()
        val rosca = manage.save(input)
        assertEquals("جمعية وهمية", rosca.name)
        assertEquals(1_000_000, rosca.payoutMinor)

        manage.link(rosca.id, "t-c1", RoscaEntryKind.CONTRIBUTION)
        assertEquals(EconomicKind.ROSCA_CONTRIBUTION, kindOf("t-c1"))
        assertEquals(RoscaPhase.SAVING, manage.list("2026-01-05").single().status.phase)

        manage.link(rosca.id, "t-in", RoscaEntryKind.PAYOUT)
        assertEquals(EconomicKind.ROSCA_PAYOUT, kindOf("t-in"))
        val st = manage.list("2026-04-02").single().status
        assertEquals(RoscaPhase.REPAYING, st.phase)
        assertEquals(-900_000, st.positionMinor)
    }

    @Test
    fun `جمعية جديدة بالأسئلة — التحليل قبل الحفظ وبعده`() = runBlocking<Unit> {
        val setup = RoscaSetup()
        val answers = listOf(
            app.masroufy.core.RoscaAnswer.Name("جمعية وهمية"), app.masroufy.core.RoscaAnswer.TurnsCount(10),
            app.masroufy.core.RoscaAnswer.ShareAmount(100_000), app.masroufy.core.RoscaAnswer.Frequency(app.masroufy.core.RoscaFrequency.MONTHLY),
            app.masroufy.core.RoscaAnswer.FirstDate("2026-01-01"), app.masroufy.core.RoscaAnswer.Share(app.masroufy.core.RoscaShare.ONE),
            app.masroufy.core.RoscaAnswer.MyTurns(listOf(4)),
        )
        var state = setup.begin(Currency.SAR)
        for (a in answers) {
            assertEquals(null, state.preview)
            state = setup.answer(state.draft, a)
        }
        assertEquals(app.masroufy.core.RoscaQuestion.PAYOUT, state.prompt?.question)
        state = setup.answer(state.draft, app.masroufy.core.RoscaAnswer.Payout(null))
        assertEquals(null, state.prompt)
        assertEquals(listOf("2026-04-01"), state.preview?.payoutDates)

        val manage = roscas()
        val saved = manage.createFromDraft(state.draft)
        assertEquals(state.preview, manage.forecast(saved.id))
    }

    @Test
    fun `الربط الغلط بيترفض بسبب`() = runBlocking<Unit> {
        val manage = roscas()
        val rosca = manage.save(input)
        manage.link(rosca.id, "t-c1", RoscaEntryKind.CONTRIBUTION)
        assertFailsWith<DueLinkError> { manage.link(rosca.id, "t-c1", RoscaEntryKind.CONTRIBUTION) }
        assertFailsWith<DueLinkError> { manage.link(rosca.id, "t-in", RoscaEntryKind.CONTRIBUTION) }
        assertFailsWith<DueLinkError> { manage.link(rosca.id, "t-egp", RoscaEntryKind.CONTRIBUTION) }
        assertFailsWith<DueLinkError> { manage.link(rosca.id, "t-c2", RoscaEntryKind.CONTRIBUTION, 100_001) }
        assertFailsWith<DueLinkError> { manage.link(rosca.id, "مش-موجودة", RoscaEntryKind.CONTRIBUTION) }
        assertFailsWith<RoscaError> { manage.link("rosca-مش-موجودة", "t-c2", RoscaEntryKind.CONTRIBUTION) }
        // قيمة الدور ما بتتقسمش على 3 أدوار ⇒ المستخدم يكتبها
        assertFailsWith<RoscaError> { manage.save(input.copy(myTurns = listOf(1, 2, 3))) }
        // العملة ما تتغيرش بعد الربط
        assertFailsWith<RoscaError> { manage.save(input.copy(id = rosca.id, currency = Currency.EGP)) }
    }

    @Test
    fun `لو تعديل العملية فشل الربط ما بيتحفظش`() = runBlocking<Unit> {
        val failing = object : TransactionRepository by txns {
            override suspend fun update(id: String, patch: TransactionPatch) = throw IllegalStateException("انقطاع وهمي")
        }
        val manage = roscas(tx = failing)
        val rosca = manage.save(input)
        assertFailsWith<IllegalStateException> { manage.link(rosca.id, "t-c1", RoscaEntryKind.CONTRIBUTION) }
        assertEquals(emptyList(), entries.all())
    }

    @Test
    fun `فك الربط بيرجّع العملية للمراجعة`() = runBlocking<Unit> {
        val manage = roscas()
        val rosca = manage.save(input)
        manage.link(rosca.id, "t-c1", RoscaEntryKind.CONTRIBUTION)
        manage.unlink("t-c1")
        assertEquals(emptyList(), entries.all())
        val t = txns.findByIds(listOf("t-c1")).single()
        assertEquals(EconomicKind.UNCLASSIFIED to ReviewState.NEEDS_REVIEW, t.economicKind to t.reviewState)
    }

    private val obligations = MemoryObligationRepository(listOf(Obligation("o-1", "p-1", null, ObligationKind.RECEIVABLE, 50_000, Currency.SAR)))

    private fun installments() = ManageInstallments(
        ManageInstallmentsDeps(
            MemoryInstallmentPlanRepository(), payments, entries, MemoryDebtTermsRepository(), obligations, txns,
            MemoryUnitOfWork(listOf(payments, txns)), ids, clock, categories,
        ),
    )

    private val loanInput = InstallmentInput(
        name = "تمويل وهمي", provider = "بنك وهمي", kind = InstallmentKind.FINANCING, currency = Currency.SAR,
        principalMinor = 1_000_000, totalMinor = 1_200_000, installmentMinor = 100_000, firstDueAt = "2026-01-10",
    )

    @Test
    fun `قسط التمويل بياخد نوعه والعملية ما تتربطش بحاجتين`() = runBlocking<Unit> {
        val manage = installments()
        val plan = manage.save(loanInput)
        manage.link(plan.id, "t-loan")
        assertEquals(EconomicKind.INSTALLMENT_PAID, kindOf("t-loan"))
        assertEquals(2, manage.list("2026-02-15").single().progress.nextNumber)

        roscas().let { r -> r.link(r.save(input).id, "t-c1", RoscaEntryKind.CONTRIBUTION) }
        assertFailsWith<DueLinkError> { manage.link(plan.id, "t-c1") }
        // الإجمالي الجديد أقل من المدفوع مرفوض، والنوع ما يتغيرش بعد الربط
        assertFailsWith<InstallmentError> { manage.save(loanInput.copy(id = plan.id, kind = InstallmentKind.PURCHASE_PLAN)) }
    }

    @Test
    fun `مواعيد الدين لازم على دين موجود`() = runBlocking<Unit> {
        val manage = installments()
        assertFailsWith<InstallmentError> { manage.setDebtTerms(DebtTerms("o-مش-موجود", "p-1", "2026-02-20")) }
        assertEquals("o-1", manage.setDebtTerms(DebtTerms("o-1", "p-1", "2026-02-20")).obligationId)
    }

    @Test
    fun `شاشة المستحقات — الأرصدة والقايمة وسطر الشهر وأرباح التمويل`() = runBlocking<Unit> {
        val rosca = Rosca("rc-1", "جمعية وهمية", Currency.SAR, 100_000, 1, "2026-01-01", 10, listOf(4), 1_000_000)
        val plan = InstallmentPlan("ip-1", "تمويل وهمي", "بنك وهمي", InstallmentKind.FINANCING, Currency.SAR, 1_000_000, 1_200_000, 100_000, 1, "2026-01-10")
        val load = LoadDues(
            LoadDuesDeps(
                roscas = MemoryRoscaRepository(listOf(rosca)),
                roscaEntries = MemoryRoscaEntryRepository(listOf(RoscaEntry("e-1", "rc-1", "t-c1", RoscaEntryKind.CONTRIBUTION, 100_000))),
                plans = MemoryInstallmentPlanRepository(listOf(plan)),
                payments = MemoryInstallmentPaymentRepository(listOf(InstallmentPayment("pay-1", "ip-1", "t-loan", 100_000))),
                terms = MemoryDebtTermsRepository(listOf(DebtTerms("o-1", "p-1", "2026-02-20"))),
                people = MemoryPersonRepository(listOf(Person("p-1", "شخص وهمي"))),
                obligations = obligations,
                settlements = MemorySettlementRepository(),
                recurring = MemoryRecurringRepository(listOf(RecurringItem("r-1", "اشتراك وهمي", "manual:x", "subscription", 1, 4_500, Currency.SAR, "2026-02-05", true, true))),
                txns = txns,
            ),
        )
        // فترة يوم الراتب 28: من 28 يناير لـ 27 فبراير
        val view = load.load("2026-02-15", Period("2026-01", "2026-01-28", "2026-02-27", 31), Currency.SAR, 800_000)

        assertEquals(DuesTotals(50_000, 100_000, 0, 0, 1_100_000, 0), view.totals)
        // عليك: قسط الجمعية (فبراير، متأخر) + قسط التمويل (فبراير، متأخر) + الاشتراك (5 فبراير)
        assertEquals(204_500, view.month.toPayMinor)
        assertEquals(3, view.month.payCount)
        assertEquals(50_000, view.month.toReceiveMinor)
        assertEquals(595_500, view.month.remainingAfterMinor)
        // قسط التمويل اتدفع 30 يناير جوه الفترة ⇒ جزء الأرباح بتاعه مصروف الفترة
        assertEquals(16_667, view.financingCostMinor)

        val first = view.agenda.first()
        assertEquals(Triple(DueSource.ROSCA_CONTRIBUTION, "2026-02-01", DueStatus.OVERDUE), Triple(first.source, first.dueAt, first.status))
        assertTrue(view.agenda.any { it.source == DueSource.ROSCA_PAYOUT && it.flow == DueFlow.RECEIVE && it.dueAt == "2026-04-01" })
        assertEquals(view.agenda.map { it.dueAt }.sorted(), view.agenda.map { it.dueAt })
    }
}
