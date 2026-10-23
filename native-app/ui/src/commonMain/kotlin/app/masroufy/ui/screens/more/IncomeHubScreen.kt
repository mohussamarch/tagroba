package app.masroufy.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomeSourceKind
import app.masroufy.core.LinkedEmployer
import app.masroufy.core.UiKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * «مصادر الدخل» (`IncomeHub` — OVERRIDES §112-6): «مصادر الدخل» و«جهات العمل» شاشة واحدة. فوق 7 أنواع ثابتة ([HUB_KINDS]) — النوع اللي فيه
 * مصدر شغال اسمه أخضر، والفاضي «ضيف» بإطار خفيف. النوع بيفتح [IncomeKindScreen]. حالات الاستخدام نفسها (`ManageIncomeSources`) — مفيش منطق جديد.
 */
@Composable
fun IncomeHubScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val changes = app.masroufy.ui.app.LocalDataChanges.current
    var sources by remember(deps) { mutableStateOf<List<IncomeSource>?>(null) }
    LaunchedEffect(deps, changes.version) { sources = runCatching { deps.more.incomeSources.list() }.getOrNull() ?: emptyList() }
    InnerScaffold(t(app.masroufy.core.R06Key.INC_HUB_TITLE)) {
        val list = sources
        if (list == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(380.dp)) }
            return@InnerScaffold
        }
        val tiles = incomeHub(list).tiles
        item(key = "kinds") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (pair in tiles.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (tile in pair) KindTileCard(tile, Modifier.weight(1f)) { nav.push(IncomeKindRoute(tile.kind)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KindTileCard(tile: KindTile, modifier: Modifier, onClick: () -> Unit) {
    val on = tile.state == TileState.CURRENT
    val shape = RoundedCornerShape(22.dp)
    val body: @Composable () -> Unit = {
        Column(Modifier.heightIn(min = 92.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconTile(if (on) Ink.primary else Ink.muted, size = 40.dp, radius = 12.dp) { LucideIcon(incomeIcon(tile.kind), size = 20.dp, tint = if (on) Ink.primary else Ink.muted) }
            BasicText(tile.label, style = Type.of(15, FontWeight.Bold))
            BasicText(tile.sub, style = if (on) Type.of(12, FontWeight.Bold).copy(color = Ink.primary) else Type.caption().copy(color = Ink.muted))
        }
    }
    if (tile.state == TileState.EMPTY) {
        Box(
            modifier.clip(shape).background(Color.White.copy(alpha = 0.55f)).border(1.5.dp, Ink.muted.copy(alpha = 0.3f), shape)
                .clickable(onClickLabel = tile.label, onClick = onClick).padding(14.dp),
        ) { body() }
    } else {
        FloatingCard(modifier, shape = shape, contentPadding = PaddingValues(14.dp), onClick = onClick, clickLabel = tile.label) { body() }
    }
}

/**
 * جوه النوع: «شغلك دلوقتي» (الاسم · ميعاد القبض · من إمتى · المتوقع — مبلغ لكل عملة لو الجهة في البلدين §111-2) · «غيّرت شغلي» و«ضيف تاني»
 * (نفس `JobChangeSheet`) · «أماكن اشتغلت فيها» خط زمني · ولنوع الشغل رابط «جهات العمل» (حساباتها وقواعدها — `EmployersScreen` زي ما هي).
 */
@Composable
fun IncomeKindScreen(kind: IncomeSourceKind) {
    val deps = LocalSpace.current
    val more = deps.more
    val nav = LocalNavigator.current
    var sources by remember(deps) { mutableStateOf<List<IncomeSource>?>(null) }
    var linked by remember(deps) { mutableStateOf<List<LinkedEmployer>>(emptyList()) }
    var monthStart by remember(deps) { mutableIntStateOf(28) }
    var sheet by remember { mutableStateOf<JobSheetMode?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(deps, tick) {
        sources = runCatching { more.incomeSources.list() }.getOrNull() ?: emptyList()
        linked = runCatching { more.employerLinks?.list(deps.shell.today()) }.getOrNull().orEmpty()
        monthStart = runCatching { more.profile.load().payday }.getOrDefault(monthStart)
    }
    val list = sources
    val page = list?.let { kindPage(kind, it, linked) }
    InnerScaffold(t(hubKindLabel(kind))) {
        if (page == null) {
            item(key = "sk") { Skeleton(Modifier.fillMaxWidth().height(260.dp)) }
            return@InnerScaffold
        }
        item(key = "now") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(page.nowLabel, Modifier.padding(horizontal = 4.dp), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
                for (r in page.now) NowCard(r, kind) { nav.push(IncomeSourceDetailRoute(r.id)) }
                if (page.now.isEmpty()) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.primary.copy(alpha = 0.06f)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconTile(Ink.primary, size = 40.dp, radius = 12.dp) { LucideIcon(incomeIcon(kind), size = 20.dp, tint = Ink.primary) }
                    BasicText(page.noneText, style = Type.of(14, FontWeight.Bold))
                }
            }
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((i, a) in page.actions.withIndex()) {
                    val open = { sheet = if (a == KindAction.CHANGE) JobSheetMode.Change else JobSheetMode.Add }
                    if (i == 0) PrimaryButton(t(kindActionLabel(a)), onClick = open, height = 48.dp, modifier = Modifier.weight(1f))
                    else TonalButton(t(kindActionLabel(a)), onClick = open, modifier = Modifier.weight(1f))
                }
            }
        }
        if (page.past.isNotEmpty()) item(key = "past") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(page.pastLabel, Modifier.padding(horizontal = 4.dp), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
                FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Column { for ((i, p) in page.past.withIndex()) PastLine(p, last = i == page.past.lastIndex) { nav.push(IncomeSourceDetailRoute(p.id)) } }
                }
            }
        }
        if (page.employersLink) item(key = "employers") {
            FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = { nav.push(EmployersRoute) }, clickLabel = t(UiKey.EMPLOYER_MORE_ITEM)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(t(UiKey.EMPLOYER_MORE_ITEM), style = Type.of(15, FontWeight.Bold))
                        BasicText(t(UiKey.EMPLOYER_MORE_HINT), style = Type.caption().copy(color = Ink.muted))
                    }
                    LucideIcon(Lucide.CHEVRON_LEFT, Modifier.size(16.dp).mirrorInLtr(), size = 16.dp, tint = Ink.muted)
                }
            }
        }
    }
    val current = list.orEmpty().filter { it.endedAt == null && it.kind == kind }
    JobChangeSheet(sheet, current, monthStart, onClose = { sheet = null }, onDone = { tick++ }, addKind = kind)
}

