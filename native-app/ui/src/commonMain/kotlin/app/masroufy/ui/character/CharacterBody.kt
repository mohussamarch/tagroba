package app.masroufy.ui.character

import app.masroufy.core.CharacterLook

/**
 * «شخصيتك» — الجسم واللبس (`outCol` · `body` 200×440 · `bust` · `capeFull` · `chain` في `CharacterArt.dc.html` @2df8134d).
 * اللبس كله بيترسم جوه مجموعة بتتمط بالعرض حسب هيئة الجسم (`stretch` في [characterScene]) ⇒ أي لبس بيناسب أي هيئة.
 */
internal fun outfitColor(look: CharacterLook): String = when {
    look.outfit == 5 -> pick(ABAYA, look.outfitColor)
    look.outfit == 4 && look.gender == 1 -> "#1C1C1E"
    else -> pick(OUTC, look.outfitColor)
}

private const val SHOE = "#2A2320"
private const val GOLD = "#D4A53A"
private val EDGE = Outline("rgba(0,0,0,0.10)", 1.5)

internal fun body(k: Int, col: String, fem: Boolean): Ops {
    val ln = if (light(col)) "rgba(0,0,0,0.14)" else "rgba(255,255,255,0.12)"
    fun arms(c: String, w: Double): Ops = listOf("M62 124 C52 144 46 194 45 248", "M138 124 C148 144 154 194 155 248").flatMap { d ->
        line(d, "rgba(0,0,0,0.12)", w + 3) + line(d, c, w)
    }
    val ground = ellipse(100.0, 426.0, 62.0, 7.0, "rgba(0,0,0,0.14)")
    fun top(yb: Int): Ops = path("M70 108 Q100 102 130 108 Q146 112 148 128 L144 $yb Q100 ${yb + 6} 56 $yb L52 128 Q54 112 70 108 Z", col, EDGE)
    fun legs(c: String): Ops = path("M64 232 L98 232 L95 414 L73 414 Z M102 232 L136 232 L127 414 L105 414 Z", c) +
        ellipse(83.0, 418.0, 15.0, 6.5, SHOE) + ellipse(117.0, 418.0, 15.0, 6.5, SHOE)
    fun feet(c: String): Ops = ellipse(85.0, 422.0, 13.0, 5.0, c) + ellipse(115.0, 422.0, 13.0, 5.0, c)
    fun robe(c: String): Ops = path("M70 108 Q100 102 130 108 Q146 112 150 128 L157 418 Q100 426 43 418 L50 128 Q54 112 70 108 Z", c, EDGE)
    if (k == 1) {
        return ground + legs("#2F3B37") + top(236) + listOf(RectOp(56.0, 230.0, 88.0, 6.0, 2.0, SHOE)) +
            path("M86 108 L100 124 L114 108 L108 105 L100 114 L92 105 Z", "#FFFFFF", opacity = 0.9) + line("M100 124 L100 228", "rgba(0,0,0,0.10)", 2.0) + arms(col, 19.0)
    }
    if (k == 2) {
        return ground + legs(col) + top(252) + path("M86 108 L100 152 L114 108 Z", "#F4F3EE") + line("M86 108 L97 162 M114 108 L103 162", "rgba(0,0,0,0.25)", 2.2) +
            circle(100.0, 196.0, 2.6, "rgba(0,0,0,0.25)") + arms(col, 19.0)
    }
    // فستان: لون واحد من الكتف للكعب، وسط خفيف وجيبة واسعة
    if (k == 3) {
        return ground + feet(SHOE) + path("M58 198 L142 198 L162 414 Q100 424 38 414 Z", col, EDGE) + top(206) + line("M57 202 Q100 208 143 202", ln, 1.6) +
            line("M86 214 Q80 320 72 410 M114 214 Q120 320 128 410", ln, 1.2) + line("M88 109 Q100 121 112 109", ln, 1.8) + arms(col, 18.0)
    }
    if (k == 4 && !fem) {
        val cc = if (col == "#F4F3EE") "#2A2320" else col
        return ground + feet("#5A4030") + robe("#F4F3EE") + line("M100 116 L100 176", "rgba(0,0,0,0.10)", 2.0) +
            path("M70 106 Q50 112 44 130 L30 420 Q58 426 84 422 L90 118 Z M130 106 Q150 112 156 130 L170 420 Q142 426 116 422 L110 118 Z", cc) +
            line("M70 106 Q80 110 90 118 L84 422 M130 106 Q120 110 110 118 L116 422", GOLD, 3.0) + arms(cc, 19.0)
    }
    // عباية: واسعة لحد الأرض، مفتوحة من قدام بشريطين، وأكمام واسعة — والمكافأة نفس العباية بتطريز دهب
    if (k == 5 || k == 4) {
        val tr = if (k == 4) GOLD else ln; val w = if (k == 4) 2.4 else 1.3
        return ground + feet(SHOE) + path("M70 106 Q100 100 130 106 Q148 110 152 128 L160 418 Q100 426 40 418 L48 128 Q52 110 70 106 Z", col, EDGE) +
            line("M97 110 L95 420 M103 110 L105 420", tr, w) + line("M86 108 Q100 118 114 108", tr, w) + arms(col, 24.0) +
            (if (k == 4) line("M42 414 Q100 422 158 414", GOLD, 2.4) + line("M37 238 L53 242 M163 238 L147 242", GOLD, 3.0) else emptyList())
    }
    // تونيك لحد الركبة بفتحات جنب + بنطلون
    if (k == 6) {
        return ground + legs("#2F3B37") + path("M70 108 Q100 102 130 108 Q146 112 149 128 L152 300 Q100 306 48 300 L51 128 Q54 112 70 108 Z", col, EDGE) +
            line("M52 272 L50 300 M148 272 L150 300", "rgba(0,0,0,0.16)", 1.6) + line("M90 109 L100 122 L110 109", ln, 1.6) +
            listOf(130.0, 140.0, 150.0).flatMap { y -> circle(100.0, y, 1.6, ln) } + arms(col, 19.0)
    }
    return ground + feet("#5A4030") + robe(col) + line("M92 109 Q100 117 108 109", ln, 2.0) + line("M100 116 L100 176", "rgba(0,0,0,0.10)", 2.0) + arms(col, 19.0)
}

