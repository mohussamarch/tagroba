package app.masroufy.ui.screens.more

import app.masroufy.core.InvestInsightKey
import app.masroufy.core.R06Key
import app.masroufy.ui.screens.investment.insights.FaqRoute
import app.masroufy.core.Space
import app.masroufy.core.TextRef
import app.masroufy.core.UiKey
import app.masroufy.core.countryLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Route
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.shell.countryLabel
import app.masroufy.ui.screens.budgets.CategoriesRoute
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «المزيد» (`More` — من الترس، OVERRIDES §74 · إعادة الترتيب §116 · `More.dc.html` فرع التصميم c37ce76a): كارت الملف وجنبه علم البلد ([MeRow]) +
 * 3 مجموعات: **فلوسك** (المحافظ · التصنيفات والقواعد) · **بياناتك** (الاستيراد · النسخة والتصدير) · **الإعدادات** سطر واحد بترس.
 * اتشال: «ملفك» (نفس الكارت) · «مصادر الدخل» (بقت في «ملفك» — §116-1) · «البلدان» (العلم + القايمة السريعة) · «المشاريع» · المكرر.
 * اللي مالوش مصدر لسه («4 بانتظار تأكيدك» · «آخر نسخة اليوم» في النموذج) **ما بيتكتبش** (القاعدة 10).
 */
@Composable
fun MoreScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val admin = deps.more.spacesAdmin
    var me by remember(deps) { mutableStateOf<MeInfo?>(null) }
    var payday by remember(deps) { mutableStateOf<Int?>(null) }
    var spaces by remember(deps) { mutableStateOf<List<SpaceCard>?>(null) }
    var archived by remember(deps) { mutableStateOf<List<Space>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    val country = rememberCountryMenu()
    LaunchedEffect(deps) {
        me = runCatching { deps.shell.me() }.getOrNull()
        payday = runCatching { deps.more.profile.load().payday }.getOrNull()
    }
    LaunchedEffect(deps, tick) {
        spaces = runCatching { deps.more.spaces() }.getOrNull()
        archived = runCatching { admin?.archived() }.getOrNull().orEmpty()
    }

    // نفس `act` بتاع «البلدان»: زرار واحد شغال في المرة · رسالة بعد ما يخلص · تحميل البلاد تاني
    fun act(block: suspend () -> String?) {
        if (country.busy) return
        country.busy = true
        scope.launch {
            val done = runCatching { block() }
            country.busy = false
            country.sheet = null
            done.getOrNull()?.let { toaster.show(it, dark = true) }
            tick++
        }
    }

    val open = spaces.orEmpty().map { it.space }
    val view = moreView(me, payday, spaces, deps.space)
    val menuItems = {
        countryMenuItems(
            open,
            onAdd = { country.sheet = SpaceSheet.Add },
            onArchive = { s -> country.sheet = SpaceSheet.Archive(s, s.id == deps.space.id) },
            onManage = { nav.push(SpacesRoute) },
        )
    }
    InnerScaffold(t(UiKey.MORE_TITLE), actions = { HeaderTiles(onFaq = { nav.push(FaqRoute) }, onSupport = { nav.push(SupportRoute) }) }) {
        item(key = "me") {
            MeRow(
                me, view, deps.space, open, country, menuItems,
                onProfile = { nav.push(AccountRoute) },
                onSwap = { target -> act { if (deps.shell.switchSpace(target.id)) t(UiKey.SPACE_SWITCHED, countryLabel(target)) else null } },
            )
        }
        moreGroups().forEachIndexed { gi, group ->
            item(key = "g$gi") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.title?.let { GroupTitle(t(it)) }
                    GroupCard {
                        group.items.forEachIndexed { i, it ->
                            MenuRow(it.icon, t(it.label), it.hint, last = i == group.items.lastIndex, onClick = { nav.push(it.to) })
                        }
                    }
                }
            }
        }
    }
    CountryHosts(
        country, admin != null, open, archived,
        onCreate = { code -> act { admin?.create(code); t(UiKey.SPC_CREATED, countryLabel(code)) } },
        onArchive = { s -> act { admin?.archive(s.id); t(UiKey.SPC_ARCHIVED_DONE, countryLabel(s.countryCode)) } },
    )
}

