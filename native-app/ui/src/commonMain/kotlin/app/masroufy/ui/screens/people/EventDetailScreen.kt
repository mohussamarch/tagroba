package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.LifeEventKind
import app.masroufy.core.Person
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.core.ownEventOccasionId
import app.masroufy.core.prepAllowed
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Badge
import app.masroufy.ui.components.BadgeKind
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.mirrorInLtr
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.EventDetail
import kotlinx.coroutines.launch

/**
 * تفاصيل الحدث (لوحة `EventDetail`): البطاقة (النوع والتاريخ · صاحبه · المصروف · النقوط للمعلومية) · المصروف بنصيب كل عملية ·
 * النقوط (اللي جاتلك في حدثك أو اللي نقّطته في حدث حد) · «ذكّرني به كل سنة» (حدثك) · التجهيزات (الجاي غير العزاء) · «تعديل الحدث».
 */
internal class EventScreenData(val ui: EventDetailUi, val detail: EventDetail, val wallets: List<Wallet>, val people: List<Person>)

@Composable
internal fun EventDetailScreen(eventId: String) {
    val deps = LocalSpace.current
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps, eventId) { mutableStateOf<Load<EventScreenData>>(Load.Loading) }
    LaunchedEffect(deps, eventId, version, retry) {
        load = loadOf {
            val p = deps.people
            val today = deps.shell.today()
            val d = p.events.detail(eventId)
            val wallets = runCatching { deps.shell.addOptions().wallets }.getOrDefault(emptyList())
            val people = runCatching { p.people.listWithBalances().map { it.person } }.getOrDefault(emptyList())
            val prep = if (prepAllowed(d.event)) runCatching { p.prep.items(eventId) }.getOrDefault(emptyList()) else emptyList()
            val reminder = if (d.event.mine) runCatching { p.occasions.forPerson(null, today).firstOrNull { it.occasion.sourceEventId == eventId }?.occasion }.getOrNull() else null
            EventScreenData(eventDetailUi(d, wallets.associate { it.id to it.name }, prep, reminder, today), d, wallets, people)
        }
    }
    val ready = (load as? Load.Ready)?.value
    InnerScaffold(
        ready?.ui?.event?.name ?: t(TextKey.EVENTS_TITLE),
        actions = { if (ready?.ui?.event?.archived == true) Badge(t(TextKey.PPL_ARCHIVED), BadgeKind.NOT_AVAILABLE) },
    ) {
        when (val l = load) {
            Load.Loading -> items(2) { Skeleton(Modifier.fillMaxWidth().height(if (it == 0) 150.dp else 220.dp), radius = if (it == 0) 28.dp else 22.dp) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(TextKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> {
                val data = l.value
                val ui = data.ui
                item(key = "hero") { EventHero(ui) }
                item(key = "spend") { SpendSection(data) }
                item(key = "gifts") { GiftSection(data) }
                if (ui.event.mine) item(key = "remind") { ReminderCard(ui) }
                item(key = "prep") { PrepEntry(ui) }
                item(key = "edit") { EventEditButton(data) }
            }
        }
    }
}

@Composable
private fun EventHero(ui: EventDetailUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                BasicText(ui.kindLine, style = Type.of(15, FontWeight.Bold).copy(color = Ink.onPrimary))
                BasicText(ui.whenLine, style = Type.caption().copy(color = Ink.onHeroMuted))
            }
            Pill(ui.owner, Ink.onPrimary, Ink.onPrimary.copy(alpha = 0.16f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStat(t(TextKey.EVENT_DETAIL_SPEND), ui.spend, t(TextKey.EVENTS_NOT_LINKED), Ink.onPrimary, null, Modifier.weight(1f))
            val giftInk = if (ui.event.mine) Ink.mint else Ink.rose
            HeroStat(ui.giftLabel, ui.gifts, ui.giftEmpty, giftInk, if (ui.event.mine) "+" else "−", Modifier.weight(1f))
        }
        BasicText(ui.heroNote, Modifier.padding(top = 10.dp), style = Type.caption().copy(color = Ink.onHeroMuted))
    }
}

@Composable
private fun HeroStat(label: String, lines: List<MoneyLine>?, empty: String, ink: androidx.compose.ui.graphics.Color, sign: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(label, style = Type.caption().copy(color = Ink.onHeroMuted))
        if (lines == null) BasicText(empty, Modifier.padding(top = 6.dp), style = Type.of(14, FontWeight.Bold).copy(color = Ink.onHeroMuted))
        else lines.forEach {
            val tone = when (sign) { "+" -> app.masroufy.ui.components.AmountTone.INCOME; "−" -> app.masroufy.ui.components.AmountTone.EXPENSE; else -> app.masroufy.ui.components.AmountTone.PLAIN }
            AmountText(it.minor, it.currency, size = 22, tone = tone, color = ink)
        }
    }
}

