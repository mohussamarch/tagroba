package app.masroufy.ui.screens.more

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.ApproxBadge
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.HeroAmount
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.countryLabel
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.WithYouNow

/**
 * «المحافظ» (`Wallets` — spec/02 «الكاش» · §26 · §41): محافظ البلد الشغالة بس. فوق «معك الآن» (نفس رقم الرئيسية — `LoadWithYouNow`)،
 * وتحته البنوك · المحافظ الإلكترونية · الكاش، ولكل محفظة رصيدها أو «غير متاح» ورصيد بدايتها، و«الأساسية» على محفظتك الأساسية.
 * «أضف محفظة» (`WalletAddSheet`) نقطة ربط ([WalletEditor]) — مفيش حالة استخدام لإدارة المحافظ لسه.
 */
@Composable
fun WalletsScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    var now by remember(deps) { mutableStateOf<WithYouNow?>(null) }
    var mainId by remember(deps) { mutableStateOf<Id?>(null) }
    var adding by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        now = runCatching { deps.more.wallets.load(deps.shell.today()) }.getOrNull()
        mainId = runCatching { deps.more.mainWallet?.current() }.getOrNull()
    }
    val space = deps.space
    InnerScaffold(t(UiKey.WLIST_TITLE), actions = {
        Badge(t(UiKey.SPACE_SUB, countryLabel(space), currencySymbol(space.currency)), BadgeKind.INFO)
    }) {
        val n = now
        if (n == null) {
            item(key = "sk") { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { Skeleton(Modifier.fillMaxWidth().height(120.dp), radius = 26.dp, strong = true); Skeleton(Modifier.fillMaxWidth().height(220.dp)) } }
            return@InnerScaffold
        }
        val view = walletsView(n, mainId)
        item(key = "total") {
            HeroCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(t(UiKey.SPACE_WITH_YOU), style = Type.of(13).copy(color = Ink.onHeroMuted))
                    HeroAmount(view.totalMinor, n.currency, Modifier.fillMaxWidth(), size = 30)
                    BasicText(walletsTotalLine(view), style = Type.caption().copy(color = Ink.onHeroMuted))
                }
            }
        }
        for (g in view.groups) item(key = g.kind.name) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(g.kind.title), Modifier.padding(horizontal = 4.dp), style = Type.of(15, FontWeight.Bold))
                GroupCard {
                    g.rows.forEachIndexed { i, r -> WalletRow(r, n, last = i == g.rows.lastIndex) { nav.push(WalletDetailRoute(r.id)) } }
                }
            }
        }
        item(key = "add") { TonalButton(t(UiKey.WADD_OPEN), onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) }
    }
    WalletAddSheet(adding, WalletSheetMode.Add, existing = now?.wallets?.map { it.wallet }.orEmpty(), onClose = { adding = false }, onSaved = { tick++ })
}

@Composable
private fun WalletRow(r: WalletRowView, now: WithYouNow, last: Boolean, onClick: () -> Unit) {
    val press = rememberPress()
    Column {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).pressScale(press).clip(RoundedCornerShape(16.dp))
                .tap(press, label = r.name, onClick = onClick).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalletLens(r.name, r.isCash)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(r.name, Modifier.weight(1f), style = Type.of(15, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AmountText(r.balanceMinor, now.currency)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(walletMeta(r), Modifier.weight(1f, fill = false), style = Type.caption().copy(color = Ink.muted), maxLines = 1)
                    if (r.isMain) Badge(t(UiKey.WLIST_MAIN), BadgeKind.INFO)
                    Box(Modifier.weight(1f))
                    if (r.approx) ApproxBadge()
                }
                BasicText(t(UiKey.WLIST_OPENING, amountLabel(r.openingMinor, now.currency, showCurrency = false), fullDate(r.openingAt) ?: r.openingAt), style = Type.of(11).copy(color = Ink.muted))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 16.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
        if (!last) RowRule()
    }
}

/** «حساب بنكي · •••• 4407» — أو النوع لوحده. */
fun walletMeta(r: WalletRowView): String = if (r.last4 != null) t(UiKey.WLIST_META_LAST4, t(r.kindLabel), r.last4) else t(r.kindLabel)

/** عدسة المحفظة 40: الكاش أخضر فاتح برمز الفلوس، والبنك أبيض بحد رفيع وأول حرف من اسمه (مكان اللوجو لحد ما يتعتمد — §74). */
@Composable
fun WalletLens(name: String, cash: Boolean, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(size).clip(shape).background(if (cash) Ink.selected else Color.White).then(if (cash) Modifier else Modifier.insetRing(shape, 1.dp, Ink.fieldEdge)),
        contentAlignment = Alignment.Center,
    ) {
        if (cash) LucideIcon(Lucide.BANKNOTE, size = 20.dp, tint = Ink.primary)
        else BasicText(walletMark(name), style = Type.of(16, FontWeight.Bold).copy(color = Ink.primary))
    }
}

/** حرف مكان اللوجو: أول حرف من الاسم من غير «بنك/مصرف» و«ال». */
fun walletMark(name: String): String {
    val skip = setOf("بنك", "مصرف", "البنك", "المصرف")
    val word = name.trim().split(Regex("\\s+")).firstOrNull { it !in skip } ?: name.trim()
    return word.removePrefix("ال").take(1).uppercase()
}
