package app.masroufy.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.port.AuthError
import app.masroufy.port.AuthField
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PasswordInput
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SegmentStyle
import app.masroufy.ui.components.SegmentedTabs
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.overlay.Overlay
import app.masroufy.ui.screens.common.ScreenHeader
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

/**
 * الدخول بالبريد (`SignInEmail`): «تسجيل الدخول | حساب جديد» · البريد · كلمة المرور بالعين · «٦ أحرف على الأقل.» للحساب الجديد ·
 * «نسيت كلمة المرور؟» (`ResetPasswordSheet`) · زرار بيحمّل. الأخطاء **جنب الخانة** بنص `AUTH_*` (نفس التطبيق الحالي)، والبريد المسجّل
 * قبل كده جنبه زرار «تسجيل الدخول». الفحص الأولي هنا (الشكل بس) — الحكم من فايربيز عن طريق `SignIn`.
 */
@Composable
fun SignInEmailScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var newAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var passErr by remember { mutableStateOf<String?>(null) }
    var formErr by remember { mutableStateOf<String?>(null) }
    var canSwitch by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        val e = email.trim()
        emailErr = if (EMAIL.matches(e)) null else t(TextKey.AUTH_INVALID_EMAIL)
        passErr = when {
            pass.isEmpty() -> t(TextKey.AUTH_MISSING_PASSWORD)
            newAccount && pass.length < 6 -> t(TextKey.AUTH_WEAK_PASSWORD)
            else -> null
        }
        formErr = null
        canSwitch = false
        if (emailErr != null || passErr != null) return
        busy = true
        scope.launch {
            try {
                if (newAccount) app.signIn.register(e, pass) else app.signIn.withEmail(e, pass)
            } catch (x: AuthError) {
                when (x.field) {
                    AuthField.EMAIL -> {
                        emailErr = x.message
                        canSwitch = x.code == "auth/email-already-in-use"
                    }
                    AuthField.PASSWORD -> passErr = x.message
                    AuthField.FORM -> formErr = x.message
                }
            }
            busy = false
        }
    }

    // زرار الرجوع بتاع الجهاز ⇒ الترحيب (لوحة «نسيت كلمة المرور» لو مفتوحة بتتقفل الأول)
    Overlay(onBack = onBack) {}
    val pad = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(start = Space.gutter, end = Space.gutter, top = pad.calculateTopPadding() + Space.gutter, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        ScreenHeader(t(TextKey.SIGNIN_TITLE), onBack = onBack)
        if (app.emulator) EmulatorNote()
        SegmentedTabs(
            listOf(false to t(TextKey.SIGNIN_MODE_IN), true to t(TextKey.SIGNIN_MODE_NEW)), newAccount,
            { newAccount = it; emailErr = null; passErr = null; formErr = null; canSwitch = false },
            style = SegmentStyle.QUIET, enabled = !busy, height = 44.dp,
        )
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextInput(
                    email, { email = it; emailErr = null; canSwitch = false }, label = t(TextKey.SIGNIN_EMAIL), placeholder = "name@example.com",
                    error = emailErr, enabled = !busy, ltr = true, keyboard = KeyboardType.Email,
                )
                if (canSwitch) TonalButton(t(TextKey.SIGNIN_MODE_IN), onClick = { newAccount = false; emailErr = null; canSwitch = false }, height = 40.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PasswordInput(
                        pass, { pass = it; passErr = null }, shown, { shown = !shown }, t(TextKey.SIGNIN_SHOW_PASS), t(TextKey.SIGNIN_HIDE_PASS),
                        label = t(TextKey.SIGNIN_PASSWORD), error = passErr, enabled = !busy, imeAction = ImeAction.Done,
                    )
                    if (newAccount && passErr == null) BasicText(t(TextKey.SIGNIN_PASS_HINT), style = Type.caption().copy(color = Ink.muted))
                }
                if (!newAccount) ResetPasswordSheet(prefill = email)
                if (formErr != null) FieldError(formErr!!)
                PrimaryButton(
                    when {
                        busy && newAccount -> t(TextKey.SIGNIN_BUSY_NEW)
                        busy -> t(TextKey.SIGNIN_BUSY_IN)
                        newAccount -> t(TextKey.SIGNIN_SUBMIT_NEW)
                        else -> t(TextKey.SIGNIN_SUBMIT_IN)
                    },
                    onClick = { submit() }, loading = busy, height = 52.dp, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
