package app.masroufy.ui.character

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import app.masroufy.core.fitGender
import app.masroufy.ui.screens.people.StageCharacter
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «شخصيتك» مقصوصة من فوق (r041b): كارت «الأشخاص» ومربّع «في الأشخاص» لازم يبيّنوا **أول** الجسم كله (الراس والكتاف) بالظبط —
 * قبل كده `requiredSize` الأطول من المساحة كان بيتوسّط فيها فبيبان نص الجسم من غير راس. بيرسم Compose نفسه على JVM
 * (skiko لويندوز بس — اختبار بس، ARCHITECTURE §31.40).
 */
class CharacterFromTopTest {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private val looks = listOf(CharacterLook.START, CharacterLook.START.fitGender(1), CharacterLook.START.copy(head = 1, build = 2))

    @Test
    fun peopleCardShowsTheTopOfTheFullFigureFromY107() {
        if (!windows) return
        for (look in looks) {
            val full = render(270, 594) { CharacterArt(look, CharacterLayer.FULL, Modifier.size(270.dp, 594.dp)) }
            // الكارت 350×360: الرسمة 270 في النص (من 40) ومن 107 لتحت ⇒ أول 253 سطر من الجسم كله
            val card = render(350, 360) { Box(Modifier.size(350.dp, 360.dp)) { StageCharacter(look) } }
            assertTopCrop(card, 40, 107, full, 270, 253, "كارت الأشخاص $look")
        }
    }

    @Test
    fun narrowBoxShowsTheHeadAndShoulders() {
        if (!windows) return
        for (look in looks) {
            val full = render(85, 188) { CharacterArt(look, CharacterLayer.FULL, Modifier.size(85.dp, 188.dp)) }
            val tile = render(113, 78) { CharacterFromTop(look, 85.dp, 188.dp, Modifier.size(113.dp, 78.dp)) }
            assertTopCrop(tile, 14, 0, full, 85, 78, "مربّع في الأشخاص $look")
        }
    }

    /** المنطقة ([x],[y]) بمقاس [w]×[h] في [shot] = أول [h] سطر من [full] (فرق أقل من 0.5% للتنعيم)، ومش فاضية. */
    private fun assertTopCrop(shot: BufferedImage, x: Int, y: Int, full: BufferedImage, w: Int, h: Int, what: String) {
        var diff = 0; var ink = 0
        for (j in 0 until h) for (i in 0 until w) {
            val want = full.getRGB(i, j)
            if (want ushr 24 != 0) ink++
            if (!near(shot.getRGB(x + i, y + j), want)) diff++
        }
        assertTrue(ink > w * h / 8, "$what: أول الجسم فاضي ($ink)")
        assertTrue(diff * 200 < w * h, "$what: المقصوص مش أول الجسم — $diff من ${w * h} بكسل مختلف")
    }

    private fun near(a: Int, b: Int) = (0..24 step 8).all { s -> kotlin.math.abs((a ushr s and 0xFF) - (b ushr s and 0xFF)) <= 8 }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(w: Int, h: Int, content: @Composable () -> Unit): BufferedImage {
        val scene = ImageComposeScene(w, h, Density(1f)) { content() }
        try {
            return ImageIO.read(ByteArrayInputStream(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes))
        } finally {
            scene.close()
        }
    }
}
