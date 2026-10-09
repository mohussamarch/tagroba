package app.masroufy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.LocalReduceMotion
import app.masroufy.ui.theme.MotionMs
import kotlin.random.Random

/**
 * قصاصات ملونة خفيفة للاحتفال (اختيار المالك `celebrate` — تسوية دين بالكامل · الوصول لهدف ادخار): ١٦ قطعة بالكتير، 6×10 بزاوية 2،
 * بألوان الهوية بس، بتنزل من فوق البطاقة اللي فيها الخبر **مرة واحدة** وتخلص في أقل من ثانيتين.
 * اللي بيحطها مسؤول: **ما تغطيش المبلغ ولا الأزرار** (حطها في مساحة فوق البطاقة)، وما تتكررش لو الشاشة اتفتحت تاني ([key]).
 * «تقليل الحركة» ⇒ ولا حاجة (الجملة بس — شغل الشاشة).
 */
private val CONFETTI = listOf(Color(0xFFA6DEC1), Color(0xFF9BC5FF), Color(0xFFF3C96B), Color(0xFFFFA8AC), Color(0xFF08634F))

private class Piece(val x: Float, val delay: Float, val drift: Float, val spin: Float, val color: Color)

@Composable
fun Confetti(modifier: Modifier = Modifier, key: Any = Unit, pieces: Int = 16) {
    if (LocalReduceMotion.current) return
    val count = pieces.coerceIn(1, 16)
    val set = remember(key) {
        val r = Random(key.hashCode())
        List(count) { Piece(r.nextFloat(), r.nextFloat() * 0.25f, r.nextFloat() * 2f - 1f, r.nextFloat() * 540f - 270f, CONFETTI[it % CONFETTI.size]) }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(MotionMs.confettiMax - 200, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f))) }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val w = 6.dp.toPx()
        val h = 10.dp.toPx()
        for (piece in set) {
            val local = ((p - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val x = piece.x * size.width + piece.drift * 24.dp.toPx() * local
            val y = -h + local * (size.height + h)
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotate(piece.spin * local, pivot = Offset(x + w / 2, y + h / 2)) {
                drawRoundRect(piece.color.copy(alpha = alpha), topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
    }
}
