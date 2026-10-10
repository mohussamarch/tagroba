package app.masroufy.ui.screens.budgets

import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.IsoDate
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.arabicCompare
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.factsFromProfile
import app.masroufy.core.normalizeText
import app.masroufy.core.parseIsoDate
import app.masroufy.core.presentCategories
import app.masroufy.core.sentenceNumber
import app.masroufy.core.toDayNumber
import app.masroufy.ui.text.t
import app.masroufy.usecase.CategorizationReport
import app.masroufy.usecase.MerchantRow
import app.masroufy.usecase.RuleRow

/**
 * مقدِّم «القواعد والتجار» (`Rules`): `ManageRules.listRules` (بترتيب تطبيقها) و`listMerchants` (التجار اللي ليهم تصنيف ثابت) ⇒ صفوف الشاشة،
 * ومعاينة «طبّق على السابق» من `ReviewHistory.preview` (`CategorizationReport`) ⇒ عدد العمليات لكل تصنيف. **عدّ بس — مفيش فلوس.**
 */
data class RuleRowUi(
    val id: String,
    /** المكان في الترتيب (١ = بيتطبق الأول) — مش رقم الأولوية المتخزن (١٠ · ٢٠ …). */
    val position: Int,
    val modeLabel: String,
    val text: String,
    val categoryName: String,
    val categoryHex: String?,
    val missing: Boolean,
    val enabled: Boolean,
    val meta: String?,
    val rule: ClassificationRule,
)

data class MerchantRowUi(val id: String, val name: String, val categoryName: String?, val categoryHex: String?, val meta: String?, val merchant: Merchant)

data class PickCatUi(val id: String, val name: String, val colorHex: String, val sub: Boolean)

data class PickGroupUi(val key: String?, val name: String, val categories: List<PickCatUi>)

data class RulesUi(val merchants: List<MerchantRowUi>, val rules: List<RuleRowUi>, val picker: List<PickGroupUi>)

/** [keep] = تجار اتنسى تصنيفهم في الجلسة دي — بيفضلوا ظاهرين «بلا تصنيف ثابت» بدل ما يختفوا فجأة. */
suspend fun loadRules(deps: BudgetsDeps, keep: Set<String> = emptySet()): RulesUi {
    val categories = presentCategories(deps.categories.list(), factsFromProfile(deps.profile.load()))
    return mapRules(deps.rules.listRules(), deps.rules.listMerchants(), categories, keep)
}

fun modeLabel(mode: RuleMatchMode): String = t(
    when (mode) {
        RuleMatchMode.CONTAINS -> TextKey.RULES_MODE_CONTAINS
        RuleMatchMode.STARTS_WITH -> TextKey.RULES_MODE_STARTS
        RuleMatchMode.EXACT -> TextKey.RULES_MODE_EXACT
    },
)

fun mapRules(rules: List<RuleRow>, merchants: List<MerchantRow>, categories: List<Category>, keep: Set<String> = emptySet()): RulesUi {
    val byId = categories.associateBy { it.id }
    val ruleRows = rules.mapIndexed { i, row ->
        val r = row.rule
        RuleRowUi(
            id = r.id, position = i + 1, modeLabel = modeLabel(r.matchMode), text = t(TextKey.RULES_QUOTED, r.matchText),
            categoryName = row.categoryName, categoryHex = byId[r.categoryId]?.lightColor, missing = row.categoryMissing, enabled = r.enabled,
            meta = when {
                !r.enabled -> t(TextKey.RULES_OFF_META)
                row.categoryMissing -> t(TextKey.RULES_MISSING_META)
                else -> null
            },
            rule = r,
        )
    }
    val merchantRows = merchants.filter { it.merchant.verifiedCategoryId != null || it.merchant.id in keep }.map { row ->
        val m = row.merchant
        val aliases = m.aliases.orEmpty()
        MerchantRowUi(
            id = m.id, name = m.displayName, categoryName = row.verifiedCategoryName,
            categoryHex = m.verifiedCategoryId?.let { byId[it]?.lightColor },
            meta = if (row.verifiedCategoryName != null && aliases.isNotEmpty()) t(TextKey.RULES_ALIASES, sentenceNumber(aliases.size)) else null,
            merchant = m,
        )
    }
    return RulesUi(merchantRows, ruleRows, pickerGroups(categories))
}

/** اختيار التصنيف في اللوحة: المجموعات ⇒ كل رئيسي ظاهر وبعده فروعه الظاهرة (المخفي ما بيظهرش هنا — §28.1). */
fun pickerGroups(categories: List<Category>): List<PickGroupUi> {
    val ids = categories.map { it.id }.toSet()
    val order = Comparator<Category> { a, b -> if (a.order != b.order) a.order.compareTo(b.order) else arabicCompare(a.name, b.name) }
    val mains = categories.filter { it.active && (it.parentId == null || it.parentId !in ids) }.sortedWith(order)
    val known = CATEGORY_GROUPS.map { it.key }.toSet()
    val groups = CATEGORY_GROUPS.map { it.key to it.name } + (null to t(TextKey.CATS_NO_GROUP))
    return groups.mapNotNull { (key, name) ->
        val inGroup = mains.filter { if (key == null) it.groupKey !in known else it.groupKey == key }
        if (inGroup.isEmpty()) return@mapNotNull null
        PickGroupUi(key, name, inGroup.flatMap { m ->
            listOf(PickCatUi(m.id, m.name, m.lightColor, sub = false)) +
                categories.filter { it.parentId == m.id && it.active }.sortedWith(order).map { PickCatUi(it.id, it.name, it.lightColor, sub = true) }
        })
    }
}

