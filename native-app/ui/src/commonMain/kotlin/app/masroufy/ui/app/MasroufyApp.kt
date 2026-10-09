package app.masroufy.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.components.LensOnLight
import app.masroufy.ui.components.screenBackground
import app.masroufy.ui.glass.backdropSource
import app.masroufy.ui.glass.rememberBackdrop
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.overlay.OverlayRoot
import app.masroufy.ui.screens.auth.LockScreen
import app.masroufy.ui.screens.auth.SignInFlow
import app.masroufy.ui.screens.buildRegistry
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.MasroufyTheme
import app.masroufy.ui.theme.Type

/**
 * التطبيق كله (أندرويد بيناديه من `MainActivity`): الثيم · الجلسة (بيبدأ ⇒ الدخول ⇒ «بيفتح» ⇒ الهيكل) · بوابة القفل فوق الكل.
 * [shell] بيعيش طول عمر الشاشة (زرار الرجوع بيتبعتله)، و[lock] بيتبلّغ بالخلفية والرجوع منها.
 */
@Composable
fun MasroufyApp(app: AppDeps, shell: ShellState, lock: LockGate, reduceMotion: Boolean = false) {
    val registry = remember { buildRegistry() }
    val session by app.session.collectAsState()
    LaunchedEffect(session is AppSession.Ready) { if (session !is AppSession.Ready) shell.navigator.reset() }
    MasroufyTheme(reduceMotion) {
        CompositionLocalProvider(LocalApp provides app, LocalPermissions provides app.permissions) {
            val root = rememberBackdrop()
            Box(Modifier.fillMaxSize().screenBackground()) {
                Box(Modifier.fillMaxSize().backdropSource(root)) {
                    when (val s = session) {
                        AppSession.Starting -> OpeningScreen(null, null)
                        AppSession.SignedOut -> OverlayRoot(shell.overlays) { SignInFlow() }
                        is AppSession.Opening -> OpeningScreen(s.synced, s.total)
                        is AppSession.Ready -> AppShell(shell, registry, s.deps)
                    }
                }
                if (lock.locked && session !is AppSession.SignedOut) LockScreen(lock, root)
            }
            LaunchedEffect(lock.released) {
                if (lock.released) {
                    shell.toaster.show(t(TextKey.LOCK_RELEASED), dark = true)
                    lock.dismissReleased()
                }
            }
        }
    }
}

/** «بيفتح» (التنزيل الأول — §54): عدسة + «نجهّز بياناتك…» + شريط تقدم بعدد المجموعات اللي نزلت. */
@Composable
fun OpeningScreen(synced: Int?, total: Int?) {
    Column(
        Modifier.fillMaxSize().padding(top = 180.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LensOnLight(Modifier.size(76.dp), shape = RoundedCornerShape(26.dp)) { LucideIcon(Lucide.REFRESH_CW, size = 30.dp, tint = Ink.primary) }
        BasicText(t(TextKey.SHELL_OPENING), style = Type.of(18, FontWeight.Bold))
        if (synced != null && total != null && total > 0) {
            val fraction = (synced.toFloat() / total).coerceIn(0f, 1f)
            Box(
                Modifier.width(220.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x1F193D33))
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
            ) { Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Ink.primary)) }
            BasicText(t(TextKey.SHELL_OPENING_PROGRESS, sentenceNumber(synced), sentenceNumber(total)), style = Type.caption().copy(color = Ink.muted))
        }
    }
}
