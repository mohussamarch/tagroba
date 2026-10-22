package app.masroufy.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform

/*
 * رسم عملة «عملات بتترص» — هندسة `CoinLoader.dc.html` بالظبط: مربع 64، العملة 48×22 (الحافة 6 تحت الوش) من اليسار 8 والأسفل 8 وبعدها كل 7،
 * حافة بتدرّج غامق، حز مسنّن، وش بتدرّج دايري، حلقة داخلية، ولمعة بتعدّي على الوش. كل الإحداثيات بوحدات التصميم والرسم بيتكبّر بمقياس واحد.
 */
private const val BOX = 64f
private const val COIN_W = 48f
private const val COIN_H = 22f
private const val LEFT = 8f
private const val BASE_BOTTOM = 8f
private const val STEP = 7f

private val EDGE = listOf(Color(0xFFC08A22), Color(0xFF7E5410))
private val TOP_STOPS = arrayOf(0f to Color(0xFFFFF1BF), 0.45f to Color(0xFFF0C75C), 1f to Color(0xFFC9952B))
private val INNER = listOf(Color(0xFFD9A93C), Color(0xFFF3D17A))
private val INNER_STROKE = Color(0xFFB5832A)
private val RING = Color(0xFFFFF4CC).copy(alpha = 0.55f)
private val REED = Color(0xFFF6D98A).copy(alpha = 0.45f)
private val GLINT = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f))
private val FLOOR = listOf(Color(0xFF193D33).copy(alpha = 0.28f), Color(0xFF193D33).copy(alpha = 0f))

private val edgePath = Path().apply {
    moveTo(2f, 8f); lineTo(2f, 14f)
    arcTo(Rect(2f, 7f, 46f, 21f), 180f, -180f, false)
    lineTo(46f, 8f)
    arcTo(Rect(2f, 1f, 46f, 15f), 0f, 180f, false)
    close()
}
private val reedPath = Path().apply {
    moveTo(3f, 11.5f)
    arcTo(Rect(3f, 4.9f, 45f, 18.1f), 180f, -180f, false)
}
private val facePath = Path().apply { addOval(Rect(2f, 1f, 46f, 15f)) }
private val glintPath = Path().apply {
    // مستطيل 14..23 × -2..18 مائل -22 درجة (skewX)
    moveTo(14.8f, -2f); lineTo(23.8f, -2f); lineTo(15.7f, 18f); lineTo(6.7f, 18f); close()
}

/** رسم الرصة كلها: ظل الأرضية ثم العملات من تحت لفوق. [shines] = إزاحة اللمعة لكل عملة أو null من غير لمعة. */
internal fun DrawScope.drawCoinStack(frames: List<CoinFrame>, shines: List<Float>?, floor: FloorFrame) {
    val s = size.minDimension / BOX
    withTransform({ scale(s, s, Offset.Zero) }) {
        withTransform({ translate(32f, 56f); scale(floor.scaleX, 4f / 22f, Offset.Zero) }) {
            drawCircle(Brush.radialGradient(FLOOR, center = Offset.Zero, radius = 22f), radius = 22f, center = Offset.Zero, alpha = floor.alpha)
        }
        frames.forEachIndexed { i, f -> if (f.alpha > 0f) drawOneCoin(i, f, shines?.get(i)) }
    }
}

private fun DrawScope.drawOneCoin(index: Int, f: CoinFrame, shine: Float?) {
    val top = BOX - (BASE_BOTTOM + STEP * index) - COIN_H
    withTransform({
        translate(LEFT, top)
        translate(0f, f.offsetY)
        scale(f.scaleX, f.scaleY, Offset(COIN_W / 2f, COIN_H))
    }) {
        val canvas = drawContext.canvas
        val layer = Paint().apply { alpha = f.alpha }
        val bounds = Rect(-20f, -80f, 70f, 60f)
        canvas.saveLayer(bounds, layer)
        drawCoinBody(shine)
        canvas.restore()
    }
}

private fun DrawScope.drawCoinBody(shine: Float?) {
    drawPath(edgePath, Brush.verticalGradient(EDGE, startY = 8f, endY = 21f))
    drawPath(reedPath, REED, style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.9f, 1.9f))))
    // وش العملة: دايرة 44 بتدرّج دايري اتفلطحت لـ44×14 (نفس تدرّج «objectBoundingBox» في النموذج)
    withTransform({ translate(2f, 1f); scale(1f, 14f / 44f, Offset.Zero) }) {
        drawCircle(Brush.radialGradient(*TOP_STOPS, center = Offset(0.36f * 44f, 0.3f * 44f), radius = 0.8f * 44f), radius = 22f, center = Offset(22f, 22f))
    }
    val inner = Rect(8.5f, 3.2f, 39.5f, 12.8f)
    drawOval(Brush.verticalGradient(INNER, startY = 3.2f, endY = 12.8f), inner.topLeft, inner.size)
    drawOval(INNER_STROKE, inner.topLeft, inner.size, style = Stroke(0.8f))
    drawOval(RING, Offset(8.5f, 3.8f), inner.size, style = Stroke(0.7f))
    if (shine != null) clipPath(facePath) {
        withTransform({ translate(shine, 0f) }) {
            drawPath(glintPath, Brush.horizontalGradient(GLINT, startX = 10.4f, endX = 19.4f))
        }
    }
}
