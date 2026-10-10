package app.masroufy.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.periodForDate
import app.masroufy.core.weekdayDayMonth
import app.masroufy.ui.app.LocalBell
import app.masroufy.ui.app.LocalDataChanges
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.app.SpaceDeps
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.GearButton
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.screens.more.AccountRoute
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.shell.BellPopover
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.shell.SpaceSwitcher
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadHomeScreenRequest

/**
 * الرئيسية (`Home` = لوحة `Main` + `CashDetails`): الرأس · البطاقة البطلة «معك الآن» (+ `HeroBanks` · «يشمل X كاش» ⇒ لوحة الكاش ·
 * «صرفت هذا الشهر» و«الراتب بعد N») · كارت المساعد · «القادم» · كارت «كمّل ملفك». الحالات: بيحمّل (هيكل) · فشل (شريط + أعد المحاولة) ·
 * فاضي (حساب جديد) · غير متاح (رصيد محفظة مش معروف). كل رقم من حالة استخدام — الشاشة بتعرض بس.
 * لوحة «+» بتتفتح **فوق** الرئيسية وهي لسه ظاهرة ⇒ بعد الحفظ `DataChanges.version` بيزيد والرئيسية بتقرا تاني (من غير هيكل: الأرقام القديمة
 * بتفضل لحد ما الجديدة توصل).
 */
@Composable
fun HomeScreen() {
    val deps = LocalSpace.current
    val shell = deps.shell
    val changes = LocalDataChanges.current
    var me by remember(deps) { mutableStateOf<MeInfo?>(null) }
    var load by remember(deps) { mutableStateOf<HomeLoad>(HomeLoad.Loading) }
    var tick by remember { mutableStateOf(0) }
    var cashOpen by remember { mutableStateOf(false) }
    val bell = LocalBell.current
    val gone = Dismissals.of(deps.space.id).goneKeys
    LaunchedEffect(deps, tick, changes.version) {
        me = runCatching { shell.me() }.getOrNull()
        load = loadHome(deps)
    }
    TabScaffold(t(TextKey.TAB_HOME), header = { HomeHeader(me, bell.state, bell.refresh) }) {
        when (val s = load) {
            HomeLoad.Loading -> item(key = "loading") { HomeSkeleton() }
            HomeLoad.Failed -> item(key = "failed") { HomeErrorBanner { load = HomeLoad.Loading; tick++ } }
            is HomeLoad.Ready -> {
                // حساب جديد خالص ⇒ الشاشة الفاضية لوحدها (النموذج: حالة «فاضي» من غير البطاقة ولا «القادم»)
                if (isBrandNew(s.now, s.month)) {
                    item(key = "empty") { EmptyState(t(TextKey.HOME_EMPTY_TITLE), t(TextKey.HOME_EMPTY_BODY)) }
                } else {
                    item(key = "hero") { WithYouHero(s.now, s.month, shell.today(), onCash = { cashOpen = true }) }
                    if (isQuietMonth(s.now, s.month)) item(key = "quiet") { QuietMonthCard() }
                    else s.inbox?.let { advisorCardOf(it, gone) }?.let { card -> item(key = "advisor") { AdvisorCardView(card) } }
                    item(key = "upcoming") { UpcomingSection() }
                    if (s.profileCard) item(key = "profile") { ProfileCardView() }
                }
            }
        }
    }
    CashDetailsSheet(cashOpen, onDismiss = { cashOpen = false })
}

/**
 * قراية الرئيسية: «معك الآن» (لازم) ثم الشهر (يوم الراتب ⇒ الفترة ⇒ `LoadHomeScreen` + الراتب الجاي من `LoadCalendar.summary`) ثم صفحة
 * الإشعارات (كارت المساعد) والملف (كارت «كمّل ملفك»). أي جزء غير «معك الآن» يفشل ⇒ مكانه «غير متاح»/مخفي، مش الشاشة كلها.
 */
internal suspend fun loadHome(deps: SpaceDeps): HomeLoad {
    val now = runCatching { deps.shell.withYouNow() }.getOrElse { return HomeLoad.Failed }
    val today = deps.shell.today()
    val home = deps.home
    val profile = runCatching { home.profile.load() }.getOrNull()
    val month = runCatching {
        val payday = profile?.payday ?: app.masroufy.core.DEFAULT_PAYDAY
        val data = home.homeScreen.load(LoadHomeScreenRequest(periodForDate(today, payday), today, payday, includeHistory = false))
        val next = runCatching { home.calendar.summary(today).untilPayday?.nextPayday }.getOrNull()
        homeMonthOf(data, next)
    }.getOrNull()
    val inbox = runCatching { home.alerts.inbox() }.getOrNull()
    return HomeLoad.Ready(now, month, inbox, profileCard = profile != null && nextProfileCard(profile) != null)
}

/** رأس الرئيسية (KOTLIN-MAP §٣): دايرتك 46 ⇒ «ملفك» · التحية 18 + شارة البلد حرفين والتاريخ 12 · الجرس 48 · الترس 48 آخر حاجة على الشمال. */
@Composable
private fun HomeHeader(me: MeInfo?, bell: app.masroufy.ui.app.BellState?, refreshBell: () -> Unit) {
    val nav = LocalNavigator.current
    val shell = LocalSpace.current.shell
    val name = me?.displayName?.takeIf { it.isNotBlank() }
    val morning = shell.hourNow() < 12
    val greeting = when {
        name != null && morning -> t(TextKey.GREETING_MORNING_NAME, name)
        name != null -> t(TextKey.GREETING_EVENING_NAME, name)
        morning -> t(TextKey.GREETING_MORNING)
        else -> t(TextKey.GREETING_EVENING)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        val press = rememberPress()
        Box(
            Modifier.size(48.dp).pressScale(press).tap(press, label = t(TextKey.ME_PROFILE), onClick = { nav.push(AccountRoute) }),
            contentAlignment = Alignment.Center,
        ) { MeAvatar(46.dp, me?.profilePercent, look = avatarLook(LookChoice.look, me?.lookIndex)) }
        Column(Modifier.weight(1f).heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
            BasicText(greeting, style = Type.of(18, FontWeight.Bold, 1.4), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SpaceSwitcher()
                BasicText(weekdayDayMonth(shell.today()), style = Type.caption().copy(color = Ink.muted))
            }
        }
        BellPopover(bell, refreshBell)
        GearButton { nav.push(MoreRoute) }
    }
}

/** «بيحمّل»: نفس شكل المحتوى (البطاقة 176 · كارت 92 · كارتين 78) — مش دوّامة في نص الشاشة. */
@Composable
private fun HomeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.fillMaxWidth().height(176.dp), radius = 28.dp, strong = true)
        Skeleton(Modifier.fillMaxWidth().height(92.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(Modifier.weight(1f).height(78.dp), radius = 18.dp)
            Skeleton(Modifier.weight(1f).height(78.dp), radius = 18.dp)
        }
    }
}