/** علامتين فوق «المزيد» (تصميم 6935136c): «الأسئلة الشائعة» (؟) و«الدعم» (سماعة) — مربع 36 زاوية 12 زي أيقونات السطور، بينهم 6. */
@Composable
private fun HeaderTiles(onFaq: () -> Unit, onSupport: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HeaderTile(app.masroufy.ui.screens.investment.insights.InsightIcons.HELP, t(InvestInsightKey.FAQ_TITLE), onFaq)
        HeaderTile(MoreIcons.HEADSET, app.masroufy.core.SupportChrome(true).pageTitle, onSupport)
    }
}

@Composable
private fun HeaderTile(icon: Lucide, label: String, onClick: () -> Unit) {
    val press = app.masroufy.ui.components.rememberPress()
    // fix053: اسم للقارئ الصوتي («الأسئلة الشائعة» · «الدعم») — الأيقونة لوحدها مالهاش نص
    val described = Modifier.semantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button }
    app.masroufy.ui.components.IconTile(Ink.primary, described.tap(press, label = label, onClick = onClick)) {
        LucideIcon(icon, size = 18.dp, tint = Ink.primary)
    }
}

/** كارت الملف: الضغطة ⇒ «ملفك» · الطويلة ⇒ القايمة السريعة ([MeRow]). */
@Composable
internal fun MeCardBody(me: MeInfo?, view: MoreView, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    FloatingCard(
        Modifier.fillMaxWidth().fillMaxHeight(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick, onLongClick = onLongClick, clickLabel = t(UiKey.ME_PROFILE),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MeAvatar(46.dp, me?.profilePercent, Modifier.size(46.dp), character = me?.character)
            Column(Modifier.weight(1f)) {
                BasicText(view.name ?: t(UiKey.ACC_NO_NAME), style = Type.of(16, FontWeight.Bold))
                BasicText(view.spaceLine, style = Type.caption().copy(color = Ink.muted))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
    }
}

/** سطر في قايمة «المزيد»: [hint] = null ⇒ مفيش سطر تاني (مش «0»). */
data class MoreItem(val icon: Lucide, val label: TextRef, val hint: String?, val to: Route)

/** [title] = null ⇒ مجموعة من غير عنوان (سطر «الإعدادات» لوحده — زي النموذج). */
data class MoreGroup(val title: TextRef?, val items: List<MoreItem>)

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

/** المجموعات بالظبط زي `More.dc.html` (c37ce76a): 5 سطور. التصنيفات ⇒ القواعد والتجار من جوه («القواعد» في آخر الصفحة). */
fun moreGroups(): List<MoreGroup> = listOf(
    MoreGroup(
        R06Key.MR_GROUP_MONEY,
        listOf(
            MoreItem(MoreIcons.WALLET_CARD, UiKey.MORE_ITEM_WALLETS, t(R06Key.MR_WALLETS_HINT), WalletsRoute),
            MoreItem(Lucide.TAG, R06Key.MR_CATS, t(R06Key.MR_CATS_HINT), CategoriesRoute),
        ),
    ),
    MoreGroup(
        UiKey.MORE_GROUP_DATA,
        listOf(
            MoreItem(MoreIcons.IMPORT, R06Key.MR_IMPORT, t(R06Key.MR_IMPORT_HINT), ImportHubRoute),
            MoreItem(MoreIcons.BACKUP, R06Key.MR_BACKUP, null, BackupRoute),
        ),
    ),
    MoreGroup(null, listOf(MoreItem(Lucide.SETTINGS, R06Key.MR_SETTINGS, t(R06Key.MR_SETTINGS_HINT), SettingsHubRoute))),
)
