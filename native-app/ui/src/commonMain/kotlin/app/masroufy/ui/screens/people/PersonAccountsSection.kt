package app.masroufy.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.AutoMeaning
import app.masroufy.core.Id
import app.masroufy.core.TransferParty
import app.masroufy.core.UiKey
import app.masroufy.ui.components.Divider
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.operations.UndoStrip
import app.masroufy.ui.screens.operations.autoLabel
import app.masroufy.ui.screens.operations.undoWindowFor
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ManagePersonLinks
import kotlinx.coroutines.launch

/**
 * «الحسابات المربوطة» في صفحة الشخص (المالك 2026-10-16: «عايز أشوف إيه الحساب اللي مربوط بالشخص وأتحكم فيه»): كل حساب باسمه زي ما جه وآخر 4 ·
 * «شيل الربط» (بتراجع 8 ثواني) · «سجّل تحويلاته لوحده» بمعنى تختاره (مستحقات · سلفة · هدية · دعم · حاجة تانية) · «أضف حساب» بآخر 4 أرقام والبنك.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonAccountsCard(personId: Id, personName: String, accounts: List<TransferParty>, links: ManagePersonLinks, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var removed by remember { mutableStateOf<TransferParty?>(null) }
    val window = undoWindowFor(removed) { removed = null }
    var picking by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    fun run(done: String?, block: suspend () -> Unit) = scope.launch {
        val err = runCatching { block() }.exceptionOrNull()?.message
        (err ?: done)?.let { toaster.show(it) }
        PeopleChanges.bump()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(t(UiKey.PLINK_ACC_TITLE), style = Type.section())
        removed?.let { p ->
            UndoStrip(t(UiKey.PLINK_ACC_REMOVED, personName), window) { removed = null; run(null) { links.accounts.restore(p) } }
        }
        FloatingCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (accounts.isEmpty()) BasicText(t(UiKey.PLINK_ACC_EMPTY), Modifier.padding(vertical = 8.dp), style = Type.body().copy(color = Ink.muted))
            accounts.forEachIndexed { i, p ->
                if (i > 0) Divider()
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(p.label, style = Type.bodyBold())
                            p.last4?.let { BasicText("••••$it", style = Type.caption().copy(color = Ink.muted)) }
                        }
                        SecondaryButton(t(UiKey.PLINK_ACC_REMOVE), onClick = { run(null) { links.accounts.remove(personId, p.key)?.let { removed = it } } }, height = 40.dp)
                    }
                    val auto = p.autoMeaning
                    ToggleSwitch(auto != null, t(UiKey.PLINK_ACC_AUTO), onToggle = {
                        if (auto != null) run(t(UiKey.PLINK_ACC_AUTO_OFF)) { links.accounts.setAuto(p.key, null) } else picking = p.key
                    })
                    if (auto != null) BasicText(autoLabel(auto), style = Type.caption().copy(color = Ink.primary))
                    if (picking == p.key) {
                        BasicText(t(UiKey.PLINK_ACC_AUTO_PICK), style = Type.caption().copy(color = Ink.muted))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (a in AutoMeaning.entries) SelectChip(autoLabel(a), false, {
                                picking = null
                                run(t(UiKey.PLINK_ACC_AUTO_ON, autoLabel(a))) { links.accounts.setAuto(p.key, a); links.accounts.applyAuto() }
                            }, height = 40.dp)
                        }
                    }
                }
            }
        }
        SecondaryButton(t(UiKey.PLINK_ACC_ADD), onClick = { adding = true }, modifier = Modifier.fillMaxWidth())
    }
    AddAccountSheet(adding, onDismiss = { adding = false }) { last4, bank ->
        val err = runCatching { links.accounts.addManual(personId, last4, bank) }.exceptionOrNull()?.message
        if (err == null) { adding = false; toaster.show(t(UiKey.PLINK_ACC_ADDED)); PeopleChanges.bump() }
        err
    }
}

@Composable
private fun AddAccountSheet(open: Boolean, onDismiss: () -> Unit, onSave: suspend (String, String) -> String?) {
    val scope = rememberCoroutineScope()
    var last4 by remember(open) { mutableStateOf("") }
    var bank by remember(open) { mutableStateOf("") }
    var error by remember(open) { mutableStateOf<String?>(null) }
    var saving by remember(open) { mutableStateOf(false) }
    Sheet(open, onDismiss, title = t(UiKey.PLINK_ACC_ADD), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        SheetHeading(t(UiKey.PLINK_ACC_ADD))
        TextInput(last4, { last4 = it.take(4); error = null }, label = t(UiKey.PLINK_ACC_LAST4), placeholder = "1234", ltr = true, keyboard = KeyboardType.Number)
        TextInput(bank, { bank = it; error = null }, label = t(UiKey.PLINK_ACC_BANK))
        ErrorLine(error)
        PrimaryButton(t(UiKey.PLINK_ACC_SAVE), loading = saving, modifier = Modifier.fillMaxWidth(), onClick = {
            if (saving) return@PrimaryButton
            saving = true
            scope.launch { error = onSave(last4, bank); saving = false }
        })
    }
}
