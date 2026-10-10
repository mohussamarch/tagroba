package app.masroufy.ui.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.ui.components.screenBackground
import app.masroufy.ui.glass.backdropSource
import app.masroufy.ui.glass.rememberBackdrop
import app.masroufy.ui.nav.LocalNavigator
import app.masroufy.ui.nav.LocalRegistry
import app.masroufy.ui.nav.NavMotion
import app.masroufy.ui.nav.Navigator
import app.masroufy.ui.nav.RouteRegistry
import app.masroufy.ui.nav.SheetRoute
import app.masroufy.ui.overlay.LocalBackdrop
import app.masroufy.ui.overlay.LocalOverlayHost
import app.masroufy.ui.overlay.OverlayHost
import app.masroufy.ui.overlay.OverlayLayer
import app.masroufy.ui.screens.home.Dismissals
import app.masroufy.ui.screens.home.without
import app.masroufy.ui.shell.AddOperationSheet
import app.masroufy.ui.shell.BottomBars
import app.masroufy.ui.shell.LocalToaster
import app.masroufy.ui.shell.ToastHost
import app.masroufy.ui.shell.Toaster
import app.masroufy.ui.shell.ask.AskState
import app.masroufy.ui.shell.ask.AssistTarget
import app.masroufy.ui.shell.ask.AssistantChat
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.motion

/**
 * حالة الهيكل اللي بتعيش طول ما التطبيق مفتوح (حتى لو البلد اتبدّلت): التنقل · طبقة اللوحات · الرسايل. زرار الرجوع بتاع الجهاز ⇒ [handleBack].
 */
@Stable
class ShellState {
    val navigator = Navigator()
    val overlays = OverlayHost()
    val toaster = Toaster()

    /** كتابة من لوحة فوق شاشة لسه ظاهرة ⇒ الشاشة تقرا تاني (الرئيسية بعد «+») — [DataChanges]. */
    val changes = DataChanges()

    /** المحادثة مع المساعد — بتفضل لو الشات اتقفل أو اتنقلت أو البلد اتبدّل، لحد «محادثة جديدة» (§67 · آخر §76). */
    val ask = AskState()

    /** لوحة/نافذة مفتوحة ⇒ تتقفل · شاشة داخلية ⇒ رجوع · تبويب ⇒ الرئيسية · الرئيسية ⇒ `false` (الجهاز يقفل التطبيق). */
    fun handleBack(): Boolean = overlays.handleBack() || navigator.back()
}

/** الجرس والنقط الحمرا على التبويبات — قراية واحدة للهيكل كله (الرئيسية بتعرض الجرس، والشريط بيعرض النقط). */
class BellHolder(val state: BellState?, val refresh: () -> Unit)

val LocalBell = staticCompositionLocalOf { BellHolder(null) {} }

/**
 * الهيكل (`AppShell` = `App.dc.html`): الشاشة الظاهرة بحركتها (تبويب = تبديل سريع · داخلية = دخول من الشمال · رجوع = خروج) فوق خلفية الإضاءات،
 * والشريطين تحت في التبويبات بس، ولوحة «+» والمحادثة، وطبقة اللوحات والنوافذ (اللي بتموّه الشاشة — `Backdrop`).
 * حالة كل شاشة بتتحفظ بمفتاحها (`SaveableStateHolder`) — التبويبات الأربعة بترجع زي ما سبتها.
 */
