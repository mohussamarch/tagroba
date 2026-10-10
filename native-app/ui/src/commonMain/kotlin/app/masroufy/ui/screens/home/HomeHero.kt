package app.masroufy.ui.screens.home

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.IsoDate
import app.masroufy.core.TextKey
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.HeroDivider
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.cssLinear
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.shell.HeroBanks
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.WithYouNow

/**
 * البطاقة البطلة (OVERRIDES §73): «معك الآن» = كل المحافظ (البنوك + الكاش) وجنبها عدسات البنوك (`HeroBanks`) ·
 * غير متاح ⇒ «غير متاح» + سبب + الكاش وحده · «يشمل X كاش ‹» ⇒ لوحة الكاش · خط رفيع وتحته «صرفت هذا الشهر» يمين و«الراتب بعد N» شمال.
 */
@Composable
internal fun WithYouHero(now: WithYouNow, month: HomeMonth?, today: IsoDate, onCash: () -> Unit) {
    HeroCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(t(UiKey.HERO_WITH_YOU), style = Type.body().copy(color = Ink.onHeroMuted))
                    HeroAmount(now.totalMinor, now.currency, Modifier.fillMaxWidth())
                }
                HeroBanks(now)
            }
            if (now.totalMinor == null && now.wallets.isNotEmpty()) {
                val line = listOfNotNull(t(UiKey.HERO_NA_LINE), cashOnlyLine(now)).joinToString(" ")
                BasicText(line, style = Type.of(13).copy(color = Ink.onHeroMuted))
            }
            // الكاش معروف والمجموع معروف ⇒ الشريحة (النموذج بيخفيها في «غير متاح» — الكاش وحده بيتقال في السطر فوق)
            val cash = now.cashMinor
            if (cash != null && now.totalMinor != null) CashChip(t(UiKey.HERO_CASH_CHIP, amountLabel(cash, now.currency, showCurrency = false)), onCash)
            HeroDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(spentLine(month, now.currency), Modifier.weight(1f), style = Type.caption().copy(color = Ink.onHeroMuted))
                salaryLine(month?.nextPayday, today)?.let { BasicText(it, style = Type.caption().copy(color = Ink.onHeroMuted)) }
            }
        }
    }
}

/** «يشمل X كاش ‹» — زجاج خفيف على البترولي، والضغط بيفتح لوحة الكاش (`CashDetails`). */
@Composable
private fun CashChip(text: String, onClick: () -> Unit) {
    val press = rememberPress()
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.heightIn(min = 36.dp).pressScale(press).clip(shape)
            .background(cssLinear(145f, 0f to Color(0x38FFFFFF), 1f to Color(0x0FFFFFFF)))
            .tap(press, label = t(UiKey.CASH_DETAILS_OPEN), onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.BANKNOTE, size = 18.dp, tint = Ink.mint)
        BasicText(text, style = Type.of(13).copy(color = Color.White))
        LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.onHeroMuted, modifier = Modifier.mirrorInLtr())
    }
}

/** شريط «خطأ» (النموذج: كهرماني بزرار «أعد المحاولة» 48). */
@Composable
internal fun HomeErrorBanner(onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(t(UiKey.HOME_ERROR_TITLE), style = Type.of(14, FontWeight.Bold).copy(color = Ink.focus))
            BasicText(t(UiKey.HOME_ERROR_BODY), style = Type.caption().copy(color = Ink.focus))
        }
        app.masroufy.ui.components.SecondaryButton(t(UiKey.SHELL_RETRY), onClick = onRetry)
    }
}