@Composable
private fun NowCard(r: NowRow, kind: IncomeSourceKind, onClick: () -> Unit) {
    FloatingCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(16.dp), onClick = onClick, clickLabel = r.name) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            app.masroufy.ui.app.WithActiveCountryMark { IconTile(Ink.primary, size = 40.dp, radius = 12.dp) { LucideIcon(incomeIcon(kind), size = 20.dp, tint = Ink.primary) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(r.name, style = Type.of(16, FontWeight.Bold))
                BasicText(r.line, style = Type.caption().copy(color = Ink.muted))
            }
            Column(horizontalAlignment = Alignment.End) {
                for (a in r.amounts) BasicText(a, style = Type.of(15, FontWeight.Bold).copy(color = Ink.primary, textDirection = TextDirection.Ltr))
            }
        }
    }
}

@Composable
private fun PastLine(p: PastRow, last: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = p.name, onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 14.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(Ink.muted.copy(alpha = 0.7f)))
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).height(34.dp).background(Ink.muted.copy(alpha = 0.35f)))
        }
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(p.name, style = Type.of(15, FontWeight.Bold))
            BasicText(p.dates, style = Type.caption().copy(color = Ink.muted))
        }
    }
}

/** رمز النوع (من `IncomeHub.dc.html`): دوام كامل حقيبة · جزئي ساعة · عميل ناس · إيجار بيت · استثمار سهم طالع · معاش قلب · دخل تاني «+». */
fun incomeIcon(kind: IncomeSourceKind): Lucide = when (kind) {
    IncomeSourceKind.JOB -> Lucide.BRIEFCASE
    IncomeSourceKind.PART_TIME -> Lucide.CLOCK
    IncomeSourceKind.CLIENT -> Lucide.USERS
    IncomeSourceKind.RENT -> Lucide.HOUSE
    IncomeSourceKind.INVESTMENT -> Lucide.TRENDING_UP
    IncomeSourceKind.PENSION -> MoreIcons.PENSION
    IncomeSourceKind.OTHER -> Lucide.PLUS
}
