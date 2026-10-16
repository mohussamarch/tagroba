package app.masroufy.ui.screens.investment.insights

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.screens.more.FaqScreen

/** «النصايح» (`InvestTips`) — من رابط «كل النصايح ←» في الكارت ومن سطر «النصائح» في أدوات الاستثمار (§1.10b — المدمِج). */
object InvestTipsRoute : Route {
    override val name = "InvestTips"
}

/** «الأسئلة الشائعة» (`Faq`) — آخر سطر في «المزيد › الإعدادات» (§1.13). الشاشة في `screens/more/FaqScreen.kt`. */
object FaqRoute : Route {
    override val name = "Faq"
}

fun RouteRegistry.registerInsights() {
    screen<InvestTipsRoute> { InvestTipsScreen() }
    screen<FaqRoute> { FaqScreen() }
}
