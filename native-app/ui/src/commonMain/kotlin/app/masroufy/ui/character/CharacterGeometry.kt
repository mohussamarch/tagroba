package app.masroufy.ui.character

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin

/**
 * «شخصيتك» — الهندسة: حافة الوش وكل اللي فوق الراس بيتحسب منها (نقل `CharacterArt.dc.html` @2df8134d سطر بسطر).
 * الأسماء نفس أسماء النموذج (P · tY · arc · ring · hw · cr · shape · cut · sym · bumps) عشان المقارنة تبقى سهلة.
 */
internal val SKIN = listOf(
    "#F3D5C0" to "#D9AE93", "#E8BE9C" to "#C99773", "#D4A07A" to "#B27E58", "#B57E58" to "#93603E", "#8C5A3C" to "#6E432A", "#5E3C28" to "#47291A",
)
internal val HAIRC = listOf("#1E1916", "#3A2A1F", "#6A4A2F", "#A47A4E", "#C8A46C", "#9B9A95")
internal val WRAP = listOf("#26364A", "#5E4634", "#1E1E1E", "#7E8C88", "#8E4A57", "#E9E2D6")
internal val OUTC = listOf("#F4F3EE", "#2E5E8C", "#3D4A45", "#8A6A4A", "#0E6B57", "#7A4655")
internal val ABAYA = listOf("#1C1C1E", "#2B3140", "#4A3B32", "#585D5A", "#5E2F3B", "#D8CBB6")

/** `pick` في النموذج: الرقم برّه القايمة بيتقصّ لأقرب طرف. */
internal fun <T> pick(list: List<T>, i: Int): T = list[i.coerceIn(0, list.size - 1)]

/** `r1`: تقريب لأقرب عُشر (النموذج بيقرّب أرقام المسارات). */
internal fun r1(v: Double): Double = jsRound(v * 10) / 10

/** `Math.round` بتاع JS (النص بيتقرّب لفوق — مش للزوجي زي `kotlin.math.round`). */
internal fun jsRound(v: Double): Double = floor(v + 0.5)

/** رقم بشكل JS (`26` مش `26.0`). */
internal fun n(v: Double): String {
    if (v == 0.0) return "0"
    val l = v.toLong()
    return if (l.toDouble() == v && abs(v) < 1e15) l.toString() else v.toString()
}
internal fun n(v: Int): String = v.toString()

internal class Pt(val x: Double, val y: Double)

internal fun M(p: Pt) = "M" + n(r1(p.x)) + " " + n(r1(p.y))
internal fun L(p: Pt) = " L" + n(r1(p.x)) + " " + n(r1(p.y))
internal fun mir(p: Pt) = Pt(200 - p.x, p.y)

internal fun shade(hex: String, f: Double): String = "#" + listOf(1, 3, 5).joinToString("") { i ->
    val c = jsRound(hex.substring(i, i + 2).toInt(16) * f).toInt().coerceIn(0, 255)
    c.toString(16).padStart(2, '0')
}

internal fun light(hex: String): Boolean {
    val c = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) }
    return c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11 > 150
}

// ===== حافة الوش =====
internal const val CY = 84.0

/** rx/ry نص العرض والطول · n: 2 بيضاوي، 3.2 مربع · tp: قد إيه الدقن أضيق من الخدود. */
internal class Face(val rx: Double, val ry: Double, val n: Double, val tp: Double) {
    val top = CY - ry
    val bot = CY + ry
}

internal val FACES = listOf(Face(30.0, 37.0, 2.0, 0.13), Face(32.0, 35.0, 2.0, 0.06), Face(28.0, 39.0, 2.0, 0.12), Face(31.0, 37.0, 3.2, 0.1))

internal fun geo(k: Int): Face = FACES.getOrElse(k) { FACES[0] }

/** نقطة على الحافة منفوخة p عند الزاوية t (t = 3π/2 فوق الراس). */
internal fun P(g: Face, p: Double, t: Double): Pt {
    val c = cos(t); val s = sin(t); val e = 2 / g.n
    val sx = sign(c) * abs(c).pow(e); val sy = sign(s) * abs(s).pow(e)
    val k = if (s > 0) 1 - g.tp * s * s else 1.0
    return Pt(100 + (g.rx + p) * sx * k, CY + (g.ry + p) * sy)
}

