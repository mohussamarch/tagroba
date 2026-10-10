package app.masroufy.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * تدرجات CSS **بالحرف** عشان الوصفات تتنقل زي ما هي (DESIGN-SYSTEM «وصفات الأسطح — انقلها كما هي»).
 * الرسم فيزيائي (مش بيتقلب مع اتجاه اليمين للشمال) — زي CSS بالظبط.
 */

/** درجة لون عند نسبة من طول التدرج (0..1). */
typealias Stop = Pair<Float, Color>

private class CssLinear(private val angleDeg: Float, private val stops: List<Stop>) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val rad = angleDeg * PI / 180.0
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        // طول خط التدرج في CSS: |w·sin θ| + |h·cos θ| — بيعدّي من النص
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        return LinearGradientShader(
            from = Offset(cx - dx * half, cy - dy * half),
            to = Offset(cx + dx * half, cy + dy * half),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }

    override fun equals(other: Any?): Boolean = other is CssLinear && other.angleDeg == angleDeg && other.stops == stops

    override fun hashCode(): Int = angleDeg.hashCode() * 31 + stops.hashCode()
}

/** `linear-gradient(<deg>deg, …)` — الزاوية من «لفوق» مع عقارب الساعة. */
fun cssLinear(angleDeg: Float, vararg stops: Stop): Brush = CssLinear(angleDeg, stops.toList())

/**
 * `radial-gradient(<rx>% <ry>% at <x>% <y>%, color, transparent <stop>%)` — بقعة ضوء بيضاوية: اللون في المركز ويختفي عند [stop] من نصف القطر.
 * النِسب من عرض وطول المكان اللي بيترسم فيه.
 */
fun DrawScope.cssRadialGlow(rx: Float, ry: Float, atX: Float, atY: Float, color: Color, stop: Float) {
    val radiusX = size.width * rx
    val radiusY = size.height * ry
    if (radiusX <= 0f || radiusY <= 0f) return
    val center = Offset(size.width * atX, size.height * atY)
    scale(scaleX = radiusX / radiusY, scaleY = 1f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(0f to color, stop to color.copy(alpha = 0f), center = center, radius = radiusY),
            radius = radiusY,
            center = center,
        )
    }
}

/** ألوان الوصفات بالاسم (`rgba` ⇒ `0xAARRGGBB`). */
object Glass {
    /** الكارت الطافي: `linear-gradient(165deg, #FFFFFF 0%, #FBFAF4 100%)`. */
    val card = cssLinear(165f, 0f to Color(0xFFFFFFFF), 1f to Color(0xFFFBFAF4))

    /** الزر الأساسي: `linear-gradient(160deg, #0A7A62 0%, #08634F 100%)`. */
    val primary = cssLinear(160f, 0f to Color(0xFF0A7A62), 1f to Color(0xFF08634F))

    /** زرار «+»: `linear-gradient(160deg, #0E8A6E 0%, #08634F 60%, #064B40 100%)` من غير هالة (KOTLIN-MAP §3). */
    val plus = cssLinear(160f, 0f to Color(0xFF0E8A6E), 0.6f to Color(0xFF08634F), 1f to Color(0xFF064B40))

    /** أساس البطاقة البطلة: `linear-gradient(125deg, #064B40 0%, #08705A 100%)`. */
    val heroBase = cssLinear(125f, 0f to Color(0xFF064B40), 1f to Color(0xFF08705A))

    /** شريط التنقل وشريط السؤال — [opaque] تحت أندرويد 12 (من غير تمويه ⇒ شفافية ≈ 0.94). */
    fun nav(opaque: Boolean) = if (opaque) cssLinear(135f, 0f to Color(0xF0FFFFFF), 0.5f to Color(0xF0E1E9EA), 1f to Color(0xF2FFFFFF))
    else cssLinear(135f, 0f to Color(0xDBFFFFFF), 0.5f to Color(0xB3E1E9EA), 1f to Color(0xE6FFFFFF))

    /** عدسة التبويب المختار. */
    val navLens = cssLinear(145f, 0f to Color(0xF2FFFFFF), 0.55f to Color(0xB3DCEBD6), 1f to Color(0x8CA6DEC1))

    /** عدسة على الفاتح (رمز صاحب الحساب · الميكروفون · رمز البلد). */
    val lensOnLight = cssLinear(145f, 0f to Color(0xF2FFFFFF), 0.55f to Color(0xB3E3EFE6), 1f to Color(0xCCC9E6D4))

    /** عدسة على البترولي. */
    val lensOnHero = cssLinear(145f, 0f to Color(0x3DFFFFFF), 0.48f to Color(0x08FFFFFF), 1f to Color(0x2996F3CC))

    /** عدسة بنك على البطاقة البطلة (`HeroBanks`). */
    val heroBank = cssLinear(145f, 0f to Color(0x42FFFFFF), 0.48f to Color(0x0DFFFFFF), 1f to Color(0x2E96F3CC))

    /** القائمة السائلة (الضغط المطوّل) — [opaque] من غير تمويه. */
    fun liquidMenu(opaque: Boolean) = if (opaque) cssLinear(125f, 0f to Color(0xF0EFF9EF), 0.55f to Color(0xF0E7F1E3), 1f to Color(0xF2CDE8D2))
    else cssLinear(125f, 0f to Color(0xDBEFF9EF), 0.55f to Color(0xA8E7F1E3), 1f to Color(0xC2CDE8D2))

    /** نافذة الجرس ونافذة «أين ما معك الآن؟» (زجاج أفتح شوية من القائمة). */
    fun popover(opaque: Boolean) = if (opaque) cssLinear(125f, 0f to Color(0xF7F6FBF5), 0.55f to Color(0xF2ECF4E9), 1f to Color(0xF5D6ECDB))
    else cssLinear(125f, 0f to Color(0xF0F6FBF5), 0.55f to Color(0xDBECF4E9), 1f to Color(0xE6D6ECDB))

    /** الصف المرفوع. */
    val liftedRow = cssLinear(145f, 0f to Color(0xF2FFFFFF), 1f to Color(0xCCF0F6F0))

    /** البند المميّز جوه القائمة السائلة. */
    val menuHighlight = cssLinear(145f, 0f to Color(0xE6FFFFFF), 1f to Color(0x66FFFFFF))

    /** لمعة النعناع على صف اتسجّل لوحده (اختيار المالك `magic`). */
    val mint = Color(0xBFA6DEC1)
}
