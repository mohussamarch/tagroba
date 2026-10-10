package app.masroufy.ui.screens.auth

import app.masroufy.core.UiKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.port.AuthError
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.overlay.Sheet
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WAIT_SECONDS = 60

/** نوع اللوحة: «نسيت كلمة المرور؟» في الدخول بالبريد · «تغيير كلمة السر» في «ملفك» (نفس الرابط على الإيميل — رد المالك 2026-10-09). */
enum class ResetMode { FORGOT, CHANGE }
private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

/**
 * «نسيت كلمة المرور؟» ولوحته (`ResetPasswordSheet` — OVERRIDES §76): البريد ⇒ «راجع بريدك» **بنفس الرسالة سواء البريد ليه حساب أو لأ**
 * (`SignIn.sendPasswordReset` — تصليح الأمان: «مفيش حساب» = نجاح ظاهريًا) · إعادة الإرسال بعد 60 ثانية · «غيّر البريد» · «العودة لتسجيل الدخول».
 * أخطاء الشكل والشبكة جنب الخانة (ما بتكشفش حاجة).
 */
@Composable
fun ResetPasswordSheet(prefill: String, mode: ResetMode = ResetMode.FORGOT) {
    val change = mode == ResetMode.CHANGE
    val app = LocalApp.current
    // رد المالك L4: «تغيير كلمة السر» من غير نت ⇒ «أرسل الرابط» مقفول وجنبه السبب (نسيت كلمة المرور في الدخول زي ما هي — الخطأ جنب الخانة)
    val online by app.online.collectAsState()
    val offline = change && !online
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var again by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    LaunchedEffect(left) { if (left > 0) { delay(1000); left -= 1 } }

    fun send(to: String, isAgain: Boolean) {
        busy = true
        error = null
        scope.launch {
            try {
                app.signIn.sendPasswordReset(to)
                sentTo = to
                again = isAgain
                left = WAIT_SECONDS
            } catch (e: AuthError) {
                error = e.message
            }
            busy = false
        }
    }

    val press = rememberPress()
    val openSheet = { open = true; error = null; email = sentTo ?: prefill.trim() }
    if (change) {
        // «تغيير كلمة السر» في «ملفك» (رد المالك 2026-10-09): صف في مجموعة «الحساب» بنفس اللوحة، والإيميل مكتوب
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(press).tap(press, onClick = openSheet).padding(horizontal = 8.dp, vertical = 8.dp)) {
            BasicText(t(UiKey.RESET_CHANGE_TRIGGER), style = Type.of(15, FontWeight.Medium))
            BasicText(t(UiKey.RESET_CHANGE_HINT), style = Type.caption().copy(color = Ink.muted))
        }
    } else Box(Modifier.heightIn(min = 44.dp).pressScale(press).tap(press, onClick = openSheet), contentAlignment = Alignment.CenterStart) {
        BasicText(t(UiKey.RESET_TRIGGER), style = Type.of(14, FontWeight.Bold).copy(color = Ink.primary))
    }
    val title = if (change) t(UiKey.RESET_CHANGE_TITLE) else t(UiKey.RESET_TITLE)

    Sheet(open, { open = false }, title = title, closeLabel = t(UiKey.SHELL_CLOSE), corner = 28.dp, minHeight = 400.dp, spacing = 12.dp) {
        val sent = sentTo
        if (sent == null) {
            BasicText(title, style = Type.of(17, FontWeight.Bold))
            BasicText(if (change) t(UiKey.RESET_CHANGE_BODY) else t(UiKey.RESET_BODY), style = Type.of(13).copy(color = Ink.muted))
            TextInput(
                email, { email = it; error = null }, label = t(UiKey.SIGNIN_EMAIL), placeholder = "name@example.com", error = error,
                enabled = !busy, ltr = true, keyboard = KeyboardType.Email, imeAction = ImeAction.Send,
            )
            PrimaryButton(
                if (busy) t(UiKey.RESET_SENDING) else if (change) t(UiKey.RESET_CHANGE_SEND) else t(UiKey.RESET_SEND),
                onClick = {
                    val e = email.trim()
                    error = when {
                        e.isEmpty() -> t(UiKey.RESET_EMAIL_EMPTY)
                        !EMAIL.matches(e) -> t(TextKey.AUTH_INVALID_EMAIL)
                        else -> null
                    }
                    if (error == null) send(e, false)
                },
                loading = busy, enabled = !offline, height = 52.dp, modifier = Modifier.fillMaxWidth(),
            )
            if (offline) BasicText(t(UiKey.RESET_CHANGE_OFFLINE), style = Type.caption().copy(color = Ink.muted))
            if (!change) BasicText(t(UiKey.RESET_PRIVACY), style = Type.caption().copy(color = Ink.muted))
        } else {
            BasicText(t(UiKey.RESET_SENT_TITLE), style = Type.of(17, FontWeight.Bold))
            BasicText(t(UiKey.RESET_SENT_BODY, sent), style = Type.body())
            BasicText(t(UiKey.RESET_SPAM), style = Type.of(13).copy(color = Ink.muted))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(if (busy) t(UiKey.RESET_SENDING) else t(UiKey.RESET_RESEND), onClick = { send(sent, true) }, enabled = left == 0 && !busy && !offline, modifier = Modifier.weight(1f))
                TonalButton(t(UiKey.RESET_CHANGE), onClick = { sentTo = null; left = 0; again = false; email = sent }, modifier = Modifier.weight(1f))
            }
            val wait = when {
                left <= 0 -> null
                left == 1 -> t(UiKey.RESET_WAIT_ONE)
                left == 2 -> t(UiKey.RESET_WAIT_TWO)
                left <= 10 -> t(UiKey.RESET_WAIT_FEW, sentenceNumber(left))
                else -> t(UiKey.RESET_WAIT_MANY, sentenceNumber(left))
            }
            val note = listOfNotNull(if (again && !busy) t(UiKey.RESET_AGAIN) else null, wait, if (offline) t(UiKey.RESET_OFFLINE) else null).joinToString(" ")
            if (note.isNotEmpty()) BasicText(note, style = Type.caption().copy(color = Ink.muted))
            if (error != null) BasicText(error!!, style = Type.of(13).copy(color = Ink.expense))
            Column(Modifier.fillMaxWidth().height(4.dp)) {}
            PrimaryButton(if (change) t(UiKey.MORE_DONE) else t(UiKey.RESET_FINISH), onClick = { open = false }, height = 48.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}
