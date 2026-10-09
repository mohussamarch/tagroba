package app.masroufy.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconButton44
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.screens.onboarding.ProfileQuestionRoute
import app.masroufy.ui.shell.UpcomingStrip
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * كارت المساعد (النموذج: «القهوة هذا الشهر ٦٤٠ ر.س» بكوب كهرماني) = آخر تنبيه من مجموعة المساعد بعنوانه وتفاصيله.
 * الضغط ⇒ الاستثمار (صفحة المساعد `Advisor` جوه منطقة الاستثمار — ⚠️ المسار نفسه بيتوصل وقت الدمج).
 */
@Composable
internal fun AdvisorCardView(card: AdvisorCard) {
    val nav = LocalNavigator.current
    FloatingCard(Modifier.fillMaxWidth(), onClick = { nav.switchTab(Tab.INVESTMENT) }, clickLabel = t(TextKey.HOME_ADVISOR_OPEN)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconTile(Ink.focus, size = 40.dp) { LucideIcon(Lucide.COFFEE, size = 20.dp, tint = Ink.focus) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(card.title, style = Type.bodyBold())
                BasicText(card.body, style = Type.of(13).copy(color = Ink.muted))
                BasicText(t(TextKey.HOME_ADVISOR_OPEN), style = Type.of(13, androidx.compose.ui.text.font.FontWeight.Bold).copy(color = Ink.primary))
            }
        }
    }
}

/** شهر من غير عمليات (بس فيه محافظ): نفس جملة الشاشة الفاضية في كارت هادي. */
@Composable
internal fun QuietMonthCard() {
    FloatingCard(Modifier.fillMaxWidth()) {
        BasicText(t(TextKey.HOME_EMPTY_TITLE), style = Type.bodyBold())
        BasicText(t(TextKey.HOME_EMPTY_BODY), style = Type.of(13).copy(color = Ink.muted))
    }
}

/** «القادم» + رمز التقويم 44 (الكلمة والسهم بقوا رمز — §76). */
@Composable
internal fun UpcomingSection() {
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

/**
 * «كمّل ملفك» تحت «القادم» ⇒ `ProfileQuestion`. بيظهر بس لو فيه سؤال ليه حفظ (`nextProfileCard`).
 * ⚠️ النسبة «ملفك ٦٠٪» ودايرتها مالهاش حسبة في كوتلن لسه (`MeInfo.profilePercent = null`) ⇒ العنوان من غير رقم ورمز بدل الدايرة.
 */
@Composable
internal fun ProfileCardView() {
    val nav = LocalNavigator.current
    FloatingCard(Modifier.fillMaxWidth(), onClick = { nav.push(ProfileQuestionRoute) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ink.primary, size = 44.dp, radius = 22.dp) { LucideIcon(Lucide.USER, size = 22.dp, tint = Ink.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(TextKey.HOME_PROFILE_TITLE), style = Type.bodyBold())
                BasicText(t(TextKey.HOME_PROFILE_BODY), style = Type.caption().copy(color = Ink.muted))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
    }
}
