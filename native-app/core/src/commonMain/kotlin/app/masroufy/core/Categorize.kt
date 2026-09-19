package app.masroufy.core

/**
 * محرك التصنيف — نقل `src/domain/categorize.ts` + `merchantIndex.ts` + `merchantMemory.ts` (الأولوية في spec/05):
 *   ١. تأكيد المستخدم (ما يتكتبش فوقه) ← ٢. التاجر المؤكد ← ٣. القواعد بالأولوية ← ٤. عمود الملف ← وإلا من غير تصنيف.
 */
data class Merchant(
    val id: Id,
    val displayName: String,
    val normalizedName: String,
    val aliases: List<String>? = null,
    val logoAsset: String? = null,
    val logoSource: String? = null,
    /** التصنيف المؤكد من المستخدم — أقوى من القواعد. */
    val verifiedCategoryId: Id? = null,
)

enum class RuleMatchMode(val wire: String) {
    CONTAINS("contains"), STARTS_WITH("startsWith"), EXACT("exact");

    companion object {
        fun fromWire(wire: String): RuleMatchMode = entries.first { it.wire == wire }
    }
}

data class ClassificationRule(
    val id: Id,
    /** الأصغر بيتطبق الأول. */
    val priority: Int,
    val matchText: String,
    val matchMode: RuleMatchMode,
    val categoryId: Id,
    val enabled: Boolean,
)

enum class CategorizationSource(val wire: String) {
    USER_CONFIRMED("user_confirmed"), VERIFIED_MERCHANT("verified_merchant"), RULE("rule"), SOURCE_CATEGORY("source_category"), NONE("none"),
}

data class CategorizationInput(
    val currentConfirmed: Boolean,
    val currentCategoryId: Id? = null,
    val merchantName: String? = null,
    val description: String? = null,
    /** عمود التصنيف في الملف — دليل مش حكم. */
    val sourceCategory: String? = null,
)

data class CategorizationResult(
    val categoryId: Id?,
    val source: CategorizationSource,
    val reviewState: ReviewState,
    /** سبب القرار بلغة المستخدم (spec/04). */
    val reason: String,
    val matchedBy: String? = null,
)

class CategorizeDeps(
    /** التجار بأسمائهم المطبعنة (والأسماء البديلة). */
    val merchantsByNormalizedName: Map<String, Merchant>,
    /** القواعد مرتبة بالأولوية ومن غير المقفولة (`prepareRules`). */
    val rules: List<ClassificationRule>,
    /** اسم التصنيف المطبعن ⇒ معرّفه (لعمود الملف). */
    val categoryIdByName: Map<String, Id>,
)

/** الأسماء البديلة الأول، وبعدها الاسم الأساسي بيكسب لو اتكرر. */
fun merchantIndex(merchants: List<Merchant>): Map<String, Merchant> {
    val out = LinkedHashMap<String, Merchant>()
    for (m in merchants) for (a in m.aliases.orEmpty()) out[normalizeText(a)] = m
    for (m in merchants) out[normalizeText(m.normalizedName)] = m
    return out
}

/** بيرتّب القواعد ويشيل المقفولة — ترتيب ثابت للمتساويين زي جافاسكربت. */
fun prepareRules(rules: List<ClassificationRule>): List<ClassificationRule> = rules.filter { it.enabled }.sortedBy { it.priority }

/** مطابقة نص قاعدة على اسم أو وصف — نفس المطابقة لقواعد التصنيف والمشاريع (OVERRIDES §34). */
fun matchesText(matchText: String, matchMode: RuleMatchMode, haystack: String): Boolean {
    val target = normalizeText(matchText)
    if (target.isEmpty()) return false
    val text = normalizeText(haystack)
    return when (matchMode) {
        RuleMatchMode.EXACT -> text == target
        RuleMatchMode.STARTS_WITH -> text.startsWith(target)
        RuleMatchMode.CONTAINS -> normalizedContains(haystack, matchText)
    }
}

fun categorize(input: CategorizationInput, deps: CategorizeDeps): CategorizationResult {
    if (input.currentConfirmed && input.currentCategoryId != null) {
        return CategorizationResult(input.currentCategoryId, CategorizationSource.USER_CONFIRMED, ReviewState.CONFIRMED, "أنت أكّدت التصنيف ده بنفسك")
    }
    if (!input.merchantName.isNullOrEmpty()) {
        val merchant = deps.merchantsByNormalizedName[normalizeText(input.merchantName)]
        if (merchant?.verifiedCategoryId != null) {
            return CategorizationResult(
                merchant.verifiedCategoryId, CategorizationSource.VERIFIED_MERCHANT, ReviewState.CONFIRMED,
                "التاجر «${merchant.displayName}» له تصنيف مؤكد", merchant.displayName,
            )
        }
    }
    // القواعد على اسم التاجر الأول وبعدين الوصف — التاجر أدل من نص العملية الطويل
    val haystacks = listOfNotNull(input.merchantName, input.description).filter { it.isNotEmpty() }
    for (rule in deps.rules) {
        for (haystack in haystacks) {
            if (matchesText(rule.matchText, rule.matchMode, haystack)) {
                return CategorizationResult(rule.categoryId, CategorizationSource.RULE, ReviewState.SUGGESTED, "قاعدة «${rule.matchText}» طابقت", rule.matchText)
            }
        }
    }
    if (!input.sourceCategory.isNullOrEmpty()) {
        val id = deps.categoryIdByName[normalizeText(input.sourceCategory)]
        if (id != null) {
            return CategorizationResult(
                id, CategorizationSource.SOURCE_CATEGORY, ReviewState.SUGGESTED,
                "التصنيف جه من عمود التصنيف في الملف: «${input.sourceCategory}»", input.sourceCategory,
            )
        }
    }
    return CategorizationResult(null, CategorizationSource.NONE, ReviewState.NEEDS_REVIEW, "مفيش قاعدة ولا تاجر مؤكد طابق العملية دي")
}

/**
 * «افتكر المحل ده» (OVERRIDES §36): الموجود بالاسم أو اسم بديل بياخد التصنيف المؤكد، والجديد بيتعمل.
 * null لو الاسم فاضي أو أرقام بس.
 */
fun rememberMerchant(all: List<Merchant>, rawName: String, categoryId: Id, newId: Id): Merchant? {
    val displayName = collapseSpaces(JsText.trim(rawName))
    val normalizedName = normalizeText(displayName)
    if (normalizedName.isEmpty() || displayName.all { it in '0'..'9' || JsText.isWhitespace(it) || it in "*•.:-" }) return null
    val existing = merchantIndex(all)[normalizedName]
    if (existing != null) return existing.copy(verifiedCategoryId = categoryId)
    return Merchant(newId, displayName.take(120), normalizedName, verifiedCategoryId = categoryId)
}

/** نفس استبدال المسافات المتتالية (بكل أنواعها) بمسافة واحدة في جافاسكربت. */
private fun collapseSpaces(text: String): String = buildString {
    var inSpace = false
    for (c in text) {
        if (JsText.isWhitespace(c)) {
            if (!inSpace) append(' ')
            inSpace = true
        } else {
            append(c)
            inSpace = false
        }
    }
}
