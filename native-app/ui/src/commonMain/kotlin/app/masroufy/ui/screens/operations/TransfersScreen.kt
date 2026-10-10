package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Person
import app.masroufy.core.TextKey
import app.masroufy.core.TransferPartyRef
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.EmptyState
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.Skeleton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.common.InnerScaffold
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * زون التحويلات (`Transfers`): لكل طرف (الاسم وآخر ٤ أرقام بس) بكل عملة لوحدها — العدد · آخرها · الداخل والخارج · القرار. «بانتظار ردك» للأطراف اللي
 * ليها ٥ تحويلات أو أكتر في شهر. الأفعال: «حسابي الآخر» (`markOwnAccount` — القديم والجديد) · «شخص» (`markPerson` بشخص جديد أو من أشخاصك)
 * · «ليس هذا» (`dismiss`) · «إلغاء القرار» (`forget`). التحويلات اللي مالهاش طرف بتتعد بس، من غير تخمين.
 */
@Composable
fun TransfersScreen() {
    val deps = LocalSpace.current.operations
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var view by remember(deps) { mutableStateOf<TransfersView?>(null) }
    var failed by remember { mutableStateOf(false) }
    var people by remember(deps) { mutableStateOf<List<Person>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var personFor by remember { mutableStateOf<TransferPartyRef?>(null) }
    LaunchedEffect(deps, reload) {
        people = attempt { deps.people.listWithBalances().map { it.person } }.orEmpty()
        val zone = attempt { deps.transfers.zone() }
        failed = zone == null
        view = zone?.let { z -> transfersView(z, people.associate { it.id to it.name }) }
        if (expanded == null) expanded = view?.sections?.firstOrNull { it.first == PartySection.WAITING }?.second?.firstOrNull()?.ref?.key
    }
    fun act(done: (Int) -> String, block: suspend () -> Int) = scope.launch {
        var n = 0
        val err = failureOf { n = block() }
        toaster.show(err ?: done(n))
        if (err == null) { expanded = null; personFor = null; reload++ }
    }
    InnerScaffold(t(TextKey.TRANSFERS_TITLE)) {
        item(key = "intro") { BasicText(t(TextKey.TRANSFERS_INTRO), style = Type.of(13).copy(color = Ink.muted)) }
        val v = view
        when {
            failed -> item(key = "error") { ErrorBanner(t(TextKey.TRANSFERS_ERROR), t(TextKey.OPERATIONS_ERROR_BODY), t(TextKey.SHELL_RETRY), { reload++ }) }
            v == null -> item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { Skeleton(Modifier.fillMaxWidth().height(84.dp), radius = 20.dp) } }
            }
            v.empty -> item(key = "empty") { EmptyState(t(TextKey.TRANSFERS_EMPTY), t(TextKey.TRANSFERS_EMPTY_BODY)) }
            else -> for ((section, rows) in v.sections) {
                item(key = "title-$section") { BasicText(sectionTitle(section), style = Type.of(15, FontWeight.Bold)) }
                for (r in rows) item(key = "party-${r.ref.key}-${r.currency}") {
                    PartyCard(r, expanded == r.ref.key, onToggle = { expanded = if (expanded == r.ref.key) null else r.ref.key }) {
                        if (r.verdict == null) {
                            SmallAction(t(TextKey.TRANSFER_PARTY_OWN)) { act({ n -> t(TextKey.TRANSFERS_DONE_OWN, transfersCount(n)) }) { deps.transfers.markOwnAccount(r.ref) } }
                            SmallAction(t(TextKey.TRANSFERS_PERSON), strong = true) { personFor = r.ref }
                            SmallAction(t(TextKey.TRANSFER_PARTY_NOT)) { act({ t(TextKey.TRANSFERS_DONE_NOT) }) { deps.transfers.dismiss(r.ref); 0 } }
                        } else {
                            SmallAction(t(TextKey.TRANSFERS_FORGET)) { act({ t(TextKey.TRANSFERS_DONE_FORGET) }) { deps.transfers.forget(r.ref.key) } }
                        }
                    }
                }
            }
        }
        if (v != null && v.unidentified > 0) item(key = "no-party") { NoteBox(t(TextKey.TRANSFERS_NO_PARTY, transfersCount(v.unidentified))) }
    }
    PersonForPartySheet(personFor, people, onDismiss = { personFor = null }) { ref, who, newName ->
        act({ t(if (who == null) TextKey.TRANSFERS_DONE_PERSON_NEW else TextKey.TRANSFERS_DONE_PERSON, who?.name ?: newName) }) {
            val person = who ?: deps.people.addPerson(newName)
            deps.transfers.markPerson(ref, person.id)
        }
    }
}

