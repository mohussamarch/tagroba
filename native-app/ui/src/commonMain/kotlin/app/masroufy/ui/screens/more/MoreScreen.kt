package app.masroufy.ui.screens.more

import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.screens.imports.BankSmsRoute
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.screens.budgets.CategoriesRoute
import app.masroufy.ui.screens.budgets.RulesRoute
import app.masroufy.ui.screens.imports.ImportBatchesRoute
import app.masroufy.ui.screens.imports.StatementImportRoute
import app.masroufy.ui.screens.people.ProjectsRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «المزيد» (`More` — من الترس، OVERRIDES §74): كارت «ملفك» فوق + ٣ مجموعات (حسابك · بياناتك · الإعدادات — §76). السطور التانية من
 * البيانات بس: يوم الراتب من الملف · أسماء البلاد المفتوحة. اللي مالوش مصدر لسه (نسبة «ملفك ٪» · «٤ بانتظار تأكيدك») **ما بيتكتبش** (القاعدة 10).
 */
@Composable
fun MoreScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    var me by remember(deps) { mutableStateOf<MeInfo?>(null) }
    var payday by remember(deps) { mutableStateOf<Int?>(null) }
    var spaces by remember(deps) { mutableStateOf<List<SpaceCard>?>(null) }
    LaunchedEffect(deps) {
        me = runCatching { deps.shell.me() }.getOrNull()
        payday = runCatching { deps.more.profile.load().payday }.getOrNull()
        spaces = runCatching { deps.more.spaces() }.getOrNull()
    }
    val view = moreView(me, payday, spaces, deps.space)
    InnerScaffold(t(UiKey.MORE_TITLE)) {
        item(key = "me") { MeCard(me, view) { nav.push(AccountRoute) } }
        for (group in moreGroups(view)) item(key = group.title.name) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupTitle(t(group.title))
                GroupCard {
                    group.items.forEachIndexed { i, it ->
                        MenuRow(it.icon, t(it.label), it.hint, last = i == group.items.lastIndex, onClick = { nav.push(it.to) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MeCard(me: MeInfo?, view: MoreView, onClick: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick = onClick, clickLabel = t(UiKey.ME_PROFILE)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MeAvatar(46.dp, me?.profilePercent, Modifier.size(46.dp), look = me?.lookIndex ?: 1)
            Column(Modifier.weight(1f)) {
                BasicText(view.name ?: t(UiKey.ACC_NO_NAME), style = Type.of(16, FontWeight.Bold))
                BasicText(view.spaceLine, style = Type.caption().copy(color = Ink.muted))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
    }
}

/** سطر في قايمة «المزيد»: [hint] = null ⇒ مفيش سطر تاني (مش «٠»). */
data class MoreItem(val icon: Lucide, val label: TextRef, val hint: String?, val to: Route)

data class MoreGroup(val title: TextRef, val items: List<MoreItem>)

/** اللي بيتعرض في «المزيد» من البيانات — دالة نقية (بتتختبر على JVM). */
data class MoreView(val name: String?, val spaceLine: String, val incomeHint: String?, val spacesHint: String?)

fun moreView(me: MeInfo?, payday: Int?, spaces: List<SpaceCard>?, active: app.masroufy.core.Space): MoreView {
    val name = me?.displayName?.trim()?.takeIf { it.isNotEmpty() }
    val line = t(UiKey.MORE_SPACE_LINE, app.masroufy.ui.shell.countryLabel(active), app.masroufy.core.currencySymbol(active.currency))
    val income = payday?.let { t(UiKey.MORE_INCOME_HINT, app.masroufy.core.sentenceNumber(it)) }
    // البلد الشغالة الأول، وبعدها الباقي بترتيبها
    val names = spaces?.sortedByDescending { it.active }?.map { app.masroufy.ui.shell.countryLabel(it.space) }
    return MoreView(name, line, income, names?.takeIf { it.isNotEmpty() }?.joinToString(" · "))
}

fun moreGroups(view: MoreView): List<MoreGroup> = listOf(
    MoreGroup(
        UiKey.MORE_GROUP_ACCOUNT,
        listOf(
            MoreItem(Lucide.USER, UiKey.MORE_ITEM_PROFILE, null, AccountRoute),
            MoreItem(MoreIcons.INCOME, UiKey.MORE_ITEM_INCOME, view.incomeHint, IncomeSourcesRoute),
            MoreItem(Lucide.GLOBE, UiKey.MORE_ITEM_SPACES, view.spacesHint, SpacesRoute),
        ),
    ),
    MoreGroup(
        UiKey.MORE_GROUP_DATA,
        listOf(
            MoreItem(MoreIcons.WALLET_CARD, UiKey.MORE_ITEM_WALLETS, null, WalletsRoute),
            MoreItem(Lucide.TAG, UiKey.MORE_ITEM_CATEGORIES, null, CategoriesRoute),
            MoreItem(Lucide.TAG, UiKey.MORE_ITEM_RULES, t(UiKey.MORE_HINT_RULES), RulesRoute),
            MoreItem(Lucide.TAG, UiKey.MORE_ITEM_PROJECTS, t(UiKey.MORE_HINT_PROJECTS), ProjectsRoute),
            MoreItem(MoreIcons.IMPORT, UiKey.MORE_ITEM_BANK_SMS, null, BankSmsRoute),
            MoreItem(MoreIcons.IMPORT, UiKey.MORE_ITEM_STATEMENT, t(UiKey.MORE_HINT_STATEMENT), StatementImportRoute),
            MoreItem(MoreIcons.IMPORT, UiKey.MORE_ITEM_BATCHES, t(UiKey.MORE_HINT_BATCHES), ImportBatchesRoute),
            MoreItem(MoreIcons.BACKUP, UiKey.MORE_ITEM_BACKUP, null, BackupRoute),
            MoreItem(MoreIcons.EXPORT, UiKey.MORE_ITEM_EXPORT, t(UiKey.MORE_HINT_EXPORT), BackupRoute),
        ),
    ),
    MoreGroup(
        UiKey.MORE_GROUP_SETTINGS,
        listOf(
            MoreItem(Lucide.BELL, UiKey.MORE_ITEM_NOTIFICATIONS, null, NotificationSettingsRoute),
            MoreItem(Lucide.LOCK, UiKey.MORE_ITEM_LOCK, null, AppSettingsRoute),
            MoreItem(Lucide.LANGUAGES, UiKey.MORE_ITEM_LANGUAGE, null, AppSettingsRoute),
            MoreItem(Lucide.MOON, UiKey.MORE_ITEM_ISLAMIC, null, AppSettingsRoute),
        ),
    ),
)
