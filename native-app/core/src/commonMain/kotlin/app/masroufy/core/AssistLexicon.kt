package app.masroufy.core

/**
 * أسامي المستخدم نفسه اللي المساعد بيدوّر عليها في الكلام (تصنيفاته · محلاته وأساميها التانية · أشخاصه · محافظه · أهدافه · أحداثه ·
 * مشاريعه · فواتيره · أصوله · جمعياته · أقساطه). بيتبني في طبقة الاستخدامات من المستودعات — هنا المطابقة بس (دالة نقية).
 */
enum class AssistEntityType { CATEGORY, MERCHANT, PERSON, WALLET, GOAL, EVENT, PROJECT, RECURRING, ASSET, ROSCA, PLAN }

/** اسم واحد من أسامي المستخدم: [names] الاسم وأساميه التانية (زي ما اتكتبت). */
data class LexItem(val id: Id, val names: List<String>, val archived: Boolean = false)

data class AssistLexicon(
    val categories: List<Category> = emptyList(),
    val merchants: List<Merchant> = emptyList(),
    val people: List<Person> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val goals: List<LexItem> = emptyList(),
    val events: List<LexItem> = emptyList(),
    val projects: List<LexItem> = emptyList(),
    val recurring: List<RecurringItem> = emptyList(),
    val assets: List<LexItem> = emptyList(),
    val roscas: List<LexItem> = emptyList(),
    val plans: List<LexItem> = emptyList(),
)

/**
 * اسم لقيناه في الكلام: النوع والمعرّف والاسم المعروض، ومكانه في الكلمات. [generic] = جه من كلمة عامة («قهوة» ⇒ تصنيف القهوة ·
 * «كاش» ⇒ محفظة الكاش) مش من الاسم نفسه. [partial] = جزء مميز من الاسم بس («المرسى» من «مقهى المرسى»).
 */
data class AssistEntity(
    val type: AssistEntityType,
    val id: Id,
    val name: String,
    val start: Int,
    val end: Int,
    val generic: Boolean = false,
    val partial: Boolean = false,
)

private fun formsOf(name: String): List<String> = assistTokens(assistNormalize(name))

/** الكلمة في الكلام = كلمة الاسم: نفس الشكل بعد شيل الأدوات من الطرفين. */
internal fun sameWord(textToken: String, nameToken: String): Boolean {
    val a = cliticForms(textToken)
    val b = cliticForms(nameToken)
    return a.any { it in b }
}

/** أماكن الاسم (كلماته بالترتيب) في الكلام. */
private fun spans(tokens: List<String>, name: List<String>): List<IntRange> {
    if (name.isEmpty() || tokens.size < name.size) return emptyList()
    return (0..tokens.size - name.size).filter { s -> name.indices.all { k -> sameWord(tokens[s + k], name[k]) } }.map { it until it + name.size }
}

/** الجزء المميز من الاسم (من غير الكلمات العامة زي «مقهى» و«مصرف») — حرفين على الأقل. */
private fun distinctive(name: List<String>): List<String> = name.filter { t -> cliticForms(t).none { it in GENERIC_NAME_WORDS } && t.length >= 3 }

private fun match(
    tokens: List<String>,
    type: AssistEntityType,
    id: Id,
    display: String,
    names: List<String>,
    allowPartial: Boolean,
): List<AssistEntity> {
    val out = mutableListOf<AssistEntity>()
    for (n in names) {
        val words = formsOf(n)
        spans(tokens, words).forEach { out += AssistEntity(type, id, display, it.first, it.last + 1) }
        if (allowPartial && out.isEmpty()) {
            val key = distinctive(words)
            if (key.isNotEmpty() && key.size < words.size) spans(tokens, key).forEach { out += AssistEntity(type, id, display, it.first, it.last + 1, partial = true) }
        }
    }
    return out.distinctBy { it.start to it.end }
}

/**
 * كل الأسامي اللي في الكلام. الشخص: الاسم كله، أو الاسم الأول لوحده (لو اتنين ليهم نفس الاسم الأول ⇒ الاتنين بيطلعوا والمساعد بيسأل ·
 * «ما بيخمّنش»). التصنيف: اسمه (القديم والجديد §66) وإلا الكلمة العامة لو تصنيفها موجود عنده. المحفظة: اسمها أو «كاش»/«البنك».
 */
