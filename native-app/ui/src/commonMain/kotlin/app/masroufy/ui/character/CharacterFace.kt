package app.masroufy.ui.character

/** «شخصيتك» — ملامح الوش (`eyes` · `brows` · `nose` · `mouth` · `beard` · `glasses` في `CharacterArt.dc.html` @2df8134d). */
internal fun eyes(k: Int, fem: Boolean): Ops {
    val d = "#2A211C"
    val s = ArrayList<CharOp>()
    for ((xi, o) in listOf(88 to -1, 112 to 1)) {
        val x = xi.toDouble()
        when (k) {
            0 -> s += circle(x, 86.0, 2.6, d)
            1 -> { s += ellipse(x, 86.0, 3.8, 2.5, d); s += line("M" + n(x - 5) + " 84 Q" + n(x) + " 80.5 " + n(x + 5) + " 84", d, 1.3) }
            2 -> s += line("M" + n(x - 4.5) + " 87 Q" + n(x) + " 83 " + n(x + 4.5) + " 87", d, 2.0)
            else -> { s += circle(x, 86.0, 3.3, d); s += circle(x + 1, 85.0, 1.0, "#FFFFFF") }
        }
        if (fem) s += line("M" + n(x + o * 3.6) + " 84.6 l" + n(o * 2.4) + " -1.8 M" + n(x + o * 2) + " 83.4 l" + n(o * 1.4) + " -2", d, 1.1)
    }
    return s
}

internal fun brows(k: Int, col: String): Ops {
    val w = listOf(1.8, 1.8, 3.2, 1.3).getOrElse(k) { 1.8 }
    return listOf(88 to -1, 112 to 1).flatMap { (x, dir) ->
        if (k == 1) line("M" + n(x - 6) + " 78 Q" + n(x) + " 73.5 " + n(x + 6) + " 78", col, w)
        else line("M" + n(x - 6) + " " + (if (dir < 0) 77 else 76) + " L" + n(x + 6) + " " + (if (dir < 0) 76 else 77), col, w)
    }
}

internal fun nose(k: Int, dark: String): Ops = when (k) {
    0 -> line("M100 90 Q97 98 101 100", dark, 1.6)
    1 -> line("M100 88 L98 99 L102.5 99", dark, 1.5)
    2 -> line("M95 98.5 Q100 102 105 98.5", dark, 1.6)
    else -> emptyList()
}

/** [w] = سُمك الخط (1.8، أو 1.2 لما الدقن مغطية البق — `replace('stroke-width="1.8"', …)` في النموذج). */
internal fun mouth(k: Int, m: String, w: Double = 1.8): Ops = when (k) {
    0 -> line("M93 107 L107 107", m, w)
    1 -> line("M92 106 Q100 111 108 106", m, w)
    2 -> path("M91.5 105 Q100 113 108.5 105 Z", m)
    3 -> line("M96 107 Q100 109 104 107", m, w)
    else -> emptyList()
}

internal fun beard(k: Int, col: String, rx: Double, ry: Double): Ops {
    val lx = 100 - rx; val r = 100 + rx; val b = 84 + ry
    val mus = path("M90 103 Q95 99 100 101.5 Q105 99 110 103 Q100 105 90 103 Z", col)
    val jaw = "M" + n(lx) + " 88 C" + n(lx) + " " + n(b - 6) + " 86 " + n(b + 2) + " 100 " + n(b + 2) + " C114 " + n(b + 2) + " " + n(r) + " " + n(b - 6) + " " + n(r) + " 88"
    return when (k) {
        1 -> path(jaw + " C" + n(r - 6) + " 100 112 112 100 112 C88 112 " + n(lx + 6) + " 100 " + n(lx) + " 88 Z", col, opacity = 0.28)
        2 -> path(jaw + " C" + n(r - 4) + " 104 112 104 100 104 C88 104 " + n(lx + 4) + " 104 " + n(lx) + " 88 Z M93 109.5 Q100 112 107 109.5 Q100 111 93 109.5", col, evenOdd = true) + mus
        3 -> path(
            "M" + n(lx - 1) + " 86 C" + n(lx - 2) + " " + n(b + 4) + " 84 " + n(b + 12) + " 100 " + n(b + 12) + " C116 " + n(b + 12) + " " + n(r + 2) + " " + n(b + 4) + " " +
                n(r + 1) + " 86 C" + n(r - 4) + " 102 112 103 100 103 C88 103 " + n(lx + 4) + " 102 " + n(lx - 1) + " 86 Z",
            col,
        ) + mus
        4 -> path("M92 111 Q100 " + n(b + 8) + " 108 111 Q100 115 92 111 Z", col) + mus
        5 -> mus
        else -> emptyList()
    }
}

internal fun glasses(k: Int): Ops {
    if (k == 4) {
        return listOf(88.0, 112.0).flatMap { x ->
            listOf(RectOp(x - 9.5, 79.5, 19.0, 12.5, 4.5, "#1E1E1E", Outline("#D4A53A", 1.8))) +
                line("M" + n(x - 6) + " 83 L" + n(x - 2) + " 82", "rgba(255,255,255,0.45)", 1.4)
        } + line("M97.5 84.5 Q100 82.5 102.5 84.5", "#D4A53A", 1.8)
    }
    if (k == 0) return emptyList()
    val c = if (k == 3) "#7A7A74" else "#2B2B2B"
    val w = if (k == 3) 1.2 else 1.7
    fun lens(x: Double): Ops =
        if (k == 1) circle(x, 86.0, 7.5, "rgba(255,255,255,0.12)", Outline(c, w))
        else listOf(RectOp(x - 8.5, 80.0, 17.0, 12.0, if (k == 2) 3.0 else 5.0, "rgba(255,255,255,0.12)", Outline(c, w)))
    return lens(88.0) + lens(112.0) + line("M95.5 85 Q100 83 104.5 85", c, w)
}
