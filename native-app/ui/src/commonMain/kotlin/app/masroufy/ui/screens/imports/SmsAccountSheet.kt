package app.masroufy.ui.screens.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.core.Wallet
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/** اختيار «في أي محفظة الحساب المنتهي بـNNNN؟»: محفظة جديدة للحساب · أو محفظة موجودة (ولو عليها أرقام تانية ⇒ نقل الأرقام). */
sealed interface AccountPick {
    data object NewWallet : AccountPick

    data class Existing(val wallet: Wallet, val replaces: String?) : AccountPick
}

/**
 * r041b P10 — لوحة «أيوه، ده حسابي». قبل كده كانت بتعرض المحافظ اللي مالهاش أرقام بس، فلو كل محافظ البنك عليها أرقام اللوحة فاضية و«سجّلها
 * هنا» مقفول ومفيش مخرج. دلوقتي: «محفظة جديدة لهذا الحساب» أول اختيار (نفس بنك الرسالة · رصيدها «غير متاح») + **كل** محافظ البنك
 * (غير الكاش)، واللي عليها أرقام تانية مكتوب تحتها أرقامها، ولو اتختارت بتظهر ملاحظة صريحة إن الأرقام الجديدة هتتحط مكان القديمة.
 * [last4] null ⇒ اللوحة مقفولة. الكتابة نفسها في حالة الاستخدام (`AnswerSmsAccount`) — الشاشة بتعرض وتختار بس.
 */
@Composable
fun AccountWalletSheet(last4: String?, wallets: List<Wallet>, onDismiss: () -> Unit, onSave: (AccountPick) -> Unit) {
    var picked by remember(last4) { mutableStateOf<AccountPick?>(null) }
    val title = last4?.let { t(UiKey.SMS_ACCOUNT_SHEET_TITLE, it) }.orEmpty()
    Sheet(last4 != null, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE)) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(UiKey.SMS_ACCOUNT_SHEET_BODY), style = Type.of(13).copy(color = Ink.muted))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioRow(t(UiKey.SMS_ACCOUNT_NEW_WALLET), t(UiKey.SMS_ACCOUNT_NEW_WALLET_SUB), picked == AccountPick.NewWallet, { picked = AccountPick.NewWallet })
            for (w in wallets.filter { it.kind != "cash" }) {
                val old = w.accountLast4?.trim()?.takeIf { it.isNotEmpty() && it != last4 }
                val sub = old?.let { t(UiKey.SMS_ACCOUNT_HAS_DIGITS, it) } ?: t(UiKey.SMS_WAITING_SHEET_BANK)
                RadioRow(w.name, sub, (picked as? AccountPick.Existing)?.wallet?.id == w.id, { picked = AccountPick.Existing(w, old) })
            }
        }
        val replacing = picked as? AccountPick.Existing
        if (replacing?.replaces != null && last4 != null) {
            BasicText(t(UiKey.SMS_ACCOUNT_REPLACE_NOTE, last4, replacing.replaces, replacing.wallet.name), style = Type.of(13).copy(color = Ink.expense))
        }
        PrimaryButton(t(UiKey.SMS_WAITING_SHEET_SAVE), { picked?.let(onSave) }, Modifier.fillMaxWidth(), enabled = picked != null)
    }
}
