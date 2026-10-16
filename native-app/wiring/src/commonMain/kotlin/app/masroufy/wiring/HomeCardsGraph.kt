package app.masroufy.wiring

import app.masroufy.ui.screens.home.cards.HomeCardsDeps
import app.masroufy.usecase.GoalLedger
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadDailyAllowance
import app.masroufy.usecase.LoadGoalsOverview
import app.masroufy.usecase.LoadGoalsOverviewDeps
import app.masroufy.usecase.LoadGoalsProgress
import app.masroufy.usecase.LoadHomeScreen
import app.masroufy.usecase.LoadLeftover
import app.masroufy.usecase.LoadLeftoverDeps
import app.masroufy.usecase.LoadTopCategories
import app.masroufy.usecase.ManageHomeCards

/**
 * كروت الرئيسية (اختيار المالك 2026-10-15) — جزء من «الرئيسية» ([HomeGraph.cards]). نفس اعتمادات اختبارات `:app`:
 * الترتيب في `userSettings` على الحساب (زي صف «أهم 5» في [PeopleGraph]) · «تقدر تصرف» من `LoadLeftover` (نفس «فاضلك تقريبًا» في
 * [BudgetsGraph]) · «أهدافك» بدفتر لكل بلد مفتوحة (زي [BudgetsGraph.goalsOverview]).
 */
class HomeCardsGraph(c: AreaContext, calendar: LoadCalendar, homeScreen: LoadHomeScreen) : HomeCardsDeps {
    private val r = c.repos

    override val layout = ManageHomeCards(r.assistant.settings, c.env.clock)

    override val daily = LoadDailyAllowance(
        LoadLeftover(LoadLeftoverDeps(calendar, r.wallets, r.transactions, r.reservations, r.profile, c.space.currency, r.incomeSources)),
        r.profile,
        c.space.currency,
    )

    override val top = LoadTopCategories(homeScreen)

    // البلاد المفتوحة بتتقرا أول ما الكارت يتقري (مش وقت بناء الجراف)
    private val ledgers by lazy { c.session.spaces().map { (space, repos) -> GoalLedger(space.id, repos.wallets, repos.transactions) } }
    override val goals by lazy { LoadGoalsProgress(LoadGoalsOverview(LoadGoalsOverviewDeps(r.savingsGoals, r.goalContributions, ledgers))) }
}
