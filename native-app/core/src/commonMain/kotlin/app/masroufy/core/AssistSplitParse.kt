package app.masroufy.core

/**
 * مين في التقسيم (التصميم `AssistSplit`): الأشخاص المعروفين من أسامي المستخدم، والأسامي الجديدة اللي بعد «مع» · «بيني وبين» · «with»
 * (كل اسم مش معروف ⇒ «أضيف «X» إلى الأشخاص؟» — ما بنخمّنش). «على ٣» من غير أسامي ⇒ عدد بس. [lastTransaction] = «قسّم آخر عملية».
 */
data class SplitParse(val known: List<AssistEntity>, val newNames: List<String>, val heads: Int?, val lastTransaction: Boolean)

private val SPLIT_AFTER = setOf("مع", "وبين", "بين", "with", "and")
private val SPLIT_SKIP = setOf(
    "و", "مع", "بين", "وبين", "بيني", "وبيني", "انا", "وانا", "with", "and", "me", "على", "علي", "بالتساوي", "بالنص", "الحساب", "الفاتوره",
    "فاتوره", "العشا", "العشاء", "الغدا", "الغداء", "آخر", "اخر", "عمليه", "ريال", "جنيه", "رس", "جم", "sar", "egp", "كاش", "من", "في",
).map(::assistNormalize).toSet()

fun parseSplit(s: AssistSignals): SplitParse {
    val known = s.entities.ofType(AssistEntityType.PERSON).filter { !it.generic }.distinctBy { it.id }
    val knownTokens = known.flatMap { (it.start until it.end).toList() }.toSet()
    val start = s.tokens.indexOfFirst { t -> cliticForms(t).any { it in SPLIT_AFTER } }
    val newNames = if (start < 0) emptyList() else s.tokens.withIndex().drop(start + 1)
        .filter { (i, t) -> i !in knownTokens && t.none(Char::isDigit) && t.length >= 2 }
        .map { (_, t) -> if (t.startsWith("و") && t.length > 2 && t !in SPLIT_SKIP) t.drop(1) else t }
        .filter { it !in SPLIT_SKIP && s.money.amounts.none { a -> s.tokens.getOrNull(a.tokenIndex) == it } && !AssistWords.SPLIT.single.contains(it) }
        .distinct()
    val last = s.has(AssistWords.LAST)
    return SplitParse(known, newNames.map { rawWord(s.raw, it) }, s.money.headCount, last)
}

/** الاسم زي ما المستخدم كتبه (مش الشكل الموحّد: «سارة» مش «ساره»)، من غير «و» العطف. */
private fun rawWord(raw: String, token: String): String {
    val words = raw.split(Regex("\\s+")).map { it.trim { c -> !c.isLetterOrDigit() } }
    words.firstOrNull { assistNormalize(it) == token }?.let { return it }
    return words.firstOrNull { it.startsWith("و") && assistNormalize(it.drop(1)) == token }?.drop(1) ?: token
}
