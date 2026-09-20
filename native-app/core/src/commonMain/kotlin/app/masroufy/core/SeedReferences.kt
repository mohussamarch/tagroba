package app.masroufy.core

/**
 * المراجع الأولية للحساب الجديد — نقل `categoryTreeLoader.ts` + `referenceLoader.ts` + `detectSourceType.ts` + `inspectFile.ts`.
 * **المعرّفات لازم تطلع نفسها بالظبط** (`cat-<الاسم>`, `rule-0001`, `merch-00001`): الحسابين (القديم والجديد)
 * بيقروا نفس فايربيز، وقاعدة التجار المشتركة بتستعمل معرّفات الشجرة.
 */
// النصوص في الملف ده كلها **مطابقة** لملفات المراجع المرفقة مع التطبيق، أو رسايل سلامة
// بتظهر للمطور لو الملفات دي اتكسرت — مش نصوص واجهة، وما تتترجمش (Texts.kt)
data class RawSub(val name: String, val icon: String, val requires: String? = null)
data class RawMain(
    val name: String,
    val icon: String,
    val h: Double,
    val s: Double,
    val l: Double,
    val from: List<String> = emptyList(),
    val requires: String? = null,
    val noCarName: String? = null,
    val noCarIcon: String? = null,
    val subs: List<RawSub>,
)
data class RawGroup(val key: String, val mains: List<RawMain>)
data class RawCategoryTree(val groups: List<RawGroup>, val ruleWordOverrides: Map<String, List<String>> = emptyMap())

data class BuiltCategoryTree(val categories: List<Category>, val aliases: Map<String, Id>, val wordOverrides: Map<String, Id>)

class SeedError(message: String) : IllegalArgumentException(message)

private fun slug(text: String) = normalizeText(text).replace(' ', '-').lowercase()

private fun checkedRequirement(value: String?, where: String): String? {
    if (value == null) return null
    if (!isCategoryRequirement(value)) throw SeedError("شرط ظهور مش معروف «$value» في $where")
    return value
}

/** الشجرة (OVERRIDES §28–28.1): المعرّف من الاسم، والأسماء القديمة وكلمات القواعد بتوصل للتصنيف الجديد. */
fun buildCategoryTree(raw: RawCategoryTree): BuiltCategoryTree {
    val categories = mutableListOf<Category>()
    val idByPath = HashMap<String, Id>()
    var order = 0
    for (group in raw.groups) {
        if (!isCategoryGroupKey(group.key)) throw SeedError("مجموعة مش معروفة «${group.key}»")
        for (main in group.mains) {
            val id = "cat-${slug(main.name)}"
            val colors = categoryColors(main.h, main.s, main.l)
            categories += Category(
                id, null, main.name, main.icon, colors.lightColor, colors.darkColor, true, order++, group.key,
                checkedRequirement(main.requires, main.name), main.noCarName, main.noCarIcon,
            )
            idByPath[main.name] = id
            main.subs.forEachIndexed { index, sub ->
                if (sub.name.isEmpty() || sub.icon.isEmpty()) throw SeedError("فرعي ناقص تحت «${main.name}»")
                val subId = "$id--${slug(sub.name)}"
                val c = subCategoryColors(main.h, main.s, main.l, index)
                categories += Category(subId, id, sub.name, sub.icon, c.lightColor, c.darkColor, true, order++, requires = checkedRequirement(sub.requires, sub.name))
                idByPath["${main.name}›${sub.name}"] = subId
            }
        }
    }
    val seen = HashSet<Id>()
    for (c in categories) if (!seen.add(c.id)) throw SeedError("اسم تصنيف مكرر بنفس المعرّف: ${c.id}")

    // الأسماء الموجودة الأول (الاسم المتكرر ما يتربطش بحد)، وبعدين الأسماء القديمة
    val aliases = LinkedHashMap<String, Id>()
    val ambiguous = LinkedHashSet<String>()
    for (c in categories) {
        val key = normalizeText(c.name)
        if (key in aliases) ambiguous += key else aliases[key] = c.id
    }
    ambiguous.forEach { aliases.remove(it) }
    for (group in raw.groups) for (main in group.mains) {
        val mainId = idByPath.getValue(main.name)
        for (old in main.from) {
            val key = normalizeText(old)
            if (key !in aliases && key !in ambiguous) aliases[key] = mainId
        }
    }
    val wordOverrides = LinkedHashMap<String, Id>()
    for ((word, path) in raw.ruleWordOverrides) {
        val target = idByPath["${path.getOrNull(0)}›${path.getOrNull(1)}"] ?: throw SeedError("كلمة «$word» بتشاور على فرعي مش موجود: ${path.joinToString(" › ")}")
        wordOverrides[normalizeText(word)] = target
    }
    return BuiltCategoryTree(categories, aliases, wordOverrides)
}