@Composable
fun AppShell(shell: ShellState, registry: RouteRegistry, deps: SpaceDeps) {
    val nav = shell.navigator
    val backdrop = rememberBackdrop()
    var adding by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Boolean?>(null) }
    var bellTick by remember { mutableStateOf(0) }
    var bell by remember(deps) { mutableStateOf<BellState?>(null) }
    // الجرس بيتقري تاني كمان بعد أي كتابة (عملية جديدة ممكن تغيّر الأسئلة ونقط التبويبات)
    LaunchedEffect(deps, bellTick, shell.changes.version) { bell = runCatching { deps.shell.bell() }.getOrNull() }
    // «×» على إشعار بيشيله من الجرس ومن عدّ «جديد» ومن نقطة تبويبه (قرار المالك 2026-10-09) — المسح للجلسة دي (`Dismissals`)
    // لحد ما حفظه مع الحساب يتوصل من فرع `assistant-engine`
    val shownBell = bell?.without(Dismissals.of(deps.space.id).goneKeys)
    CompositionLocalProvider(
        LocalNavigator provides nav,
        LocalRegistry provides registry,
        LocalOverlayHost provides shell.overlays,
        LocalBackdrop provides backdrop,
        LocalSpace provides deps,
        LocalToaster provides shell.toaster,
        LocalBell provides BellHolder(shownBell) { bellTick++ },
        LocalDataChanges provides shell.changes,
    ) {
        Box(Modifier.fillMaxSize()) {
            // الخلفية **جوه** المتسجل: النسخة المموّهة لازم تبقى معتمة عشان تغطي الأصل — من غيرها النص اللي على الخلفية كان بيبان حاد
            // من ورا كل ستارة (اتشاف على المحاكي 2026-10-09: «مساء الخير» حاد ورا لوحة «+» والشات)
            Box(Modifier.fillMaxSize().backdropSource(backdrop).screenBackground()) {
                key(deps.space.id) { ScreenHost(nav, registry) }
            }
            val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // صفحة الشات بتغطي الشاشة كلها ومستطيل الكتابة مكان الشريطين (النموذج: `AssistantChat` — `inset 0`) ⇒ الشريطين بيستخبوا وهي مفتوحة
            if (nav.atTabRoot && ask == null) {
                BottomBars(
                    current = nav.tab, dots = shownBell?.dots.orEmpty(), backdrop = backdrop, onTab = nav::switchTab,
                    onAdd = { adding = true }, onAsk = { ask = false }, onMic = { ask = true },
                )
            }
            val toastBottom = if (nav.atTabRoot) Space.navBottom + Space.navHeight + Space.askGap + Space.askHeight + 14.dp else 32.dp
            ToastHost(shell.toaster, Modifier.align(Alignment.BottomCenter).padding(bottom = toastBottom + inset))
            for (s in nav.sheets) key(s.id) {
                val sheet = s.route as SheetRoute
                registry.Sheet(sheet) { nav.close(sheet) }
            }
            AddOperationSheet(adding) { adding = false }
            // زراير الشاشات في ردود المساعد بتقفل الشات وتنقل (رابط المحرك ⇒ `targetOf`)
            AssistantChat(ask != null, ask == true, nav.tab, shell.ask, onClose = { ask = null }) { target ->
                when (target) {
                    is AssistTarget.Push -> nav.push(target.route)
                    is AssistTarget.Open -> nav.open(target.sheet)
                    is AssistTarget.SwitchTab -> nav.switchTab(target.tab)
                    AssistTarget.AddOperation -> adding = true
                }
            }
            OverlayLayer(shell.overlays)
        }
    }
}

/** الشاشة الظاهرة بحركتها — «تقليل الحركة» ⇒ ظهور بالشفافية بس. */
@Composable
private fun ScreenHost(nav: Navigator, registry: RouteRegistry) {
    val holder = rememberSaveableStateHolder()
    val reduce = LocalReduceMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val seen = remember { mutableSetOf<String>() }
    val liveKeys = nav.stack.map { "s${it.id}" }.toSet()
    LaunchedEffect(liveKeys) {
        // حالة الشاشات اللي اتقفلت بتتشال (التبويبات بتفضل)
        seen.filter { it.startsWith("s") && it !in liveKeys }.forEach { holder.removeState(it); seen.remove(it) }
    }
    val visible = nav.visibleKey
    val kind = nav.motion
    val top = nav.top
    val tab = nav.tab
    key(visible) {
        seen += visible
        val progress = remember { Animatable(if (kind == NavMotion.NONE) 1f else 0f) }
        val spec = motion<Float>(if (kind == NavMotion.TAB) Springs.SNAPPY else Springs.GENTLE)
        LaunchedEffect(Unit) { progress.animateTo(1f, spec) }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                if (reduce) {
                    alpha = p
                    return@graphicsLayer
                }
                when (kind) {
                    NavMotion.TAB -> {
                        alpha = 0.4f + 0.6f * p
                        scaleX = 0.985f + 0.015f * p
                        scaleY = scaleX
                    }
                    // الشاشة الداخلية بتدخل من الشمال في العربي (من اليمين في الإنجليزي)
                    NavMotion.PUSH -> translationX = (1f - p) * size.width * (if (rtl) -1f else 1f)
                    NavMotion.POP -> {
                        alpha = 0.5f + 0.5f * p
                        translationX = (1f - p) * 0.3f * size.width * (if (rtl) 1f else -1f)
                    }
                    NavMotion.NONE -> Unit
                }
            },
        ) {
            holder.SaveableStateProvider(visible) {
                if (top == null) registry.Root(tab) else registry.Screen(top.route)
            }
        }
    }
}
