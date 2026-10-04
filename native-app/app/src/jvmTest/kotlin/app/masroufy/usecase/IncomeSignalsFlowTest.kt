package app.masroufy.usecase

import app.masroufy.core.AlertGroup
import app.masroufy.core.AlertKind
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.IncomeSourceError
import app.masroufy.core.LocalMoment
import app.masroufy.core.Period
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.TransferParty
import app.masroufy.core.TransferVerdict
import app.masroufy.core.isLockSafe
import app.masroufy.core.transferPartyOf
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemoryAlertInbox
import app.masroufy.memory.MemoryAlertInteractions
import app.masroufy.memory.MemoryAlertReceipts
import app.masroufy.memory.MemoryAlertSettings
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryDebtTermsRepository
import app.masroufy.memory.MemoryIncomeSourceRepository
import app.masroufy.memory.MemoryInstallmentPaymentRepository
import app.masroufy.memory.MemoryInstallmentPlanRepository
import app.masroufy.memory.MemoryObligationRepository
import app.masroufy.memory.MemoryPersonRepository
import app.masroufy.memory.MemoryProfileRepository
import app.masroufy.memory.MemoryRecurringRepository
import app.masroufy.memory.MemoryRoscaEntryRepository
import app.masroufy.memory.MemoryRoscaRepository
import app.masroufy.memory.MemorySettlementRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.memory.MemoryTransferPartyRepository
import app.masroufy.memory.MemoryUnitOfWork
import app.masroufy.memory.MemoryUsualHours
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «ده مرتب من …؟» والمرتب المتأخر والمقارنة على مستودعات الذاكرة (OVERRIDES §48 · §64 · §61) — أسماء ومبالغ مخترعة. */
class IncomeSignalsFlowTest {
    private var seq = 0
    private val star = "شركة النجمة الوهمية"
    private val moon = "شركة القمر الوهمية"

    private fun deposit(company: String, date: String, amount: Long = 1_000_000, kind: EconomicKind = EconomicKind.UNCLASSIFIED, confirmed: Boolean = false) = Transaction(
        id = "t-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = Direction.IN, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = false, excludedFromBudget = false,
        reviewState = ReviewState.NEEDS_REVIEW, isCashTagged = false, createdAt = "x", updatedAt = "x",
        rawDescription = "PAYROLL-PA1234:ملاحظة | سامي-INMAINM1234567RJ-$company | x", sourceOperationType = "حواالت سريع الواردة",
    )

    private val txns = MemoryTransactionRepository()
    private val sources = MemoryIncomeSourceRepository()
    private val parties = MemoryTransferPartyRepository()
    private val profiles = MemoryProfileRepository()
    private val clock = FixedClock("2026-10-04T10:00:00.000Z")
    private val manage = ManageIncomeSources(
        ManageIncomeSourcesDeps(sources, ManageProfile(ManageProfileDeps(profiles, app.masroufy.memory.MemoryAccount(), clock, sources)), MemoryUnitOfWork(listOf(sources)), app.masroufy.memory.SequentialIdGenerator(), clock),
    )
    private val signals = IncomeSourceSignals(IncomeSignalsDeps(sources, txns, MemoryAllocationRepository(), profiles, MemoryUnitOfWork(listOf(sources, txns)), clock, parties))

    @Test fun firstDepositAsksOnceThenTheSameCompanyIsKnownWithoutTouchingAnyAmount() = runBlocking<Unit> {
        val job = manage.add(IncomeSourceInput("مصدر وهمي", "2026-01-01", expectedDayOfMonth = 27))
        val first = deposit(star, "2026-01-27")
        txns.saveMany(listOf(first, deposit(star, "2026-02-27", 1_250_000)))
        val q = signals.payerQuestions().single()
        assertEquals(first.id, q.transactionId)
        assertEquals(job.id, q.sourceId)
        signals.answerPayer(q, yes = true)
        assertTrue(signals.payerQuestions().isEmpty(), "الإيداعات الجاية من نفس الشركة من غير سؤال")
        val src = sources.listAll().single()
        assertEquals(listOf(q.party.key), src.payerKeys)
        assertNull(src.expectedMinor, "الإيداع التاني أكبر — وبرضه ما بنستنتجش زيادة")
        val asked = txns.findByIds(listOf(first.id)).single()
        assertEquals(EconomicKind.SALARY, asked.economicKind)
        assertTrue(asked.economicKindConfirmed)
        // السؤال اللي اتجاوب ما ينفعش يتجاوب تاني
        assertFailsWith<IncomeSourceError> { signals.answerPayer(q, yes = false) }
    }