data class RawRule(val word: String?, val cat: String?)
data class RawMerchant(val name: String?, val cat: String?, val confidence: String?)

data class LoadedReferences(
    val rules: List<ClassificationRule>,
    val merchants: List<Merchant>,
    /** أسماء تصنيفات في المراجع مالهاش تصنيف — بتتبلّغ ومش بتتخترع. */
    val unknownCategoryNames: List<String>,
    val unverifiedMerchantCount: Int,
)

/** القواعد بأولوية ترتيبها في الملف (الأخص للأعم)؛ التاجر «يحتاج تأكيد» ما بياخدش تصنيف مؤكد (spec/05). */
fun loadReferences(rawRules: List<RawRule>, rawMerchants: List<RawMerchant>, categories: List<Category>, tree: BuiltCategoryTree? = null): LoadedReferences {
    val byName = LinkedHashMap<String, Id>(tree?.aliases ?: emptyMap())
    for (c in categories) byName[normalizeText(c.name)] = c.id
    val unknown = LinkedHashSet<String>()
    val rules = mutableListOf<ClassificationRule>()
    rawRules.forEachIndexed { index, raw ->
        val word = raw.word?.let(JsText::trim).orEmpty()
        val cat = raw.cat?.let(JsText::trim).orEmpty()
        if (word.isEmpty() || cat.isEmpty()) return@forEachIndexed
        val categoryId = tree?.wordOverrides?.get(normalizeText(word)) ?: byName[normalizeText(cat)]
        if (categoryId == null) {
            unknown += cat
            return@forEachIndexed
        }
        rules += ClassificationRule("rule-${(index + 1).toString().padStart(4, '0')}", index + 1, word, RuleMatchMode.CONTAINS, categoryId, true)
    }
    val merchants = mutableListOf<Merchant>()
    var unverified = 0
    val seen = HashSet<String>()
    rawMerchants.forEachIndexed { index, raw ->
        val name = raw.name?.let(JsText::trim).orEmpty()
        if (name.isEmpty()) return@forEachIndexed
        val normalized = normalizeText(name)
        if (normalized.isEmpty() || !seen.add(normalized)) return@forEachIndexed
        var verified: Id? = null
        val cat = raw.cat?.let(JsText::trim).orEmpty()
        val confident = raw.confidence?.let(JsText::trim) == "مؤكد"
        if (cat.isNotEmpty() && cat != "يحتاج تأكيد" && confident) {
            verified = byName[normalizeText(cat)]
            if (verified == null) unknown += cat
        }
        if (verified.isNullOrEmpty()) unverified++
        merchants += Merchant("merch-${(index + 1).toString().padStart(5, '0')}", name, normalized, verifiedCategoryId = verified)
    }
    return LoadedReferences(rules, merchants, unknown.sorted(), unverified)
}

/** وسم مصدر الدفعة (المخطط نفسه بيتكشف من الترويسة). */
fun guessSourceType(content: String): String {
    val header = stripBom(content).take(200)
    if (header.startsWith("date,name,amount")) return "csv_preview"
    if ("التاريخ" in header && "مدين" in header) return "csv_legacy"
    return "csv_preview"
}

data class FileCheck(
    /** "csv" / "pdf" / "binary" / "empty". */
    val kind: String,
    /** يكمل لقارئ الـCSV؟ */
    val ok: Boolean,
    val message: String?,
)

/** فحص نوع الملف **قبل** التحليل — عشان PDF ما يوصلش قارئ CSV ويطلع رسالة كذب. */
fun inspectFile(fileName: String, content: String): FileCheck {
    val trimmedStart = if (content.startsWith(0xFEFF.toChar())) content.substring(1) else content
    if (JsText.trim(trimmedStart).isEmpty()) return FileCheck("empty", false, "الملف فاضي.")
    val lowerName = fileName.lowercase()
    if (trimmedStart.startsWith("%PDF-") || lowerName.endsWith(".pdf")) return FileCheck("pdf", false, "ده ملف PDF — بنقراه بقارئ مختلف عن الـCSV.")
    val sample = trimmedStart.take(4000)
    var control = 0
    for (ch in sample) {
        val code = ch.code
        if (code == 9 || code == 10 || code == 13) continue
        if (code < 32 || code == 127 || code == 0xFFFD) control++
    }
    val ratio = if (sample.isEmpty()) 0.0 else control.toDouble() / sample.length
    if (0.toChar() in sample || ratio > 0.02) {
        val dot = lowerName.lastIndexOf('.')
        val ext = if (dot > 0) lowerName.substring(dot) else ""
        return FileCheck("binary", false, "الملف ده مش ملف نصي${if (ext.isNotEmpty()) " ($ext)" else ""}. " + "المتوقع ملف CSV — يعني نص فيه أعمدة مفصولة بفواصل.")
    }
    return FileCheck("csv", true, null)
}
