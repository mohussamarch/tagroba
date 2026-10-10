package app.masroufy.ui.shell

import app.masroufy.core.UiKey
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.TextKey
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.glass.Glass
import app.masroufy.ui.glass.Shadows
import app.masroufy.ui.glass.innerSheen
import app.masroufy.ui.glass.layeredShadow
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Springs
import app.masroufy.ui.theme.motion
import kotlin.math.max

/**
 * دايرة صاحب الحساب (`MeAvatar` — KOTLIN-MAP §3 · OVERRIDES §76): زجاج على الفاتح وجواها الكاركتر، و**شريط الاكتمال برّه الدايرة**
 * (سُمكه `max(3, size/16)` وبينه وبين الدايرة 2، التقدم `#13764D` على `rgba(25,61,51,0.12)`) وبيختفي عند 100%.
 * [percent] = null ⇒ مفيش شريط (حسبة «ملفك %» لسه ما اتبنتش — مش بنعرض رقم من غير مصدر). الكاركتر النهائي لسه بيتصمم: [look] 1–6 أشكال مؤقتة.
 */
private val LOOKS = listOf(
    Color(0xFF0E8A6E) to Color(0xFF08634F), Color(0xFF3866A7) to Color(0xFF2A4F85), Color(0xFFA36A21) to Color(0xFF7E5118),
    Color(0xFFA55060) to Color(0xFF7F3C49), Color(0xFF4D747C) to Color(0xFF39575D), Color(0xFF637570) to Color(0xFF4B5A56),
)

private val ringProgress = Color(0xFF13764D)
private val ringTrack = Color(0x1F193D33)

@Composable
fun MeAvatar(size: Dp, percent: Int?, modifier: Modifier = Modifier, look: Int = 1) {
    val pct = percent?.coerceIn(0, 100)
    val showRing = pct != null && pct < 100
    val sweep by animateFloatAsState(((pct ?: 0) / 100f) * 360f, motion(Springs.GENTLE), label = "ring")
    val (head, body) = LOOKS[(look - 1).coerceIn(0, LOOKS.lastIndex)]
    val label = if (showRing) t(UiKey.ME_PROFILE_PERCENT, sentenceNumber(pct!!)) else t(UiKey.ME_PROFILE)
    Box(modifier.size(size).semantics { contentDescription = label; role = Role.Image }, contentAlignment = Alignment.Center) {
        if (showRing) {
            val thick = max(3f, size.value / 16f)
            Canvas(Modifier.requiredSize(size + (thick * 2 + 4).dp)) {
                val w = thick.dp.toPx()
                val inset = w / 2f
                val arcSize = Size(this.size.width - w, this.size.height - w)
                drawArc(ringTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                // من فوق مع عقارب الساعة (زي `conic-gradient` في النموذج)
                drawArc(ringProgress, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt))
            }
        }
        Box(
            Modifier.size(size).layeredShadow(CircleShape, Shadows.lensOnLight).clip(CircleShape).background(Glass.lensOnLight)
                .innerSheen(CircleShape, Shadows.lensOnLight),
        ) {
            // الشكل المؤقت: راس + كتاف (viewBox 40) بعرض 84% ولازق تحت
            Canvas(Modifier.fillMaxSize().padding(horizontal = size * 0.08f)) {
                val s = this.size.width / 40f
                translate(top = this.size.height - 40f * s) {
                    scale(s, pivot = Offset.Zero) {
                        drawCircle(head, radius = 7.5f, center = Offset(20f, 15.5f))
                        drawPath(figureBody, body)
                    }
                }
            }
        }
    }
}

/** `M4.5 41c0-9 6.9-15 15.5-15s15.5 6 15.5 15z` من النموذج. */
private val figureBody = Path().apply {
    moveTo(4.5f, 41f)
    cubicTo(4.5f, 32f, 11.4f, 26f, 20f, 26f)
    cubicTo(28.6f, 26f, 35.5f, 32f, 35.5f, 41f)
    close()
}