internal fun bust(k: Int, col: String, fem: Boolean): Ops {
    val ln = if (light(col)) "rgba(0,0,0,0.14)" else "rgba(255,255,255,0.12)"
    val sh = "M18 200 C22 160 60 142 100 142 C140 142 178 160 182 200 Z"
    val base = path(sh, col)
    if (k == 0) return base + line("M90 142 L100 156 L110 142", "rgba(0,0,0,0.14)", 2.0) + line("M100 156 L100 190", "rgba(0,0,0,0.10)", 2.0)
    if (k == 1) return base + path("M86 142 L100 162 L114 142 L108 140 L100 150 L92 140 Z", "#FFFFFF")
    if (k == 2) return base + path("M84 144 L100 176 L116 144", "#F4F3EE") + line("M84 144 L96 186 M116 144 L104 186", "rgba(0,0,0,0.25)", 2.2)
    if (k == 4 && !fem) {
        val cc = if (col == "#F4F3EE") "#2A2320" else col
        return path(sh, "#F4F3EE") + line("M90 142 L100 156 L110 142", "rgba(0,0,0,0.14)", 2.0) +
            path("M18 200 C22 162 56 144 88 142 L94 200 Z M182 200 C178 162 144 144 112 142 L106 200 Z", cc) + line("M88 142 L94 200 M112 142 L106 200", GOLD, 3.0)
    }
    if (k == 5 || k == 4) {
        val tr = if (k == 4) GOLD else ln; val w = if (k == 4) 3.0 else 1.6
        return base + line("M97 150 L96 200 M103 150 L104 200", tr, w) + line("M86 143 Q100 156 114 143", tr, w)
    }
    if (k == 6) return base + line("M90 143 L100 158 L110 143", ln, 2.0) + listOf(168.0, 180.0, 192.0).flatMap { y -> circle(100.0, y, 2.0, ln) }
    return base + line("M84 143 Q100 162 116 143", ln, 2.0)
}

/** الطرحة الطويلة للخمار والنقاب في الجسم كله (بتتمط مع اللبس). */
internal fun capeFull(col: String): Ops {
    val lt = light(col)
    return path("M70 96 C52 100 34 118 30 150 C26 190 28 228 32 252 Q100 264 168 252 C172 228 174 190 170 150 C166 118 148 100 130 96 Z", col) +
        line("M78 150 Q72 200 70 250 M122 150 Q128 200 130 250", if (lt) "rgba(0,0,0,0.10)" else "rgba(255,255,255,0.07)", 1.6)
}

/** سلسلة الذهب (مكافأة). */
internal fun chain(full: Boolean): Ops {
    val y = if (full) 106.0 else 136.0; val py = y + 30
    return line("M88 " + n(y) + " Q100 " + n(y + 38) + " 112 " + n(y), "#E2B53E", if (full) 2.4 else 3.0, if (full) listOf(3.0, 1.2) else listOf(4.0, 1.4)) +
        circle(100.0, py, if (full) 6.0 else 7.5, "#E2B53E", Outline("#B8902A", 1.0)) + dollar(100.0, py, if (full) 4.4 else 5.6, "#7A5A12", 1.3)
}
