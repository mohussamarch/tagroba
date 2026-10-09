package app.masroufy.ui.screens.home

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.Tab

/**
 * شاشات منطقة «الرئيسية» — **الملف ده بتاع المنطقة بس** (المناطق التانية ما بتلمسوش). اسم كل شاشة = اسم لوحتها في النموذج.
 * الهيكل بيستعمل [NotificationsRoute] (من الجرس) و[CalendarRoute] (من «القادم») — سيبهم بنفس الاسم.
 */
object NotificationsRoute : Route {
    override val name = "Notifications"
}

object CalendarRoute : Route {
    override val name = "Calendar"
}

object PeriodPickerRoute : Route {
    override val name = "PeriodPicker"
}

fun RouteRegistry.registerHome() {
    tabRoot(Tab.HOME) { HomeScreen() }
    // screen<NotificationsRoute> { NotificationsScreen() } · screen<CalendarRoute> { CalendarScreen() } — لما يتبنوا (من غيرها: «قيد البناء»)
}
