package app.masroufy.wiring

import app.masroufy.core.DebtLine
import app.masroufy.core.FutureGoal
import app.masroufy.ui.screens.investment.future.InvestFutureDeps
import app.masroufy.usecase.FeedState
import app.masroufy.usecase.FutureDebtsSource
import app.masroufy.usecase.FutureGoalsSource
import app.masroufy.usecase.FutureIncomeSource
import app.masroufy.usecase.FutureSavingSource
import app.masroufy.usecase.FutureSpaceSource
import app.masroufy.usecase.GoalLedger
import app.masroufy.usecase.LoadActualSaving
import app.masroufy.usecase.LoadActualSavingDeps
import app.masroufy.usecase.LoadFutureInputs
import app.masroufy.usecase.LoadFutureInputsDeps
import app.masroufy.usecase.LoadGoalsOverview
import app.masroufy.usecase.LoadGoalsOverviewDeps
import app.masroufy.usecase.LoadWithYouNow
import app.masroufy.usecase.ManageBirthDate
import app.masroufy.usecase.ManageInstallments
import app.masroufy.usecase.ManageInstallmentsDeps
import app.masroufy.usecase.ManageInvestView
import app.masroufy.usecase.ManagePeople
import app.masroufy.usecase.ManagePeopleDeps
import app.masroufy.usecase.ProjectInvestFuture

/**
 * «هتبقى معاك كام» (حزمة FUTURE) — **الملف ده بتاع الحزمة بس.** كل مصدر من [AreaContext] بنفس اعتماداته في اختبارات `:app`:
 * «معك الآن» · التحويش الفعلي (`LoadActualSaving`) · الأقساط والقروض والأمانات اللي عليك · مصادر الدخل (الإيجار والعائد) · خطط الادخار ·
 * تاريخ الميلاد والعلامات في `userSettings` على الحساب · سعر الدولار من ملف الأسعار.
 * ⚠️ الدخل: قرار المالك 2026-10-16 = المرتب بالمبلغ اللي المستخدم كتبه (فرع `r05-salary`) — لحد ما يندمج ⇒ null («دخلك مش معروف»).
 * ⚠️ البلاد التانية («في البلدين»): محتاجة لقطة «حاجاتي» للبلد التاني (`LoadHoldings` — الخطوة 0) ⇒ فاضية لحد الدمج (الكارت مستخبي).
 */
class InvestFutureGraph(private val c: AreaContext) : InvestFutureDeps {
    private val r = c.repos
    private val currency = c.space.currency

    override val view = ManageInvestView(r.assistant.settings, c.env.clock)
    override val birth = ManageBirthDate(r.assistant.settings, c.env.clock)
    override val project = ProjectInvestFuture()

    private val saving = LoadActualSaving(LoadActualSavingDeps(r.transactions, r.allocations, r.categories, r.profile))
    private val installments = ManageInstallments(
        ManageInstallmentsDeps(
            r.installmentPlans, r.installmentPayments, r.roscaEntries, r.debtTerms, r.obligations, r.transactions, r.uow, c.env.ids, c.env.clock,
            r.categories, zakatPayments = r.zakatPayments, eventLinks = r.eventLinks,
        ),
    )
    private val people = ManagePeople(
        ManagePeopleDeps(r.people, r.obligations, r.settlements, r.settlementWriter, r.allocations, r.transactions, r.uow, c.env.ids, c.env.clock),
    )
    private val goals = LoadGoalsOverview(LoadGoalsOverviewDeps(r.savingsGoals, r.goalContributions, listOf(GoalLedger(c.space.id, r.wallets, r.transactions))))

    private val debts = FutureDebtsSource { today ->
        val plans = installments.list(today).filter { it.plan.currency == currency && !it.progress.done }
            .map { DebtLine(it.progress.remainingMinor, it.progress.nextAmountMinor) }
        // «له عندك» عند الناس (قروض + أمانات معاك) — ثابت لحد ما يتسدد
        val owed = people.listWithBalances().map { it.balance.payableLoanMinor + it.balance.payableCustodyMinor }.filter { it > 0 }.map { DebtLine(it, 0) }
        plans + owed
    }

    override val inputs = LoadFutureInputs(
        LoadFutureInputsDeps(
            mine = FutureSpaceSource(
                c.space.id, c.space.countryCode, currency, LoadWithYouNow(r.wallets, r.transactions, currency),
                saving = FutureSavingSource { today -> saving.load(currency, today).averageMinor },
                // المُجمِّع بعد دمج r05-salary: UserProfile.salaryMinor لو salaryCurrency = عملة البلد، وإلا null (النطاق القديم عمره ما يتحول لرقم)
                // رد المالك Q7: متوسط الدخل الفعلي لآخر 3 شهور كاملة؛ مش معروف ⇒ المرتب المكتوب (الجهات، وإلا الملف)؛ ولا ده ⇒ null
                income = FutureIncomeSource { today ->
                    saving.load(currency, today).incomeAverageMinor?.takeIf { it > 0 }
                        ?: app.masroufy.core.salaryState(r.profile.load(), r.incomeSources.listAll(), currency, today).amountMinor
                },
                debts = debts,
                incomeSources = r.incomeSources,
                goals = FutureGoalsSource { today ->
                    goals.load(today).filter { !it.goal.archived && it.goal.currency == currency }.map { FutureGoal(it.goal.name, it.goal.targetDate) }
                },
            ),
            others = emptyList(),
            birth = birth,
            prices = { (c.feeds.prices() as? FeedState.Ready)?.feed },
        ),
    )

    override fun today() = c.env.today()
}
