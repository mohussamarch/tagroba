package app.masroufy.ui.character

/**
 * «شخصيتك» — نص SVG من المشهد (Kotlin بحت): نفس عناصر `render` في النموذج، عشان مقارنة الدقة بنفس المتصفح (Edge) تعزل الهندسة.
 * النقشات (`shm` · `tq`) في `<defs>` بنفس تعريف النموذج.
 */
private const val DEFS = "<defs><pattern id=\"shm\" width=\"6\" height=\"6\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate(45)\">" +
    "<rect width=\"6\" height=\"6\" fill=\"#D2363C\"/><circle cx=\"3\" cy=\"3\" r=\"1.15\" fill=\"#FBEFEC\"/></pattern>" +
    "<pattern id=\"tq\" width=\"4\" height=\"4\" patternUnits=\"userSpaceOnUse\"><circle cx=\"2\" cy=\"2\" r=\"0.7\" fill=\"rgba(0,0,0,0.10)\"/></pattern></defs>"

fun CharacterScene.toSvg(): String {
    val sb = StringBuilder()
    val vb = viewBox
    sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"").append(n(vb.x.toDouble())).append(' ').append(n(vb.y.toDouble())).append(' ')
        .append(n(vb.width.toDouble())).append(' ').append(n(vb.height.toDouble())).append("\">").append(DEFS)
    ops.forEach { sb.svgOp(it) }
    return sb.append("</svg>").toString()
}

private fun StringBuilder.outline(o: Outline?) {
    if (o == null) return
    append(" stroke=\"").append(o.color).append("\" stroke-width=\"").append(n(o.width)).append('"')
    if (o.cap == LineCap.ROUND) append(" stroke-linecap=\"round\"")
    if (o.join == LineJoin.ROUND) append(" stroke-linejoin=\"round\"")
    o.dash?.let { d -> append(" stroke-dasharray=\"").append(d.joinToString(" ") { n(it) }).append('"') }
}

private fun StringBuilder.svgOp(op: CharOp) {
    when (op) {
        is PathOp -> {
            append("<path d=\"").append(op.d).append("\" fill=\"").append(op.fill ?: "none").append('"')
            outline(op.outline)
            if (op.evenOdd) append(" fill-rule=\"evenodd\"")
            if (op.opacity != 1.0) append(" opacity=\"").append(n(op.opacity)).append('"')
            append("/>")
        }
        is CircleOp -> {
            append("<circle cx=\"").append(n(op.cx)).append("\" cy=\"").append(n(op.cy)).append("\" r=\"").append(n(op.r)).append("\" fill=\"")
                .append(op.fill ?: "none").append('"')
            outline(op.outline)
            append("/>")
        }
        is EllipseOp -> {
            append("<ellipse cx=\"").append(n(op.cx)).append("\" cy=\"").append(n(op.cy)).append("\" rx=\"").append(n(op.rx)).append("\" ry=\"")
                .append(n(op.ry)).append("\" fill=\"").append(op.fill).append('"')
            if (op.opacity != 1.0) append(" opacity=\"").append(n(op.opacity)).append('"')
            append("/>")
        }
        is RectOp -> {
            append("<rect x=\"").append(n(op.x)).append("\" y=\"").append(n(op.y)).append("\" width=\"").append(n(op.w)).append("\" height=\"")
                .append(n(op.h)).append("\" rx=\"").append(n(op.rx)).append("\" fill=\"").append(op.fill ?: "none").append('"')
            outline(op.outline)
            append("/>")
        }
        is GroupOp -> {
            append("<g transform=\"")
            append(op.transform.joinToString(" ") { tf ->
                when (tf) {
                    is Tf.Move -> "translate(" + n(tf.x) + " " + n(tf.y) + ")"
                    is Tf.Scale -> "scale(" + n(tf.x) + " " + n(tf.y) + ")"
                }
            })
            append("\">")
            op.children.forEach { svgOp(it) }
            append("</g>")
        }
    }
}
