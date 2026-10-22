package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import app.masroufy.ui.text.BasicText
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
import app.masroufy.ui.screens.imports.SmsAllowCard
import app.masroufy.ui.screens.imports.homeEmptyBodyKey
import app.masroufy.ui.screens.imports.smsAllowCardShown
import app.masroufy.ui.screens.imports.smsShapeWaiting
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
    var refreshing by remember { mutableStateOf(false) }
    val pull = app.masroufy.ui.screens.common.rememberPullRefresh(refreshing) { if (!refreshing) { refreshing = true; tick++ } }
    var cashOpen by remember { mutableStateOf(false) }
    val bell = LocalBell.current
    val gone = Dismissals.of(deps.space.id).goneKeys
    // r041 A8: كلام الرئيسية الفاضية من حالة القراءة (مش «فعّل القراءة» وهي شغالة)
    var readingOn by remember(deps) { mutableStateOf(false) }
    // r041b P3: رسايل البنك المستنية «أكّد الأشكال» ⇒ كارت فوق بيفتح «رسائل البنك» على طول
    var smsWaiting by remember(deps) { mutableStateOf(0) }
    LaunchedEffect(deps, tick, changes.version) {
        app.masroufy.perf.PerfTrace.span("screen:home") {
            me = runCatching { shell.me() }.getOrNull()
            val loaded = loadHome(deps)
            // r041b P3: الكارت الهادي «لا توجد عمليات» كمان (مش الشاشة الفاضية بس) بيقول القراءة شغالة لو شغالة.
            // R041B-2: حالة القراءة بتتقري **قبل** ما الشاشة تتعرض — وإلا أول إطار بيقول «فعّل القراءة» وهي شغالة لحد ما القراية ترجع
            if (loaded.let { it is HomeLoad.Ready && (isBrandNew(it.now, it.month) || isQuietMonth(it.now, it.month)) }) {
                readingOn = runCatching { deps.imports.sms?.readingOn() }.getOrNull() == true
            }
            load = loaded
            smsWaiting = deps.imports.sms?.let { s -> smsShapeWaiting(runCatching { s.historyReport() }.getOrNull()) } ?: 0
            refreshing = false
        }
    }
    // اختار «رسائل البنك» في أول تشغيل والإذن اترفض (أو الشرح اتقفل) ⇒ كارت هادي «اسمح لاحقًا» (OVERRIDES §91)
    val smsCard = smsAllowCardShown()
    val emptyBody = homeEmptyBodyKey(readable = deps.imports.sms != null, readingOn = readingOn)
    TabScaffold(t(UiKey.TAB_HOME), refresh = pull, header = { HomeHeader(me, bell.state, bell.refresh) }) {
        if (smsCard) item(key = "smsAllow") { SmsAllowCard() }
        if (smsWaiting > 0) item(key = "smsWaiting") { SmsWaitingCard(smsWaiting) }
        when (val s = load) {
            HomeLoad.Loading -> item(key = "loading") { HomeSkeleton() }
            HomeLoad.Failed -> item(key = "failed") { HomeErrorBanner { load = HomeLoad.Loading; tick++ } }
            is HomeLoad.Ready -> {
                // حساب جديد خالص ⇒ الشاشة الفاضية لوحدها (النموذج: حالة «فاضي» من غير البطاقة ولا «القادم»)
                if (isBrandNew(s.now, s.month)) {
                    item(key = "empty") { EmptyState(t(UiKey.HOME_EMPTY_TITLE), t(emptyBody)) }
                } else {
                    item(key = "hero") { WithYouHero(s.now, s.month, shell.today(), onCash = { cashOpen = true }) }
                    if (isQuietMonth(s.now, s.month)) item(key = "quiet") { QuietMonthCard(emptyBody) }
                    else s.inbox?.let { advisorCardOf(it, gone) }?.let { card -> item(key = "advisor") { AdvisorCardView(card) } }
                    if (s.profileCard) item(key = "profile") { ProfileCardView() }
                    item(key = "cards") { app.masroufy.ui.screens.home.cards.HomeCards() }
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
    val trace = app.masroufy.perf.PerfTrace
    val now = runCatching { trace.span("home:withYouNow") { deps.shell.withYouNow() } }.getOrElse { return HomeLoad.Failed }
    val today = deps.shell.today()
    val home = deps.home
    val profile = runCatching { trace.span("home:profile") { home.profile.load() } }.getOrNull()
    val month = runCatching {
        val payday = profile?.payday ?: app.masroufy.core.DEFAULT_PAYDAY
        val data = trace.span("home:homeScreen") { home.homeScreen.load(LoadHomeScreenRequest(periodForDate(today, payday), today, payday, includeHistory = false)) }
        val next = runCatching { trace.span("home:calendar") { home.calendar.summary(today).untilPayday?.nextPayday } }.getOrNull()
        homeMonthOf(data, next)
    }.getOrNull()
    val inbox = runCatching { trace.span("home:inbox") { home.alerts.inbox() } }.getOrNull()
    val salary = runCatching { home.salary?.current() }.getOrNull()
    return HomeLoad.Ready(now, month, inbox, profileCard = profile != null && nextProfileCard(profile, salary) != null)
}

/** رأس الرئيسية (KOTLIN-MAP §3): دايرتك 46 ⇒ «ملفك» · التحية 18 + شارة البلد حرفين والتاريخ 12 · الجرس 48 · الترس 48 آخر حاجة على الشمال. */
@Composable
private fun HomeHeader(me: MeInfo?, bell: app.masroufy.ui.app.BellState?, refreshBell: () -> Unit) {
    val nav = LocalNavigator.current
    val shell = LocalSpace.current.shell
    val name = me?.displayName?.takeIf { it.isNotBlank() }
    val morning = shell.hourNow() < 12
    val greeting = when {
        name != null && morning -> t(UiKey.GREETING_MORNING_NAME, name)
        name != null -> t(UiKey.GREETING_EVENING_NAME, name)
        morning -> t(UiKey.GREETING_MORNING)
        else -> t(UiKey.GREETING_EVENING)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        val press = rememberPress()
        Box(
            Modifier.size(48.dp).pressScale(press).tap(press, label = t(UiKey.ME_PROFILE), onClick = { nav.push(AccountRoute) }),
            contentAlignment = Alignment.Center,
        ) { MeAvatar(46.dp, me?.profilePercent, character = me?.character) }
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
        Skeleton(quiet = true, modifier = Modifier.fillMaxWidth().height(176.dp), radius = 28.dp, strong = true)
        Skeleton(quiet = true, modifier = Modifier.fillMaxWidth().height(92.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Skeleton(quiet = true, modifier = Modifier.weight(1f).height(78.dp), radius = 18.dp)
            Skeleton(quiet = true, modifier = Modifier.weight(1f).height(78.dp), radius = 18.dp)
        }
    }
}
