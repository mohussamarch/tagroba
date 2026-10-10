package app.masroufy.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * ظل طبقة واحدة = سطر من `box-shadow` في وصفات v0.3/v0.4 بالحرف: `x y blur color` (و`inset` = لمعة جوه الحافة).
 * الترتيب مهم (DESIGN-SYSTEM «ملاحظات للتنفيذ»): الداخلية للمعة، ثم القريبة الخفيفة، ثم البعيدة الواسعة.
 */
data class ShadowLayer(val x: Dp, val y: Dp, val blur: Dp, val color: Color, val inset: Boolean = false, val spread: Dp = 0.dp)

private fun drop(x: Int, y: Int, blur: Int, color: Long) = ShadowLayer(x.dp, y.dp, blur.dp, Color(color))
private fun inset(x: Int, y: Int, blur: Int, color: Long) = ShadowLayer(x.dp, y.dp, blur.dp, Color(color), inset = true)
private fun ring(width: Double, color: Long) = ShadowLayer(0.dp, 0.dp, 0.dp, Color(color), spread = width.dp)

/** الوصفات بأسمائها — ممنوع ظل من برّه الجدول ده (قائمة الفحص بند 4). الألوان `0xAARRGGBB` من `rgba` بالظبط. */
object Shadows {
    /** الكارت الطافي: `inset 0 1px 0 #FFF, 0 1px 2px rgba(29,54,53,.05), 0 14px 32px rgba(29,54,53,.09)`. */
    val card = listOf(inset(0, 1, 0, 0xFFFFFFFF), drop(0, 1, 2, 0x0D1D3635), drop(0, 14, 32, 0x171D3635))

    /** كارت أصغر (شرائح وحبّات): `0 6px 16px rgba(29,54,53,.08)`. */
    val chip = listOf(inset(0, 1, 0, 0xFFFFFFFF), drop(0, 1, 2, 0x0D1D3635), drop(0, 6, 16, 0x141D3635))

    /** البطاقة البطلة: `inset 0 1px 0 rgba(255,255,255,.22), 0 20px 44px rgba(6,75,64,.30), 0 4px 10px rgba(6,75,64,.18)`. */
    val hero = listOf(inset(0, 1, 0, 0x38FFFFFF), drop(0, 4, 10, 0x2E064B40), drop(0, 20, 44, 0x4D064B40))

    /** الزر الأساسي: `inset 0 1px 0 rgba(255,255,255,.25), 0 10px 22px rgba(8,99,79,.28)`. */
    val primary = listOf(inset(0, 1, 0, 0x40FFFFFF), drop(0, 10, 22, 0x4708634F))

    /** التبويب المختار جوه شريط تبويبات (زاوية 14): `0 6px 14px rgba(8,99,79,.25)`. */
    val segment = listOf(inset(0, 1, 0, 0x40FFFFFF), drop(0, 6, 14, 0x4008634F))

    /** زرار «+»: من غير هالة بيضا (KOTLIN-MAP §3). */
    val plus = listOf(inset(0, 1, 0, 0x4DFFFFFF), inset(0, -2, 4, 0x40002820), drop(0, 2, 5, 0x38064B40), drop(0, 10, 22, 0x4D064B40))

    /** شريط التنقل الزجاج. */
    val nav = listOf(inset(1, 2, 3, 0xFFFFFFFF), inset(0, -4, 6, 0xD9FFFFFF), ring(0.5, 0x409BB3B3), drop(0, 18, 44, 0x2E1D3635))

    /** شريط «اسأل مصروفي» (نفس الزجاج بظل أقصر). */
    val ask = listOf(inset(1, 2, 3, 0xFFFFFFFF), inset(0, -3, 5, 0xD9FFFFFF), ring(0.5, 0x409BB3B3), drop(0, 10, 24, 0x241D3635))

