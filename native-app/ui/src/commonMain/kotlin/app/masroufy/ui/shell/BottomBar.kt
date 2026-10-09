package app.masroufy.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.Backdrop
import app.masroufy.ui.glass.BackdropBlur
import app.masroufy.ui.glass.BlurRadius
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.blurSupported
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.nav.Tab
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Space
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.Type
import app.masroufy.ui.theme.motion

/**
 * الشريطين تحت في التبويبات الأربعة (`BottomBar` — KOTLIN-MAP §٣ · OVERRIDES §73/§74/§76):
 * - **شريط التنقل:** زجاج سائل عايم `left/right 20 · bottom 14 · ارتفاع 70 · زاوية 26`، ٤ تبويبات + «+» في النص (64، من غير هالة).
 *   التبويب المختار = عدسة 40×30 والاسم أخضر عريض. **نقطة الإشعار** ٦×٦ من غير إطار (`top 3 · right 7` من العدسة).
 * - **«اسأل مصروفي»:** كبسولة 44 جوه لمس 48 على بعد `14 + 70 + 18 = 102` من تحت، والميكروفون دايرة 36 جوه آخرها.
 * - **بلور متدرّج تحت الشريطين:** ٤ طبقات (2 · 5 · 10 · 18) بأقنعة (0←12 · 6←20 · 14←28 · 22←36) من أول المنطقة (١٤ فوق حافة شريط
 *   السؤال)، وفوقهم لون من الخلفية. تحت أندرويد 12: اللون بس.
 * الترتيب من اليمين: الرئيسية · العمليات · «+» · الأشخاص · الاستثمار.
 */
@Composable
fun BoxScope.BottomBars(
    current: Tab,
    dots: Set<Tab>,
    backdrop: Backdrop,
    onTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    onAsk: () -> Unit,
    onMic: () -> Unit,
) {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val askBottom = Space.navBottom + Space.navHeight + Space.askGap
    Fog(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(askBottom + Space.askHeight + 14.dp + inset), backdrop)
    AskBar(
        Modifier.align(Alignment.BottomCenter).padding(start = Space.navInset, end = Space.navInset, bottom = askBottom + inset - 2.dp),
        backdrop, onAsk, onMic,
    )
    NavBar(
        Modifier.align(Alignment.BottomCenter).padding(start = Space.navInset, end = Space.navInset, bottom = Space.navBottom + inset),
        current, dots, backdrop, onTab, onAdd,
    )
}

private val FOG_LAYERS = listOf(Triple(2.dp, 0.dp, 12.dp), Triple(5.dp, 6.dp, 20.dp), Triple(10.dp, 14.dp, 28.dp), Triple(18.dp, 22.dp, 36.dp))

@Composable
private fun Fog(modifier: Modifier, backdrop: Backdrop) {
    Box(modifier) {
        if (blurSupported()) for ((blur, from, to) in FOG_LAYERS) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = from.toPx(), endY = to.toPx()), blendMode = BlendMode.DstIn)
                    },
            ) { BackdropBlur(backdrop, blur) }
        }
        Box(
            Modifier.matchParentSize().drawWithContent {
                val at = (36.dp.toPx() / size.height).coerceIn(0f, 1f)
                drawRect(Brush.verticalGradient(0f to Color(0x00EFF3ED), at to Color(0x66ECF2ED), 1f to Color(0xB8E2EDE5)))
            },
        )
    }
}

@Composable
private fun NavBar(modifier: Modifier, current: Tab, dots: Set<Tab>, backdrop: Backdrop, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(Radius.nav)
    Box(modifier.fillMaxWidth().height(Space.navHeight).semantics { contentDescription = t(TextKey.SHELL_NAV) }) {
        Box(Modifier.matchParentSize().layeredShadow(shape, Shadows.nav).clip(shape)) {
            BackdropBlur(backdrop, BlurRadius.nav, shape, saturation = 1.3f)
            Box(Modifier.matchParentSize().background(Glass.nav(!blurSupported())).innerSheen(shape, Shadows.nav))
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            TabItem(Tab.HOME, current, Tab.HOME in dots, onTab)
            TabItem(Tab.OPERATIONS, current, Tab.OPERATIONS in dots, onTab)
            PlusButton(onAdd)
            TabItem(Tab.PEOPLE, current, Tab.PEOPLE in dots, onTab)
            TabItem(Tab.INVESTMENT, current, Tab.INVESTMENT in dots, onTab)
        }
    }
}