fun findEntities(tokens: List<String>, lex: AssistLexicon): List<AssistEntity> {
    val out = mutableListOf<AssistEntity>()
    for (c in lex.categories.filter { it.active }) {
        val aliases = listOf(c.name) + CATEGORY_NAME_ALIASES.filterValues { it == canonicalCategoryName(c.name) }.keys
        out += match(tokens, AssistEntityType.CATEGORY, c.id, c.name, aliases, allowPartial = false)
    }
    val known = lex.categories.filter { it.active }.associateBy { it.id }
    for ((i, t) in tokens.withIndex()) {
        if (out.any { it.type == AssistEntityType.CATEGORY && i in it.start until it.end }) continue
        val targets = cliticForms(t).firstNotNullOfOrNull { SEED_WORD_INDEX[it.removePrefix("ال")] ?: SEED_WORD_INDEX[it] } ?: continue
        val c = targets.firstNotNullOfOrNull { known[it] } ?: continue
        out += AssistEntity(AssistEntityType.CATEGORY, c.id, c.name, i, i + 1, generic = true)
    }
    for (m in lex.merchants) {
        val names = listOf(m.displayName, m.normalizedName) + m.aliases.orEmpty()
        out += match(tokens, AssistEntityType.MERCHANT, m.id, m.displayName, names, allowPartial = true)
    }
    for (p in lex.people) {
        val full = match(tokens, AssistEntityType.PERSON, p.id, p.name, listOf(p.name), allowPartial = false)
        out += full.ifEmpty {
            val first = formsOf(p.name).firstOrNull()?.takeIf { it.length >= 3 && formsOf(p.name).size > 1 } ?: return@ifEmpty emptyList()
            spans(tokens, listOf(first)).map { AssistEntity(AssistEntityType.PERSON, p.id, p.name, it.first, it.last + 1, partial = true) }
        }
    }
    for (w in lex.wallets) out += match(tokens, AssistEntityType.WALLET, w.id, w.name, listOf(w.name), allowPartial = true)
    for ((i, t) in tokens.withIndex()) {
        val word = when {
            cliticForms(t).any { it in CASH_WORDS } -> WalletWord.CASH
            cliticForms(t).any { it in BANK_WORDS } -> WalletWord.BANK
            else -> null
        } ?: continue
        if (out.any { it.type == AssistEntityType.WALLET && i in it.start until it.end }) continue
        val pool = lex.wallets.filter { if (word == WalletWord.CASH) it.kind == "cash" else it.kind == "bank" || it.kind == "digital_wallet" }
        pool.forEach { out += AssistEntity(AssistEntityType.WALLET, it.id, it.name, i, i + 1, generic = true) }
    }
    val simple = listOf(
        AssistEntityType.GOAL to lex.goals, AssistEntityType.EVENT to lex.events, AssistEntityType.PROJECT to lex.projects,
        AssistEntityType.ASSET to lex.assets, AssistEntityType.ROSCA to lex.roscas, AssistEntityType.PLAN to lex.plans,
    )
    for ((type, items) in simple) for (item in items.filter { !it.archived }) {
        out += match(tokens, type, item.id, item.names.first(), item.names, allowPartial = true)
    }
    for (r in lex.recurring.filter { it.active }) out += match(tokens, AssistEntityType.RECURRING, r.id, r.name, listOf(r.name), allowPartial = true)
    return out
}

/** كل الأسامي من نوع معين (بالترتيب في الكلام). */
fun List<AssistEntity>.ofType(type: AssistEntityType): List<AssistEntity> = filter { it.type == type }.sortedBy { it.start }

/** معرّفات مختلفة لنفس المكان ⇒ الاسم ملتبس (شخصين بنفس الاسم · محفظتين كاش) ⇒ المساعد بيسأل. */
fun List<AssistEntity>.ambiguous(type: AssistEntityType): Boolean = ofType(type).groupBy { it.start }.values.any { g -> g.map { it.id }.distinct().size > 1 }

/** التصنيف وكل فروعه (الأب بيشمل ولاده زي سطور الميزانية في الشجرة). */
fun categoryWithChildren(id: Id, categories: List<Category>): Set<Id> {
    val out = linkedSetOf(id)
    var frontier = setOf(id)
    repeat(4) {
        frontier = categories.filter { it.parentId != null && it.parentId in frontier && it.id !in out }.map { it.id }.toSet()
        out += frontier
    }
    return out
}