internal fun tY(g: Face, p: Double, y: Double): Double {
    val q = max(-1.0, min(1.0, (y - CY) / (g.ry + p)))
    return asin(sign(q) * abs(q).pow(g.n / 2))
}

/** الحافة من الشمال (عند yl) فوق الراس لليمين (عند yr). */
internal fun arc(g: Face, p: Double, yl: Double, yr: Double, m: Int): List<Pt> {
    val a0 = PI - tY(g, p, yl); val a1 = 2 * PI + tY(g, p, yr)
    return (0..m).map { i -> P(g, p, a0 + (a1 - a0) * i / m) }
}

internal fun ring(g: Face, p: Double, m: Int): List<Pt> = (0 until m).map { i -> P(g, p, 2 * PI * i / m) }

internal fun hw(g: Face, p: Double, y: Double): Double = 100 - P(g, p, PI - tY(g, p, y)).x

/** منحنى ناعم بيعدّي على النقط (Catmull-Rom ⇒ Bézier تكعيبي) من النقطة الحالية. */
internal fun cr(pts: List<Pt>, closed: Boolean): String {
    val sz = pts.size
    fun at(i: Int) = pts[if (closed) (i + sz) % sz else max(0, min(sz - 1, i))]
    val d = StringBuilder()
    for (i in 0 until (if (closed) sz else sz - 1)) {
        val a = at(i - 1); val b = at(i); val c = at(i + 1); val e = at(i + 2)
        d.append(" C").append(n(r1(b.x + (c.x - a.x) / 6))).append(' ').append(n(r1(b.y + (c.y - a.y) / 6))).append(' ')
            .append(n(r1(c.x - (e.x - b.x) / 6))).append(' ').append(n(r1(c.y - (e.y - b.y) / 6))).append(' ')
            .append(n(r1(c.x))).append(' ').append(n(r1(c.y)))
    }
    return d.toString()
}

internal fun shape(pts: List<Pt>) = M(pts[0]) + cr(pts, true) + "Z"

// ===== العناصر (زي `path` و`line` في النموذج) =====
internal typealias Ops = List<CharOp>

internal fun path(d: String, fill: String, outline: Outline? = null, evenOdd: Boolean = false, opacity: Double = 1.0): Ops =
    listOf(PathOp(d, fill, outline, evenOdd, opacity))

/** خط: أطراف مدوّرة ولفّات مدوّرة، من غير تعبئة. */
internal fun line(d: String, col: String, w: Double, dash: List<Double>? = null): Ops =
    listOf(PathOp(d, null, Outline(col, w, LineCap.ROUND, LineJoin.ROUND, dash)))

internal fun circle(cx: Double, cy: Double, r: Double, fill: String?, outline: Outline? = null): Ops = listOf(CircleOp(cx, cy, r, fill, outline))
internal fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, fill: String, opacity: Double = 1.0): Ops =
    listOf(EllipseOp(cx, cy, rx, ry, fill, opacity))

// ===== الشعر: أدوات القَصّة =====
/** قَصّة: حافة برّه منفوخة p من الصدغ للصدغ فوق الراس، وخط منبت الشعر (من اليمين للشمال) جوه على الجبهة. */
internal fun cut(g: Face, p: Double, yb: Double, ln: List<Pt>): String {
    val o = arc(g, p, yb, yb, 18); val x = hw(g, 0.0, yb) - 1.5
    return M(o[0]) + cr(o, false) + L(Pt(100 + x, yb)) + cr(listOf(Pt(100 + x, yb)) + ln + listOf(Pt(100 - x, yb)), false) + "Z"
}

internal fun sym(w: Double, side: List<Pair<Double, Double>>, mid: Double): List<Pt> {
    val r = side.map { (f, y) -> Pt(100 + f * w, y) }
    return r + listOf(Pt(100.0, mid)) + r.reversed().map(::mir)
}

internal fun bumps(g: Face, p: Double, y0: Double, r: Double, col: String, above: Double): Ops =
    arc(g, p, y0, y0, 12).filter { !(above != 0.0 && it.y < above) }.flatMap { circle(r1(it.x), r1(it.y), r, col) }
