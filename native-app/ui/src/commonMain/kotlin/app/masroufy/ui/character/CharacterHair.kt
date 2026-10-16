package app.masroufy.ui.character

import kotlin.math.PI

/** «شخصيتك» — الشعر (`hairBack` · `hairFront` في `CharacterArt.dc.html` @2df8134d، سطر بسطر). */
internal fun hairBack(k: Int, col: String, g: Face, capped: Boolean): Ops {
    val w = g.rx; val t = g.top
    if (k == 3) {
        return path(shape(arc(g, 6.0, 100.0, 100.0, 16) + listOf(Pt(100 + w + 9, 116.0), Pt(100 + w + 8, 128.0), Pt(100.0, 132.0), Pt(100 - w - 8, 128.0), Pt(100 - w - 9, 116.0))), col)
    }
    if (k == 6) {
        return path(
            shape(arc(g, 7.0, 104.0, 104.0, 18) + listOf(
                Pt(100 + w + 10, 124.0), Pt(100 + w + 11, 146.0), Pt(100 + w + 6, 154.0), Pt(100.0, 156.0), Pt(100 - w - 6, 154.0), Pt(100 - w - 11, 146.0),
                Pt(100 - w - 10, 124.0),
            )),
            col,
        )
    }
    if (k == 7) {
        val d = "M" + n(100 + w * 0.5) + " " + n(t + 4) + " C" + n(100 + w + 22) + " " + n(t + 2) + " " + n(100 + w + 22) + " " + n(t + 40) + " " +
            n(100 + w + 16) + " 120 C" + n(100 + w + 13) + " 132 " + n(100 + w + 8) + " 140 " + n(100 + w + 4) + " 144 C" + n(100 + w + 6) + " 128 " +
            n(100 + w + 6) + " 112 " + n(100 + w + 2) + " " + n(t + 34) + " Z"
        return path(d, col) + ellipse(100 + w + 10, t + 16, 4.0, 5.5, shade(col, 0.6))
    }
    if (k == 8) {
        val pts = arc(g, 9.0, 104.0, 104.0, 14) + listOf(
            Pt(100 + w + 13, 120.0), Pt(100 + w + 11, 136.0), Pt(100 + w - 2, 144.0), Pt(100 - w + 2, 144.0), Pt(100 - w - 11, 136.0), Pt(100 - w - 13, 120.0),
        )
        return path(shape(pts), col) + pts.filter { !(capped && it.y < t + 14) }.flatMap { circle(r1(it.x), r1(it.y), 9.0, col) }
    }
    if (k == 9) {
        return path(shape(arc(g, 8.0, 100.0, 100.0, 18) + listOf(Pt(100 + w + 10, 112.0), Pt(100 + w + 7, 120.0), Pt(100.0, 121.0), Pt(100 - w - 7, 120.0), Pt(100 - w - 10, 112.0))), col)
    }
    if (k == 10 && !capped) {
        return circle(100.0, t - 7, 13.0, col) +
            listOf(PathOp("M88 " + n(t + 1) + " Q100 " + n(t + 6) + " 112 " + n(t + 1), null, Outline(shade(col, 0.6), 3.0)))
    }
    return emptyList()
}

