package app.masroufy.ui.character

/**
 * «شخصيتك» — قايمة رسم محايدة (من غير Compose ولا SVG): نفس عناصر `CharacterArt.dc.html` @2df8134d بالظبط.
 * مسارات الرسم بتتكتب **نص `d` زي النموذج حرفيًا** (النقل سطر بسطر) وبتتقري هنا مرة واحدة لأوامر مطلقة (M L Q C Z)،
 * وبعدها في خلفيتين: [drawCharacter] (Compose) و [toSvg] (نص SVG لمقارنة الدقة مع النموذج).
 * الألوان نص CSS زي النموذج (`#RRGGBB` · `rgba(r,g,b,a)` · `url(#shm)` · `url(#tq)`).
 */
class CharacterScene(val viewBox: ViewBox, val ops: List<CharOp>)

data class ViewBox(val x: Float, val y: Float, val width: Float, val height: Float)

enum class LineCap { BUTT, ROUND }
enum class LineJoin { MITER, ROUND }

/** حد مرسوم حوالين العنصر (أو خط لوحده). */
data class Outline(
    val color: String,
    val width: Double,
    val cap: LineCap = LineCap.BUTT,
    val join: LineJoin = LineJoin.MITER,
    val dash: List<Double>? = null,
)

sealed interface CharOp

/** `<path>`: [fill] = null ⇒ `fill="none"` (خط بس). */
class PathOp(
    val d: String,
    val fill: String?,
    val outline: Outline? = null,
    val evenOdd: Boolean = false,
    val opacity: Double = 1.0,
) : CharOp {
    val cmds: List<PathCmd> = parsePathData(d)
    /** نسخة الرسم في Compose — بتتبني أول مرة بس. */
    internal var cache: Any? = null
}

class CircleOp(val cx: Double, val cy: Double, val r: Double, val fill: String?, val outline: Outline? = null) : CharOp
class EllipseOp(val cx: Double, val cy: Double, val rx: Double, val ry: Double, val fill: String, val opacity: Double = 1.0) : CharOp
class RectOp(
    val x: Double, val y: Double, val w: Double, val h: Double, val rx: Double, val fill: String?, val outline: Outline? = null,
) : CharOp

/** تحويلة: `translate` أو `scale` (بترتيب SVG — الأولى برّه). */
sealed interface Tf {
    data class Move(val x: Double, val y: Double) : Tf
    data class Scale(val x: Double, val y: Double) : Tf
}

class GroupOp(val transform: List<Tf>, val children: List<CharOp>) : CharOp

/** أمر مسار مطلق. */
sealed interface PathCmd {
    data class Move(val x: Double, val y: Double) : PathCmd
    data class Line(val x: Double, val y: Double) : PathCmd
    data class Quad(val x1: Double, val y1: Double, val x: Double, val y: Double) : PathCmd
    data class Cubic(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : PathCmd
    data object Close : PathCmd
}

/** قراية نص `d` (الأوامر اللي النموذج بيستعملها: M L l V Q C Z) لأوامر مطلقة. */
fun parsePathData(d: String): List<PathCmd> {
    val out = ArrayList<PathCmd>()
    var i = 0
    var cmd = ' '
    var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0
    val nums = ArrayList<Double>()
    fun number(): Double? {
        while (i < d.length && (d[i] == ' ' || d[i] == ',')) i++
        val start = i
        if (i < d.length && (d[i] == '-' || d[i] == '+')) i++
        while (i < d.length && (d[i].isDigit() || d[i] == '.')) i++
        if (i < d.length && (d[i] == 'e' || d[i] == 'E')) { i++; if (i < d.length && (d[i] == '-' || d[i] == '+')) i++; while (i < d.length && d[i].isDigit()) i++ }
        return if (i > start) d.substring(start, i).toDouble() else null
    }
    fun take(n: Int): Boolean {
        nums.clear()
        repeat(n) { nums.add(number() ?: return false) }
        return true
    }
    while (i < d.length) {
        val c = d[i]
        if (c.isLetter()) { cmd = c; i++ } else if (c == ' ' || c == ',') { i++; continue }
        when (cmd) {
            'M' -> if (take(2)) { cx = nums[0]; cy = nums[1]; sx = cx; sy = cy; out.add(PathCmd.Move(cx, cy)); cmd = 'L' } else break
            'L' -> if (take(2)) { cx = nums[0]; cy = nums[1]; out.add(PathCmd.Line(cx, cy)) } else break
            'l' -> if (take(2)) { cx += nums[0]; cy += nums[1]; out.add(PathCmd.Line(cx, cy)) } else break
            'V' -> if (take(1)) { cy = nums[0]; out.add(PathCmd.Line(cx, cy)) } else break
            'H' -> if (take(1)) { cx = nums[0]; out.add(PathCmd.Line(cx, cy)) } else break
            'Q' -> if (take(4)) { out.add(PathCmd.Quad(nums[0], nums[1], nums[2], nums[3])); cx = nums[2]; cy = nums[3] } else break
            'C' -> if (take(6)) { out.add(PathCmd.Cubic(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5])); cx = nums[4]; cy = nums[5] } else break
            'Z', 'z' -> { out.add(PathCmd.Close); cx = sx; cy = sy; cmd = ' ' }
            else -> i++
        }
    }
    return out
}

/** كل الأرقام في المشهد (للفحص إن مفيش NaN ولا ما لا نهاية). */
fun CharacterScene.allNumbers(): List<Double> {
    val out = ArrayList<Double>()
    fun walk(op: CharOp) {
        when (op) {
            is PathOp -> op.cmds.forEach { c ->
                when (c) {
                    is PathCmd.Move -> { out += c.x; out += c.y }
                    is PathCmd.Line -> { out += c.x; out += c.y }
                    is PathCmd.Quad -> { out += c.x1; out += c.y1; out += c.x; out += c.y }
                    is PathCmd.Cubic -> { out += c.x1; out += c.y1; out += c.x2; out += c.y2; out += c.x; out += c.y }
                    PathCmd.Close -> Unit
                }
            }
            is CircleOp -> { out += op.cx; out += op.cy; out += op.r }
            is EllipseOp -> { out += op.cx; out += op.cy; out += op.rx; out += op.ry }
            is RectOp -> { out += op.x; out += op.y; out += op.w; out += op.h }
            is GroupOp -> op.children.forEach(::walk)
        }
    }
    ops.forEach(::walk)
    return out
}

/** عدد أوامر الرسم الفعلية في المشهد (للفحص إن المسار ما اتقراش فاضي). */
fun CharacterScene.pathCommandCount(): Int {
    fun count(op: CharOp): Int = when (op) {
        is PathOp -> op.cmds.size
        is GroupOp -> op.children.sumOf(::count)
        else -> 1
    }
    return ops.sumOf(::count)
}
