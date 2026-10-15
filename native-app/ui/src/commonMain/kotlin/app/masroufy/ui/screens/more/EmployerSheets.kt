package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.ui.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.Id
import app.masroufy.core.IncomeSource
import app.masroufy.core.IncomingSender
import app.masroufy.core.UiKey
import app.masroufy.core.sentenceDigits
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.operations.attempt
import app.masroufy.ui.screens.operations.failureOf
import app.masroufy.ui.screens.operations.operationsCount
import app.masroufy.ui.screens.operations.partyAccount
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/** لوحة «جهة العمل» (إضافة لو [edit].source null، أو تعديل): الاسم · من · إلى ⇒ `ManageEmployers.save`. */
@Composable
fun EmployerEditSheet(edit: EmployerEdit?, onDismiss: () -> Unit, onSaved: (IncomeSource) -> Unit) {
    val employers = LocalSpace.current.more.employers ?: return
    val scope = rememberCoroutineScope()
    val source = edit?.source
    var name by remember(edit) { mutableStateOf(source?.name.orEmpty()) }
    var from by remember(edit) { mutableStateOf(source?.startedAt.orEmpty()) }
    var to by remember(edit) { mutableStateOf(source?.endedAt.orEmpty()) }
    var saving by remember(edit) { mutableStateOf(false) }
    var error by remember(edit) { mutableStateOf<String?>(null) }
    Sheet(edit != null, onDismiss, title = t(UiKey.EMPLOYER_EDIT_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(t(UiKey.EMPLOYER_EDIT_TITLE), style = Type.of(17, FontWeight.Bold))
        EmployerFields(name, { name = it; error = null }, from, { from = it; error = null }, to, { to = it; error = null })
        error?.let { FieldError(it) }
        PrimaryButton(t(UiKey.EMPLOYER_SAVE), loading = saving, height = 52.dp, modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                saving = true
                var saved: IncomeSource? = null
                error = failureOf { saved = employers.save(source?.id, employerInput(name, from, to)) }
                saving = false
                saved?.let(onSaved)
            }
        })
    }
}

/** «من حوّل إليك؟»: المحوِّلين من التحويلات الداخلة (الاسم وآخر ٤ أرقام) ⇒ `ManageEmployers.attach` للجهة [employerId]. */
@Composable
fun SenderPickSheet(employerId: Id?, onDismiss: () -> Unit, onAttached: (Id) -> Unit) {
    val employers = LocalSpace.current.more.employers ?: return
    val scope = rememberCoroutineScope()
    var senders by remember { mutableStateOf<List<IncomingSender>?>(null) }
    var error by remember(employerId) { mutableStateOf<String?>(null) }
    LaunchedEffect(employerId) {
        if (employerId == null) return@LaunchedEffect
        senders = null
        senders = attempt { employers.senders() }.orEmpty()
    }
    Sheet(employerId != null, onDismiss, title = t(UiKey.EMPLOYER_SENDERS_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(t(UiKey.EMPLOYER_SENDERS_TITLE), style = Type.of(17, FontWeight.Bold))
        val list = senders.orEmpty()
        if (senders != null && list.isEmpty()) BasicText(t(UiKey.EMPLOYER_SENDERS_EMPTY), style = Type.of(13).copy(color = Ink.muted))
        for (s in list) {
            val label = s.party.last4?.let { t(UiKey.EMPLOYER_ACCOUNT_LAST4, s.party.label, sentenceDigits(it)) } ?: partyAccount(s.party)
            FloatingCard(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), clickLabel = label,
                onClick = {
                    employerId?.let { id ->
                        scope.launch {
                            error = failureOf { employers.attach(id, s.party) }
                            if (error == null) onAttached(id)
                        }
                    }
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(label, style = Type.of(14, FontWeight.Bold))
                    BasicText(t(UiKey.EMPLOYER_SENDER_META, operationsCount(s.count), sentenceDigits(s.lastDate)), style = Type.caption().copy(color = Ink.muted))
                }
            }
        }
        error?.let { FieldError(it) }
    }
}
