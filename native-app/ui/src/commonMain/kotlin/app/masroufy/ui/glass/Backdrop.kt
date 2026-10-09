package app.masroufy.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **الزجاج = تمويه اللي ورا العنصر** (`backdrop-filter: blur()` في الوصفات). Compose ما فيهوش ده جاهز، فهنا بإيدنا من غير مكتبة:
 * المحتوى (الشاشة) بيتسجل في طبقة رسم ([backdropSource])، والعنصر الزجاج بيرسم **نفس الطبقة** مكانها تحته ومموّهة ([BackdropBlur]).
 * - التمويه شغال من **أندرويد 12 (API 31)** ([blurSupported]). أقل من كده ⇒ مفيش تمويه والتدرج نفسه بشفافية أعلى (≈ 0.94) —
 *   DESIGN-SYSTEM «ملاحظات للتنفيذ». كل وصفة زجاج في [Glass] ليها نسخة `opaque`.
 * - ⚠️ العنصر الزجاج **لازم يبقى برّه** المحتوى المتسجل (شريط التنقل · اللوحات · القوائم في `OverlayHost`) — وإلا الطبقة بترسم نفسها.
 */
expect fun blurSupported(): Boolean

@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** المحتوى اللي هيتموّه ورا الزجاج — بيترسم عادي وبيتسجل في نفس الوقت. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * المحتوى اللي ورا العنصر ده مموّه بـ[radius] (و`saturate` = [saturation]) ومقصوص على [shape]. تحت أندرويد 12 ما بيرسمش حاجة
 * (الخلفية `opaque` بتغطي). بيتحط **تحت** محتوى العنصر (أول ابن في `Box`) عشان التمويه ما يلمسش الأيقونات والنص.
 */
@Composable
fun BoxScope.BackdropBlur(backdrop: Backdrop?, radius: Dp, shape: Shape = RectangleShape, saturation: Float = 1f) {
    if (backdrop == null || !blurSupported()) return
    var mine by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { mine = it.positionInRoot() }
            .clip(shape)
            .graphicsLayer {
                val px = radius.toPx()
                if (px > 0f) renderEffect = BlurEffect(px, px, TileMode.Clamp)
                clip = true
                this.shape = shape
            }
            .drawBehind {
                val shift = backdrop.origin - mine
                translate(shift.x, shift.y) {
                    if (saturation == 1f) drawLayer(backdrop.layer)
                    else drawIntoCanvas { canvas ->
                        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
                        val s = backdrop.layer.size
                        canvas.saveLayer(Rect(0f, 0f, s.width.toFloat(), s.height.toFloat()), paint)
                        drawLayer(backdrop.layer)
                        canvas.restore()
                    }
                }
            },
    )
}

/** قيم التمويه من الوصفات. */
object BlurRadius {
    val nav = 24.dp
    val lens = 8.dp
    val veil = 6.dp
    val menuVeil = 8.dp
    val askVeil = 18.dp
}
