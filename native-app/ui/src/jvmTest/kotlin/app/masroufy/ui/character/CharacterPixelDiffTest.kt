package app.masroufy.ui.character

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test

/**
 * «شخصيتك» — مقارنة صور النموذج (`proto/<id>.png` من Edge) بصور Compose (`compose/<id>.png`) — بس لو `CHARACTER_DIFF_DIR` متظبط.
 * البكسل «مختلف» لو أي قناة اختلفت بأكتر من 40. بيكتب `diff-report.txt` (النسبة لكل شكل، الأسوأ الأول) وصورة لكل زوج في `pairs/`
 * (النموذج شمال · Compose يمين · الفرق تحت بالأحمر) عشان المراجعة بالعين.
 */
class CharacterPixelDiffTest {
    @Test
    fun comparePrototypeAndComposePngs() {
        val dir = System.getenv("CHARACTER_DIFF_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val pairs = File(dir, "pairs").apply { mkdirs() }
        val rows = ArrayList<Pair<String, Double>>()
        for (it in readFidelitySet()) {
            val pf = File(dir, "proto/${it.id}.png"); val cf = File(dir, "compose/${it.id}.png")
            if (!pf.isFile || !cf.isFile) { rows += it.id to -1.0; continue }
            val p = ImageIO.read(pf); val c = ImageIO.read(cf)
            val w = minOf(p.width, c.width); val h = minOf(p.height, c.height)
            val out = BufferedImage(w * 3, h, BufferedImage.TYPE_INT_RGB)
            var bad = 0
            for (y in 0 until h) for (x in 0 until w) {
                val a = onWhite(p.getRGB(x, y)); val b = onWhite(c.getRGB(x, y))
                val differs = (0..2).any { k -> abs((a shr (8 * k) and 255) - (b shr (8 * k) and 255)) > 40 }
                if (differs) bad++
                out.setRGB(x, y, a); out.setRGB(x + w, y, b)
                out.setRGB(x + 2 * w, y, if (differs) Color(220, 30, 30).rgb else Color(245, 245, 245).rgb)
            }
            ImageIO.write(out, "png", File(pairs, "${it.id}.png"))
            rows += it.id to bad * 100.0 / (w * h)
        }
        val report = rows.sortedByDescending { it.second }.joinToString("\n") { (id, pct) ->
            if (pct < 0) "$id\tMISSING" else "$id\t" + "%.2f".format(pct) + "%"
        }
        File(dir, "diff-report.txt").writeText(report + "\n")
        println("worst 10:\n" + report.lines().take(10).joinToString("\n"))
        writeComboSheets(dir)
    }

    /**
     * لوحات المقارنة للتركيبات الـ12 (`tm1..tm6` رجالة · `tw1..tw6` ستات): كل تركيبة صف واحد —
     * راس (نموذج · Compose) · راس وكتاف (نموذج · Compose) · جسم كله (نموذج · Compose) — 3 صفوف في اللوحة.
     */
    private fun writeComboSheets(dir: File) {
        val combos = readFidelitySet().map { it.id.substringBeforeLast('-') }.filter { Regex("t[mw]\\d.*").matches(it) }.distinct()
        val sheets = File(dir, "sheets").apply { mkdirs() }
        combos.chunked(3).forEachIndexed { s, rows ->
            val img = BufferedImage(1200, 440 * rows.size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics(); g.color = Color.WHITE; g.fillRect(0, 0, img.width, img.height)
            rows.forEachIndexed { r, combo ->
                listOf("head", "bust", "full").forEachIndexed { k, layer ->
                    listOf("proto", "compose").forEachIndexed { side, src ->
                        val f = File(dir, "$src/$combo-$layer.png")
                        if (f.isFile) g.drawImage(ImageIO.read(f), (k * 2 + side) * 200, r * 440, null)
                    }
                }
                g.color = Color(200, 200, 200); g.drawLine(0, r * 440, 1200, r * 440)
            }
            g.dispose()
            ImageIO.write(img, "png", File(sheets, "combos-${s + 1}.png"))
        }
    }

    /** البكسل على خلفية بيضا (الشفاف ⇒ أبيض). */
    private fun onWhite(argb: Int): Int {
        val a = (argb ushr 24) and 255
        if (a == 255) return argb and 0xFFFFFF
        fun ch(s: Int) = (((argb shr s) and 255) * a + 255 * (255 - a)) / 255
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
