package app.masroufy.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.masroufy.core.CharacterColorKey
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * «شخصيتك» — الرسم في Compose: المشهد ([characterScene]) بيترسم «contain + في النص» جوه المساحة (زي `background: center / contain`
 * في النموذج)، ومقصوص على الـviewBox (زي `<img>` بالـSVG). النقشات بتترسم في نفس تحويلة العنصر (زي `userSpaceOnUse`).
 */
@Composable
fun CharacterArt(look: CharacterLook, layer: CharacterLayer, modifier: Modifier = Modifier, label: String? = null) {
    val scene = remember(look, layer) { characterScene(look, layer) }
    Canvas(if (label != null) modifier.semantics { contentDescription = label } else modifier) { drawCharacter(scene) }
}

/**
 * الجسم كله بمقاسه الكامل [width]×[height]، متثبّت **من فوق** وفي النص، والزيادة تحت بتتقص على حدود المساحة (كارت «الأشخاص» ومربّع
 * «في الأشخاص» — `overflow: hidden` + `justify-content: center` في النموذج). `requiredSize` لوحده وهو أطول من المساحة كان بيتحط في
 * **نصها** (Compose بيوسّط اللي أكبر من القيود) فالراس بتطلع برّه من فوق وتتقص (r041b).
 */
@Composable
fun CharacterFromTop(look: CharacterLook, width: Dp, height: Dp, modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.clipToBounds(), contentAlignment = Alignment.TopCenter) {
        CharacterArt(look, CharacterLayer.FULL, Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).size(width, height), label)
    }
}

/** الشكل المحايد (لسه ما اتبنتش): راس ودايرة كتاف، 84% من العرض وتحت في النص (`MeAvatar.dc.html`). */
@Composable
fun NeutralFigure(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width * 0.84f
        val left = (size.width - s) / 2; val top = size.height - s
        clipRect(left, top, left + s, top + s) {
            withTransform({ translate(left, top); scale(s / 40f, s / 40f, Offset.Zero) }) {
                drawCircle(Color(0xFF4D747C), 7.5f, Offset(20f, 15.5f))
                drawPath(NEUTRAL_BODY.composePath(), Color(0xFF39575D))
            }
        }
    }
}

private val NEUTRAL_BODY = PathOp("M4.5 41 C4.5 32 11.4 26 20 26 C28.6 26 35.5 32 35.5 41 Z", "#39575D")

/** ألوان الاختيارات في شاشة البناء (نفس لوحات الرسم). */
object CharacterPalette {
    val skin: List<Color> = SKIN.map { cssColor(it.first) }
    val hair: List<Color> = HAIRC.map(::cssColor)
    val wrap: List<Color> = WRAP.map(::cssColor)
    val outfit: List<Color> = OUTC.map(::cssColor)
    val abaya: List<Color> = ABAYA.map(::cssColor)
}

/** لوحة الألوان لمفتاح الجزء — ألوان العباية لما اللبس عباية (5). */
fun CharacterPalette.forKey(key: CharacterColorKey, look: CharacterLook): List<Color> = when (key) {
    CharacterColorKey.SKIN -> skin
    CharacterColorKey.HAIR -> hair
    CharacterColorKey.WRAP -> wrap
    CharacterColorKey.OUTFIT -> if (look.outfit == 5) abaya else outfit
}

/** رسم المشهد: contain + في النص، ومقصوص على الـviewBox. */
fun DrawScope.drawCharacter(scene: CharacterScene) {
    val vb = scene.viewBox
    if (size.width <= 0f || size.height <= 0f) return
    val s = min(size.width / vb.width, size.height / vb.height)
    val dx = (size.width - vb.width * s) / 2; val dy = (size.height - vb.height * s) / 2
    clipRect(dx, dy, dx + vb.width * s, dy + vb.height * s) {
        withTransform({ translate(dx, dy); scale(s, s, Offset.Zero); translate(-vb.x, -vb.y) }) {
            scene.ops.forEach { drawOp(it) }
        }
    }
}

private val colors = HashMap<String, Color>()

/** `#RRGGBB` أو `rgba(r,g,b,a)` ⇒ لون. */
internal fun cssColor(css: String): Color = colors.getOrPut(css) {
    if (css.startsWith("#")) Color(0xFF000000 or css.substring(1).toLong(16))
    else {
        val p = css.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
        Color(p[0] / 255f, p[1] / 255f, p[2] / 255f, p[3])
    }
}

private fun stroke(o: Outline) = Stroke(
    width = o.width.toFloat(),
    cap = if (o.cap == LineCap.ROUND) StrokeCap.Round else StrokeCap.Butt,
    join = if (o.join == LineJoin.ROUND) StrokeJoin.Round else StrokeJoin.Miter,
    pathEffect = o.dash?.let { d -> PathEffect.dashPathEffect(d.map { it.toFloat() }.toFloatArray()) },
)

