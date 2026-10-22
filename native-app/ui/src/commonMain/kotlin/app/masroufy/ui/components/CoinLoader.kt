package app.masroufy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.core.R06Key
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.LocalReduceMotion

private val COIN_TOP = Color(0xFFF1CF7A)
private val COIN_BOTTOM = Color(0xFFC9952B)
private val COIN_RIM = Color(0xFFA47619)
private val COIN_DROP_SHADOW = Color(0x1F193D33)

/** مقاسات التصميم (مربع 64): العملة 40×14 · البداية من اليسار 12 · الأسفل 8 وبعدها كل 12. */
private const val BOX = 64f
private const val COIN_W = 40f
private const val COIN_H = 14f
private const val COIN_LEFT = 12f
private const val COIN_BASE_BOTTOM = 8f
private const val COIN_STEP = 12f
private const val RIM = 3f

/**
 * علامة التحميل بتاعة البرنامج «عملات بتترص» (قرار المالك 2026-10-22 — بدل دايرة أندرويد): 3 عملات دهبي بتنزل فوق بعض وتختفي وتعيد.
 * **مكوّن واحد** لكل الأماكن: تحميل الشاشة [size] = 64.dp · السحب للتحديث 22.dp جنب «بنحدّث…» · جوه الزرار الأساسي 18.dp و[light] = true.
 * «تقليل الحركة» (`LocalReduceMotion` — مقياس حركة الجهاز صفر) ⇒ العملات ثابتة. التوقيت دالة صافية ([CoinLoaderTiming]).
 */
@Composable
fun CoinLoader(modifier: Modifier = Modifier, size: Dp = 64.dp, light: Boolean = false, description: String? = t(R06Key.LOADER_A11Y)) {
    val reduce = LocalReduceMotion.current
    val time: State<Float> = if (reduce) remember { mutableStateOf(0f) } else rememberInfiniteTransition(label = "coins")
        .animateFloat(0f, CoinLoaderTiming.CYCLE_MS.toFloat(), infiniteRepeatable(tween(CoinLoaderTiming.CYCLE_MS, easing = LinearEasing)), label = "coinsTime")
    val top = if (light) lerp(COIN_TOP, Color.White, 0.45f) else COIN_TOP
    val bottom = if (light) lerp(COIN_BOTTOM, Color.White, 0.45f) else COIN_BOTTOM
    val rim = if (light) lerp(COIN_RIM, Color.White, 0.45f) else COIN_RIM
    val a11y = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Canvas(modifier.size(size).then(a11y)) {
        val s = this.size.minDimension / BOX
        for (i in 0 until CoinLoaderTiming.COINS) {
            val f = if (reduce) CoinLoaderTiming.STATIC else CoinLoaderTiming.frame(time.value, i)
            if (f.alpha <= 0f) continue
            val y = (BOX - (COIN_BASE_BOTTOM + COIN_STEP * i) - COIN_H + f.offsetY) * s
            val x = COIN_LEFT * s
            val w = COIN_W * s
            // ظل خفيف تحت العملة، وبعدين الحافة الغامقة، وبعدين وش العملة (تدرّج رأسي) — الحافة 3 وحدات تحت
            drawOval(COIN_DROP_SHADOW, Offset(x, y + 1f * s), Size(w, COIN_H * s), alpha = f.alpha)
            drawOval(rim, Offset(x, y), Size(w, COIN_H * s), alpha = f.alpha)
            drawOval(Brush.verticalGradient(listOf(top, bottom), startY = y, endY = y + (COIN_H - RIM) * s), Offset(x, y), Size(w, (COIN_H - RIM) * s), alpha = f.alpha)
        }
    }
}
