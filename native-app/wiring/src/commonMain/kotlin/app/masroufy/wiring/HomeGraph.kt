package app.masroufy.wiring

import app.masroufy.ui.screens.home.HomeDeps
import app.masroufy.usecase.LoadCalendar
import app.masroufy.usecase.LoadCalendarDeps

/**
 * «الرئيسية» — **الملف ده بتاع المنطقة بس.** ضيف هنا اللي شاشاتها محتاجاه (`LoadHomeScreen` · `LoadLeftover` · `LoadCashSummary` ·
 * `LoadHomeHistory` · `c.shell.engine` لصفحة الإشعارات …) بنفس اعتماداتها في اختبارات `:app`.
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
}
