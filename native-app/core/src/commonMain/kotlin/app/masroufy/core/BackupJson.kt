package app.masroufy.core

/**
 * نص JSON بنفس شكل جافاسكربت بالحرف (اتفصل من `Backup.kt` عشان حد الـ300 سطر — CLAUDE.md #7).
 * النسخة الشاملة وبصمتها لازم تطلع زي التطبيق الحالي حرف بحرف.
 */

/** نفس `String(x)` في جافاسكربت للقيم اللي بتيجي من JSON. */
internal fun jsString(value: Any?): String = when (value) {
    null -> "null"
    is String -> value
    is Boolean -> value.toString()
    is Int, is Long -> value.toString()
    is Double -> jsNumber(value)
    is List<*> -> value.joinToString(",") { if (it == null) "" else jsString(it) }
    is Map<*, *> -> "[object Object]"
    else -> value.toString()
}

/** نفس `Number.prototype.toString()` (الأرقام في النسخ أعداد صحيحة؛ الكسور نادرة). */
internal fun jsNumber(d: Double): String {
    if (d.isNaN()) return "NaN"
    if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
    if (d == 0.0) return "0"
    if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e21) return d.toLong().toString()
    // أقصر أرقام من toString، وبعدين شكل جافاسكربت (أُس لو ≥ 1e21 أو < 1e-6)
    val raw = kotlin.math.abs(d).toString().lowercase()
    val mantissa = raw.substringBefore('e')
    val exp = raw.substringAfter('e', "0").toInt()
    val intPart = mantissa.substringBefore('.')
    val fracPart = mantissa.substringAfter('.', "")
    var digits = (intPart + fracPart).trimStart('0')
    val lead = (intPart + fracPart).length - (intPart + fracPart).trimStart('0').length
    var n = intPart.length + exp - lead
    digits = digits.trimEnd('0').ifEmpty { "0" }
    val k = digits.length
    val body = when {
        n in k..21 -> digits + "0".repeat(n - k)
        n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
        n in -5..0 -> "0." + "0".repeat(-n) + digits
        else -> {
            val e = n - 1
            (if (k == 1) digits else digits[0] + "." + digits.substring(1)) + "e" + (if (e >= 0) "+" else "-") + kotlin.math.abs(e)
        }
    }
    return if (d < 0) "-$body" else body
}

/** نفس `JSON.stringify` لقيمة بسيطة أو مركبة (المفاتيح بترتيبها). */
internal fun jsJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> JsText.jsonString(value)
    is Boolean -> value.toString()
    is Int, is Long -> value.toString()
    is Double -> if (value.isFinite()) jsNumber(value) else "null"
    is List<*> -> value.joinToString(",", "[", "]") { jsJson(it) }
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) -> JsText.jsonString(k.toString()) + ":" + jsJson(v) }
    else -> JsText.jsonString(value.toString())
}

/** `JSON.stringify(value)` بالحرف — لنص ملف بيتكتب أو بيتبصم. المفاتيح بترتيب الخريطة. */
fun jsonStringify(value: Any?): String = jsJson(value)

/** `JSON.stringify(value, null, 2)` بالحرف: مسافتين لكل مستوى، والفاضي `[]` و`{}`. */
fun jsonStringifyPretty(value: Any?, indent: String = ""): String = when (value) {
    is List<*> -> if (value.isEmpty()) "[]" else value.joinToString(",\n", "[\n", "\n$indent]") { "$indent  " + jsonStringifyPretty(it, "$indent  ") }
    is Map<*, *> -> if (value.isEmpty()) "{}" else value.entries.joinToString(",\n", "{\n", "\n$indent}") { (k, v) ->
        "$indent  " + JsText.jsonString(k.toString()) + ": " + jsonStringifyPretty(v, "$indent  ")
    }
    else -> jsJson(value)
}
