package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.SelectChip
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.text.t
import app.masroufy.usecase.EDITABLE_KINDS
import kotlinx.coroutines.launch

/** وسم النوع في شرايح «النوع». */
internal fun walletKindKey(kind: String): UiKey = when (kind) {
    "cash" -> UiKey.WLIST_KIND_CASH
    "digital_wallet" -> UiKey.WLIST_KIND_DIGITAL
    else -> UiKey.WLIST_KIND_BANK
}

/**
 * r06-links2 (فجوة ٥): **اسم الحساب ونوعه** بيتعدلوا من صفحة الحساب (`ManageWallets.rename` · `setKind`). التغيير اللي مش آمن على
 * حساب الرصيد (من الكاش أو ليه والحساب عليه عمليات · كاش تاني) بيترفض وسببه بيظهر تحت. حساب برا البلد نوعه ثابت ⇒ الشرايح مش ظاهرة.
 */
@Composable
internal fun WalletNameKindCard(wallet: Wallet, onChanged: () -> Unit) {
    val editor = LocalSpace.current.more.walletEditor ?: return
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var name by remember(wallet.id, wallet.name) { mutableStateOf(wallet.name) }
    var kind by remember(wallet.id, wallet.kind) { mutableStateOf(wallet.kind) }
    var error by remember(wallet.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(name, { name = it; error = null }, label = t(UiKey.WADD_NAME))
        if (wallet.kind in EDITABLE_KINDS) {
            FieldLabel(t(UiKey.WADD_KIND))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in EDITABLE_KINDS) SelectChip(t(walletKindKey(k)), kind == k, { kind = k; error = null }, Modifier.weight(1f), height = 44.dp)
            }
        }
        error?.let { FieldError(it) }
        if (name != wallet.name || kind != wallet.kind) TonalButton(t(R06Key.L2_WK_SAVE), {
            scope.launch {
                val err = (if (name != wallet.name) editor.rename(wallet.id, name) else null)
                    ?: (if (kind != wallet.kind) editor.setKind(wallet.id, kind) else null)
                if (err != null) error = err else { toaster.show(t(R06Key.L2_WK_SAVED)); onChanged() }
            }
        }, Modifier.fillMaxWidth())
    }
}
