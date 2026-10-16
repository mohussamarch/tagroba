package app.masroufy.ui.character

import kotlin.math.max
import kotlin.math.pow

/**
 * «شخصيتك» — أغطية الراس (`shm` · `clothBack` · `clothFront` · `ballCap` · `dollar` في `CharacterArt.dc.html` @2df8134d).
 * كل غطا محسوب من حافة الوش نفسها (`g`) ⇒ بيغطي أي شكل وش من غير ما الراس تبان من تحته.
 */
internal const val SLIT = "M76 86.3 C76 81.4 82 80 100 80 C118 80 124 81.4 124 86.3 C124 91.2 118 92.6 100 92.6 C82 92.6 76 91.2 76 86.3 Z"

internal class Shm(val sil: String, val front: String, val inner: String, val aw: Double)

/** الشماغ والغترة: عقال واحد تقيل فوق الراس، والقماش لازق على الوش من الجنبين ونازل يوسع فوق الكتاف ويفتح على الصدر. */
internal fun shm(g: Face): Shm {
    val w = g.rx; val t = g.top
    val oL = listOf(
        Pt(100 - w - 44, 198.0), Pt(100 - w - 30, 160.0), Pt(100 - w - 14, 124.0), Pt(100 - w - 11, 100.0), Pt(100 - w - 11, 80.0), Pt(100 - w - 10, t + 6),
        Pt(100 - w + 2, t - 5), Pt(100.0 - 14, t - 9.5), Pt(100.0, t - 10.5),
    )
    val oR = oL.reversed().map(::mir)
    val iR = listOf(Pt(100 + w + 8, 199.0), Pt(100 + w, 150.0), Pt(100 + w - 3, 116.0), Pt(100 + w - 1.5, 94.0), Pt(100 + w - 3, t + 22), Pt(100 + w * 0.55, t + 13), Pt(100.0, t + 11))
    val inn = iR + iR.dropLast(1).reversed().map(::mir)
    return Shm(
        sil = M(oL[0]) + cr(oL, false) + cr(oR, false) + "Z",
        front = M(oL[0]) + cr(oL, false) + cr(oR, false) + L(inn[0]) + cr(inn, false) + "Z",
        inner = M(inn[0]) + cr(inn, false),
        aw = w + 9.5,
    )
}

internal fun clothBack(k: Int, g: Face): Ops = if (k == 1 || k == 2) path(shm(g).sil, if (k == 1) "#B0303A" else "#E2E0D8") else emptyList()

