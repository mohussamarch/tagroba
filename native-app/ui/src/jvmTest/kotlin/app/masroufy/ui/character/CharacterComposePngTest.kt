package app.masroufy.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import app.masroufy.core.CharacterLayer
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «شخصيتك» — رسم Compose نفسه لصور PNG (بس لو `CHARACTER_PNG_DIR` متظبط): كل شكل في مجموعة الدقة بنفس مقاس صور النموذج
 * (200×200 للراس والكتاف · 200×440 للجسم كله) على خلفية بيضا زي صفحة النموذج. المقارنة في [CharacterPixelDiffTest].
 * محتاج skiko الأصلية لويندوز (اختبار بس — ARCHITECTURE §31.40).
 */
class CharacterComposePngTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun rendersEveryFidelityLookToPng() {
        val out = System.getenv("CHARACTER_PNG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        val only = System.getenv("CHARACTER_PNG_ONLY")?.takeIf { it.isNotBlank() }
        var n = 0
        for (it in readFidelitySet()) {
            if (only != null && !it.id.startsWith(only)) continue
            val h = if (it.layer == CharacterLayer.FULL) 440 else 200
            val scene = ImageComposeScene(200, h, Density(1f)) {
                Box(Modifier.fillMaxSize().background(Color.White)) { CharacterArt(it.look, it.layer, Modifier.fillMaxSize()) }
            }
            try {
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(out, "${it.id}.png").writeBytes(png); n++
            } finally {
                scene.close()
            }
        }
        assertTrue(n > 0)
        if (only == null) writeContactSheet(out)
    }

    /** لوحة واحدة بكل الأشكال (12 في الصف · خانة 200×440) — `contact-sheet.png` جنب الصور. */
    private fun writeContactSheet(out: File) {
        val looks = readFidelitySet()
        val cols = 12; val rows = (looks.size + cols - 1) / cols
        val sheet = BufferedImage(cols * 200, rows * 440, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics(); g.color = java.awt.Color.WHITE; g.fillRect(0, 0, sheet.width, sheet.height)
        looks.forEachIndexed { i, it ->
            val f = File(out, "${it.id}.png")
            if (f.isFile) g.drawImage(ImageIO.read(f), (i % cols) * 200, (i / cols) * 440, null)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(out, "contact-sheet.png"))
    }
}
