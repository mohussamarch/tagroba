// ART-STUB (replaced by r041-char-art at merge)
package app.masroufy.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import kotlin.math.min

/** مكان الرسم المؤقت لحد ما فرع الرسم يتدمج: دايرة بلون البشرة فوق شكل بلون اللبس — بنفس مقاسات الطبقة (contain + centre). */
@Composable
fun CharacterArt(look: CharacterLook, layer: CharacterLayer, modifier: Modifier = Modifier, label: String? = null) {
    val m = if (label != null) modifier.semantics { contentDescription = label } else modifier
    val skin = CharacterPalette.skin[look.skin.coerceIn(0, 5)]
    val cloth = CharacterPalette.outfit[look.outfitColor.coerceIn(0, 5)]
    Canvas(m) {
        val vw = 200f
        val vh = if (layer == CharacterLayer.FULL) 440f else 200f
        val s = min(size.width / vw, size.height / vh)
        translate((size.width - vw * s) / 2f, (size.height - vh * s) / 2f) {
            scale(s, pivot = Offset.Zero) {
                drawRect(cloth, Offset(40f, vh * 0.55f), Size(120f, vh * 0.45f))
                drawCircle(skin, radius = 40f, center = Offset(100f, if (layer == CharacterLayer.FULL) 84f else 90f))
            }
        }
    }
}

/** الشكل المحايد الوحيد في النموذج (راس + كتاف · viewBox 40) — بعرض 84% ولازق تحت. */
@Composable
fun NeutralFigure(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width * 0.84f / 40f
        translate(left = size.width * 0.08f, top = size.height - 40f * s) {
            scale(s, pivot = Offset.Zero) {
                drawCircle(Color(0xFF4D747C), radius = 7.5f, center = Offset(20f, 15.5f))
                drawPath(neutralBody, Color(0xFF39575D))
            }
        }
    }
}

private val neutralBody = Path().apply {
    moveTo(4.5f, 41f)
    cubicTo(4.5f, 32f, 11.4f, 26f, 20f, 26f)
    cubicTo(28.6f, 26f, 35.5f, 32f, 35.5f, 41f)
    close()
}
