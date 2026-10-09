package app.masroufy.ui.screens.home

import app.masroufy.usecase.LoadCalendar

/**
 * منطقة «الرئيسية» (`SCREENS.md` §٢.٢ — Home · PeriodPicker · CashDetails · Notifications · Calendar): حالات الاستخدام اللي شاشاتها محتاجاها **بس**.
 * التنفيذ: `:wiring` → `HomeGraph`. ضيف هنا اللي محتاجه (مثلًا `LoadHomeScreen` · `LoadLeftover` · `LoadCashSummary` · `RunAlertEngine`)
 * وابنيه في `HomeGraph` من `SpaceRepositories` — **ممنوع** مستودع هنا (CLAUDE.md #4).
 * «معك الآن» والجرس والبلد في `ShellDeps` (`LocalSpace.current.shell`) لأنهم مشتركين.
 */
interface HomeDeps {
    /** «القادم» والتقويم الذكي — `LoadCalendar.items(من، لحد، النهارده)` · `month` · `summary`. */
    val calendar: LoadCalendar
}
