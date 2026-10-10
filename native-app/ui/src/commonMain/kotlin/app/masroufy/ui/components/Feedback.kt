package app.masroufy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.MotionMs
import app.masroufy.ui.theme.Type

private val calm = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

/** هيكل «بيحمّل» (مستوى 1): نفس شكل المحتوى رمادي بينبض `0.5↔1` كل 1.4 ثانية — مش دوّامة في نص الشاشة. «تقليل الحركة» ⇒ ثابت. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, radius: Dp = 22.dp, strong: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val a by pulse.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1400, easing = calm), RepeatMode.Reverse), label = "alpha")
    Box(modifier.alpha(if (reduce) 1f else a).clip(RoundedCornerShape(radius)).background(if (strong) Color(0x14193D33) else Color(0x0F193D33)))
}

/**
 * الشاشة الفاضية: رسمة بخط واحد (اختيار المالك `illus` — 120×80، خط `#08634F` 1.6) + جملة 14 عريض + سطر رمادي + فعل اختياري.
 * [illustration] = null ⇒ مكان الرسمة فاضي لحد ما المالك يرسمها (كل رسمة بتتعرض عليه — ممنوع رسمة من أول محاولة).
 */
@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(top = 36.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(120.dp, 80.dp), contentAlignment = Alignment.Center) { illustration?.invoke() }
        BasicText(title, Modifier.padding(top = 8.dp), style = Type.bodyBold().copy(textAlign = TextAlign.Center))
        if (body != null) BasicText(body, style = Type.of(13).copy(color = Ink.muted, textAlign = TextAlign.Center))
        action?.invoke()
    }
}

/**
 * لمعة نعناعي على صف اتسجّل من رسالة البنك لوحده (اختيار المالك `magic` — مستوى 3): شريط `transparent 30% → rgba(166,222,193,.75) 50%
 * → transparent 70%` بيعدّي **من اليمين للشمال مرة واحدة** في 1200ms بالنابض الهادي. «تقليل الحركة» ⇒ من غير لمعة.
 */
@Composable
fun Modifier.mintSheen(play: Boolean): Modifier {
    if (!play || LocalReduceMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(MotionMs.sheen, easing = calm)) }
    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p >= 1f) return@drawWithContent
        val band = size.width
        // من اليمين (x = عرض الصف) للشمال (x = -عرض الصف)
        val startX = size.width - p * 2f * band
        drawRect(
            Brush.linearGradient(
                0.3f to Color.Transparent, 0.5f to Color(0xBFA6DEC1), 0.7f to Color.Transparent,
                start = Offset(startX - band / 2f, 0f), end = Offset(startX + band / 2f, size.height * 0.36f),
            ),
        )
    }
}

/**
 * رسالة صغيرة فوق الشريطين («سُجّلت: −42.00 ر.س · مطاعم وقهوة») — زجاج فاتح بعلامة صح. بتختفي لوحدها (اللي بيعرضها بيشيلها بعد 2.6 ثانية).
 */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    val shape = RoundedCornerShape(if (dark) 18.dp else 22.dp)
    val surface = if (dark) Modifier.clip(shape).background(Color(0xEB193D33))
    else Modifier.layeredShadow(shape, Shadows.toast).clip(shape).background(Color(0xEBFFFFFF)).innerSheen(shape, Shadows.toast)
    androidx.compose.foundation.layout.Row(
        modifier.then(surface).semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!dark) LucideIcon(Lucide.CHECK, size = 18.dp, tint = Ink.primary)
        BasicText(text, style = Type.of(13, FontWeight.Bold).copy(color = if (dark) Color.White else Ink.text), maxLines = 2)
    }
}

/** يقلب الرمز في الإنجليزي (شمال لليمين) — السهم «لقدام» في العربي بيبص للشمال. */
@Composable
fun Modifier.mirrorInLtr(): Modifier = if (LocalLayoutDirection.current == LayoutDirection.Ltr) graphicsLayer { scaleX = -1f } else this