    @Test fun noStopsTheQuestionAndAPartyDecidedInTheTransferZoneIsNeverAsked() = runBlocking<Unit> {
        manage.add(IncomeSourceInput("مصدر وهمي", "2026-01-01"))
        val fromMoon = deposit(moon, "2026-02-03")
        txns.saveMany(listOf(deposit(star, "2026-01-27"), fromMoon))
        parties.save(TransferParty(transferPartyOf(fromMoon)!!.key, moon, null, TransferVerdict.PERSON, "p-1", "x"))
        val q = signals.payerQuestions().single()
        signals.answerPayer(q, yes = false)
        assertTrue(signals.payerQuestions().isEmpty())
        assertEquals(EconomicKind.UNCLASSIFIED, txns.findByIds(listOf(q.transactionId)).single().economicKind, "«لأ» ما بيغيّرش العملية")
    }

    @Test fun lateSalaryFlowsThroughTheEngineAndDisappearsWhenItArrives() = runBlocking<Unit> {
        val job = manage.add(IncomeSourceInput("مصدر وهمي", "2026-01-01", expectedDayOfMonth = 25, expectedMinor = 1_000_000))
        txns.saveMany(listOf(deposit(star, "2026-09-25")))
        signals.answerPayer(signals.payerQuestions().single(), yes = true)
        val gather = GatherAlerts(
            GatherAlertsDeps(
                LoadDues(
                    LoadDuesDeps(
                        MemoryRoscaRepository(), MemoryRoscaEntryRepository(), MemoryInstallmentPlanRepository(), MemoryInstallmentPaymentRepository(),
                        MemoryDebtTermsRepository(), MemoryPersonRepository(), MemoryObligationRepository(), MemorySettlementRepository(), MemoryRecurringRepository(), txns,
                    ),
                ),
                income = signals,
            ),
        )
        val inbox = MemoryAlertInbox()
        val engine = RunAlertEngine(AlertEngineDeps(MemoryAlertSettings(), MemoryAlertInteractions(), MemoryUsualHours(), MemoryAlertReceipts(), inbox, clock))
        val period = Period("2026-09", "2026-09-28", "2026-10-27", 30)
        suspend fun runDay(day: String) = engine.run(gather.gather(AlertGatherInput(day, period, Currency.SAR)), LocalMoment(day, 14))

        assertTrue(runDay("2026-10-27").posts.isEmpty(), "لسه في المهلة")
        assertTrue(inbox.listAll().none { it.kind.group == AlertGroup.INCOME })
        val late = runDay("2026-10-28")
        val entry = inbox.listAll().single { it.kind.group == AlertGroup.INCOME }
        assertEquals(AlertKind.INCOME_LATE, entry.kind)
        assertTrue(entry.title.contains("مصدر وهمي"))
        for (p in late.posts) {
            assertTrue(isLockSafe(p.notice.title) && isLockSafe(p.notice.body))
            assertFalse(p.notice.body.contains("مصدر"), p.notice.body)
        }
        txns.saveMany(listOf(deposit(star, "2026-10-29", 400_000)))
        runDay("2026-10-29")
        assertTrue(inbox.listAll().none { it.kind.group == AlertGroup.INCOME }, "وصل (حتى لو أقل) ⇒ اختفى")
        assertEquals(job.id, sources.listAll().single().id)
    }

    @Test fun comparisonComesFromStoredTransactions() = runBlocking<Unit> {
        profiles.save(app.masroufy.core.emptyProfile().copy(payday = 28))
        val job = manage.add(IncomeSourceInput("مصدر وهمي", "2026-04-28"))
        for ((i, start) in listOf("2026-01-28", "2026-02-28", "2026-03-28", "2026-04-28", "2026-05-28", "2026-06-28").withIndex()) {
            txns.saveMany(listOf(deposit(star, start, if (i < 3) 1_000_000 else 1_100_000, EconomicKind.SALARY, confirmed = true)))
        }
        assertNull(signals.compareAroundStart(job.id, "2026-07-27"))
        val c = assertNotNull(signals.compareAroundStart(job.id, "2026-07-28"))
        assertEquals(100, c.incomeChangeTenthPercent, "10.0٪")
        assertNull(c.expenseChangeTenthPercent, "مفيش مصروف قبل ⇒ مفيش نسبة")
    }
}
