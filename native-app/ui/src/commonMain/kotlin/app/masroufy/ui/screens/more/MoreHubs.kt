package app.masroufy.ui.screens.more

import androidx.compose.runtime.Composable
import app.masroufy.core.R06Key
import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.screens.imports.ImportBatchesRoute
import app.masroufy.ui.screens.imports.StatementImportRoute
import app.masroufy.ui.text.t

/**
 * صفحتين صغيرين ورا سطرين في «المزيد» (OVERRIDES §116-3 · `More.dc.html` فرع التصميم c37ce76a). التصميم ما رسمهمش من جوه لسه
 * (❓ في OVERRIDES — المالك هيعيد تصميم الإعدادات) ⇒ نفس شكل سطور «المزيد» وبتفتح الشاشات الموجودة زي ما هي.
 */
object ImportHubRoute : Route {
    override val name = "ImportHub"
}

object SettingsHubRoute : Route {
    override val name = "SettingsHub"
}

/** «الاستيراد»: رسائل البنك · كشف الحساب · دفعات الاستيراد. دالة نقية. */
fun importHubItems(): List<MoreItem> = listOf(
    MoreItem(MoreIcons.IMPORT, UiKey.MORE_ITEM_BANK_SMS, null, BankSmsRoute),
    MoreItem(MoreIcons.IMPORT, UiKey.MORE_ITEM_STATEMENT, t(UiKey.MORE_HINT_STATEMENT), StatementImportRoute),
    MoreItem(MoreIcons.IMPORT, UiKey.MORE_ITEM_BATCHES, t(UiKey.MORE_HINT_BATCHES), ImportBatchesRoute),
)

/**
 * «الإعدادات» — نفس ترتيب سطر «المزيد» (الإشعارات · القفل · المظهر · اللغة · سجل المشكلات). القفل والمظهر واللغة والمحتوى الإسلامي
 * أقسام في صفحة واحدة (`AppSettings`) ⇒ بيفتحوها هي؛ اللغة والمحتوى الإسلامي سطر واحد (قسمين جنب بعض في نفس الصفحة).
 */
fun settingsHubItems(): List<MoreItem> = listOf(
    MoreItem(Lucide.BELL, UiKey.MORE_ITEM_NOTIFICATIONS, null, NotificationSettingsRoute),
    MoreItem(Lucide.LOCK, UiKey.MORE_ITEM_LOCK, null, AppSettingsRoute),
    MoreItem(Lucide.MOON, R06Key.NP_THEME_TITLE, null, AppSettingsRoute),
    MoreItem(Lucide.LANGUAGES, R06Key.MR_SET_LANG, null, AppSettingsRoute),
    MoreItem(Lucide.FILE_TEXT, UiKey.PLOG_TITLE, t(UiKey.PLOG_OPEN_HINT), ProblemLogRoute),
)

@Composable
fun ImportHubScreen() = HubScreen(R06Key.MR_IMPORT, importHubItems())

@Composable
fun SettingsHubScreen() = HubScreen(R06Key.MR_SETTINGS, settingsHubItems())

@Composable
private fun HubScreen(title: TextRef, items: List<MoreItem>) {
    val nav = LocalNavigator.current
    InnerScaffold(t(title)) {
        item(key = "rows") {
            GroupCard {
                items.forEachIndexed { i, it ->
                    MenuRow(it.icon, t(it.label), it.hint, last = i == items.lastIndex, onClick = { nav.push(it.to) })
                }
            }
        }
    }
}
