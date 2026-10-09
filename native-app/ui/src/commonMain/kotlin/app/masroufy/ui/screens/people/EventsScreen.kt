package app.masroufy.ui.screens.people

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.LifeEventKind
import app.masroufy.core.TextKey
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.IconTile
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion

/**
 * «الأحداث» (لوحة `Events`): «أقرب حدث» · النشطة (الجاية بالأقرب ثم اللي فاتت) بكارت لكل حدث فيه المصروف والنقوط من غير صافي ·
 * المؤرشفة مطوية · زرار «حدث جديد» (`EventAddSheet`). الحالات: بيحمّل · فاضي · عادي.
 */
@Composable
internal fun EventsScreen() {
    val deps = LocalSpace.current
    val nav = LocalNavigator.current
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps) { mutableStateOf<Load<EventsUi>>(Load.Loading) }
    var archivedOpen by remember { mutableStateOf(false) }
    LaunchedEffect(deps, version, retry) {
        load = loadOf {
            val names = runCatching { deps.people.people.listWithBalances().associate { it.person.id to it.person.name } }.getOrDefault(emptyMap())
            eventsUi(deps.people.events.list(), names, deps.shell.today())
        }
    }
    InnerScaffold(t(TextKey.EVENTS_TITLE)) {
        when (val l = load) {
            Load.Loading -> items(3) { Skeleton(Modifier.fillMaxWidth().height(if (it == 0) 72.dp else 150.dp)) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(TextKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> {
                val ui = l.value
                if (ui.active.isEmpty() && ui.archived.isEmpty()) {
                    item { EmptyState(t(TextKey.EVENTS_EMPTY_TITLE), t(TextKey.EVENTS_EMPTY_BODY)) }
                } else {
                    ui.next?.let { n -> item(key = "next") { NextCard(n) { nav.push(EventDetailRoute(n.id)) } } }
                    item(key = "activeTitle") { BasicText(t(TextKey.EVENTS_ACTIVE, app.masroufy.core.sentenceNumber(ui.active.size)), style = Type.section()) }
                    items(ui.active, key = { it.id }) { e -> EventCard(e) { nav.push(EventDetailRoute(e.id)) } }
                    if (ui.archived.isNotEmpty()) {
                        item(key = "archTitle") { ArchivedToggle(t(TextKey.EVENTS_ARCHIVED, app.masroufy.core.sentenceNumber(ui.archived.size)), archivedOpen) { archivedOpen = !archivedOpen } }
                        if (archivedOpen) item(key = "archived") { ArchivedList(ui.archived) { nav.push(EventDetailRoute(it)) } }
                    }
                }
            }
        }
        item(key = "add") { PrimaryButton(t(TextKey.EVENTS_NEW), onClick = { nav.open(EventAddSheetRoute()) }, leading = Lucide.PLUS, height = 52.dp, modifier = Modifier.fillMaxWidth()) }
    }
}

/** رمز ولون النوع (النموذج: الفرح والخطوبة جوهرة وردي · السفر كرة أزرق · العزا ناس رمادي · العيد هلال كهرماني …). */
internal fun kindIcon(k: LifeEventKind): Pair<Lucide, Color> = when (k) {
    LifeEventKind.WEDDING, LifeEventKind.ENGAGEMENT -> Lucide.GEM to CategoryInk.gifts
    LifeEventKind.TRAVEL -> Lucide.GLOBE to Ink.transfer
    LifeEventKind.MEDICAL -> Lucide.SPARKLES to Ink.transfer
    LifeEventKind.SCHOOL -> Lucide.SPARKLES to Ink.focus
    LifeEventKind.BIRTH -> Lucide.SPARKLES to Ink.income
    LifeEventKind.CONDOLENCE -> Lucide.USERS to Ink.muted
    LifeEventKind.EID -> Lucide.MOON to Ink.focus
    LifeEventKind.OTHER -> Lucide.SPARKLES to Ink.muted
}

@Composable
private fun NextCard(n: NextEventUi, onClick: () -> Unit) {
    val press = rememberPress()
    HeroCard(Modifier.fillMaxWidth().pressScale(press).tap(press, label = n.name, onClick = onClick), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        BasicText(t(TextKey.EVENTS_NEXT), style = Type.caption().copy(color = Ink.onHeroMuted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(n.name, Modifier.weight(1f), style = Type.of(18, FontWeight.Bold).copy(color = Ink.onPrimary))
            BasicText(n.rel, style = Type.of(13, FontWeight.Bold).copy(color = Ink.mint))
        }
    }
}

@Composable
internal fun OwnerPill(text: String, mine: Boolean) {
    if (mine) Pill(text, Ink.primary, Ink.selected) else Pill(text, Ink.transfer, PeopleInk.transferSoft)
}

@Composable
private fun EventCard(e: EventCardUi, onClick: () -> Unit) {
    FloatingCard(onClick = onClick, clickLabel = e.name) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, ink) = kindIcon(e.kind)
            IconTile(ink, size = 44.dp, radius = 14.dp) { LucideIcon(icon, size = 22.dp, tint = ink) }
            Column(Modifier.weight(1f)) {
                BasicText(e.name, style = Type.of(15, FontWeight.Bold))
                BasicText(e.meta, style = Type.caption().copy(color = Ink.muted))
            }
            OwnerPill(e.owner, e.mine)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in e.stats) StatCell(s, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun StatCell(s: EventStat, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(s.label, style = Type.caption().copy(color = Ink.muted))
        if (s.amounts.isEmpty()) BasicText(s.emptyText.orEmpty(), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        else s.amounts.forEach { AmountText(it.minor, it.currency, size = 14, tone = s.tone, color = if (s.tone == app.masroufy.ui.components.AmountTone.PLAIN) Ink.text else null) }
        s.sub?.let { BasicText(it, style = Type.of(11).copy(color = Ink.muted)) }
    }
}

@Composable
private fun ArchivedToggle(title: String, open: Boolean, onToggle: () -> Unit) {
    val press = rememberPress()
    val turn by animateFloatAsState(if (open) 180f else 0f, motion(Springs.SNAPPY), label = "chev")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(press).tap(press, label = title, onClick = onToggle),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, style = Type.of(15, FontWeight.Bold).copy(color = Ink.muted))
        LucideIcon(Lucide.CHEVRON_DOWN, size = 18.dp, tint = Ink.muted, modifier = Modifier.graphicsLayer { rotationZ = turn })
    }
}

@Composable
private fun ArchivedList(items: List<EventCardUi>, open: (String) -> Unit) {
    FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        items.forEachIndexed { i, e ->
            val press = rememberPress()
            Rowed(i == items.lastIndex, Modifier.pressScale(press).tap(press, label = e.name, onClick = { open(e.id) }), minHeight = 60.dp) {
                Column(Modifier.weight(1f)) {
                    BasicText(e.name, style = Type.bodyBold())
                    BasicText(e.meta, style = Type.caption().copy(color = Ink.muted))
                }
                val last = e.stats.last()
                Column(horizontalAlignment = Alignment.End) {
                    if (last.amounts.isEmpty()) BasicText(last.emptyText.orEmpty(), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted, textAlign = TextAlign.End))
                    else last.amounts.forEach { AmountText(it.minor, it.currency, size = 14, tone = last.tone) }
                    BasicText(last.label, style = Type.of(11).copy(color = Ink.muted))
                }
            }
        }
    }
}
