package app.masroufy.core

import kotlin.math.abs

/**
 * ألوان التصنيفات — نقل `categoryColors.ts` + `categoryPalette.ts` (OVERRIDES §28 و§33.1).
 * ألوان مش فلوس، فالكسور مسموحة — نفس عمليات الـdouble بالظبط عشان نفس الألوان.
 */
data class CategoryColorPair(val lightColor: String, val darkColor: String)

data class CategorySwatch(val key: String, val nameKey: TextKey, val h: Double, val s: Double, val l: Double) {
    /** الاسم المعروض باللغة الحالية (Texts.kt). */
    val name: String get() = uiText(nameKey)
}

val SUB_LIGHTNESS_OFFSETS: List<Double> = listOf(7.0, -6.0, 12.0, -10.0, 16.0, -3.0, 10.0)

fun hslToHex(h: Double, s: Double, l: Double): String {
    val sat = s / 100
    val light = l / 100
    fun k(n: Double) = (n + h / 30) % 12
    val a = sat * minOf(light, 1 - light)
    fun channel(n: Double) = light - a * maxOf(-1.0, minOf(k(n) - 3, minOf(9 - k(n), 1.0)))
    return "#" + listOf(0.0, 8.0, 4.0).joinToString("") { n -> JsText.round(channel(n) * 255).toInt().toString(16).padStart(2, '0') }.uppercase()
}

private fun clamp(value: Double, low: Double, high: Double) = maxOf(low, minOf(high, value))
private fun lightCap(h: Double) = if (h >= 35 && h <= 110) 40.0 else 52.0

fun categoryColors(h: Double, s: Double, l: Double) = CategoryColorPair(
    lightColor = hslToHex(h, s, clamp(l, 22.0, lightCap(h))),
    darkColor = hslToHex(h, minOf(s + 5, 90.0), clamp(l + 24, 58.0, 74.0)),
)

fun subCategoryColors(h: Double, s: Double, l: Double, index: Int): CategoryColorPair =
    categoryColors(h, s, l + SUB_LIGHTNESS_OFFSETS[index % SUB_LIGHTNESS_OFFSETS.size])

val CATEGORY_SWATCHES: List<CategorySwatch> = listOf(
    CategorySwatch("red", TextKey.COLOR_RED, 2.0, 70.0, 44.0), CategorySwatch("orange", TextKey.COLOR_ORANGE, 22.0, 78.0, 46.0),
    CategorySwatch("amber", TextKey.COLOR_AMBER, 40.0, 90.0, 38.0), CategorySwatch("olive", TextKey.COLOR_OLIVE, 60.0, 80.0, 28.0),
    CategorySwatch("lime", TextKey.COLOR_LIME, 85.0, 65.0, 30.0), CategorySwatch("green", TextKey.COLOR_GREEN, 135.0, 55.0, 34.0),
    CategorySwatch("mint", TextKey.COLOR_MINT, 158.0, 65.0, 30.0), CategorySwatch("teal", TextKey.COLOR_TEAL, 174.0, 70.0, 29.0),
    CategorySwatch("cyan", TextKey.COLOR_CYAN, 190.0, 80.0, 33.0), CategorySwatch("blue", TextKey.COLOR_BLUE, 210.0, 70.0, 42.0),
    CategorySwatch("indigo", TextKey.COLOR_INDIGO, 250.0, 55.0, 50.0), CategorySwatch("purple", TextKey.COLOR_PURPLE, 268.0, 40.0, 40.0),
    CategorySwatch("lilac", TextKey.COLOR_LILAC, 285.0, 50.0, 48.0), CategorySwatch("magenta", TextKey.COLOR_MAGENTA, 305.0, 50.0, 42.0),
    CategorySwatch("fuchsia", TextKey.COLOR_FUCHSIA, 322.0, 62.0, 40.0), CategorySwatch("rose", TextKey.COLOR_ROSE, 340.0, 65.0, 48.0),
    CategorySwatch("slate", TextKey.COLOR_SLATE, 200.0, 22.0, 32.0), CategorySwatch("gray", TextKey.COLOR_GRAY, 210.0, 8.0, 45.0),
)

private fun CategorySwatch.colors() = categoryColors(h, s, l)

fun swatchColors(key: String): CategoryColorPair? = CATEGORY_SWATCHES.firstOrNull { it.key == key }?.colors()

/** الدرجة اللي لونها هو لون التصنيف ده بالظبط — شاشة التعديل بتعلّم اللون الحالي. */
fun swatchKeyOf(lightColor: String): String? {
    val wanted = lightColor.uppercase()
    return CATEGORY_SWATCHES.firstOrNull { it.colors().lightColor == wanted }?.key
}

/** أول درجة مش مستخدمة في أي أساسي — اللون المبدئي للتصنيف الجديد. */
fun firstFreeSwatch(categories: List<Category>): String {
    val used = categories.filter { it.parentId == null }.map { it.lightColor.uppercase() }.toSet()
    return (CATEGORY_SWATCHES.firstOrNull { it.colors().lightColor !in used } ?: CATEGORY_SWATCHES[0]).key
}

data class Hsl(val h: Double, val s: Double, val l: Double)

private val HEX = Regex("^#?([0-9a-f]{6})$", RegexOption.IGNORE_CASE)

fun hexToHsl(hex: String): Hsl? {
    val match = HEX.find(JsText.trim(hex)) ?: return null
    val value = match.groupValues[1].toInt(16)
    val r = ((value shr 16) and 255) / 255.0
    val g = ((value shr 8) and 255) / 255.0
    val b = (value and 255) / 255.0
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2
    val d = max - min
    if (d == 0.0) return Hsl(0.0, 0.0, l * 100)
    val s = d / (1 - abs(2 * l - 1))
    var h = when (max) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    h *= 60
    if (h < 0) h += 360
    return Hsl(h, s * 100, l * 100)
}

/** لون الفرعي رقم `index` تحت أساسي: نفس الدرجة أفتح أو أغمق شوية. لون مش مفهوم ⇒ لون أبوه زي ما هو. */
fun childColors(parent: CategoryColorPair, index: Int): CategoryColorPair {
    val hsl = hexToHsl(parent.lightColor) ?: return parent.copy()
    return subCategoryColors(hsl.h, hsl.s, hsl.l, index)
}
