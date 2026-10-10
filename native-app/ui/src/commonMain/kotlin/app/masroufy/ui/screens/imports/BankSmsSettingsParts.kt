package app.masroufy.ui.screens.imports

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.Wallet
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type

/**
 * لوحة المرسل: «رسائل «البنك» أين تُسجَّل؟» (تعديل — مع «فك الربط» و«احذف المرسل») أو «مرسل جديد» (الاسم كما يظهر في الرسائل + محفظته).
 * «محفظة جديدة برصيد غير معروف» من النموذج **مش هنا**: مفيش حالة استخدام لإضافة محفظة لسه (OVERRIDES §76 «ناقص في كوتلن»).
 */
@Composable
fun SenderSheetView(
    sheet: SenderSheet?,
    wallets: List<Wallet>,
    isLast: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, wallet: Wallet) -> Unit,
    onUnlink: (SenderUi) -> Unit,
    onRemove: (SenderUi) -> Unit,
) {
    val edit = (sheet as? SenderSheet.Edit)?.sender
    var picked by remember(sheet) { mutableStateOf(wallets.firstOrNull { it.id == edit?.walletId }) }
    var name by remember(sheet) { mutableStateOf("") }
    var tried by remember(sheet) { mutableStateOf(false) }
    val title = if (edit != null) t(UiKey.SMS_WAITING_SHEET_TITLE, edit.sender) else t(UiKey.SMS_SETTINGS_NEW_TITLE)
    Sheet(sheet != null, onDismiss, title, closeLabel = t(UiKey.SHELL_CLOSE), spacing = 10.dp) {
        BasicText(title, style = Type.of(17, FontWeight.Bold))
        BasicText(t(if (edit != null) UiKey.SMS_SETTINGS_EDIT_BODY else UiKey.SMS_SETTINGS_NEW_BODY), style = Type.of(13).copy(color = Ink.muted))
        if (sheet == SenderSheet.Add) {
            TextInput(name, { name = it.take(MAX_SENDER_LENGTH) }, placeholder = t(UiKey.SMS_SETTINGS_NAME), ltr = true)
            if (tried && name.isBlank()) FieldError(t(UiKey.SMS_SETTINGS_NAME_ERROR))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in wallets) RadioRow(w.name, t(UiKey.SMS_WAITING_SHEET_BANK), picked?.id == w.id, { picked = w })
        }
        PrimaryButton(
            t(if (edit != null) UiKey.SMS_SETTINGS_SAVE else UiKey.SMS_SETTINGS_ADD_IT),
            {
                val w = picked
                if (sheet == SenderSheet.Add && name.isBlank()) tried = true
                else if (w != null) onSave(edit?.sender ?: name, w)
            },
            Modifier.fillMaxWidth(),
            enabled = picked != null,
        )
        if (edit != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (edit.walletId != null) QuietButton(t(UiKey.SMS_SETTINGS_UNLINK), { onUnlink(edit) }, Modifier.weight(1f), height = 44.dp)
                QuietButton(t(UiKey.SMS_SETTINGS_REMOVE), { onRemove(edit) }, Modifier.weight(1f), danger = true, enabled = !isLast, height = 44.dp)
            }
        }
    }
}

/** اسم المرسل في الرسالة (زي النموذج: ١١ حرف — أسماء مرسلي الرسايل القصيرة). */
private const val MAX_SENDER_LENGTH = 11

/**
 * شرح قبل نافذة الإذن (`SmsPermission`): ليه الإذن · مرة واحدة بس · اللي بيتسجل لوحده واللي بيستنى. موافق ⇒ نافذة النظام. الرفض ⇒ سطر
 * بسببه و«الصق رسالة» بدل القراءة.
 */
@Composable
fun SmsPermissionSheet(visible: Boolean, denied: Boolean, onDismiss: () -> Unit, onAllow: () -> Unit, onPaste: () -> Unit) {
    Sheet(visible, onDismiss, t(UiKey.SMS_PERM_TITLE), closeLabel = t(UiKey.SHELL_CLOSE)) {
        BasicText(t(UiKey.SMS_PERM_TITLE), style = Type.of(17, FontWeight.Bold))
        for (line in listOf(UiKey.SMS_PERM_WHY, UiKey.SMS_PERM_ONCE, UiKey.SMS_PERM_WHAT)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                LucideIcon(Lucide.CHECK, size = 18.dp, tint = Ink.primary)
                BasicText(t(line), Modifier.weight(1f), style = Type.of(14, lineHeight = 1.6))
            }
        }
        if (denied) FieldError(t(UiKey.SMS_PERM_DENIED))
        PrimaryButton(t(UiKey.SMS_PERM_ALLOW), onAllow, Modifier.fillMaxWidth())
        QuietButton(t(if (denied) UiKey.BANK_SMS_PASTE else UiKey.SMS_PERM_LATER), if (denied) onPaste else onDismiss, Modifier.fillMaxWidth())
    }
}
