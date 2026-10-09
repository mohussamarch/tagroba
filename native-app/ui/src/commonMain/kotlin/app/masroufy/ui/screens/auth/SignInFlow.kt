package app.masroufy.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.port.AuthError
import app.masroufy.ui.app.LocalApp
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.LensOnLight
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.SecondaryButton
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.GoogleSignIn
import kotlinx.coroutines.launch

/**
 * الدخول قبل ما الحساب يجهز (كارت «كيف تريد الدخول؟» من `Onboarding` + `SignInEmail`) — **الحد الأدنى**: منطقة «أول تشغيل» بتكمّل رحلة
 * الكروت (الشكل · البلد · يوم المرتب · مصدر العمليات) بعد الدخول. شاشة الدخول **فصحى دايمًا** (§66 — الجلسة بترجع للفصحى عند الخروج).
 * جوجل: `SignIn.withGoogle()` (نافذة الجهاز)؛ لو مش متاح بيظهر السبب بدل الزرار. الإيميل ⇒ [SignInEmailScreen].
 */
@Composable
fun SignInFlow() {
    var email by rememberSaveable { mutableStateOf(false) }
    if (email) SignInEmailScreen(onBack = { email = false }) else WelcomeCard(onEmail = { email = true })
}

@Composable
private fun WelcomeCard(onEmail: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val googleReason = app.signIn.googleUnavailableReason
    val pad = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Space.gutter, end = Space.gutter, top = pad.calculateTopPadding() + 48.dp, bottom = pad.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(Space.block),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (app.emulator) EmulatorNote()
        LensOnLight(Modifier.size(76.dp), shape = RoundedCornerShape(26.dp)) { LucideIcon(Lucide.WALLET, size = 32.dp, tint = Ink.primary) }
        BasicText(t(TextKey.SIGNIN_WELCOME_TITLE), Modifier.semantics { heading() }, style = Type.of(24, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        BasicText(t(TextKey.SIGNIN_WELCOME_SUB), style = Type.body().copy(color = Ink.muted, textAlign = TextAlign.Center))
        FloatingCard(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(t(TextKey.SIGNIN_HOW), style = Type.section())
                BasicText(t(TextKey.SIGNIN_HOW_SUB), style = Type.of(13).copy(color = Ink.muted))
                if (googleReason == null) {
                    PrimaryButton(
                        if (busy) t(TextKey.SIGNIN_BUSY_IN) else t(TextKey.SIGNIN_GOOGLE),
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                error = try {
                                    when (val r = app.signIn.withGoogle()) {
                                        is GoogleSignIn.Unavailable -> r.reason
                                        else -> null
                                    }
                                } catch (e: AuthError) {
                                    e.message
                                }
                                busy = false
                            }
                        },
                        loading = busy, height = 52.dp, modifier = Modifier.fillMaxWidth(),
                    )
                } else BasicText(googleReason, style = Type.of(13).copy(color = Ink.muted))
                if (error != null) FieldError(error!!)
                SecondaryButton(t(TextKey.SIGNIN_WITH_EMAIL), onClick = onEmail, enabled = !busy, height = 52.dp, leading = Lucide.MAIL, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** شريط «نسخة اختبار» على المحاكي — عشان ما تتلخبطش مع النسخة الحقيقية. */
@Composable
internal fun EmulatorNote() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink.alertBg).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucideIcon(Lucide.TRIANGLE_ALERT, size = 18.dp, tint = Ink.focus)
        BasicText(t(TextKey.SIGNIN_EMULATOR_NOTE), Modifier.weight(1f), style = Type.of(13).copy(color = Ink.focus))
    }
    Box(Modifier.size(0.dp))
}
