package app.masroufy.ui.screens.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.currencySymbol
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.overlay.Veil
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.OWN_LEG_SUFFIX
import kotlinx.coroutines.launch

/** المبلغ بالوحدة الصغرى كنص خانة («40.00») — حساب أعداد صحيحة بس. */
internal fun minorAsInput(minor: Long): String = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

/**
 * r06-links (خريطة الربط): «عدّل المبلغ والتاريخ» في تفاصيل العملية — لوحة فيها المبلغ والتاريخ (نفس خانة التاريخ `DateField`) والحساب.
 * كل خانة اتغيرت بتروح لحالة استخدامها (`EditTransaction.setAmount` · `setDate` · `setWallet`) والشاشة بتحمّل من جديد. الرِجل التلقائية
 * (§112-3) عليها سطر «اتسجلت لوحدها».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OperationCoreEdit(transaction: Transaction, onChanged: () -> Unit) {
    val space = LocalSpace.current
    val deps = space.operations
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var amount by remember(transaction) { mutableStateOf(minorAsInput(transaction.amountMinor)) }
    var date by remember(transaction) { mutableStateOf(transaction.occurredAt.take(10)) }
    var walletId by remember(transaction) { mutableStateOf(transaction.walletId) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) { if (open) wallets = runCatching { deps.wallets() }.getOrDefault(emptyList()).filter { it.currency == transaction.currency } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (transaction.id.endsWith(OWN_LEG_SUFFIX)) BasicText(t(R06Key.OWN_LEG_AUTO_NOTE), style = Type.of(12).copy(color = Ink.muted))
        TonalButton(t(R06Key.OP_EDIT_ACTION), { error = null; open = true }, Modifier.fillMaxWidth(), muted = true)
    }
    Sheet(open, { open = false }, title = t(R06Key.OP_EDIT_TITLE), veil = Veil.MENU, closeLabel = t(app.masroufy.core.UiKey.SHELL_CLOSE)) {
        TextInput(
            amount, { amount = it; error = null }, label = t(R06Key.OP_EDIT_AMOUNT), placeholder = "0.00", ltr = true, keyboard = KeyboardType.Decimal,
            imeAction = ImeAction.Done, error = error, unit = currencySymbol(transaction.currency),
        )
        FieldLabel(t(R06Key.OP_EDIT_DATE))
        app.masroufy.ui.screens.people.DateField(date, space.shell.today(), { date = it; error = null })
        if (wallets.size > 1) {
            FieldLabel(t(R06Key.OP_EDIT_WALLET))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in wallets) SelectChip(w.name, walletId == w.id, { walletId = w.id; error = null })
            }
        }
        PrimaryButton(t(R06Key.OP_EDIT_SAVE), onClick = {
            val minor = tryParseMoney(amount, transaction.currency)
            if (minor == null || minor <= 0) { error = t(app.masroufy.core.UiKey.ADD_AMOUNT_INVALID); return@PrimaryButton }
            scope.launch {
                val err = failureOf {
                    if (minor != transaction.amountMinor) deps.edit.setAmount(transaction.id, minor)
                    if (date != transaction.occurredAt.take(10)) deps.edit.setDate(transaction.id, date)
                    val w = walletId
                    if (w != null && w != transaction.walletId) deps.edit.setWallet(transaction.id, w)
                }
                if (err != null) { error = err; return@launch }
                open = false
                toaster.show(t(R06Key.OP_EDIT_SAVED))
                onChanged()
            }
        }, modifier = Modifier.fillMaxWidth())
    }
}
