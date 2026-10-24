package app.masroufy.ui.screens.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.core.formatAmount
import app.masroufy.core.tryParseMoney
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.screens.people.DateField
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.SettlementRow
import app.masroufy.usecase.settlementKey
import kotlinx.coroutines.launch

/**
 * r06-links2 (فجوة ٥): السدادات في صفحة الدين — كل سداد بـ«عدّل» (المبلغ · التاريخ · الحساب — `ManageSettlements.edit`) و«امسح» (لوحة
 * المسح الواحدة بتراجع — `Removals.settlement`؛ العملية نفسها بتفضل). الشاشة ما بتحسبش: المبلغ بيتقرا بس والتأكد في حالة الاستخدام.
 */
@Composable
internal fun DebtSettlements(obligationId: String, personId: String) {
    val space = LocalSpace.current
    val manage = space.shell.settlementsEdit ?: return
    var rows by remember(obligationId) { mutableStateOf<List<SettlementRow>>(emptyList()) }
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(obligationId, tick, DuesChanges.version) {
        rows = runCatching { manage.list(obligationId, personId) }.getOrDefault(emptyList())
        wallets = runCatching { space.shell.addOptions().wallets }.getOrDefault(emptyList())
    }
    if (rows.isEmpty()) return
    FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(t(R06Key.L2_ST_TITLE), style = Type.of(14, FontWeight.Bold))
            rows.forEach { r -> SettlementLine(r, personId, wallets) { tick++; DuesChanges.bump() } }
        }
    }
}

@Composable
private fun SettlementLine(r: SettlementRow, personId: String, wallets: List<Wallet>, onChanged: () -> Unit) {
    val space = LocalSpace.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var open by remember(r) { mutableStateOf(false) }
    var amount by remember(r) { mutableStateOf(formatAmount(r.amountMinor, r.currency)) }
    var day by remember(r) { mutableStateOf(r.day) }
    var wallet by remember(r) { mutableStateOf(r.walletId) }
    var error by remember(r) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(r.day ?: t(R06Key.L2_ST_NO_OP), style = Type.bodyBold())
                if (r.walletName.isNotEmpty()) BasicText(r.walletName, style = Type.caption().copy(color = Ink.muted))
            }
            AmountText(r.amountMinor, r.currency)
            TonalButton(t(R06Key.L2_ST_EDIT), { open = !open; error = null }, height = 40.dp)
        }
        if (open) {
            MoneyField(amount, { amount = it; error = null }, null, r.currency)
            if (r.transactionId != null && r.movable) {
                DateField(day, space.shell.today(), { day = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (w in wallets.filter { it.currency == r.currency }.take(3)) SelectChip(w.name, wallet == w.id, { wallet = w.id }, Modifier.weight(1f), height = 40.dp)
                }
            } else if (r.transactionId != null) BasicText(t(R06Key.L2_ST_BANK_LOCKED), style = Type.caption().copy(color = Ink.muted))
            error?.let { FieldError(it) }
            PrimaryButton(t(R06Key.L2_ST_SAVE), onClick = {
                val minor = tryParseMoney(amount, r.currency)
                if (minor == null) { error = t(UiKey.ADD_AMOUNT_INVALID); return@PrimaryButton }
                scope.launch {
                    try {
                        space.shell.settlementsEdit?.edit(r.obligationId, personId, r.settlementId, minor, day.takeIf { r.movable }, wallet.takeIf { r.movable })
                        toaster.show(t(R06Key.L2_ST_SAVED))
                        open = false
                        onChanged()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        error = e.message ?: t(UiKey.SHELL_LOAD_FAILED)
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), height = 44.dp)
            // الشريط بيفضل لحد ما «تراجع» يخلص، وبعدها القايمة بتتقري تاني
            DeleteWithChoices(space.shell.removals?.settlement, settlementKey(r.obligationId, r.settlementId), afterDone = { onChanged() })
        }
    }
}
