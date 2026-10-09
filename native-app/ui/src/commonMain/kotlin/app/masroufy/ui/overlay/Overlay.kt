package app.masroufy.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.Backdrop
import app.masroufy.ui.glass.BackdropBlur
import app.masroufy.ui.glass.backdropSource
import app.masroufy.ui.glass.blurSupported
import app.masroufy.ui.glass.rememberBackdrop
import androidx.compose.runtime.CompositionLocalProvider

/**
 * طبقة اللوحات والنوافذ فوق الشاشة (`AppShell`): أي لوحة أو نافذة أو قائمة بتترسم **هنا** مهما كانت الشاشة اللي فتحتها —
 * عشان الستارة تقدر تموّه الشاشة (الزجاج لازم يبقى برّه المحتوى المتسجل — `Backdrop`).
 * الشاشة بتكتب [Overlay] عادي جوه نفسها وحالتها عندها؛ المحتوى بيتنقل للطبقة لوحده. الرجوع بيقفل **آخر** واحدة مفتوحة.
 */
@Stable
class OverlayHost {
    internal class Entry(val id: Long) {
        var content: @Composable () -> Unit by mutableStateOf({})
        var onBack: (() -> Unit)? = null
    }

    internal val entries = mutableStateListOf<Entry>()
    private var next = 0L

    internal fun newEntry() = Entry(next++)

    /** زرار الرجوع: يقفل آخر لوحة/نافذة ليها رجوع. `false` = مفيش حاجة مفتوحة. */
    fun handleBack(): Boolean {
        val top = entries.lastOrNull { it.onBack != null } ?: return false
        top.onBack?.invoke()
        return true
    }

    val hasOpen: Boolean get() = entries.isNotEmpty()
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

/** المحتوى المتسجل اللي الستارة والزجاج بيموّهوه (بيديه `AppShell`). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** محتوى فوق الشاشة (في طبقة `AppShell`). [onBack] = اللي بيحصل لما زرار الرجوع يتداس وهو مفتوح. من غير طبقة (اختبار) ⇒ بيترسم مكانه. */
@Composable
fun Overlay(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    if (host == null) {
        content()
        return
    }
    val entry = remember(host) { host.newEntry() }
    SideEffect {
        entry.content = content
        entry.onBack = onBack
    }
    DisposableEffect(host, entry) {
        entry.content = content
        entry.onBack = onBack
        host.entries += entry
        onDispose { host.entries -= entry }
    }
}

/**
 * جذر لوحات لأي شاشة برّه `AppShell` (الدخول · القفل): المحتوى بيتسجل للتمويه، والطبقة فوقه. `AppShell` بيعمل نفس الحكاية لنفسه.
 * الرجوع من غير لوحة (مثلًا شاشة الإيميل ⇒ الترحيب): `Overlay(onBack = …) {}` من غير محتوى.
 */
@Composable
fun OverlayRoot(host: OverlayHost, content: @Composable () -> Unit) {
    val backdrop = rememberBackdrop()
    CompositionLocalProvider(LocalOverlayHost provides host, LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) { content() }
            OverlayLayer(host)
        }
    }
}

/** الطبقة نفسها — `AppShell` بيحطها فوق كل حاجة. */
@Composable
fun OverlayLayer(host: OverlayHost, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        for (e in host.entries) key(e.id) { e.content() }
    }
}

/**
 * الستارة ورا اللوحات والقوائم (KOTLIN-MAP §٣ · DESIGN-SYSTEM):
 * [NORMAL] اللوحات: `rgba(32,59,48,0.22)` + بلور 6 · [MENU] الضغط المطوّل والإضافة: `rgba(32,59,48,0.16)` + بلور 8 وتشبّع 0.88 ·
 * [LIGHT] نافذة الجرس: `rgba(32,59,48,0.06)` **من غير بلور** · [CHAT] صفحة الشات (`AssistantChat`): `rgba(250,249,243,0.76)` + بلور 22 وتشبّع 1.1.
 * تحت أندرويد 12: نفس اللون أتقل شوية من غير بلور.
 */
enum class Veil(internal val color: Color, internal val opaqueColor: Color, internal val blur: Dp, internal val saturation: Float) {
    NORMAL(Color(0x38203B30), Color(0x52203B30), 6.dp, 1f),
    MENU(Color(0x29203B30), Color(0x47203B30), 8.dp, 0.88f),
    LIGHT(Color(0x0F203B30), Color(0x0F203B30), 0.dp, 1f),
    CHAT(Color(0xC2FAF9F3), Color(0xF7FAF9F3), 22.dp, 1.1f),
}

/** الستارة بحالة ظهورها [alpha] (0..1). الضغط عليها = [onDismiss] (من غير تموّج). */
@Composable
fun VeilLayer(veil: Veil, alpha: Float, onDismiss: (() -> Unit)?, closeLabel: String?, modifier: Modifier = Modifier) {
    val blur = blurSupported() && veil.blur > 0.dp
    Box(
        modifier.fillMaxSize().alpha(alpha)
            .then(
                if (onDismiss == null) Modifier.pointerInput(Unit) { detectTapGestures { } }
                else Modifier.pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                    .semantics { if (closeLabel != null) contentDescription = closeLabel; onClick { onDismiss(); true } },
            ),
    ) {
        if (blur) BackdropBlur(LocalBackdrop.current, veil.blur, saturation = veil.saturation)
        Box(Modifier.matchParentSize().background(if (blur) veil.color else veil.opaqueColor))
    }
}
