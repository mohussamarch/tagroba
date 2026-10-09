package app.masroufy.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.masroufy.ui.theme.Ink

private val cache = HashMap<Lucide, ImageVector>()

/** الأيقونة كـ`ImageVector` (24×24) — خط 1.5 بوحدات الرسم، نهايات وزوايا مدورة، من غير ملء. بتتبني مرة واحدة. */
val Lucide.vector: ImageVector
    get() = cache.getOrPut(this) {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        b.build()
    }

/**
 * أيقونة لوسيد بلون [tint]. [contentDescription] = null ⇒ زخرفة (الزرار اللي حواليها عليه الاسم). المقاسات من النموذج: 20 في
 * شريط التنقل · 22 في زراير الرأس · 24 في حبّات «دفعت الإيجار؟».
 */
@Composable
fun LucideIcon(icon: Lucide, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Ink.text, contentDescription: String? = null) {
    val painter = rememberVectorPainter(icon.vector)
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Box(modifier.size(size).then(semantics).paint(painter, colorFilter = ColorFilter.tint(tint)))
}
