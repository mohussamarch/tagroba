package app.masroufy.ui.screens.more

import app.masroufy.ui.nav.Route
import app.masroufy.ui.nav.RouteRegistry

/**
 * منطقة «المزيد» (`SCREENS.md` §٢.٨ — More · Account · IncomeSources · IncomeSourceDetail · JobChangeSheet · Spaces · Wallets · WalletDetail ·
 * Categories · CategoryEditSheet · Rules · Projects · ProjectDetail · Backup · RestorePreview · NotificationSettings · AppSettings (القفل واللغة
 * والمحتوى الإسلامي)). الملف ده بتاع المنطقة بس. الهيكل بيستعمل [MoreRoute] (الترس) و[AccountRoute] (دايرتك) و[SpacesRoute] («إدارة البلدان»).
 * تسجيل الخروج: `LocalApp.current.signIn.signOut()` (الجلسة بترجع لشاشة الدخول لوحدها).
 */
interface MoreDeps

/** «المزيد» — بقت شاشة بتتفتح فوق التبويب من الترس (OVERRIDES §74). */
object MoreRoute : Route {
    override val name = "More"
}

object AccountRoute : Route {
    override val name = "Account"
}

object SpacesRoute : Route {
    override val name = "Spaces"
}

object NotificationSettingsRoute : Route {
    override val name = "NotificationSettings"
}

fun RouteRegistry.registerMore() {
}