internal fun clothFront(k: Int, g: Face, wr: String): Ops {
    val w = g.rx; val t = g.top
    if (k == 1 || k == 2) {
        val s = shm(g); val aw = s.aw; val fold = "rgba(0,0,0," + (if (k == 1) "0.14" else "0.08") + ")"
        val x = ArrayList<CharOp>()
        x += path(s.front, if (k == 1) "url(#shm)" else "#FDFDFA")
        x += line(s.inner, "rgba(0,0,0,0.16)", 2.2)
        for (o in listOf(-1, 1)) x += line("M" + n(r1(100.0 + o * (w + 4))) + " 128 Q" + n(r1(100.0 + o * (w + 10))) + " 166 " + n(r1(100.0 + o * (w + 22))) + " 196", fold, 1.5)
        // العقال: حزام أسود واحد تقيل على عرض القماش، وتحته على طول حافة القماش فوق الجبهة
        x += path(
            "M" + n(r1(100 - aw)) + " " + n(t + 3) + " Q100 " + n(t + 9) + " " + n(r1(100 + aw)) + " " + n(t + 3) + " L" + n(r1(100 + aw)) + " " + n(t + 10) +
                " Q100 " + n(t + 16) + " " + n(r1(100 - aw)) + " " + n(t + 10) + " Z",
            "#1A1A1A", Outline("#1A1A1A", 1.0, LineCap.BUTT, LineJoin.ROUND),
        )
        x += line("M" + n(r1(100 - aw + 3)) + " " + n(t + 6.4) + " Q100 " + n(t + 12.4) + " " + n(r1(100 + aw - 3)) + " " + n(t + 6.4), "rgba(255,255,255,0.2)", 1.0)
        return x
    }
    if (k == 3) {
        val yb = t + 17; val c = arc(g, 6.5, yb, yb, 18)
        val d = M(c[0]) + cr(c, false) + " Q100 " + n(yb + 2.5) + " " + n(r1(c[0].x)) + " " + n(r1(c[0].y)) + "Z"
        return path(d, "#FAF9F4") + path(d, "url(#tq)") + line(M(c[0]) + " Q100 " + n(yb + 2.5) + " " + n(r1(c[18].x)) + " " + n(r1(c[18].y)), "#DCD9CF", 2.0)
    }
    if (k == 7) return ballCap(g, wr, false)
    if (k < 4 || k > 6) return emptyList()
    // الحجاب والخمار والنقاب: فتحة الوش محسوبة من حافة الوش نفسها، فبتلف حوالين أي شكل وش وتحت الدقن
    val lowR = if (k == 4) {
        listOf(Pt(100 + w + 8.5, 114.0), Pt(100 + w + 6, 128.0), Pt(100 + w + 20, 146.0), Pt(100 + w + 40, 164.0), Pt(100 + w + 30, 175.0), Pt(100.0, 180.0))
    } else {
        listOf(
            Pt(100 + w + 8.5, 114.0), Pt(100 + w + 9, 128.0), Pt(100 + w + 20, 140.0), Pt(100 + w + 37, 150.0), Pt(100 + w + 50, 164.0), Pt(100 + w + 56, 184.0),
            Pt(100 + w + 57, 206.0), Pt(100.0, 212.0),
        )
    }
    val outer = shape(arc(g, 7.0, 98.0, 98.0, 18) + lowR + lowR.dropLast(1).reversed().map(::mir))
    val hole = if (k == 6) SLIT else shape(ring(g, -1.5, 36).map { Pt(it.x, max(it.y, t + 10 + 5 * ((it.x - 100) / w).pow(2))) })
    val lt = light(wr)
    val fold = if (lt) "rgba(0,0,0,0.12)" else "rgba(255,255,255,0.08)"
    val edge = if (lt) "rgba(0,0,0,0.16)" else "rgba(255,255,255,0.14)"
    val sheen = arc(g, 4.0, 84.0, 84.0, 18)
    val x = ArrayList<CharOp>()
    x += path(outer + hole, wr, evenOdd = true)
    x += line(hole, edge, 1.2)
    x += line(M(sheen[3]) + cr(sheen.subList(3, 9), false), if (lt) "rgba(255,255,255,0.35)" else "rgba(255,255,255,0.07)", 3.0)
    x += line("M" + n(100.0 - 9) + " " + n(g.bot + 5) + " Q" + n(100 - w - 6) + " " + n(g.bot + 16) + " " + n(100 - w - 22) + " 150", fold, 1.5)
    x += line("M" + n(100.0 + 9) + " " + n(g.bot + 5) + " Q" + n(100 + w + 6) + " " + n(g.bot + 16) + " " + n(100 + w + 22) + " 150", fold, 1.5)
    // النقاب: خمار من غير فتحة وش + فتحة عيون ضيقة بخيط في النص + رباط على الجبهة + الطرحة نازلة على الصدر
    if (k == 6) {
        x += path("M" + n(100 - w - 4) + " 80 Q100 78.6 " + n(100 + w + 4) + " 80 L" + n(100 + w + 4) + " 73.5 Q100 72 " + n(100 - w - 4) + " 73.5 Z", wr)
        x += line("M" + n(100 - w - 3) + " 73.6 Q100 72.1 " + n(100 + w + 3) + " 73.6", edge, 0.9)
        val veil = "M" + n(100 - w - 3) + " 93 L" + n(100 + w + 3) + " 93 C" + n(100 + w + 5) + " 118 " + n(100 + w + 2) + " 150 100 170 C" + n(100 - w - 2) + " 150 " +
            n(100 - w - 5) + " 118 " + n(100 - w - 3) + " 93 Z"
        x += path(veil, wr)
        x += line(veil, edge, 0.9)
        x += line("M" + n(100 - w - 2) + " 93.4 L" + n(100 + w + 2) + " 93.4", edge, 1.0)
        x += line("M91 100 Q89 130 87 158 M109 100 Q111 130 113 158", fold, 1.3)
        x += RectOp(98.8, 79.0, 2.4, 14.2, 1.2, wr)
        x += line(SLIT, "rgba(0,0,0,0.32)", 1.0)
    }
    return x
}

