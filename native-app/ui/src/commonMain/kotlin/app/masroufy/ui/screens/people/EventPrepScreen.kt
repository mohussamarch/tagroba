package app.masroufy.ui.screens.people

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.prepAllowed
import app.masroufy.core.sentenceNumber
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.HeroCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.CategoryInk
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * تجهيزات الحدث الجاي (لوحة `EventPrep` — §65): الملخص (اتصرف · المخطط أو «غير متاح» · اللي فاضل · المصروف اللي مش على بند) ·
 * بنود مقترحة حسب النوع **من غير مبالغ** (لما مفيش بنود) · البنود («خلص» · مبلغ اختياري · شيل) · توزيع مصروف على بند.
 * العزاء والحدث اللي عدّى ⇒ شاشة فاضية بالسبب.
 */
@Composable
internal fun EventPrepScreen(eventId: String) {
    val deps = LocalSpace.current
    val version = PeopleChanges.version
    var retry by remember { mutableStateOf(0) }
    var load by remember(deps, eventId) { mutableStateOf<Load<PrepUi>>(Load.Loading) }
    LaunchedEffect(deps, eventId, version, retry) {
        load = loadOf {
            val p = deps.people
            val today = deps.shell.today()
            val detail = p.events.detail(eventId)
            val ok = prepAllowed(detail.event)
            val summary = if (ok) p.prep.summary(eventId, deps.space.currency) else null
            val sugs = if (ok) runCatching { p.prep.suggestions(eventId, today) }.getOrDefault(emptyList()) else emptyList()
            prepUi(detail, summary, sugs, deps.space.currency, today) { planned, spent -> p.money.usedTenthPercent(planned, spent) }
        }
    }
    val ui = (load as? Load.Ready)?.value
    InnerScaffold(ui?.let { t(UiKey.EVENT_PREP_TITLE, it.event.name) } ?: t(UiKey.EVENT_DETAIL_PREP)) {
        when (val l = load) {
            Load.Loading -> items(2) { Skeleton(Modifier.fillMaxWidth().height(if (it == 0) 130.dp else 220.dp)) }
            Load.Failed -> item { ErrorCard({ retry++ }, title = t(UiKey.PPL_LOAD_FAILED), body = "") }
            is Load.Ready -> prepBody(l.value)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.prepBody(ui: PrepUi) {
    item(key = "line") { Note(ui.eventLine) }
    when (ui.block) {
        PrepBlock.CONDOLENCE -> { item { EmptyState(t(UiKey.EVENT_PREP_BLOCK_CONDOLENCE_TITLE), t(UiKey.EVENT_PREP_BLOCK_CONDOLENCE_BODY)) }; return }
        PrepBlock.PAST -> { item { EmptyState(t(UiKey.EVENT_PREP_BLOCK_PAST_TITLE), t(UiKey.EVENT_DETAIL_NO_PREP_PAST)) }; return }
        null -> Unit
    }
    item(key = "hero") { PrepHero(ui) }
    if (ui.suggestions.isNotEmpty()) item(key = "sugs") { Suggestions(ui) }
    if (ui.items.isNotEmpty()) item(key = "items") { Items(ui) }
    item(key = "new") { NewItem(ui) }
    if (ui.loose.isNotEmpty()) item(key = "loose") { Loose(ui) }
}

@Composable
private fun PrepHero(ui: PrepUi) {
    HeroCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                BasicText(t(UiKey.EVENT_PREP_SPENT), style = Type.caption().copy(color = Ink.onHeroMuted))
                AmountText(ui.spent?.minor, ui.spent?.currency ?: ui.currency, size = 22, color = Ink.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                BasicText(t(UiKey.EVENT_PREP_PLANNED), style = Type.caption().copy(color = Ink.onHeroMuted))
                if (ui.planned == null) BasicText(t(TextKey.NOT_AVAILABLE), Modifier.padding(top = 4.dp), style = Type.of(16, FontWeight.Bold).copy(color = Ink.onHeroMuted))
                else AmountText(ui.planned.minor, ui.planned.currency, size = 22, color = Ink.onPrimary)
                BasicText(ui.plannedSub, style = Type.of(11).copy(color = Ink.onHeroMuted))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ui.leftLine?.let { BasicText(it, style = Type.caption().copy(color = Ink.onHeroMuted)) }
            ui.looseLine?.let { BasicText(it, style = Type.caption().copy(color = Ink.onHeroMuted)) }
        }
        ui.otherCurrencyLine?.let { BasicText(it, style = Type.of(11).copy(color = Ink.onHeroMuted)) }
    }
}

@Composable
private fun Suggestions(ui: PrepUi) {
    val deps = LocalSpace.current.people
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var on by remember(ui.suggestions) { mutableStateOf(setOf<String>()) }
    var err by remember { mutableStateOf<String?>(null) }
    FloatingCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(t(UiKey.EVENT_PREP_SUG_TITLE), style = Type.bodyBold())
            Note(t(UiKey.EVENT_PREP_SUG_BODY))
            ChoiceFlow { for (s in ui.suggestions) Choice(s, s in on, { on = if (s in on) on - s else on + s }) }
            ErrorLine(err)
            val chosen = ui.suggestions.filter { it in on }
            PrimaryButton(
                if (chosen.isEmpty()) t(UiKey.EVENT_PREP_SUG_PICK) else t(UiKey.EVENT_PREP_SUG_SAVE, countOf(chosen.size, Noun.ITEMS_OBJ)),
                enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        runCatching { deps.prep.addMany(ui.event.id, chosen) }
                            .onSuccess { PeopleChanges.bump(); toaster.show(t(UiKey.EVENT_PREP_SUG_SAVED, countOf(chosen.size, Noun.ITEMS))) }
                            .onFailure { err = it.message }
                    }
                },
            )
        }
    }
}