@Composable
private fun TabItem(tab: Tab, current: Tab, dot: Boolean, onTab: (Tab) -> Unit) {
    val on = tab == current
    val press = rememberPress()
    val lens by animateFloatAsState(if (on) 1f else 0f, motion(Springs.BOUNCY), label = "lens")
    val label = t(tab.label)
    Column(
        Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 56.dp).pressScale(press)
            .tap(press, role = Role.Tab, onClick = { onTab(tab) })
            .semantics { selected = on; contentDescription = if (dot) t(TextKey.SHELL_TAB_WITH_NEW, label) else label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        val lensShape = RoundedCornerShape(15.dp)
        Box(Modifier.size(40.dp, 30.dp), contentAlignment = Alignment.Center) {
            if (lens > 0.01f) Box(
                Modifier.matchParentSize().graphicsLayer { alpha = lens.coerceIn(0f, 1f); scaleX = 0.8f + 0.2f * lens; scaleY = 0.8f + 0.2f * lens }
                    .layeredShadow(lensShape, Shadows.navLens).clip(lensShape).background(Glass.navLens).innerSheen(lensShape, Shadows.navLens),
            )
            LucideIcon(tab.icon, size = 20.dp, tint = if (on) Ink.primary else Ink.muted)
            if (dot) Box(Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = (-7).dp, y = 3.dp).size(6.dp).clip(CircleShape).background(Ink.dot))
        }
        BasicText(label, style = Type.of(11, if (on) FontWeight.Bold else FontWeight.Normal).copy(color = if (on) Ink.primary else Ink.muted), maxLines = 1)
    }
}

/** «+» في نص الشريط: دايرة 64 خضرا متدرجة بظل ناعم **من غير هالة بيضا** (OVERRIDES §76)، طالعة لفوق شوية. */
@Composable
private fun PlusButton(onAdd: () -> Unit) {
    val press = rememberPress()
    val label = t(TextKey.ADD_LABEL)
    Box(
        Modifier.offset(y = (-13).dp).size(64.dp).pressScale(press).layeredShadow(CircleShape, Shadows.plus).clip(CircleShape).background(Glass.plus)
            .innerSheen(CircleShape, Shadows.plus).tap(press, label = label, onClick = onAdd).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(Lucide.PLUS, size = 28.dp, tint = Ink.onPrimary) }
}

/** شريط «اسأل مصروفي» والميكروفون كبسولة واحدة (OVERRIDES §74). */
@Composable
private fun AskBar(modifier: Modifier, backdrop: Backdrop, onAsk: () -> Unit, onMic: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(Space.askHeight).layeredShadow(shape, Shadows.ask).clip(shape)) {
            BackdropBlur(backdrop, BlurRadius.nav, shape, saturation = 1.3f)
            Box(Modifier.matchParentSize().background(Glass.nav(!blurSupported())).innerSheen(shape, Shadows.ask))
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val press = rememberPress()
            Row(
                Modifier.weight(1f).height(48.dp).pressScale(press).tap(press, onClick = onAsk).padding(start = 14.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucideIcon(Lucide.SPARKLES, size = 18.dp, tint = Ink.primary)
                BasicText(t(TextKey.ASK_BAR), Modifier.weight(1f), style = Type.of(13).copy(color = Ink.muted), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            MicButton(onMic)
        }
    }
}

private val micGlass = app.masroufy.ui.glass.cssLinear(145f, 0f to Color(0xFAFFFFFF), 0.55f to Color(0xCCE3EFE6), 1f to Color(0xE6C9E6D4))
private val micShadow = listOf(
    app.masroufy.ui.glass.ShadowLayer(1.dp, 1.dp, 2.dp, Color.White, inset = true),
    app.masroufy.ui.glass.ShadowLayer((-1).dp, (-2).dp, 3.dp, Color(0x2908634F), inset = true),
    app.masroufy.ui.glass.ShadowLayer(0.dp, 3.dp, 8.dp, Color(0x2408634F)),
)

@Composable
private fun MicButton(onMic: () -> Unit, size: Dp = 36.dp) {
    val press = rememberPress()
    val label = t(TextKey.ASK_MIC)
    Box(
        Modifier.size(44.dp, 48.dp).pressScale(press).tap(press, label = label, onClick = onMic).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).layeredShadow(CircleShape, micShadow).clip(CircleShape).background(micGlass).innerSheen(CircleShape, micShadow),
            contentAlignment = Alignment.Center,
        ) { LucideIcon(Lucide.MIC, size = 19.dp, tint = Ink.primary) }
    }
}