internal fun dollar(cx: Double, cy: Double, r: Double, col: String, w: Double): Ops {
    val d = "M" + n(cx + r * 0.42) + " " + n(cy - r * 0.5) + " C" + n(cx + r * 0.2) + " " + n(cy - r * 0.78) + " " + n(cx - r * 0.5) + " " + n(cy - r * 0.7) + " " +
        n(cx - r * 0.42) + " " + n(cy - r * 0.22) + " C" + n(cx - r * 0.34) + " " + n(cy + r * 0.1) + " " + n(cx + r * 0.42) + " " + n(cy - r * 0.04) + " " +
        n(cx + r * 0.42) + " " + n(cy + r * 0.3) + " C" + n(cx + r * 0.42) + " " + n(cy + r * 0.74) + " " + n(cx - r * 0.3) + " " + n(cy + r * 0.74) + " " +
        n(cx - r * 0.46) + " " + n(cy + r * 0.46)
    return listOf(PathOp(d, null, Outline(col, w, LineCap.ROUND))) + line("M" + n(cx) + " " + n(cy - r * 0.92) + "V" + n(cy + r * 0.92), col, w * 0.9)
}

/**
 * الكاب (اختيار) وطاقية الغني (مكافأة) — حتة واحدة لازقة في الراس (طلب المالك 2026-10-16): القبة = حافة الراس منفوخة 5 نازلة على الجناب
 * لحد فوق الودان (y = 80)، والبوز راكب على حافة القبة من غير فراغ. طاقية الغني نفس الشكل بقبة أعلى عشان العملة.
 */
internal fun ballCap(g: Face, col: String, rich: Boolean): Ops {
    val w = g.rx; val t = g.top; val ys = 80.0; val yb = t + 15; val lift = if (rich) 9.0 else 3.0; val top = t - 5 - lift
    val vis = shade(col, 0.78); val seam = "rgba(0,0,0,0.14)"
    val c = arc(g, 5.0, ys, ys, 20).map { Pt(it.x, it.y - lift * max(0.0, (ys - it.y) / (ys - t + 5)).pow(1.6)) }
    val crown = M(c[0]) + cr(c, false) + cr(listOf(c[c.size - 1], Pt(100 + w * 0.8, yb + 6), Pt(100.0, yb + 6), Pt(100 - w * 0.8, yb + 6), c[0]), false) + "Z"
    val x = ArrayList<CharOp>()
    x += path(crown, col)
    x += line(
        "M100 " + n(top + 2) + " Q" + n(r1(100 - w * 0.62)) + " " + n(top + 9) + " " + n(r1(100 - w * 0.7)) + " " + n(yb + 2) + " M100 " + n(top + 2) + " Q" +
            n(r1(100 + w * 0.62)) + " " + n(top + 9) + " " + n(r1(100 + w * 0.7)) + " " + n(yb + 2),
        seam, 1.2,
    )
    x += circle(100.0, top + 0.5, 3.0, vis)
    if (rich) {
        x += circle(100.0, yb - 12, 10.0, "#F3C96B", Outline("#C99A2E", 1.5))
        x += dollar(100.0, yb - 12, 7.6, "#1F6B3A", 2.2)
    } else {
        x += line("M100 " + n(top + 3) + " L100 " + n(yb), seam, 1.4)
    }
    val xL = 100 - w - 4; val xR = 100 + w + 4
    x += ellipse(100.0, yb + 14, w - 8, 2.6, "rgba(0,0,0,0.13)")
    x += path(
        "M" + n(xL) + " " + n(yb + 8) + " Q100 " + n(yb - 6) + " " + n(xR) + " " + n(yb + 8) + " C" + n(xR - 4) + " " + n(yb + 12) + " " + n(100.0 + 20) + " " + n(yb + 13) +
            " 100 " + n(yb + 13) + " C" + n(100.0 - 20) + " " + n(yb + 13) + " " + n(xL + 4) + " " + n(yb + 12) + " " + n(xL) + " " + n(yb + 8) + " Z",
        vis,
    )
    x += line("M" + n(xL + 6) + " " + n(yb + 9) + " Q100 " + n(yb - 2) + " " + n(xR - 6) + " " + n(yb + 9), "rgba(0,0,0,0.16)", 1.0)
    return x
}
