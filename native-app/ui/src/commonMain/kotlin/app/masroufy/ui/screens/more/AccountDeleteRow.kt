package app.masroufy.ui.screens.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.DeleteAccountResult
import kotlinx.coroutines.launch

/** الكلمة اللي المستخدم بيكتبها في الخطوة التانية — بتتقارن بعد شيل المسافات. */
internal fun accountDeleteWordOk(typed: String, word: String): Boolean = typed.trim() == word.trim()

/**
 * r06-links2 (§118-ج «امسح حسابي» — مطلوب للمتجر): خطوتين. (١) لوحة بتقول إيه اللي بيتمسح وإن **مالوش رجوع**. (٢) كتابة كلمة «امسح».
 * بعدها `SignIn.deleteAccount`: السيرفر ⇒ الجوال ⇒ حساب الدخول. محتاج دخول قريب ⇒ سطر «ادخل تاني وجرّب» (البرنامج بيخرج لوحده).
 */
@Composable
internal fun AccountDeleteRow() {
    val signIn = LocalApp.current.signIn
    if (!signIn.canDeleteAccount) return
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val press = rememberPress()
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressScale(press).tap(press, onClick = { step = 1; typed = ""; error = null }).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) { BasicText(t(R06Key.L2_ACC_DELETE), style = Type.of(15, FontWeight.Bold).copy(color = Ink.expense)) }
    Sheet(step > 0, { if (!busy) step = 0 }, t(R06Key.L2_ACC_TITLE), closeLabel = t(UiKey.SHELL_CLOSE), spacing = 12.dp) {
        BasicText(t(R06Key.L2_ACC_TITLE), style = Type.section())
        BasicText(t(R06Key.L2_ACC_BODY), style = Type.of(14))
        BasicText(t(R06Key.L2_ACC_NO_UNDO), style = Type.of(14, FontWeight.Bold).copy(color = Ink.expense))
        if (step == 1) {
            PrimaryButton(t(R06Key.L2_ACC_NEXT), { step = 2 }, Modifier.fillMaxWidth(), height = 48.dp)
        } else {
            val word = t(R06Key.L2_ACC_WORD)
            TextInput(typed, { typed = it; error = null }, label = t(R06Key.L2_ACC_TYPE, word))
            error?.let { FieldError(it) }
            PrimaryButton(t(R06Key.L2_ACC_CONFIRM), {
                busy = true
                scope.launch {
                    error = when (val r = signIn.deleteAccount()) {
                        DeleteAccountResult.Done -> null
                        DeleteAccountResult.NeedsRecentLogin -> t(R06Key.L2_ACC_RELOGIN)
                        is DeleteAccountResult.Failed -> t(R06Key.L2_ACC_FAILED, r.reason)
                    }
                    busy = false
                    if (error == null) step = 0
                }
            }, Modifier.fillMaxWidth(), enabled = accountDeleteWordOk(typed, word) && !busy, loading = busy, height = 48.dp)
        }
        TonalButton(t(R06Key.L2_BK_KEEP), { if (!busy) step = 0 }, Modifier.fillMaxWidth(), muted = true)
    }
}
