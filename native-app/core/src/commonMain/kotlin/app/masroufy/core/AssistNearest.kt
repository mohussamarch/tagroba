package app.masroufy.core

/**
 * لقيان الشاشة من الكلام (خطوة «اسم شاشة» في الفهم) و«أقرب الشاشات» لما المساعد مش فاهم (§78-٧ + مطابقة النموذج):
 * - **اسم شاشة:** كل كلمات الشاشة ([SCREEN_KEYWORDS]) — الأطول الأول، والشاشة اللي كلمتها جوه كلمة شاشة أطول بتتشال
 *   («إعدادات الإشعارات» ⇒ الإعدادات بس، مش «الإشعارات» كمان). لحد ٣ شاشات.
 * - **أقرب الشاشات:** أكتر شاشات بتشارك الكلام في **مقاطع من ٣ حروف**، وإلا شاشات التبويب اللي هو فيه ([TAB_NEAREST]). لحد ٣.
 */
private data class ScreenHit(val screen: AssistScreen, val start: Int, val end: Int, val weight: Int)

/** «ال» في أول كلمة الشاشة بتتشال عشان «محلات» تلاقي «المحلات» والعكس (الكلمة في الكلام بتتقارن بكل أشكالها). */
private fun stemOf(word: String): String = if (word.startsWith("ال") && word.length >= 5) word.substring(2) else word

private fun bestHit(screen: AssistScreen, tokens: List<String>): ScreenHit? {
    var best: ScreenHit? = null
    for (kw in SCREEN_KEYWORDS[screen].orEmpty()) {
        for (start in 0..tokens.size - kw.size) {
            val ok = kw.indices.all { k -> tokenHasStem(tokens[start + k], stemOf(kw[k]), fuzzy = kw.size == 1 || k > 0) }
            if (!ok) continue
            val weight = kw.sumOf { it.length } + 10 * (kw.size - 1)
            if (best == null || weight > best.weight) best = ScreenHit(screen, start, start + kw.size, weight)
        }
    }
    return best
}

/** الشاشات اللي اسمها في الكلام (لحد [limit]) — الأقوى الأول. [visible] بيشيل اللي مش ظاهر عند المستخدم (الزكاة المخفية · …). */
fun screensNamedIn(tokens: List<String>, visible: (AssistScreen) -> Boolean = { true }, limit: Int = 3): List<AssistScreen> {
    if (tokens.isEmpty()) return emptyList()
    val hits = AssistScreen.entries.filter { !it.needsEntity && visible(it) }.mapNotNull { bestHit(it, tokens) }
    val kept = hits.filter { h ->
        hits.none { o -> o !== h && o.weight > h.weight && o.start <= h.start && o.end >= h.end }
    }
    return kept.sortedWith(compareByDescending<ScreenHit> { it.weight }.thenBy { it.screen.ordinal }).map { it.screen }.distinctBy { it.board + it.screen.section }.take(limit)
}

private fun grams(text: String): List<String> = text.split(' ').filter { it.length >= 3 }.flatMap { w -> (0..w.length - 3).map { w.substring(it, it + 3) } }

private val SCREEN_GRAM_TEXT: Map<AssistScreen, String> = SCREEN_WORDS.mapValues { (_, words) -> words.joinToString(" ") { assistNormalize(it) } }

/** أقرب ٣ شاشات للكلام اللي ما اتفهمش — مقاطع ٣ حروف مشتركة، وإلا شاشات التبويب. */
fun nearestScreens(normalized: String, tab: AssistTab, visible: (AssistScreen) -> Boolean = { true }, limit: Int = 3): List<AssistScreen> {
    val g = grams(normalized)
    val scored = SCREEN_GRAM_TEXT.entries.filter { (s, _) -> !s.needsEntity && visible(s) }
        .map { (s, text) -> s to g.count { it in text } }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<AssistScreen, Int>> { it.second }.thenBy { it.first.ordinal })
        .map { it.first }
    if (scored.isNotEmpty()) return scored.take(limit)
    return TAB_NEAREST.getValue(tab).filter(visible).take(limit)
}
