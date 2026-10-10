package app.masroufy.core

/**
 * محرك التصنيف — نقل `src/domain/categorize.ts` + `merchantIndex.ts` + `merchantMemory.ts` (الأولوية في spec/05):
 *   ١. تأكيد المستخدم (ما يتكتبش فوقه) ← ٢. التاجر المؤكد ← ٢ب. تصنيفه في بلدك التانية (اقتراح — §64) ← ٣. القواعد بالأولوية ←
 *   ٤. عمود الملف ← وإلا من غير تصنيف.
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
    /**
     * **اقتراح مش تأكيد** (رد المالك §64-٢): في بلد غير السعودية، التاجر اللي مالوش تصنيف هناك وتصنيفه المؤكد في السعودية موجود
     * في شجرة البلد دي ⇒ تصنيف السعودية بييجي هنا اقتراح. **عمره ما بيتخزن** (المحوّل ما بيكتبوش، و`SpaceMerchantRepository` بيشيله
     * قبل أي حفظ).
     */
    val suggestedCategoryId: Id? = null,
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

    /** تصنيف التاجر في بلدك التانية — اقتراح بس (رد المالك §64-٢). */
    OTHER_COUNTRY_MERCHANT("other_country_merchant"),
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
        return CategorizationResult(input.currentCategoryId, CategorizationSource.USER_CONFIRMED, ReviewState.CONFIRMED, uiText(TextKey.CATEGORIZED_USER_CONFIRMED))
    }
    if (!input.merchantName.isNullOrEmpty()) {
        val merchant = deps.merchantsByNormalizedName[normalizeText(input.merchantName)]
        if (merchant?.verifiedCategoryId != null) {
            return CategorizationResult(
                merchant.verifiedCategoryId, CategorizationSource.VERIFIED_MERCHANT, ReviewState.CONFIRMED,
                uiText(TextKey.CATEGORIZED_MERCHANT, merchant.displayName), merchant.displayName,
            )
        }
        // تصنيفه في بلدك التانية: **مقترح** والمستخدم يأكد (رد المالك §64-٢). قبل القواعد — معرفتك بالتاجر نفسه أدق من قاعدة عامة
        if (merchant?.suggestedCategoryId != null) {
            return CategorizationResult(
                merchant.suggestedCategoryId, CategorizationSource.OTHER_COUNTRY_MERCHANT, ReviewState.SUGGESTED,
                uiText(TextKey.CATEGORIZED_MERCHANT_OTHER_COUNTRY, merchant.displayName), merchant.displayName,
            )
        }
    }
    // القواعد على اسم التاجر الأول وبعدين الوصف — التاجر أدل من نص العملية الطويل
    val haystacks = listOfNotNull(input.merchantName, input.description).filter { it.isNotEmpty() }
    for (rule in deps.rules) {
        for (haystack in haystacks) {
            if (matchesText(rule.matchText, rule.matchMode, haystack)) {
                return CategorizationResult(rule.categoryId, CategorizationSource.RULE, ReviewState.SUGGESTED, uiText(TextKey.CATEGORIZED_RULE, rule.matchText), rule.matchText)
            }
        }
    }
    if (!input.sourceCategory.isNullOrEmpty()) {
        val id = deps.categoryIdByName[normalizeText(input.sourceCategory)]
        if (id != null) {
            return CategorizationResult(
                id, CategorizationSource.SOURCE_CATEGORY, ReviewState.SUGGESTED,
                uiText(TextKey.CATEGORIZED_SOURCE_COLUMN, input.sourceCategory), input.sourceCategory,
            )
        }
    }
    return CategorizationResult(null, CategorizationSource.NONE, ReviewState.NEEDS_REVIEW, uiText(TextKey.CATEGORIZED_NONE))
}

/**
 * «افتكر المحل ده» (OVERRIDES §36): الموجود بالاسم أو اسم بديل بياخد التصنيف المؤكد، والجديد بيتعمل.
 * null لو الاسم فاضي أو أرقام بس.
 */
fun rememberMerchant(all: List<Merchant>, rawName: String, categoryId: Id, newId: Id): Merchant? {
    val displayName = JsText.collapseWhitespace(JsText.trim(rawName))
    val normalizedName = normalizeText(displayName)
    if (normalizedName.isEmpty() || displayName.all { it in '0'..'9' || JsText.isWhitespace(it) || it in "*•.:-" }) return null
    val existing = merchantIndex(all)[normalizedName]
    if (existing != null) return existing.copy(verifiedCategoryId = categoryId)
    return Merchant(newId, displayName.take(120), normalizedName, verifiedCategoryId = categoryId)
}

/**
 * الاسم اللي تصنيف [t] بيتحفظ له لما المالك يختاره بإيده (§75-16: «التصنيف اللي يختاره بإيده **لمحل** بيتحفظ للمحل») — أو null
 * لو اسم العملية **مش محل** (لو اتحفظ، كل عملية بنفس الاسم العام بتاخد نفس التصنيف **مؤكد** وفوق القواعد):
 * - **سطر نوع العملية** اللي قارئ الكشف حطه مكان التاجر لما ما لقاش اسم (`merchantNameFor` في كشف الراجحي).
 * - **اسم عام** ([isGenericOperationName]) حتى لو جه في خانة التاجر: عنوان عملية موحّد («شراء عبر نقاط البيع» · «حوالة واردة» ·
 *   «خصم رسوم») أو اسم نوع اقتصادي («تحويل داخلي» — اسم رجل التحويل بين بلدين §64).
 * - **التحويل** (الاسم اسم شخص أو حساب — زون التحويلات هو اللي بيتعامل معاه، §60) و**التحويل الداخلي المؤكد**.
 */
fun rememberableMerchantName(t: Transaction): String? {
    val name = t.rawMerchantName?.takeIf { it.isNotBlank() } ?: return null
    val op = t.sourceOperationType?.takeIf { it.isNotBlank() }
    if (op != null && normalizeText(name).let { it == normalizeText(op) || it == normalizeText(tidy(op, 60)) }) return null
    if (isGenericOperationName(name)) return null
    if (isTransferLike(t) || (t.economicKindConfirmed && t.economicKind == EconomicKind.INTERNAL_TRANSFER)) return null
    return name
}

/**
 * أسماء الأنواع الاقتصادية **بكل لغات العرض** (فصحى · مصري · إنجليزي): الاسم المتخزن على عملية (زي رجل التحويل بين بلدين) بيتكتب
 * باللغة اللي كانت شغالة وقتها، فبيتقارن بالكل.
 */
private val KIND_LABEL_KEYS: Set<String> by lazy {
    listOf(MSA_TEXTS, EGYPTIAN_TEXTS, ENGLISH_TEXTS)
        .flatMap { table -> ALL_ECONOMIC_KINDS.mapNotNull { table[ruleFor(it).labelKey] } }
        .map { shapeKey(it) }
        .toSet()
}

/** [name] اسم عملية عام مش محل: عنوان من عناوين البنك المركزي الموحّدة (`SmsSamaTitles.kt`) أو اسم نوع اقتصادي. */
fun isGenericOperationName(name: String): Boolean {
    val key = shapeKey(name)
    return isSamaTitle(key) || key in KIND_LABEL_KEYS
}