/** كارت الطرف: الحرف الأول · الاسم و•••• آخر ٤ · العدد وآخرها · القرار · الداخل والخارج — الضغط بيفتح الأفعال. «بانتظار ردك» بحد كهرماني. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyCard(r: PartyRowView, open: Boolean, onToggle: () -> Unit, actions: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val press = rememberPress()
    val surface = if (r.section == PartySection.WAITING) Modifier.clip(shape).background(Color.White).insetRing(shape, 1.5.dp, Color(0x59956000))
    else Modifier.clip(shape).background(app.masroufy.ui.glass.Glass.card)
    Column(Modifier.fillMaxWidth().then(surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().pressScale(press).tap(press, onClick = onToggle), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Initial(r.ref.label, size = 40)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(r.ref.label, style = Type.of(15, FontWeight.Bold))
                    r.last4?.let { BasicText(t(TextKey.TRANSFERS_LAST4, it), style = Type.of(12).copy(color = Ink.muted)) }
                }
                BasicText(r.meta, style = Type.caption().copy(color = Ink.muted))
                StatusChip(r.chip.text, r.chip.ink, r.chip.background)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                r.incomingMinor?.let { AmountText(it, r.currency, tone = AmountTone.INCOME, size = 13) }
                r.outgoingMinor?.let { AmountText(it, r.currency, tone = AmountTone.EXPENSE, size = 13) }
            }
        }
        if (open) {
            app.masroufy.ui.components.Divider(Modifier.padding(top = 10.dp))
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
            BasicText(r.hint, Modifier.padding(top = 6.dp), style = Type.caption().copy(color = Ink.muted))
        }
    }
}

/** «مَن صاحب الحساب ٤٨٢١؟»: شخص جديد بالاسم أو واحد من أشخاصك ⇒ «اربط». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonForPartySheet(ref: TransferPartyRef?, people: List<Person>, onDismiss: () -> Unit, onSave: (TransferPartyRef, Person?, String) -> Unit) {
    var who by remember(ref) { mutableStateOf<Person?>(null) }
    var name by remember(ref) { mutableStateOf("") }
    val shown = ref
    val title = shown?.let { t(TextKey.TRANSFER_PARTY_WHO_TITLE, partyAccount(it)) }.orEmpty()
    Sheet(ref != null, onDismiss, title = title, closeLabel = t(TextKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(TextKey.TRANSFERS_PERSON_BODY), style = Type.of(13).copy(color = Ink.muted))
        TextInput(name, { name = it; if (it.isNotBlank()) who = null }, placeholder = t(TextKey.TRANSFERS_NEW_PERSON))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in people.filter { !it.archived }.take(8)) SelectChip(p.name, who?.id == p.id && name.isBlank(), { who = p; name = "" })
        }
        val ready = name.isNotBlank() || who != null
        if (!ready) FieldError(t(TextKey.LINK_PERSON_ERR_PERSON))
        PrimaryButton(t(TextKey.TRANSFERS_LINK), enabled = ready, modifier = Modifier.fillMaxWidth(), onClick = {
            if (shown != null && ready) onSave(shown, if (name.isNotBlank()) null else who, name.trim())
        })
    }
}