@Composable
private fun Items(ui: PrepUi) {
    var editing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.EVENT_PREP_ITEMS, sentenceNumber(ui.items.size)), style = Type.section())
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            ui.items.forEachIndexed { i, it -> ItemRow(it, i == ui.items.lastIndex, editing == it.item.id) { editing = if (editing == it.item.id) null else it.item.id } }
        }
    }
}

@Composable
private fun ItemRow(row: PrepItemUi, last: Boolean, editing: Boolean, toggleEdit: () -> Unit) {
    val space = LocalSpace.current
    val deps = space.people
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var amount by remember(editing) { mutableStateOf(row.item.plannedMinor?.let { app.masroufy.ui.text.amount(it, space.space.currency).replace(",", "") } ?: "") }
    var err by remember(editing) { mutableStateOf<String?>(null) }
    val item = row.item
    fun act(block: suspend () -> Unit) = scope.launch { runCatching { block() }.onSuccess { PeopleChanges.bump() }.onFailure { err = it.message } }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val press = rememberPress()
            Box(
                Modifier.size(44.dp).pressScale(press).clip(RoundedCornerShape(14.dp)).background(if (item.done) Ink.primary else Ink.onPrimary)
                    .tap(press, label = t(UiKey.EVENT_PREP_DONE_LABEL, item.name), role = androidx.compose.ui.semantics.Role.Checkbox, onClick = { act { deps.prep.setDone(item.id, !item.done) } }),
                contentAlignment = Alignment.Center,
            ) { if (item.done) LucideIcon(Lucide.CHECK, size = 20.dp, tint = Ink.onPrimary) else Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(PeopleInk.tonal)) }
            val p2 = rememberPress()
            Column(Modifier.weight(1f).pressScale(p2).tap(p2, label = item.name, onClick = toggleEdit), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(item.name, style = Type.of(15, FontWeight.Bold).copy(color = if (item.done) Ink.muted else Ink.text, textDecoration = if (item.done) TextDecoration.LineThrough else null))
                BasicText(row.sub, style = Type.caption().copy(color = if (row.over) Ink.expense else Ink.muted))
                row.usedTenth?.let { used ->
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PeopleInk.chip)) {
                        Box(Modifier.fillMaxWidth(used.coerceIn(0, 1000) / 1000f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (row.over) Ink.expense else CategoryInk.savings))
                    }
                }
            }
        }
        if (editing) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberInput(amount, { amount = it; err = null }, Modifier.weight(1f), placeholder = t(UiKey.EVENT_PREP_PLANNED_INPUT), currency = space.space.currency)
                    PrimaryButton(t(UiKey.PPL_SAVE), onClick = {
                        val v = tryParseMoney(amount, space.space.currency)
                        if (v == null || v <= 0) err = t(UiKey.EVENT_PREP_AMOUNT_ERR) else act { deps.prep.update(item.id, item.name, v) }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalButton(t(UiKey.EVENT_PREP_NO_AMOUNT), onClick = { act { deps.prep.update(item.id, item.name, null) } }, Modifier.weight(1f))
                    TonalButton(t(UiKey.EVENT_PREP_REMOVE), onClick = {
                        act {
                            deps.prep.remove(item.id)
                            toaster.show(t(if (row.spentMinor > 0) UiKey.EVENT_PREP_REMOVED_SPENT else UiKey.EVENT_PREP_REMOVED))
                        }
                    }, Modifier.weight(1f))
                }
                ErrorLine(err)
            }
        }
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
}

@Composable
private fun NewItem(ui: PrepUi) {
    val deps = LocalSpace.current.people
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(name, { name = it.take(60); err = null }, Modifier.weight(1f), placeholder = t(UiKey.EVENT_PREP_NEW))
            PrimaryButton(t(UiKey.EVENT_PREP_ADD), onClick = {
                scope.launch {
                    runCatching { deps.prep.add(ui.event.id, name) }.onSuccess { name = ""; PeopleChanges.bump() }.onFailure { err = it.message }
                }
            })
        }
        ErrorLine(err)
    }
}

@Composable
private fun Loose(ui: PrepUi) {
    val deps = LocalSpace.current.people
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.EVENT_PREP_LOOSE_TITLE), style = Type.section())
        for (l in ui.loose) {
            FloatingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(l.name, style = Type.bodyBold())
                        BasicText(l.sub, style = Type.caption().copy(color = Ink.muted))
                    }
                    AmountText(l.amountMinor, l.currency, tone = AmountTone.EXPENSE)
                }
                BasicText(t(UiKey.EVENT_PREP_ASSIGN), Modifier.padding(top = 8.dp, bottom = 6.dp), style = Type.captionBold())
                ChoiceFlow {
                    for (row in ui.items) Choice(row.item.name, false, {
                        scope.launch {
                            runCatching { deps.prep.assignSpend(ui.event.id, l.txnId, row.item.id) }
                                .onSuccess { PeopleChanges.bump(); toaster.show(t(UiKey.EVENT_PREP_ASSIGNED, row.item.name)) }
                                .onFailure { e -> toaster.show(e.message ?: "") }
                        }
                    }, textSize = 13)
                }
            }
        }
    }
}