internal fun PathOp.composePath(): Path = (cache as? Path) ?: Path().also { p ->
    for (c in cmds) when (c) {
        is PathCmd.Move -> p.moveTo(c.x.toFloat(), c.y.toFloat())
        is PathCmd.Line -> p.lineTo(c.x.toFloat(), c.y.toFloat())
        is PathCmd.Quad -> p.quadraticTo(c.x1.toFloat(), c.y1.toFloat(), c.x.toFloat(), c.y.toFloat())
        is PathCmd.Cubic -> p.cubicTo(c.x1.toFloat(), c.y1.toFloat(), c.x2.toFloat(), c.y2.toFloat(), c.x.toFloat(), c.y.toFloat())
        PathCmd.Close -> p.close()
    }
    p.fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
    cache = p
}

private fun DrawScope.drawOp(op: CharOp) {
    when (op) {
        is PathOp -> {
            val path = op.composePath()
            val a = op.opacity.toFloat()
            when (op.fill) {
                null -> Unit
                "url(#shm)" -> drawPattern(path, shemagh = true)
                "url(#tq)" -> drawPattern(path, shemagh = false)
                else -> drawPath(path, cssColor(op.fill), alpha = a)
            }
            op.outline?.let { o -> drawPath(path, cssColor(o.color), alpha = a, style = stroke(o)) }
        }
        is CircleOp -> {
            val c = Offset(op.cx.toFloat(), op.cy.toFloat())
            op.fill?.let { drawCircle(cssColor(it), op.r.toFloat(), c) }
            op.outline?.let { o -> drawCircle(cssColor(o.color), op.r.toFloat(), c, style = stroke(o)) }
        }
        is EllipseOp -> drawOval(
            cssColor(op.fill), Offset((op.cx - op.rx).toFloat(), (op.cy - op.ry).toFloat()), Size((2 * op.rx).toFloat(), (2 * op.ry).toFloat()),
            alpha = op.opacity.toFloat(),
        )
        is RectOp -> {
            val tl = Offset(op.x.toFloat(), op.y.toFloat()); val sz = Size(op.w.toFloat(), op.h.toFloat()); val r = CornerRadius(op.rx.toFloat())
            op.fill?.let { drawRoundRect(cssColor(it), tl, sz, r) }
            op.outline?.let { o -> drawRoundRect(cssColor(o.color), tl, sz, r, style = stroke(o)) }
        }
        is GroupOp -> withTransform({
            op.transform.forEach { tf ->
                when (tf) {
                    is Tf.Move -> translate(tf.x.toFloat(), tf.y.toFloat())
                    is Tf.Scale -> scale(tf.x.toFloat(), tf.y.toFloat(), Offset.Zero)
                }
            }
        }) { op.children.forEach { drawOp(it) } }
    }
}

/**
 * نقشة الشماغ (مربعات 6×6 مايلة 45° — أحمر بنقطة بيضا r 1.15) أو نقشة الطاقية (4×4 — نقطة r 0.7 غامقة خفيفة) جوه المسار،
 * في نفس إحداثيات العنصر. النقط كلها في نداء رسم واحد (`drawPoints` بأطراف مدوّرة = دواير).
 */
private fun DrawScope.drawPattern(path: Path, shemagh: Boolean) {
    val b = path.getBounds()
    val tile = if (shemagh) 6f else 4f
    clipPath(path) {
        if (shemagh) drawPath(path, Color(0xFFD2363C))
        fun dots(x0: Float, y0: Float, x1: Float, y1: Float, color: Color, r: Float) {
            val pts = ArrayList<Offset>()
            for (i in floor(x0 / tile).toInt() - 1..ceil(x1 / tile).toInt() + 1) {
                for (j in floor(y0 / tile).toInt() - 1..ceil(y1 / tile).toInt() + 1) pts += Offset(i * tile + tile / 2, j * tile + tile / 2)
            }
            drawPoints(pts, PointMode.Points, color, strokeWidth = 2 * r, cap = StrokeCap.Round)
        }
        if (shemagh) {
            // حدود المسار في إحداثيات النقشة (دوران -45°)
            val k = (sqrt(2.0) / 2).toFloat()
            val xs = listOf(b.left, b.right); val ys = listOf(b.top, b.bottom)
            val px = xs.flatMap { x -> ys.map { y -> x * k + y * k } }; val py = xs.flatMap { x -> ys.map { y -> -x * k + y * k } }
            rotate(45f, Offset.Zero) { dots(px.min(), py.min(), px.max(), py.max(), Color(0xFFFBEFEC), 1.15f) }
        } else {
            dots(b.left, b.top, b.right, b.bottom, Color(0f, 0f, 0f, 0.10f), 0.7f)
        }
    }
}

