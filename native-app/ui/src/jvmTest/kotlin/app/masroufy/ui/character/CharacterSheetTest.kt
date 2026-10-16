package app.masroufy.ui.character

import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * «شخصيتك» — رسم كوتلن مقابل النموذج (`golden/character/`):
 * - كل شكل في مجموعة الدقة بيتبني مشهد أرقامه كلها محدودة، ولو `CHARACTER_SVG_DIR` متظبط بيتكتب `<id>.svg` + `kotlin-svgs.js` جنب نسخة من
 *   `sheet.html` (تتفتح بـ`?src=kotlin` في Edge — نفس المتصفح بتاع النموذج).
 * - لو `CHARACTER_PROTO_SVG_DIR` متظبط (SVG النموذج نفسه، من `render()` في `CharacterArt.dc.html`): نص SVG كوتلن لازم يطابقه بعد تقريب الأرقام لعُشر.
 * - مسح كل التركيبات (نوع × وش × شعر × غطا × لبس × هيئة × طبقة) من غير NaN ولا ما لا نهاية (زي مسح فرع التصميم).
 */
class CharacterSheetTest {
    private val set = readFidelitySet()

    @Test
    fun everyFidelityLookBuildsAFiniteScene() {
        assertTrue(set.size >= 142, "المجموعة ناقصة: ${set.size}")
        val svgs = LinkedHashMap<String, String>()
        for (it in set) {
            val scene = characterScene(it.look, it.layer)
            assertTrue(scene.allNumbers().all { v -> v.isFinite() }, it.id)
            assertTrue(scene.pathCommandCount() > 20, it.id)
            svgs[it.id] = scene.toSvg()
        }
        val out = System.getenv("CHARACTER_SVG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        File(out, "svg").mkdirs()
        svgs.forEach { (id, svg) -> File(out, "svg/$id.svg").writeText(svg) }
        val js = svgs.entries.joinToString(",\n", "window.KOTLIN = {\n", "\n};\n") { (id, svg) -> "\"$id\": " + quote(svg) }
        File(out, "kotlin-svgs.js").writeText(js)
        listOf("sheet.html", "set.js", "prototype-art.js", "fidelity-set.json").forEach { File(goldenCharacterDir, it).copyTo(File(out, it), overwrite = true) }
    }

    @Test
    fun svgTextMatchesThePrototypeRender() {
        val dir = System.getenv("CHARACTER_PROTO_SVG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val bad = set.filter { File(dir, "${it.id}.svg").isFile }.mapNotNull {
            val proto = normalize(File(dir, "${it.id}.svg").readText()); val kotlin = normalize(characterScene(it.look, it.layer).toSvg())
            if (proto == kotlin) null else it.id + " @" + proto.zip(kotlin).indexOfFirst { (a, b) -> a != b }
        }
        assertEquals(emptyList(), bad, "SVG مختلف عن النموذج")
    }

    @Test
    fun sweepEveryCombinationHasNoBadNumbers() {
        var scenes = 0
        for (gender in 0..1) for (face in 0..3) for (hair in 0..10) for (head in 0..8) for (layer in CharacterLayer.entries) {
            val look = CharacterLook(gender = gender, face = face, hair = hair, head = if (head == 8) 1 else head, hat = if (head == 8) 1 else 0, beard = 3)
            val s = characterScene(look, layer); scenes++
            assertTrue(s.allNumbers().all { it.isFinite() }, "$look $layer")
        }
        for (gender in 0..1) for (outfit in 0..6) for (build in 0..2) for (oc in 0..5) for (head in listOf(0, 5, 6)) for (layer in CharacterLayer.entries) {
            val look = CharacterLook(gender = gender, outfit = outfit, build = build, outfitColor = oc, head = head, bling = oc % 2)
            val s = characterScene(look, layer); scenes++
            assertTrue(s.allNumbers().all { it.isFinite() }, "$look $layer")
        }
        for (face in 0..3) for (beard in 0..5) for (k in 0..3) for (glasses in 0..4) {
            val look = CharacterLook(face = face, beard = beard, eyes = k, brows = k, mouth = k, nose = k % 3, glasses = glasses)
            val s = characterScene(look, CharacterLayer.HEAD); scenes++
            assertTrue(s.allNumbers().all { it.isFinite() }, "$look")
        }
        assertTrue(scenes > 3000)
    }

    @Test
    fun drawingRulesFromThePrototype() {
        fun svg(l: CharacterLook, layer: CharacterLayer = CharacterLayer.BUST) = characterScene(l, layer).toSvg()
        // الست مالهاش دقن حتى لو متخزنة · طاقية الغني بتغطي على أي غطا راس · الشماغ بيخبّي الشعر
        assertEquals(svg(CharacterLook(gender = 1, hair = 6, beard = 0)), svg(CharacterLook(gender = 1, hair = 6, beard = 3)))
        assertEquals(svg(CharacterLook(hat = 1, head = 0)), svg(CharacterLook(hat = 1, head = 1)))
        assertEquals(svg(CharacterLook(head = 1, hair = 1)), svg(CharacterLook(head = 1, hair = 4)))
        // الطبقات: مقاس الـviewBox
        assertEquals(ViewBox(46f, 24f, 108f, 108f), characterScene(CharacterLook(), CharacterLayer.HEAD).viewBox)
        assertEquals(ViewBox(0f, 0f, 200f, 200f), characterScene(CharacterLook(), CharacterLayer.BUST).viewBox)
        assertEquals(ViewBox(0f, 0f, 200f, 440f), characterScene(CharacterLook(), CharacterLayer.FULL).viewBox)
        // اللبس بيتمط بالعرض حسب الهيئة (والست 95%)
        assertTrue("scale(0.84 1)" in svg(CharacterLook(build = 0), CharacterLayer.FULL))
        assertTrue("scale(1.22 1)" in svg(CharacterLook(build = 2), CharacterLayer.FULL))
        assertTrue("scale(1.16 1)" in svg(CharacterLook(gender = 1, hair = 6, build = 2, outfit = 5), CharacterLayer.FULL))
        // الخمار والنقاب بيرسموا الطرحة على الجسم كله، والحجاب لأ
        val cape = "M70 96 C52 100"
        assertTrue(cape in svg(CharacterLook(gender = 1, hair = 6, head = 5, outfit = 5), CharacterLayer.FULL))
        assertTrue(cape in svg(CharacterLook(gender = 1, hair = 6, head = 6, outfit = 5), CharacterLayer.FULL))
        assertTrue(cape !in svg(CharacterLook(gender = 1, hair = 6, head = 4, outfit = 5), CharacterLayer.FULL))
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private val num = Regex("-?\\d+\\.?\\d*(e-?\\d+)?")
    private val tag = Regex("<([a-zA-Z]+)((?:\\s+[\\w:-]+=\"[^\"]*\")*)\\s*(/?)>")
    private val attr = Regex("([\\w:-]+)=\"([^\"]*)\"")
    private val oneScale = Regex("scale\\(([-\\d.e]+)\\)")

    /** نفس الرسم = نفس النص بعد: ترتيب الخصايص جوه كل وسم أبجدي · `scale(a)` = `scale(a a)` · الأرقام لأقرب عُشر. */
    private fun canonical(s: String) = tag.replace(oneScale.replace(s) { "scale(${it.groupValues[1]} ${it.groupValues[1]})" }) { m ->
        val attrs = attr.findAll(m.groupValues[2]).map { it.value }.sorted().toList()
        "<" + m.groupValues[1] + (if (attrs.isEmpty()) "" else " " + attrs.joinToString(" ")) + m.groupValues[3] + ">"
    }

    private fun normalize(s: String) = num.replace(canonical(s)) { m ->
        val v = Math.round(m.value.toDouble() * 10) / 10.0
        if (v == 0.0) "0" else if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}