@Composable
private fun LinkedRows(rows: List<LinkedRowUi>, empty: String, withInitial: Boolean) {
    FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        if (rows.isEmpty()) BasicText(empty, Modifier.padding(vertical = 16.dp), style = Type.body().copy(color = Ink.muted))
        rows.forEachIndexed { i, r ->
            Rowed(i == rows.lastIndex) {
                if (withInitial) InitialCircle(r.name, 36.dp)
                Column(Modifier.weight(1f)) {
                    BasicText(r.name, style = Type.bodyBold())
                    BasicText(r.sub, style = Type.caption().copy(color = Ink.muted))
                }
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(r.amountMinor, r.currency, tone = r.tone, showCurrency = withInitial)
                    r.share?.let { BasicText(it, style = Type.of(11).copy(color = Ink.muted)) }
                }
            }
        }
    }
}

@Composable
private fun SpendSection(data: EventScreenData) {
    val ui = data.ui
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val n = ui.spends.size
        BasicText(t(TextKey.EVENT_DETAIL_SPEND_TITLE) + if (n > 0) " (" + app.masroufy.core.sentenceNumber(n) + ")" else "", style = Type.section())
        LinkedRows(ui.spends, t(TextKey.EVENT_DETAIL_NO_SPEND), withInitial = false)
        SpendLinkButton(data)
    }
}

@Composable
private fun GiftSection(data: EventScreenData) {
    val ui = data.ui
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val n = ui.giftRows.size
        BasicText(ui.giftLabel + if (n > 0) " (" + app.masroufy.core.sentenceNumber(n) + ")" else "", style = Type.section())
        LinkedRows(ui.giftRows, t(if (ui.event.mine) TextKey.EVENT_DETAIL_NO_GIFTS_MINE else TextKey.EVENT_DETAIL_NO_GIFTS_OTHER), withInitial = true)
        NuqootButton(data)
    }
}

@Composable
private fun ReminderCard(ui: EventDetailUi) {
    val deps = LocalSpace.current.people
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val on = ui.reminder != null
    val lead = ui.reminder?.leadDays ?: OccasionDraft.DEFAULT_LEAD
    val title = t(if (ui.event.kind == LifeEventKind.WEDDING) TextKey.EVENT_DETAIL_REMIND_WEDDING else TextKey.EVENT_DETAIL_REMIND)
    fun set(enable: Boolean, days: Int) = scope.launch {
        val r = runCatching { if (enable) deps.occasions.remindOwnEvent(ui.event.id, days) else deps.occasions.remove(ownEventOccasionId(ui.event.id)) }
        PeopleChanges.bump()
        r.onSuccess { toaster.show(if (enable) t(TextKey.EVENT_DETAIL_REMIND_ON, leadName(days)) else t(TextKey.EVENT_DETAIL_REMIND_OFF)) }.onFailure { toaster.show(it.message ?: "") }
    }
    FloatingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = Type.bodyBold())
                BasicText(if (on) t(TextKey.EVENT_DETAIL_REMIND_SUB, leadName(lead)) else t(TextKey.PPL_SWITCH_OFF), style = Type.caption().copy(color = Ink.muted))
            }
            ToggleSwitch(on, title, { set(!on, lead) })
        }
        if (on) ChoiceFlow(Modifier.padding(top = 8.dp)) {
            for ((v, key) in listOf(1 to TextKey.OCC_LEAD_ONE, 7 to TextKey.OCC_CHIP_WEEK, 14 to TextKey.OCC_CHIP_TWO_WEEKS, 30 to TextKey.OCC_CHIP_MONTH)) {
                Choice(t(key), lead == v, { set(true, v) }, filled = true, textSize = 13)
            }
        }
    }
}

@Composable
private fun PrepEntry(ui: EventDetailUi) {
    val nav = LocalNavigator.current
    if (!ui.canPrep) {
        ui.noPrep?.let { Note(it) }
        return
    }
    FloatingCard(onClick = { nav.push(EventPrepRoute(ui.event.id)) }, clickLabel = t(TextKey.EVENT_DETAIL_PREP)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(t(TextKey.EVENT_DETAIL_PREP), style = Type.bodyBold())
                BasicText(ui.prepLine, style = Type.caption().copy(color = Ink.muted))
            }
            LucideIcon(Lucide.CHEVRON_LEFT, size = 18.dp, tint = Ink.muted, modifier = Modifier.mirrorInLtr())
        }
    }
}
