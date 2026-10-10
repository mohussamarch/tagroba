package app.masroufy.ui.screens.operations

import app.masroufy.core.UiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.ObligationKind
import app.masroufy.core.Person
import app.masroufy.core.TextKey
import app.masroufy.core.currencySymbol
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.tabular
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.PersonRow
import kotlinx.coroutines.launch

/**
 * «شخص» تحت «اربطها بـ» + لوحته (`LinkPersonSheet`): في الشراء تقسّم جزء من المبلغ على شخص — دين عليه أو هدية له (`ManagePeople.linkToPerson`).
 * ⚠️ الحوالة لشخص (سلفة · دعم · سداد · الوارد بأنواعه — §75-5) مالهاش حالة استخدام بخطوة واحدة لسه ⇒ الكارت مقفول بسببه.
 * رجل التحويل لنفسك بين بلدين مقفولة دايمًا.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkPersonCard(v: DetailView, locked: Boolean, openInitially: Boolean) {
    val deps = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    val blocked = locked || v.flow != LinkFlow.PURCHASE
    var open by remember { mutableStateOf(openInitially && !blocked) }
    var rows by remember(v.id) { mutableStateOf<List<PersonRow>>(emptyList()) }
    var added by remember(v.id) { mutableStateOf<List<PersonLinkLine>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(v.id, reload) { rows = attempt { deps.people.listWithBalances() }.orEmpty() }
    val links = existingPersonLinks(rows, v.id) + added
    val sub = when {
        locked -> t(UiKey.LINK_PERSON_LOCKED)
        v.flow != LinkFlow.PURCHASE -> t(UiKey.LINK_PERSON_TRANSFER_SOON)
        links.isEmpty() -> t(UiKey.LINK_PERSON_SUB)
        else -> t(UiKey.LINK_PERSON_SUB_MORE)
    }
    LinkCard(Lucide.USERS, t(UiKey.LINK_PERSON_CARD), sub, if (links.isEmpty()) t(UiKey.LINK_PERSON_OPEN) else t(UiKey.LINK_PERSON_ANOTHER), enabled = !blocked, onOpen = { open = true }) {
        for (l in links) {
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(l.label, Modifier.weight(1f), style = Type.of(13, FontWeight.Bold))
                AmountText(l.amountMinor, l.currency, tone = l.tone, size = 14)
            }
        }
    }
    var person by remember { mutableStateOf<Person?>(null) }
    var newName by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<PersonLinkKind?>(null) }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) { person = null; newName = null; query = ""; kind = null; amountText = ""; error = null } }
    val people = rows.map { it.person }
    val amount = tryParseMoney(amountText, v.currency)
    val who = newName ?: person?.name
    Sheet(open, { open = false }, title = t(UiKey.LINK_PERSON_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(t(UiKey.LINK_PERSON_TITLE), style = Type.of(17, FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(v.title, style = Type.of(13).copy(color = Ink.muted))
                AmountText(v.amountMinor, v.currency, tone = v.tone, size = 13)
            }
        }
        FieldLabel(t(UiKey.LINK_PERSON_WHO))
        TextInput(query, { query = it; error = null }, placeholder = t(UiKey.LINK_PERSON_SEARCH))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            newName?.let { n -> SelectChip(n, true, {}, height = 44.dp) }
            for (p in personChoices(people, query, person)) SelectChip(p.name, newName == null && person?.id == p.id, { person = p; newName = null; error = null }, height = 44.dp)
            if (canAddPerson(people, query)) SelectChip(t(UiKey.LINK_PERSON_ADD_NEW, query.trim()), false, { newName = query.trim(); person = null; query = ""; error = null }, height = 44.dp)
        }
        if (who != null) {
            val line = if (newName != null) t(UiKey.LINK_PERSON_WILL_ADD) else personBalanceLine(rows.firstOrNull { it.person.id == person?.id }, v.currency)
            BasicText(line, style = Type.caption().copy(color = Ink.muted))
        }
        FieldLabel(t(UiKey.LINK_PERSON_KIND))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(t(UiKey.LINK_PERSON_KIND_RECEIVABLE), kind == PersonLinkKind.RECEIVABLE, { kind = PersonLinkKind.RECEIVABLE; error = null }, height = 44.dp)
            SelectChip(t(UiKey.LINK_PERSON_KIND_GIFT), kind == PersonLinkKind.GIFT, { kind = PersonLinkKind.GIFT; error = null }, height = 44.dp)
        }
        BasicText(
            t(when (kind) { PersonLinkKind.RECEIVABLE -> UiKey.LINK_PERSON_NOTE_RECEIVABLE; PersonLinkKind.GIFT -> UiKey.LINK_PERSON_NOTE_GIFT; null -> UiKey.LINK_PERSON_NOTE_NONE }),
            style = Type.of(12).copy(color = Ink.muted),
        )
        TextInput(
            amountText, { amountText = it; error = null }, label = t(UiKey.LINK_PERSON_AMOUNT), placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal,
            height = 56.dp, textSize = 22,
            trailing = { BasicText(currencySymbol(v.currency), Modifier.padding(end = 16.dp), style = Type.of(14).copy(color = Ink.muted)) },
        )
        EffectBox(personEffect(kind, who, amount, v.currency))
        error?.let { FieldError(it) }
        PrimaryButton(t(UiKey.LINK_PERSON_SAVE), loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
            val k = kind
            error = personLinkError(who != null, k, amount)
            if (error != null || k == null || amount == null || saving) return@PrimaryButton
            saving = true
            scope.launch {
                val err = failureOf {
                    val p = person ?: deps.people.addPerson(newName.orEmpty())
                    deps.people.linkToPerson(v.id, p.id, ObligationKind.RECEIVABLE, amount, asGift = k == PersonLinkKind.GIFT)
                    if (k == PersonLinkKind.GIFT) added = added + PersonLinkLine(t(UiKey.LINK_PERSON_LINE, p.name, t(UiKey.LINK_PERSON_KIND_GIFT)), amount, v.currency, app.masroufy.ui.components.AmountTone.EXPENSE)
                }
                saving = false
                if (err != null) error = err else {
                    open = false
                    reload++
                }
            }
        })
    }
}

/** صندوق «الأثر على أرقامك» (أخضر خفيف). */
@Composable
internal fun EffectBox(lines: List<EffectLine>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0F08634F)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(t(UiKey.LINK_PERSON_EFFECT_TITLE), style = Type.of(12, FontWeight.Bold).copy(color = Ink.primary))
        for (l in lines) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(l.label, Modifier.weight(1f), style = Type.of(13))
            val style = Type.of(13, FontWeight.Bold).copy(color = l.tone?.color ?: Ink.muted)
            BasicText(l.value, style = if (l.tone != null) style.tabular() else style)
        }
    }
}
