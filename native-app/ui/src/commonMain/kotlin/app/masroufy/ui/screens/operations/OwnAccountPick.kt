package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.screens.imports.RadioRow
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * «أنهي حساب من حساباتك؟» — لوحة اختيار محفظة واحدة: §120-8 («ده حسابي التاني» لطرف من غير أرقام) و§120-5 («اخصمها من حسابي التاني»).
 * [wallets] null ⇒ مقفولة.
 */
@Composable
internal fun OwnAccountPickSheet(wallets: List<Wallet>?, body: String, error: String?, onDismiss: () -> Unit, onSave: (Wallet) -> Unit) {
    var picked by remember(wallets) { mutableStateOf<Wallet?>(null) }
    val title = t(app.masroufy.core.R06Key.OWN_PICK_TITLE)
    Sheet(wallets != null, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE)) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(body, style = Type.of(13).copy(color = Ink.muted))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in wallets.orEmpty()) RadioRow(w.name, null, picked?.id == w.id, { picked = w })
        }
        error?.let { FieldError(it) }
        PrimaryButton(t(app.masroufy.core.R06Key.OWN_PICK_SAVE), { picked?.let(onSave) }, Modifier.fillMaxWidth(), enabled = picked != null)
    }
}

/**
 * OVERRIDES §120-5: فلوس داخلة مالهاش رِجل خارجة من حسابك التاني ⇒ «اخصمها من حسابي التاني» ⇒ اختيار المحفظة ⇒ `AddOwnLeg.add`.
 * §121-10: [outgoing] = تحويل خارج لحسابك التاني ⇒ «ضيفها لحسابي التاني» (رِجل داخلة على المحفظة المختارة).
 * الفعل بيظهر بس لو `AddOwnLeg.choices` مش فاضية (الرِجل مش موجودة والعملية داخلة).
 */
@Composable
internal fun OwnLegDebitAction(transactionId: Id, reload: Int, outgoing: Boolean = false, onChanged: () -> Unit) {
    val use = LocalSpace.current.operations.ownLeg ?: return
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var choices by remember(transactionId) { mutableStateOf<List<Wallet>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(transactionId, reload) { choices = runCatching { use.choices(transactionId) }.getOrDefault(emptyList()) }
    if (choices.isEmpty()) return
    // §121-10: الخارج ⇒ «ضيفها لحسابي التاني» (رِجل داخلة)
    TonalButton(t(if (outgoing) app.masroufy.core.R06Key.OWN_LEG_IN_ACTION else app.masroufy.core.R06Key.OWN_LEG_ACTION), { error = null; open = true }, Modifier.fillMaxWidth())
    OwnAccountPickSheet(if (open) choices else null, t(if (outgoing) app.masroufy.core.R06Key.OWN_LEG_IN_PICK_BODY else app.masroufy.core.R06Key.OWN_LEG_PICK_BODY), error, onDismiss = { open = false }) { w ->
        scope.launch {
            val err = failureOf { use.add(transactionId, w.id) }
            error = err
            if (err == null) {
                open = false
                toaster.show(t(if (outgoing) app.masroufy.core.R06Key.OWN_LEG_IN_DONE else app.masroufy.core.R06Key.OWN_LEG_DONE, w.name))
                onChanged()
            }
        }
    }
}

/**
 * §120-8: زرار «حسابي التاني» — طرف من غير أرقام ⇒ لوحة «أنهي حساب من حساباتك؟» ([pick] بالمحافظ)؛ ليه أرقام أو مفيش محافظ تتختار ⇒
 * القرار زي الأول ([direct]).
 */
internal fun ownTapped(
    party: app.masroufy.core.TransferPartyRef,
    transfers: app.masroufy.usecase.ManageTransfers,
    scope: kotlinx.coroutines.CoroutineScope,
    pick: (List<Wallet>) -> Unit,
    direct: () -> Unit,
) {
    if (!transfers.needsAccountChoice(party)) return direct()
    scope.launch {
        val choices = runCatching { transfers.accountChoices(party) }.getOrDefault(emptyList())
        if (choices.isEmpty()) direct() else pick(choices)
    }
}