internal fun hairFront(k: Int, col: String, g: Face, capAt: Double): Ops {
    val w = g.rx; val t = g.top; val hi = "rgba(255,255,255,0.10)"
    if (k == 1 || k == 3) {
        return path(cut(g, if (k == 3) 5.0 else 3.0, if (k == 3) 100.0 else 86.0, sym(w, listOf(0.86 to 72.0, 0.6 to t + 15), t + 12)), col)
    }
    if (k == 2) {
        val ln = listOf(Pt(100 + 0.86 * w, 72.0), Pt(100 + 0.62 * w, t + 16), Pt(100 + 0.05 * w, t + 13), Pt(100 - 0.32 * w, t + 6), Pt(100 - 0.52 * w, t + 13), Pt(100 - 0.86 * w, 72.0))
        return path(cut(g, 5.0, 86.0, ln), col) +
            line("M" + n(100 - 0.32 * w) + " " + n(t + 6) + " Q" + n(100 - 0.36 * w) + " " + n(t) + " " + n(100 - 0.42 * w) + " " + n(t - 4), shade(col, 0.55), 1.4)
    }
    if (k == 4) return path(cut(g, 4.0, 86.0, sym(w, listOf(0.86 to 74.0, 0.6 to t + 15), t + 13)), col) + bumps(g, 4.0, 84.0, 7.0, col, capAt)
    if (k == 5) {
        fun band(sg: Boolean): String {
            val o = ArrayList<Pt>(); val inn = ArrayList<Pt>()
            for (j in 0..8) {
                val y = 86 - (86 - (t + 24)) * j / 8
                val po = P(g, 3.0, PI - tY(g, 3.0, y)); val pi = P(g, -3.0, PI - tY(g, -3.0, y))
                o.add(if (sg) mir(po) else po); inn.add(if (sg) mir(pi) else pi)
            }
            // `L(i[8])` قبل `i.reverse()` زي النموذج بالظبط
            return M(o[0]) + cr(o, false) + L(inn[8]) + cr(inn.reversed(), false) + "Z"
        }
        return path(band(false) + band(true), col)
    }
    if (k == 6) {
        val o = arc(g, 5.0, 108.0, 108.0, 18); val x = hw(g, 0.0, 108.0) - 1.5
        val ln = sym(w, listOf(0.9 to 90.0, 0.62 to t + 14, 0.2 to t + 8), t + 5)
        val lf = listOf(Pt(100 - w - 9, 152.0), Pt(100 - w - 7.5, 130.0), o[0])
        val rt = listOf(o[o.size - 1], Pt(100 + w + 7.5, 130.0), Pt(100 + w + 9, 152.0))
        val d = M(lf[0]) + cr(lf, false) + cr(o, false) + cr(rt, false) + L(Pt(100 + w - 1, 152.0)) +
            cr(listOf(Pt(100 + w - 1, 152.0), Pt(100 + w - 2.5, 124.0), Pt(100 + x, 108.0)), false) +
            cr(listOf(Pt(100 + x, 108.0)) + ln + listOf(Pt(100 - x, 108.0)), false) +
            cr(listOf(Pt(100 - x, 108.0), Pt(100 - w + 2.5, 124.0), Pt(100 - w + 1, 152.0)), false) + "Z"
        return path(d, col) + line("M100 " + n(t + 5) + " L100 " + n(t - 4), shade(col, 0.55), 1.3)
    }
    if (k == 7 || k == 10) {
        return path(cut(g, 3.2, 86.0, sym(w, listOf(0.86 to 72.0, 0.58 to t + 14), t + 11.5)), col) +
            line("M" + n(100 - w * 0.3) + " " + n(t + 10) + " Q" + n(100 - w * 0.2) + " " + n(t - 1) + " " + n(100 + w * 0.1) + " " + n(t - 2), hi, 1.2) +
            line("M" + n(100 + w * 0.35) + " " + n(t + 11) + " Q" + n(100 + w * 0.4) + " " + n(t) + " " + n(100 + w * 0.6) + " " + n(t + 1), hi, 1.2)
    }
    if (k == 8) {
        val s = ArrayList<CharOp>()
        var y = 100
        while (y <= 140) { s += circle(100 - w - 7, y.toDouble(), 7.0, col); s += circle(100 + w + 7, y.toDouble(), 7.0, col); y += 10 }
        return path(cut(g, 5.0, 98.0, sym(w, listOf(0.9 to 84.0, 0.6 to t + 17), t + 15)), col) + bumps(g, 5.0, 96.0, 6.5, col, capAt) + s
    }
    if (k == 9) {
        val ln = listOf(Pt(100 + w - 1, 104.0), Pt(100 + 0.92 * w, 88.0), Pt(100 + 0.4 * w, t + 12), Pt(100 - 0.35 * w, t + 19), Pt(100 - 0.85 * w, t + 27), Pt(100 - 0.98 * w, 92.0), Pt(100 - w + 1, 104.0))
        return path(cut(g, 6.0, 114.0, ln), col)
    }
    return emptyList()
}
