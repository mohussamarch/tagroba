package app.masroufy.wiring

import app.masroufy.ui.screens.home.HomeDeps
import app.masroufy.usecase.CategorizeTransactionsDeps
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadCalendarDeps
import app.masroufy.usecase.LoadCashSummary
import app.masroufy.usecase.LoadCashSummaryDeps
import app.masroufy.usecase.LoadHomeScreen
import app.masroufy.usecase.LoadHomeScreenDeps
import app.masroufy.usecase.ManageCategories
import app.masroufy.usecase.ManageCategoriesDeps
import app.masroufy.usecase.ReviewHistory
import app.masroufy.usecase.SetEconomicKind
import app.masroufy.usecase.SetEconomicKindDeps

/**
 * «الرئيسية» — **الملف ده بتاع المنطقة بس.** نفس اعتمادات اختبارات `:app` لكل حالة استخدام. الملف والمحرك من الهيكل ([AreaContext.shell]) —
 * نسخة واحدة (الجرس وصفحة الإشعارات بيقروا نفس المقروء).
 * ⚠️ `SetEconomicKind` من غير `spaceLegs` (رجول «التحويل لنفسك بين البلدين» — `TransferBetweenSpaces.legsIn` لسه ما اتوصلش في التجميع):
 * الرجل بتتسجل مؤكدة فما بتظهرش في المراجعة، بس الحماية نفسها بتتوصل مع منطقة العمليات.
 */
class HomeGraph(c: AreaContext) : HomeDeps {
    private val r = c.repos

    override val calendar = LoadCalendar(
        LoadCalendarDeps(
            dues = loadDues(r), projects = r.projects, events = r.lifeEvents, prep = r.eventPrep, occasions = r.occasions, people = r.people,
            profile = r.profile, reservations = r.reservations, countryCode = c.space.countryCode, currency = c.space.currency,
            zakatYears = r.zakatYears, zakatPayments = r.zakatPayments, incomeSources = r.incomeSources,
        ),
    )

    override val homeScreen = LoadHomeScreen(LoadHomeScreenDeps(r.transactions, r.categories, r.allocations, r.budgets))

    override val cash = LoadCashSummary(LoadCashSummaryDeps(r.wallets, r.transactions, r.allocations, r.categories))

    override val profile = c.shell.profile

    override val alerts = c.shell.engine

    override val kinds = SetEconomicKind(SetEconomicKindDeps(r.transactions, r.categories, r.uow, c.env.clock))

    override val history = ReviewHistory(CategorizeTransactionsDeps(r.transactions, r.merchants, r.categories, r.rules, r.uow, c.env.clock))

    override val categories = ManageCategories(ManageCategoriesDeps(r.categories, c.env.ids))
}
