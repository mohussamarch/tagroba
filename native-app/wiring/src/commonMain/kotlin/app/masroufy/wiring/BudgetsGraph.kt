package app.masroufy.wiring

import app.masroufy.ui.screens.budgets.BudgetsDeps
import app.masroufy.usecase.CategorizeTransactionsDeps
import app.masroufy.usecase.GoalLedger
import app.masroufy.usecase.LoadBudgetScreen
import app.masroufy.usecase.LoadBudgetScreenDeps
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadCalendarDeps
import app.masroufy.usecase.LoadGoalsOverview
import app.masroufy.usecase.LoadGoalsOverviewDeps
import app.masroufy.usecase.LoadLeftover
import app.masroufy.usecase.LoadLeftoverDeps
import app.masroufy.usecase.LoadTransactionsScreen
import app.masroufy.usecase.LoadTransactionsScreenDeps
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageCategoriesDeps
import app.masroufy.usecase.ManageReservations
import app.masroufy.usecase.ManageRules
import app.masroufy.usecase.ManageRulesDeps
import app.masroufy.usecase.ManageSavingsGoals
import app.masroufy.usecase.ManageSavingsGoalsDeps
import app.masroufy.usecase.ReviewHistory
import app.masroufy.usecase.SetBudget
import app.masroufy.usecase.SetBudgetDeps

/**
 * منطقة «الميزانيات والخطط والتصنيفات والقواعد» (منطقة تاسعة — ARCHITECTURE §31.32) — **الملف ده بتاع المنطقة بس**.
 * كل حاجة حالة استخدام من `:app` بنفس اعتماداتها في اختبارات `:app` — مفيش حساب هنا. خطط الادخار **على الحساب** (نفس المستودع في كل
 * البلاد) والمحفظة المربوطة جوه بلد ⇒ دفتر لكل بلد مفتوحة في الجلسة ([GoalLedger]).
 */
class BudgetsGraph(c: AreaContext) : BudgetsDeps {
    private val r = c.repos

    override val profile = c.shell.profile
    override val budgetScreen = LoadBudgetScreen(LoadBudgetScreenDeps(r.transactions, r.categories, r.allocations, r.budgets))
    override val setBudget = SetBudget(SetBudgetDeps(r.budgets, r.uow, c.env.ids, c.env.clock))
    override val calendar = LoadCalendar(
        LoadCalendarDeps(
            dues = loadDues(r), projects = r.projects, events = r.lifeEvents, prep = r.eventPrep, occasions = r.occasions, people = r.people,
            profile = r.profile, reservations = r.reservations, countryCode = c.space.countryCode, currency = c.space.currency,
            zakatYears = r.zakatYears, zakatPayments = r.zakatPayments, incomeSources = r.incomeSources,
        ),
    )
    override val reservations = ManageReservations(calendar, r.reservations, c.env.clock)
    override val leftover = LoadLeftover(
        LoadLeftoverDeps(calendar, r.wallets, r.transactions, r.reservations, r.profile, c.space.currency, r.incomeSources),
    )
    override val transactions = LoadTransactionsScreen(LoadTransactionsScreenDeps(r.transactions, r.categories, r.allocations, r.merchants))

    // البلاد المفتوحة بتتقرا أول ما الخطط تتفتح (مش وقت بناء الجراف)
    private val ledgers by lazy { c.session.spaces().map { (space, repos) -> GoalLedger(space.id, repos.wallets, repos.transactions) } }
    override val goals by lazy { ManageSavingsGoals(ManageSavingsGoalsDeps(r.savingsGoals, r.goalContributions, c.env.ids, c.env.clock, ledgers)) }
    override val goalsOverview by lazy { LoadGoalsOverview(LoadGoalsOverviewDeps(r.savingsGoals, r.goalContributions, ledgers)) }

    override val categories = ManageCategories(ManageCategoriesDeps(r.categories, c.env.ids))
    override val rules = ManageRules(ManageRulesDeps(r.rules, r.merchants, r.categories, c.env.ids))
    override val history = ReviewHistory(CategorizeTransactionsDeps(r.transactions, r.merchants, r.categories, r.rules, r.uow, c.env.clock))
}