/** المجموعة اللي فيها التصنيف ده (عشان اللوحة تفتح عليها). */
fun groupOf(picker: List<PickGroupUi>, categoryId: String?): String? =
    picker.firstOrNull { g -> g.categories.any { it.id == categoryId } }?.key ?: picker.firstOrNull()?.key

/** نفس النص ونفس طريقة المطابقة لقاعدة تانية ⇒ رسالة حالة الاستخدام (`RULE_DUPLICATE`) قبل الحفظ. */
fun ruleDuplicate(rules: List<RuleRowUi>, editingId: String?, text: String, mode: RuleMatchMode): String? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    val n = normalizeText(clean)
    return if (rules.any { it.id != editingId && it.rule.matchMode == mode && normalizeText(it.rule.matchText) == n }) t(TextKey.RULE_DUPLICATE, clean) else null
}

/**
 * الترتيب بعد «قدّمها/أخّرها»: القاعدة [id] (أو الجديدة لو null) في المكان [position] ⇒ أولويات ١٠ · ٢٠ · ٣٠ … بالترتيب، و[updates] = القواعد
 * التانية اللي رقمها اتغير بس. ⚠️ `ManageRules` مالهاش «حرّك لمكان» (missingLogic) ⇒ الشاشة بتنادي `updateRule` لكل واحدة اتغيرت.
 */
data class PriorityPlan(val updates: List<Pair<String, Int>>, val priority: Int)

fun planPriorities(sorted: List<ClassificationRule>, id: String?, position: Int): PriorityPlan {
    val others = sorted.filter { it.id != id }
    val at = position.coerceIn(1, others.size + 1)
    val ordered: MutableList<ClassificationRule?> = others.toMutableList<ClassificationRule?>().apply { add(at - 1, null) }
    val updates = mutableListOf<Pair<String, Int>>()
    var mine = at * 10
    ordered.forEachIndexed { i, r ->
        val p = (i + 1) * 10
        if (r == null) mine = p else if (r.priority != p) updates += r.id to p
    }
    return PriorityPlan(updates, mine)
}

/** سطر في معاينة «طبّق على السابق». */
data class ApplyRowUi(val label: String, val count: String, val colorHex: String?)

data class ApplyPreviewUi(val rows: List<ApplyRowUi>, val changed: Int)

/** عدد العمليات اللي هتتغير لكل تصنيف + اللي أكدتها بنفسك (ما بتتلمسش) + اللي مالهاش قاعدة (تفضل للمراجعة). */
fun applyPreview(plan: CategorizationReport, categories: List<Category>): ApplyPreviewUi {
    val byId = categories.associateBy { it.id }
    val perCategory = LinkedHashMap<String, Int>()
    for (c in plan.changed) { val id = c.toCategoryId ?: continue; perCategory[id] = (perCategory[id] ?: 0) + 1 }
    val rows = perCategory.map { (id, n) -> ApplyRowUi(byId[id]?.name ?: t(TextKey.CATEGORY_DELETED), sentenceNumber(n), byId[id]?.lightColor) } +
        ApplyRowUi(t(TextKey.RULES_APPLY_CONFIRMED), sentenceNumber(plan.skippedConfirmed.size), CONFIRMED_HEX) +
        ApplyRowUi(t(TextKey.RULES_APPLY_UNMATCHED), sentenceNumber(plan.stillNeedsReview.size), UNMATCHED_HEX)
    return ApplyPreviewUi(rows, plan.changed.size)
}

private const val CONFIRMED_HEX = "#13764D"
private const val UNMATCHED_HEX = "#956000"

/** «عملية واحدة» · «عمليتان» · «٣ عمليات» · «١١ عملية». */
fun opsLabel(n: Int): String = when {
    n == 1 -> t(TextKey.RULES_OPS_ONE)
    n == 2 -> t(TextKey.RULES_OPS_TWO)
    n in 3..10 -> t(TextKey.RULES_OPS_FEW, sentenceNumber(n))
    else -> t(TextKey.RULES_OPS_MANY, sentenceNumber(n))
}

/** مدى «السابق»: آخر ١٨٣٠ يوم (أقصى مدى بتقبله `ReviewHistory` — خمس سنين) لحد النهارده. */
const val HISTORY_DAYS = 1830

fun historyRange(today: IsoDate): Pair<IsoDate, IsoDate> = dayNumberToIso(toDayNumber(parseIsoDate(today)) - HISTORY_DAYS) to today
