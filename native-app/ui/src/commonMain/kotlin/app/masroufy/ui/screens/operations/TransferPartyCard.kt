package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import app.masroufy.core.EconomicKind
import app.masroufy.core.INCOMING_FROM_PERSON_KINDS
import app.masroufy.core.Id
import app.masroufy.core.Person
import app.masroufy.core.PersonCircle
import app.masroufy.core.TextKey
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.ruleFor
import app.masroufy.core.sentenceDigits
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.mintSheen
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** قرار الطرف في الجلسة دي (للكارت بعد القرار و«تراجع»). */
sealed interface PartyDecision {
    data class AsPerson(val name: String, val kind: EconomicKind, val added: Boolean) : PartyDecision

    data class OwnAccount(val changed: Int) : PartyDecision

    data object NotThis : PartyDecision
}

/** «حساب ينتهي بـ ٤٨٢١» أو اسم الطرف لو مفيش أرقام. */
fun partyAccount(party: TransferPartyRef): String = party.last4?.let { t(TextKey.TRANSFER_PARTY_ACCOUNT, sentenceDigits(it)) } ?: party.label

/** العنوان والسطر بعد القرار. */
fun decisionTexts(d: PartyDecision, party: TransferPartyRef): Pair<String, String> = when (d) {
    is PartyDecision.AsPerson -> t(TextKey.TRANSFER_PARTY_DONE_PERSON, d.name, ruleFor(d.kind).label) to
        ((if (d.added) t(TextKey.TRANSFER_PARTY_ADDED, d.name) + " " else "") + t(TextKey.TRANSFER_PARTY_DONE_PERSON_BODY, partyAccount(party)))
    is PartyDecision.OwnAccount -> t(TextKey.TRANSFER_PARTY_DONE_OWN) to t(TextKey.TRANSFER_PARTY_DONE_OWN_BODY, operationsCount(d.changed))
    PartyDecision.NotThis -> t(TextKey.TRANSFER_PARTY_DONE_NOT) to t(TextKey.TRANSFER_PARTY_DONE_NOT_BODY)
}

/**
 * «مَن هذا؟» (`TransferParty` — جوه `OperationDetail`): حوالة داخلة من طرف لسه ما اتقررش. «شخص أعرفه» ⇒ لوحة (شخص جديد بالاسم من المصدر ودايرته،
 * أو واحد من أشخاصك ⇒ «ما نوع هذا المبلغ؟» من قايمة §42) ⇒ `ManageTransfers.markPerson` + `SetEconomicKind.setOne`. «حسابي الآخر» ⇒
 * `markOwnAccount` (القديم كمان). «ليس هذا» ⇒ `dismiss`. «تراجع» ⇒ `forget`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransferPartyCard(party: TransferPartyRef, transactionId: Id, onChanged: () -> Unit) {
    val deps = LocalSpace.current.operations
    val scope = rememberCoroutineScope()
    var decision by remember(party.key) { mutableStateOf<PartyDecision?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun act(block: suspend () -> PartyDecision?) = scope.launch {
        var d: PartyDecision? = null
        val err = failureOf { d = block() }
        error = err
        if (err == null && d != null) {
            decision = d
            sheet = false
            onChanged()
        }
    }
    val d = decision
    if (d == null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(Ink.alertBg).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(t(TextKey.TRANSFER_PARTY_ASK), style = Type.of(13, FontWeight.Bold).copy(color = Ink.focus))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(t(TextKey.TRANSFER_PARTY_NAME, party.label), style = Type.of(16, FontWeight.Bold))
                party.last4?.let { BasicText(partyAccount(party), style = Type.of(12).copy(color = PartyInk.brown)) }
            }
            PrimaryButton(t(TextKey.TRANSFER_PARTY_PERSON), onClick = { sheet = true; error = null }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartyButton(t(TextKey.TRANSFER_PARTY_OWN), Modifier.weight(1f)) { act { PartyDecision.OwnAccount(deps.transfers.markOwnAccount(party)) } }
                PartyButton(t(TextKey.TRANSFER_PARTY_NOT), Modifier.weight(1f)) { act { deps.transfers.dismiss(party); PartyDecision.NotThis } }
            }
            error?.let { FieldError(it) }
        }
    } else {
        val (bg, ink) = when (d) {
            is PartyDecision.AsPerson -> ChipInk.greenBg to ChipInk.green
            is PartyDecision.OwnAccount -> ChipInk.blueBg to ChipInk.blue
            PartyDecision.NotThis -> ChipInk.greyBg to ChipInk.grey
        }
        val (title, body) = decisionTexts(d, party)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(bg).mintSheen(true).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(title, style = Type.of(13, FontWeight.Bold).copy(color = ink))
            BasicText(body, style = Type.of(13))
            TonalButton(t(TextKey.TRANSFER_PARTY_UNDO), {
                scope.launch {
                    val err = failureOf { deps.transfers.forget(party.key) }
                    if (err == null) {
                        decision = null
                        onChanged()
                    } else error = err
                }
            }, height = 40.dp)
        }
    }
    PartySheet(sheet, party, error, onDismiss = { sheet = false }) { who, draft, kind ->
        act {
            val person = who ?: deps.people.addPerson(partyNewName(party, draft.name)).also { p -> draft.circle?.let { deps.circles.setProfile(p.id, it, null) } }
            deps.transfers.markPerson(party, person.id)
            deps.kinds.setOne(transactionId, kind)
            PartyDecision.AsPerson(person.name, kind, added = who == null)
        }
    }
}

/** الشخص الجديد: الاسم المكتوب، ولو فاضي اسم الطرف زي ما جه. */
fun partyNewName(party: TransferPartyRef, typed: String): String = typed.trim().ifEmpty { party.label }

