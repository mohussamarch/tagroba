package app.masroufy.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import app.masroufy.core.R06Key
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.Radius
import app.masroufy.ui.theme.Type

/**
 * الأزرار بحالاتها الأربعة (DESIGN-SYSTEM «حالات التفاعل»): عادي · مضغوط (`0.97` + الظل البعيد للنص) · معطّل (`opacity 0.45` من غير
 * ظل بعيد — الزرار المعطّل لازم جنبه سبب لو مش واضح) · بيحمّل (بيحافظ على عرضه، دوّامة 18 بلون النص، ومفيش ضغط تاني).
 */

/** الزر الأساسي: أخضر متدرج، زاوية 18، ارتفاع 48 (52 للزرار الكبير في آخر اللوحة). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 48.dp,
    leading: Lucide? = null,
    /** r041b P7: الضغط على الزرار وهو معطّل ⇒ الشاشة تقول الناقص (من غير ضغط ولا ظل). null ⇒ المعطّل ما بيردش زي الأول. */
    onDisabledClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPress()
    val active = enabled && !loading
    val explains = !enabled && !loading && onDisabledClick != null
    val far = farShadowFactor(press, enabled)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(press, active)
            .layeredShadow(shape, Shadows.primary, far)
            .clip(shape)
            .background(Glass.primary)
            .innerSheen(shape, Shadows.primary)
            .tap(press, active || explains, onClick = if (explains) onDisabledClick ?: onClick else onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CoinLoader(size = 18.dp, description = null)
        else if (leading != null) LucideIcon(leading, size = 20.dp, tint = Ink.onPrimary)
        BasicText(if (loading) t(R06Key.LOADER_SAVING) else text, style = Type.of(if (height >= 52.dp) 16 else 15, FontWeight.Bold).copy(color = Ink.onPrimary, textAlign = TextAlign.Center))
    }
}

/** الزر الثانوي: كارت طافي والنص أخضر عريض. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 48.dp, leading: Lucide? = null) {
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPress()
    val far = farShadowFactor(press, enabled)
    Row(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(press, enabled)
            .layeredShadow(shape, Shadows.card, far)
            .clip(shape)
            .background(Glass.card)
            .innerSheen(shape, Shadows.card)
            .tap(press, enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) LucideIcon(leading, size = 20.dp, tint = Ink.primary)
        BasicText(text, style = Type.of(15, FontWeight.Bold).copy(color = Ink.primary, textAlign = TextAlign.Center))
    }
}

/** زرار نصي هادي على خلفية `rgba(8,99,79,0.08)` (مثلًا «إدارة البلدان»، «أعد الإرسال»). [muted] = رمادي من غير خلفية («إلغاء»). */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 48.dp, muted: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    val press = rememberPress()
    Box(
        modifier
            .height(height)
            .defaultMinSize(minWidth = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(press, enabled)
            .clip(shape)
            .background(if (muted) Color.Transparent else Color(0x1408634F))
            .tap(press, enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Type.of(14, FontWeight.Bold).copy(color = if (muted) Ink.muted else Ink.primary, textAlign = TextAlign.Center))
    }
}

/**
 * «الكلمة والسهم بقوا رمز» (KOTLIN-MAP §3): زرار 44×44 زاوية 16، الرمز `#08634F` على `rgba(8,99,79,0.08)` + اسم لقارئ الشاشة.
 * صفوف القوايم بتفضل بكلامها.
 */
@Composable
fun IconButton44(icon: Lucide, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(Radius.lensSmall)
    val press = rememberPress()
    Box(
        modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(press, enabled)
            .clip(shape)
            .background(Color(0x1408634F))
            .tap(press, enabled, label = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { LucideIcon(icon, size = 20.dp, tint = Ink.primary) }
}

/**
 * زرار رأس الأقسام (48×48 زاوية 18 — الترس والجرس والرجوع): كارت طافي. [selected] = مفتوح (خلفية `#DCEBD6` بحد أخضر 1.5).
 * [badge] بيترسم فوقه (نقطة الإشعار).
 */
@Composable
fun SurfaceIconButton(
    icon: Lucide,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    iconSize: Dp = 22.dp,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radius.control)
    val press = rememberPress()
    val far = farShadowFactor(press)
    val surface = if (selected) Modifier.clip(shape).background(Ink.selected).insetRing(shape, 1.5.dp, Ink.primary)
    else Modifier.layeredShadow(shape, Shadows.card, far).clip(shape).background(Glass.card).innerSheen(shape, Shadows.card)
    Box(
        modifier.size(48.dp).pressScale(press).then(surface).tap(press, label = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(icon, size = iconSize, tint = Ink.text)
        badge?.invoke(this)
    }
}
