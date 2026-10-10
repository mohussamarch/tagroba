package app.masroufy.ui.shell

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import app.masroufy.core.Bank
import app.masroufy.core.TextKey
import app.masroufy.core.bankForWallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.BankLogo
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.LensOnHero
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.overlay.Anchor
import app.masroufy.ui.overlay.GlassPopover
import app.masroufy.ui.overlay.Veil
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.WalletNow
import app.masroufy.usecase.WithYouNow

/**
 * بنوك «معك الآن» جوه البطاقة البطلة (`HeroBanks` — OVERRIDES §74): عدسة زجاج لكل بنك في البلد الشغالة فوق بعض شوية (الأول على اليمين)،
 * وجواها **لوجو البنك** من دليل البنوك (§81 · §76 — زي ما هو من موقع البنك، للتعريف بس)، ولو مالوش لوجو أول حرف في دايرة ([BankLogo]).
 * الضغط ⇒ نافذة «أين ما معك الآن؟»: كل بنك برصيده + الكاش + المجموع (= الرقم الكبير بالظبط — نفس `LoadWithYouNow`).
 */
@Composable
fun HeroBanks(now: WithYouNow, modifier: Modifier = Modifier) {
    val banks = now.banks
    if (banks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val anchor = remember { Anchor() }
    val press = rememberPress()
    val names = banks.joinToString("، ") { it.wallet.name }
    val country = LocalSpace.current.space.countryCode
    Row(
        modifier.defaultMinSize(minHeight = 48.dp).onGloballyPositioned { anchor.update(it) }.pressScale(press)
            .tap(press, label = t(UiKey.HERO_BANKS_LABEL, names), onClick = { open = true }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        banks.forEachIndexed { i, b ->
            LensOnHero(
                Modifier.zIndex((banks.size - i).toFloat()).offset(x = if (i == 0) 0.dp else (-10 * i).dp).size(42.dp),
                shape = RoundedCornerShape(15.dp),
                background = Glass.heroBank,
            ) { BankLogo(bankForWallet(b.wallet, country), b.wallet.name, size = 30.dp, letterInk = Ink.lensInk) }
        }
    }
    GlassPopover(visible = open, onDismiss = { open = false }, anchor = anchor, title = t(UiKey.HERO_BANKS_TITLE), veil = Veil.NORMAL, width = 310.dp, closeLabel = t(UiKey.SHELL_CLOSE)) {
        BasicText(t(UiKey.HERO_BANKS_TITLE), Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp), style = Type.of(16, FontWeight.Bold))
        for (w in banks) BankLine(w, if (w.wallet.kind == "digital_wallet") t(UiKey.HERO_DIGITAL_SUB) else t(UiKey.HERO_BANK_SUB), now, bankForWallet(w.wallet, country), cash = false)
        for (w in now.cash) BankLine(w, t(UiKey.HERO_CASH_SUB), now, null, cash = true)
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(Color(0x1F193D33)))
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText(t(UiKey.HERO_WITH_YOU), style = Type.bodyBold())
            AmountText(now.totalMinor, now.currency, size = 18, color = Ink.primary)
        }
        BasicText(t(UiKey.HERO_LOGO_NOTE), Modifier.padding(horizontal = 6.dp), style = Type.of(11).copy(color = Ink.muted))
    }
}

@Composable
private fun BankLine(w: WalletNow, sub: String, now: WithYouNow, bank: Bank?, cash: Boolean) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tile = RoundedCornerShape(12.dp)
        if (cash) Box(Modifier.size(34.dp).clip(tile).background(Ink.selected).insetRing(tile, 1.dp, Ink.fieldEdge))
        else BankLogo(bank, w.wallet.name, size = 34.dp)
        Column(Modifier.weight(1f)) {
            BasicText(w.wallet.name, style = Type.bodyBold(), maxLines = 1)
            BasicText(sub, style = Type.caption().copy(color = Ink.muted))
        }
        AmountText(w.balanceMinor, now.currency)
    }
}