/** اختيار «شخص جديد»: الاسم والدايرة. */
data class NewPersonDraft(val name: String, val circle: PersonCircle?)

private object PartyInk {
    val brown = Color(0xFF6B4600)
    val buttonBg = Color(0xB3FFFFFF)
}

@Composable
private fun PartyButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        modifier.height(44.dp).pressScale(press).clip(RoundedCornerShape(16.dp)).background(PartyInk.buttonBg).tap(press, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = Type.of(14, FontWeight.Bold).copy(color = PartyInk.brown)) }
}

/** لوحة «مَن صاحب الحساب؟» بخطوتين: الشخص ⇒ نوع المبلغ. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartySheet(
    visible: Boolean,
    party: TransferPartyRef,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (who: Person?, draft: NewPersonDraft, kind: EconomicKind) -> Unit,
) {
    val deps = LocalSpace.current.operations
    var step by remember { mutableStateOf(0) }
    var people by remember { mutableStateOf<List<Person>>(emptyList()) }
    var picked by remember { mutableStateOf<Person?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var name by remember(party.key) { mutableStateOf(party.label) }
    var circle by remember { mutableStateOf<PersonCircle?>(null) }
    var kind by remember { mutableStateOf<EconomicKind?>(null) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        step = 0; picked = null; isNew = false; kind = null; circle = null
        people = attempt { deps.people.listWithBalances().filter { !it.person.archived }.map { it.person } }.orEmpty()
    }
    val chosenName = if (isNew) partyNewName(party, name) else picked?.name.orEmpty()
    val title = if (step == 0) t(TextKey.TRANSFER_PARTY_WHO_TITLE, partyAccount(party)) else t(TextKey.TRANSFER_PARTY_KIND_TITLE, chosenName)
    Sheet(visible, onDismiss, title = title, closeLabel = t(TextKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        if (step == 0) {
            NewPersonBox(name, isNew, circle, onName = { name = it; isNew = true; picked = null }, onCircle = { circle = if (circle == it) null else it; isNew = true; picked = null })
            BasicText(t(TextKey.TRANSFER_PARTY_OR), style = Type.of(12, FontWeight.Bold).copy(color = Ink.muted))
            PeopleList(people.take(8), picked) { picked = it; isNew = false }
            PrimaryButton(t(TextKey.TRANSFER_PARTY_NEXT), onClick = { step = 1 }, enabled = picked != null || (isNew && chosenName.isNotBlank()), modifier = Modifier.fillMaxWidth())
        } else {
            BasicText(t(TextKey.TRANSFER_PARTY_KIND_Q), style = Type.of(14).copy(color = Ink.muted))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in INCOMING_FROM_PERSON_KINDS) SelectChip(ruleFor(k).label, kind == k, { kind = k })
            }
            error?.let { FieldError(it) }
            PrimaryButton(t(TextKey.TRANSFER_PARTY_SAVE), onClick = {
                val k = kind ?: return@PrimaryButton
                onSave(if (isNew) null else picked, NewPersonDraft(name, circle), k)
            }, enabled = kind != null, modifier = Modifier.fillMaxWidth())
        }
    }
}