    /** عدسة التبويب المختار (40×30). */
    val navLens = listOf(inset(1, 1, 2, 0xFFFFFFFF), inset(-1, -2, 3, 0x2908634F), drop(0, 6, 14, 0x2E08634F))

    /** عدسة على الفاتح (رمز صاحب الحساب). */
    val lensOnLight = listOf(inset(1, 1, 2, 0xFFFFFFFF), inset(-1, -2, 3, 0x2E08634F), drop(0, 8, 18, 0x2908634F))

    /** عدسة على البترولي. */
    val lensOnHero = listOf(inset(1, 1, 2, 0x73C8FFEB), inset(-1, -2, 3, 0x47002F30), drop(0, 8, 16, 0x24002A35))

    /** عدسة البنك جوه البطاقة البطلة (`HeroBanks`) — بحلقة بلون البطاقة عشان العدسات فوق بعض تبان. */
    val heroBank = listOf(inset(1, 1, 2, 0x73C8FFEB), inset(-1, -2, 3, 0x47002F30), ring(1.5, 0x8C064B40), drop(0, 6, 12, 0x29002A35))

    /** الصف المرفوع في قائمة الضغط المطوّل. */
    val liftedRow = listOf(inset(0, 2, 0, 0xFFFFFFFF), inset(0, -3, 5, 0xFFFFFFFF), ring(1.0, 0xE6FFFFFF), drop(0, 22, 48, 0x33102D35))

    /** القائمة السائلة ونافذة الجرس. */
    val liquidMenu = listOf(inset(1, 1, 2, 0xBFFFFFFF), inset(-1, -1, 3, 0x2E3B7166), drop(0, 18, 44, 0x331D3635))

    /** اللوحة اللي بتطلع من تحت: `inset 0 1px 0 #FFF, 0 -14px 40px rgba(29,54,53,.16)`. */
    val sheet = listOf(inset(0, 1, 0, 0xFFFFFFFF), drop(0, -14, 40, 0x291D3635))

    /** أيقونة التصنيف: `inset 0 1px 0 rgba(255,255,255,0.7)`. */
    val iconTile = listOf(inset(0, 1, 0, 0xB3FFFFFF))

    /** رسالة صغيرة (toast). */
    val toast = listOf(inset(1, 1, 2, 0xFFFFFFFF), drop(0, 10, 24, 0x291D3635))
}

/**
 * الطبقات على [shape]. [farFactor] بيصغّر **أبعد** ظل (أكبر blur) — حالة «مضغوط» بتنزله للنص (DESIGN-SYSTEM «حالات التفاعل»)،
 * و«معطّل» بيشيله (0).
 */
fun Modifier.layeredShadow(shape: Shape, layers: List<ShadowLayer>, farFactor: Float = 1f): Modifier {
    val far = layers.filter { !it.inset }.maxByOrNull { it.blur.value }
    var m = this
    for (l in layers.filter { !it.inset }) {
        val alpha = if (l === far) farFactor.coerceIn(0f, 1f) else 1f
        if (alpha <= 0f) continue
        m = m.dropShadow(shape, Shadow(radius = l.blur, color = l.color, spread = l.spread, offset = DpOffset(l.x, l.y), alpha = alpha))
    }
    return m
}

/** اللمعة الداخلية (الطبقات `inset`) — بتتحط **بعد** الخلفية عشان تبان فوقها. */
fun Modifier.innerSheen(shape: Shape, layers: List<ShadowLayer>): Modifier {
    var m = this
    for (l in layers.filter { it.inset }) {
        m = m.innerShadow(shape, Shadow(radius = l.blur, color = l.color, spread = l.spread, offset = DpOffset(l.x, l.y)))
    }
    return m
}

/** حد جوه الشكل من غير ما يكبّره: `box-shadow: inset 0 0 0 <width> <color>` (الخانة المختارة · الحقل). */
fun Modifier.insetRing(shape: Shape, width: Dp, color: Color): Modifier =
    innerShadow(shape, Shadow(radius = 0.dp, color = color, spread = width))
