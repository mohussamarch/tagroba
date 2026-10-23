package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * r06-links — صفحة الحساب: **آخر 4 أرقام** (بتتعدل بعد الإضافة — `ManageWallets.setLast4`، والرقم الكامل لو اتكتب بيتخزن منه آخر 4 بس
 * CLAUDE.md #11) و**إخفاء/إظهار** (OVERRIDES §112-1 — `ManageWallets.setHidden`: برّه لوجوهات الرئيسية ورقم «معك الآن»).
 */
@Composable
internal fun WalletAccountCard(wallet: Wallet, onChanged: () -> Unit) {
    val editor = LocalSpace.current.more.walletEditor ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var last4 by remember(wallet.id, wallet.accountLast4) { mutableStateOf(wallet.accountLast4.orEmpty()) }
    var error by remember(wallet.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (wallet.kind != "cash") {
            TextInput(
                last4, { last4 = it.filter { c -> c.isDigit() }; error = null }, label = t(R06Key.WALLET_LAST4_LABEL), placeholder = "1234", ltr = true,
                keyboard = KeyboardType.Number, imeAction = ImeAction.Done, error = error,
            )
            if (last4 != wallet.accountLast4.orEmpty()) TonalButton(t(R06Key.WALLET_LAST4_SAVE), {
                scope.launch {
                    val err = editor.setLast4(wallet.id, last4.ifEmpty { null })
                    if (err != null) error = err else { toaster.show(t(R06Key.WALLET_LAST4_SAVED)); onChanged() }
                }
            }, Modifier.fillMaxWidth())
        }
        if (wallet.hiddenOnHome) BasicText(t(R06Key.WALLET_HIDDEN_NOTE), style = Type.of(12).copy(color = Ink.muted))
        TonalButton(t(if (wallet.hiddenOnHome) R06Key.WALLET_SHOW else R06Key.WALLET_HIDE), {
            scope.launch {
                val err = editor.setHidden(wallet.id, !wallet.hiddenOnHome)
                toaster.show(err ?: t(if (wallet.hiddenOnHome) R06Key.WALLET_SHOWN_DONE else R06Key.WALLET_HIDDEN_DONE))
                if (err == null) onChanged()
            }
        }, Modifier.fillMaxWidth(), muted = true)
    }
}
