package app.masroufy.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.weekdayDayMonth
import app.masroufy.ui.app.LocalBell
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.app.MeInfo
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.GearButton
import app.masroufy.ui.screens.common.TabScaffold
import app.masroufy.ui.screens.more.AccountRoute
import app.masroufy.ui.screens.more.MoreRoute
import app.masroufy.ui.shell.BellPopover
import app.masroufy.ui.shell.HeroBanks
import app.masroufy.ui.shell.MeAvatar
import app.masroufy.ui.shell.SpaceSwitcher
import app.masroufy.ui.shell.UpcomingStrip
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.WithYouNow

/**
 * الرئيسية (`Home` = لوحة `Main`) — **الأساس بس** (الرأس · البطاقة البطلة «معك الآن» + `HeroBanks` · «القادم»). منطقة الرئيسية بتكمّل:
 * «صرفت هذا الشهر» و«الراتب بعد N» (`LoadHomeScreen`)، لوحة الكاش (`CashDetails`)، كارت المساعد، كارت «ملفك X%»، والحالات (فاضي · خطأ).
 */
@Composable
fun HomeScreen() {
    val deps = LocalSpace.current
    val shell = deps.shell
    var me by remember(deps) { mutableStateOf<MeInfo?>(null) }
    var now by remember(deps) { mutableStateOf<WithYouNow?>(null) }
    val bell = LocalBell.current
    LaunchedEffect(deps) {
        me = runCatching { shell.me() }.getOrNull()
        now = runCatching { shell.withYouNow() }.getOrNull()
    }
    TabScaffold(t(TextKey.TAB_HOME), header = { HomeHeader(me, bell.state, bell.refresh) }) {
        item(key = "hero") { WithYouHero(now) }
        item(key = "upcoming") { UpcomingSection() }
    }
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
        ) { MeAvatar(46.dp, me?.profilePercent, look = me?.lookIndex ?: 1) }
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

@Composable
private fun WithYouHero(now: WithYouNow?) {
    if (now == null) {
        Skeleton(Modifier.fillMaxWidth().height(176.dp), radius = 28.dp, strong = true)
        return
    }
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(t(TextKey.HERO_WITH_YOU), style = Type.body().copy(color = Ink.onHeroMuted))
                    HeroAmount(now.totalMinor, now.currency, Modifier.fillMaxWidth())
                }
                HeroBanks(now)
            }
            if (now.totalMinor == null && now.wallets.isNotEmpty()) {
                BasicText(t(TextKey.HERO_NA_LINE), style = Type.of(13).copy(color = Ink.onHeroMuted))
            }
            now.cashMinor?.let { cash -> CashChip(t(TextKey.HERO_CASH_CHIP, amountLabel(cash, now.currency, showCurrency = false))) }
            HeroDivider()
        }
    }
}

/** شريحة «يشمل X كاش» (زجاج خفيف على البترولي). لوحة الكاش (`CashDetails`) شغل منطقة الرئيسية. */
@Composable
private fun CashChip(text: String) {
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x26FFFFFF)).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.BANKNOTE, size = 18.dp, tint = Ink.mint)
        BasicText(text, style = Type.of(13).copy(color = Color.White))
    }
}

@Composable
private fun UpcomingSection() {
    val nav = LocalNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(t(TextKey.UPCOMING_TITLE), style = Type.section())
            IconButton44(Lucide.CALENDAR, t(TextKey.CALENDAR_OPEN), onClick = { nav.push(CalendarRoute) })
        }
        val cards = rememberUpcomingCards()
        if (cards == null) Skeleton(Modifier.fillMaxWidth().height(150.dp))
        else UpcomingStrip(cards)
    }
}
