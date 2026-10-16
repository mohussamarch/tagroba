package app.masroufy.ui.character

import app.masroufy.core.CharacterLayer
import app.masroufy.core.CharacterLook
import app.masroufy.core.normalized

/**
 * «شخصيتك» — التركيب (`render` في `CharacterArt.dc.html` @2df8134d) للطبقات التلاتة:
 * HEAD (viewBox 46 24 108 108) · BUST (0 0 200 200) · FULL (0 0 200 440، الراس 80% والجسم بيتمط بالعرض حسب الهيئة).
 */
private val BUILD = listOf(0.84, 1.0, 1.22)

fun characterScene(look: CharacterLook, layer: CharacterLayer): CharacterScene {
    val c = look.normalized()
    val fem = c.gender == 1
    val (sk, skd) = pick(SKIN, c.skin)
    val hc = pick(HAIRC, c.hairColor); val wr = pick(WRAP, c.wrap); val oc = outfitColor(c)
    val g = geo(c.face)
    val hat = c.hat == 1
    val hk = if (hat) 0 else c.head
    val bd = if (fem) 0 else c.beard
    val hides = hk in listOf(1, 2, 4, 5, 6)
    val capAt = if (hat || hk == 7) 82.0 else if (hk == 3) g.top + 17 else 0.0
    val sx = jsRound(pick(BUILD, c.build) * (if (fem) 0.95 else 1.0) * 100) / 100
    fun stretch(x: Ops): CharOp = GroupOp(listOf(Tf.Move(100.0, 0.0), Tf.Scale(sx, 1.0), Tf.Move(-100.0, 0.0)), x)
    val back = (if (hides) emptyList() else hairBack(c.hair, hc, g, capAt != 0.0)) + clothBack(hk, g)
    val parts = path("M89 110 L89 146 Q100 152 111 146 L111 110 Z", skd) +
        (if (hides) emptyList() else ellipse(100 - g.rx, 88.0, 5.0, 8.0, skd) + ellipse(100 + g.rx, 88.0, 5.0, 8.0, skd)) +
        path(shape(ring(g, 0.0, 36)), sk) +
        (if (fem) ellipse(87.0, 97.0, 5.0, 3.0, "#E8826F", 0.2) + ellipse(113.0, 97.0, 5.0, 3.0, "#E8826F", 0.2) else emptyList()) +
        beard(bd, hc, g.rx, g.ry) + eyes(c.eyes, fem) + brows(c.brows, if (hk in 4..6) "#3A2A1F" else hc) + nose(c.nose, skd) +
        (if (bd >= 2) mouth(c.mouth, "#F2E6E0", 1.2) else mouth(c.mouth, if (fem) "#A9505A" else "#7E4337")) +
        (if (hides) emptyList() else hairFront(c.hair, hc, g, capAt)) + glasses(c.glasses) + clothFront(hk, g, wr) +
        (if (hat) ballCap(g, "#1F8A4D", true) else emptyList())
    if (layer == CharacterLayer.FULL) {
        fun gh(x: Ops): CharOp = GroupOp(listOf(Tf.Move(20.0, -2.0), Tf.Scale(0.8, 0.8)), x)
        val hx = r1(100 - 55 * sx)
        val ops = listOf(gh(back), stretch(body(c.outfit, oc, fem))) + circle(hx, 259.0, 8.5, sk) + circle(r1(200 - hx), 259.0, 8.5, sk) +
            (if (c.bling == 1) chain(true) else emptyList()) + (if (hk == 5 || hk == 6) listOf(stretch(capeFull(wr))) else emptyList()) + listOf(gh(parts))
        return CharacterScene(ViewBox(0f, 0f, 200f, 440f), ops)
    }
    val head = layer == CharacterLayer.HEAD
    val shoulders = if (head) path("M30 200 C34 160 64 146 100 146 C136 146 166 160 170 200 Z", oc) else bust(c.outfit, oc, fem)
    val pulled = GroupOp(listOf(Tf.Move(0.0, 134.0), Tf.Scale(1.0, 1.138), Tf.Move(0.0, -142.0)), listOf(stretch(shoulders)))
    val ops = back + pulled + (if (c.bling == 1 && !head) chain(false) else emptyList()) + parts
    return CharacterScene(if (head) ViewBox(46f, 24f, 108f, 108f) else ViewBox(0f, 0f, 200f, 200f), ops)
}
