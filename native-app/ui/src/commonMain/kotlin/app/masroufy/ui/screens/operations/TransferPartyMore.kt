package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.COMPANY_INCOMING_KINDS
import app.masroufy.core.EconomicKind
import app.masroufy.core.LABEL_DEFAULT_KIND
import app.masroufy.core.PARTY_NOTE_MAX
import app.masroufy.core.TransferPartyRef
import app.masroufy.core.UiKey
import app.masroufy.core.ruleFor
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.IncomeAnswers
import app.masroufy.usecase.PartyUndo

/**
 * ردود «مَن هذا؟» الجديدة (طلب المالك 2026-10-16) بعد القرار — لكل واحد «تراجع» بتاعه ([undo] ⇒ `TransferPartyAnswers.undo`).
 * [choices] في «مستحقات دايمًا» = مفيش حاجة اتسددت ⇒ «المبلغ ده إيه؟» مرة.
 */
sealed interface NewPartyDecision : PartyDecision {
    val undo: PartyUndo

    data class Company(val kind: EconomicKind, override val undo: PartyUndo) : NewPartyDecision

    data class Dues(val name: String, val settledCount: Int, val choices: List<EconomicKind>?, override val undo: PartyUndo) : NewPartyDecision

    data class Label(val text: String, override val undo: PartyUndo) : NewPartyDecision

    data class Ignored(override val undo: PartyUndo) : NewPartyDecision
}

/** العنوان والسطر بعد الردود الجديدة. */
fun newDecisionTexts(d: NewPartyDecision, party: TransferPartyRef): Pair<String, String> = when (d) {
    is NewPartyDecision.Company -> t(UiKey.TRANSFER_PARTY_DONE_COMPANY, party.label) to t(UiKey.TRANSFER_PARTY_DONE_COMPANY_BODY, ruleFor(d.kind).label)
    is NewPartyDecision.Dues -> t(UiKey.TRANSFER_PARTY_DONE_DUES, d.name) to
        (if (d.choices != null) t(UiKey.TRANSFER_PARTY_DUES_NOTHING, d.name) else t(UiKey.TRANSFER_PARTY_DONE_DUES_BODY, d.settledCount.toString()))
    is NewPartyDecision.Label -> t(UiKey.TRANSFER_PARTY_DONE_LABEL, d.text) to t(UiKey.TRANSFER_PARTY_DONE_LABEL_BODY, partyAccount(party), d.text)
    is NewPartyDecision.Ignored -> t(UiKey.TRANSFER_PARTY_DONE_NOT) to t(UiKey.TRANSFER_PARTY_DONE_NOT_BODY)
}

/** «شركة أو جهة»: نوع المبلغ من [COMPANY_INCOMING_KINDS] (مش «متجر جديد»). الاختيار بيحفظ على طول. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanyKindSheet(visible: Boolean, party: TransferPartyRef, error: String?, onDismiss: () -> Unit, onPick: (EconomicKind) -> Unit) {
    val title = t(UiKey.TRANSFER_PARTY_COMPANY_TITLE, party.label)
    Sheet(visible, onDismiss, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.TRANSFER_PARTY_COMPANY_NOTE), style = Type.of(13).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in COMPANY_INCOMING_KINDS) SelectChip(ruleFor(k).label, false, { onPick(k) }, height = 44.dp)
        }
        error?.let { FieldError(it) }
    }
}

/** «حاجة تانية (أكتبها)»: الاسم (لحد [PARTY_NOTE_MAX]) ونوع اختياري — من غير اختيار ⇒ [LABEL_DEFAULT_KIND]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelSheet(visible: Boolean, party: TransferPartyRef, error: String?, onDismiss: () -> Unit, onSave: (String, EconomicKind?) -> Unit) {
    var text by remember(party.key) { mutableStateOf("") }
    var kind by remember(party.key) { mutableStateOf<EconomicKind?>(null) }
    LaunchedEffect(visible) { if (visible) { text = ""; kind = null } }
    val title = t(UiKey.TRANSFER_PARTY_LABEL_TITLE)
    Sheet(visible, onDismiss, title = title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(partyAccount(party), style = Type.of(13).copy(color = Ink.muted))
        TextInput(text, { if (it.length <= PARTY_NOTE_MAX) text = it }, Modifier.fillMaxWidth(), placeholder = t(UiKey.TRANSFER_PARTY_LABEL_HINT))
        BasicText(t(UiKey.TRANSFER_PARTY_LABEL_KIND, ruleFor(LABEL_DEFAULT_KIND).label), style = Type.of(13, FontWeight.Bold).copy(color = Ink.muted))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in (COMPANY_INCOMING_KINDS + IncomeAnswers.OTHER_KINDS).distinct()) SelectChip(ruleFor(k).label, kind == k, { kind = if (kind == k) null else k })
        }
        error?.let { FieldError(it) }
        PrimaryButton(t(UiKey.TRANSFER_PARTY_LABEL_SAVE), onClick = { onSave(text, kind) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

/** «مفيش حاجة على فلان — المبلغ ده إيه؟» بعد «مستحقات دايمًا» (مرة واحدة — ما بنخترعش دين). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuesKindChips(choices: List<EconomicKind>, onPick: (EconomicKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in choices) SelectChip(ruleFor(k).label, false, { onPick(k) })
    }
}
