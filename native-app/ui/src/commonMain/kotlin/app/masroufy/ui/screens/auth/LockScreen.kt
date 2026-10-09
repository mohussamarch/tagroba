package app.masroufy.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.port.LockResult
import app.masroufy.ui.app.LockGate
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.LensOnLight
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.screenBackground
import app.masroufy.ui.glass.Backdrop
import app.masroufy.ui.glass.BackdropBlur
import app.masroufy.ui.glass.blurSupported
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * شاشة القفل (`Lock` — §21 · §31): التطبيق ورا بلور (18 + `rgba(239,243,237,.40)`) لحد ما نافذة الجهاز تأكد — تحت أندرويد 12 الخلفية معتمة
 * (المبالغ ما تبانش). العدسة 76 بالقفل · «مصروفي» · «بالبصمة أو برمز الجوال» · الرسالة (فشل/اتلغى من `AppLock`) · «فتح» أو «إعادة المحاولة».
 * نافذة البصمة/الرمز **بيرسمها النظام** (النموذج بيمثّلها بس) وبتطلع لوحدها أول ما الشاشة تظهر. القرار (إمتى يتقفل) في `AppLock`.
 */
@Composable
fun LockScreen(gate: LockGate, backdrop: Backdrop?) {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    fun ask() {
        if (asking) return
        asking = true
        scope.launch { gate.unlock(); asking = false }
    }
    LaunchedEffect(Unit) { ask() }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        if (blurSupported() && backdrop != null) {
            BackdropBlur(backdrop, 18.dp)
            Box(Modifier.matchParentSize().background(Color(0x66EFF3ED)))
        } else Box(Modifier.matchParentSize().screenBackground())
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 150.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LensOnLight(Modifier.size(76.dp), shape = RoundedCornerShape(26.dp), backdrop = backdrop) { LucideIcon(Lucide.LOCK, size = 32.dp, tint = Ink.primary) }
            BasicText(t(TextKey.ASK_TITLE), style = Type.of(26, FontWeight.Bold))
            BasicText(t(TextKey.LOCK_SCREEN_BODY), style = Type.body().copy(color = Ink.muted, textAlign = TextAlign.Center))
            val msg = gate.message
            if (msg != null) BasicText(
                msg,
                Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(Ink.alertBg).padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                style = Type.body().copy(color = Color(0xFF6B4600), textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                t(lockButtonKey(gate.lastResult)),
                onClick = { ask() }, loading = asking, height = 52.dp, modifier = Modifier.widthIn(min = 200.dp), leading = Lucide.FINGERPRINT,
            )
        }
    }
}

/** زرار شاشة القفل (النموذج): البصمة ما اتطابقتش ⇒ «إعادة المحاولة» · اتلغت أو أول مرة أو مش متاحة ⇒ «فتح». */
fun lockButtonKey(last: LockResult?): TextKey = if (last == LockResult.FAILED) TextKey.LOCK_SCREEN_RETRY else TextKey.LOCK_SCREEN_OPEN

/**
 * «أُوقف قفل التطبيق» (حالة «غير متاح» في النموذج — `AppLock.releaseIfDeviceHasNoLock`): الجوال مالوش قفل شاشة ولا بصمة ⇒ القفل بيتوقف ويتقال
 * ده بوضوح في كارت فوق التطبيق (بدل رسالة صغيرة بتختفي) ⇒ «متابعة».
 */
@Composable
fun LockReleasedCard(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x38203B30)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.BottomCenter) {
        FloatingCard(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LucideIcon(Lucide.LOCK_OPEN, size = 22.dp, tint = Ink.focus)
                    BasicText(t(TextKey.LOCK_SCREEN_RELEASED_TITLE), style = Type.of(16, FontWeight.Bold))
                }
                BasicText(t(TextKey.LOCK_SCREEN_RELEASED_BODY), style = Type.of(13).copy(color = Ink.soft))
                PrimaryButton(t(TextKey.LOCK_SCREEN_CONTINUE), onClick = onContinue, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
