package app.masroufy.wiring

import app.masroufy.core.Currency
import app.masroufy.core.DebtTerms
import app.masroufy.core.InstallmentKind
import app.masroufy.core.ObligationKind
import app.masroufy.core.RoscaAnswer
import app.masroufy.core.RoscaFrequency
import app.masroufy.core.RoscaShare
import app.masroufy.core.Space
import app.masroufy.core.shiftMonths
import app.masroufy.memory.MemoryAccount
import app.masroufy.ui.screens.dues.DebtSide
import app.masroufy.ui.screens.dues.DuesCounts
import app.masroufy.ui.screens.dues.debtDetailUi
import app.masroufy.ui.screens.dues.duesDebtsUi
import app.masroufy.ui.screens.dues.duesPanelUi
import app.masroufy.ui.screens.dues.installmentsUi
import app.masroufy.ui.screens.dues.roscaCard
import app.masroufy.usecase.InstallmentInput
import app.masroufy.usecase.LoadOnlineFeeds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * منطقة «المستحقات» من آخرها لآخرها على مستودعات الذاكرة (نفس التجميع اللي الجوال بيمشي فيه فوق فايربيز): `DuesGraph` بيدّي الشاشات
 * حالات استخدام شغالة — دين قديم وموعده وتسوية · جمعية من المعالج · خطة أقساط — والشاشة بتعرض اللي رجع. بيانات وهمية بس.
 */
class DuesWiringTest {
    private val today = "2026-10-09"
    private val saudi = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
    private val repos = memorySpaceRepositories()
    private val env = memoryEnv(today)
    private val session = object : SessionLinks {
        override val account = MemoryAccount()
        override fun spaces() = listOf(saudi to repos)
        override fun switchSpace(spaceId: String): Boolean = true
    }
    private val graph = SpaceGraph(saudi, repos, env, session, LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis))
    private val dues = graph.dues

    @Test fun anOldDebtWithADueDateAndAPartialSettlementReachTheScreens() = runBlocking<Unit> {
        val fahd = dues.people.addPerson("فهد")
        val debt = dues.people.addOpeningDebt(fahd.id, ObligationKind.RECEIVABLE, 150_000)
        dues.installments.setDebtTerms(DebtTerms(debt.id, fahd.id, "2026-12-01"))
        dues.people.settle(debt.id, fahd.id, 50_000)

        val people = dues.people.listWithBalances()
        val items = dues.loadDues.dueItems(today, shiftMonths(today, 120))
        val period = dues.period(today)
        assertEquals("2026-10-27", period.end, "يوم الراتب ٢٨ لو مش متسجل ⇒ الفترة بتخلص ٢٧")
        val view = dues.loadDues.load(today, period, Currency.SAR, null)
        assertEquals(100_000L, view.totals.receivableMinor, "المتبقي بعد التسوية من حالة الاستخدام")

        val list = duesDebtsUi(people, view.totals, items, today, Currency.SAR, DebtSide.ALL, "")
        val row = list.groups.single().rows.single()
        assertEquals(100_000L, row.amountMinor)
        assertEquals("2026-12-01", row.dueAt, "ميعاد بعد شهرين بيوصل (`dueItems` لحد ١٠ سنين)")

        val detail = assertNotNull(debtDetailUi(people, items, debt.id, today))
        assertTrue(detail.opening)
        assertEquals(100_000L, detail.remainingMinor)
        assertTrue(detail.ofOriginal.startsWith("من أصل") && !detail.ofOriginal.contains("لم يُسدَّد"), "اتسدد جزء")

        dues.people.settle(debt.id, fahd.id, 100_000)
        assertNull(debtDetailUi(dues.people.listWithBalances(), items, debt.id, today), "اتسدد بالكامل ⇒ خرج من القايمة النشطة")
    }

    @Test fun aRoscaFromTheWizardAndAnInstallmentPlanShowUp() = runBlocking<Unit> {
        var s = dues.roscaSetup.begin(Currency.SAR)
        for (a in listOf(
            RoscaAnswer.Name("جمعية وهمية"), RoscaAnswer.TurnsCount(5), RoscaAnswer.ShareAmount(100_000), RoscaAnswer.Frequency(RoscaFrequency.MONTHLY),
            RoscaAnswer.FirstDate("2026-11-01"), RoscaAnswer.Share(RoscaShare.ONE), RoscaAnswer.MyTurns(listOf(2)), RoscaAnswer.Payout(null),
        )) s = dues.roscaSetup.answer(s.draft, a)
        assertNotNull(s.preview)
        val rosca = dues.roscas.createFromDraft(s.draft)
        val card = roscaCard(dues.roscas.list(today).single { it.rosca.id == rosca.id })
        assertEquals("جمعية وهمية", card.name)
        assertEquals(0L, card.paidMinor)
        assertEquals(5, dues.roscas.forecast(rosca.id).rows.size)

        dues.installments.save(
            InstallmentInput(
                name = "قسط وهمي", provider = "جهة وهمية", kind = InstallmentKind.PURCHASE_PLAN, currency = Currency.SAR,
                principalMinor = 120_000, totalMinor = 120_000, installmentMinor = 10_000, firstDueAt = "2026-10-27",
            ),
        )
        val plans = dues.installments.list(today)
        val view = dues.loadDues.load(today, dues.period(today), Currency.SAR, null)
        val ui = installmentsUi(plans, view.totals, today, Currency.SAR)
        assertEquals(120_000L, ui.leftMinor)
        assertEquals("قسط وهمي", ui.cards.single().name)

        val recurring = dues.recurring.load(today)
        val panel = duesPanelUi(view, DuesCounts.of(dues.people.listWithBalances(), dues.roscas.list(today), plans, recurring), dues.period(today), today, Currency.SAR)
        assertEquals(listOf(0, 1, 1, 0), panel.tiles.map { it.count })
        assertTrue(panel.agenda.any { it.title == "قسط وهمي" }, "قسط ٢٧ أكتوبر في المواعيد")
        assertNull(panel.forYouTotal)
    }
}
