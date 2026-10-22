package app.masroufy.core

/**
 * r06-darahim (PLAN-0.6 §12-4): **تنضيف اسم الكارت للعرض بس** — الاسم الأصلي (`rawMerchantName`) بيفضل زي ما هو، والمطابقة
 * والقواعد وذاكرة التجار بتشتغل عليه هو.
 * - «<الاسم> <مدينة> <كود بلد من حرفين>» أو «<الاسم> <مدينة>» ⇒ «<الاسم>» — **المدينة لازم تبقى معروفة** (قوايم `SmsCountryNames.kt`)،
 *   فـ«SOME SHOP SA» لوحدها ما بتتقصّش (ممكن «SA» جزء من الاسم).
 * - الاسم كله حروف لاتيني كبيرة ⇒ أول حرف كبير والباقي صغير («TEST DINER» ⇒ «Test Diner»). المكتوب بحروف مختلطة أو بالعربي ما بيتلمسش.
 * - عمر الاسم ما يتشال كله: لو مفيش غير المدينة ⇒ الاسم زي ما هو.
 */
fun cleanCardName(raw: String): String {
    val tokens = raw.trim().split(WS).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return raw.trim()
    var body = tokens
    if (body.size >= 3 && isCountryCode2(body.last())) {
        cityTail(body.dropLast(1))?.let { n -> if (body.size - 1 - n >= 1) body = body.dropLast(1 + n) }
    } else {
        cityTail(body)?.let { n -> if (body.size - n >= 1) body = body.dropLast(n) }
    }
    val joined = body.joinToString(" ")
    return if (joined.any { it in 'A'..'Z' } && joined.none { it in 'a'..'z' }) body.joinToString(" ", transform = ::titleWord) else joined
}

private val WS = Regex("\\s+")

private fun isCountryCode2(token: String): Boolean = token.length == 2 && token.uppercase() in ISO_ALPHA2

/** عدد كلمات المدينة المعروفة في آخر [tokens] (لحد 3، والأطول الأول)، أو null. */
private fun cityTail(tokens: List<String>): Int? {
    for (n in minOf(3, tokens.size - 1) downTo 1) if (placeKey(tokens.takeLast(n).joinToString(" ")) in KNOWN_CITIES) return n
    return null
}

private fun titleWord(word: String): String = word.lowercase().replaceFirstChar { if (it in 'a'..'z') it.uppercaseChar() else it }
