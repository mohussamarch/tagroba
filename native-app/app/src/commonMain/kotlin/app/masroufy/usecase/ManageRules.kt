package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Id
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.arabicCompare
import app.masroufy.core.jsTrim
import app.masroufy.core.normalizeText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.IdGenerator
import app.masroufy.port.MerchantRepository
import app.masroufy.port.RuleRepository

/**
 * ManageRules — نقل `manageRules.ts`: تحرير قواعد التصنيف والتجار (spec/05: المراجع قابلة للتحرير).
 * القاعدة بتتقفل ما بتتحذفش، وتعديل قاعدة **ما بيعيدش تصنيف القديم** — القواعد الجديدة على اللي جاي.
 */

const val MAX_MATCH_TEXT = 60

data class RuleRow(
    val rule: ClassificationRule,
    val categoryName: String,
    /** التصنيف اتشال؟ القاعدة بتفضل ظاهرة بسببها مش بتختفي بصمت. */
    val categoryMissing: Boolean,
)

data class MerchantRow(
    val merchant: Merchant,
    /** التصنيف الموثّق — أقوى من كل القواعد. */
    val verifiedCategoryName: String?,
)

/** تعديل جزئي لقاعدة — `null` = ما يتغيرش. */
data class RulePatch(
    val priority: Int? = null,
    val matchText: String? = null,
    val matchMode: RuleMatchMode? = null,
    val categoryId: Id? = null,
    val enabled: Boolean? = null,
)

data class ManageRulesDeps(
    val rules: RuleRepository,
    val merchants: MerchantRepository,
    val categories: CategoryRepository,
    val ids: IdGenerator,
)

class ManageRules(private val deps: ManageRulesDeps) {
    private suspend fun categoryIndex(): Map<Id, Category> = deps.categories.listAll().associateBy { it.id }

    /** القواعد بترتيب تطبيقها — الأصغر أولوية الأول، والمتساويين بترتيب الحروف العربي. */
    suspend fun listRules(): List<RuleRow> {
        val categories = categoryIndex()
        return deps.rules.listAll()
            .sortedWith { a, b -> if (a.priority != b.priority) a.priority - b.priority else arabicCompare(a.matchText, b.matchText) }
            .map { rule ->
                val category = categories[rule.categoryId]
                RuleRow(rule, category?.name ?: "تصنيف محذوف", categoryMissing = category == null)
            }
    }

    suspend fun listMerchants(): List<MerchantRow> {
        val categories = categoryIndex()
        return deps.merchants.listAll()
            .sortedWith { a, b -> arabicCompare(a.displayName, b.displayName) }
            .map { merchant ->
                MerchantRow(
                    merchant,
                    merchant.verifiedCategoryId?.let { categories[it]?.name ?: "تصنيف محذوف" },
                )
            }
    }

    private suspend fun requireCategory(categoryId: Id) {
        if (deps.categories.listAll().none { it.id == categoryId }) throw IllegalStateException("التصنيف ده مش موجود")
    }

    suspend fun addRule(matchText: String, matchMode: RuleMatchMode, categoryId: Id, priority: Int? = null): ClassificationRule {
        val text = jsTrim(matchText)
        if (text.isEmpty()) throw IllegalStateException("اكتب النص اللي القاعدة تدوّر عليه")
        if (text.length > MAX_MATCH_TEXT) throw IllegalStateException("النص أطول من $MAX_MATCH_TEXT حرف")
        requireCategory(categoryId)

        val existing = deps.rules.listAll()
        val normalized = normalizeText(text)
        if (existing.any { normalizeText(it.matchText) == normalized && it.matchMode == matchMode }) {
            throw IllegalStateException("فيه قاعدة بنفس النص «$text» ونفس طريقة المطابقة")
        }

        // الجديدة بتيجي في الآخر فما تسبقش قاعدة موجودة من غير ما المستخدم يطلب
        val resolvedPriority = priority ?: if (existing.isEmpty()) 10 else existing.maxOf { it.priority } + 10

        val rule = ClassificationRule(
            id = deps.ids.next("rule"),
            priority = resolvedPriority,
            matchText = text,
            matchMode = matchMode,
            categoryId = categoryId,
            enabled = true,
        )
        deps.rules.saveMany(listOf(rule))
        return rule
    }

    suspend fun updateRule(id: Id, patch: RulePatch) {
        val rule = deps.rules.listAll().find { it.id == id } ?: throw IllegalStateException("القاعدة دي مش موجودة")

        if (patch.categoryId != null) requireCategory(patch.categoryId)
        var matchText = patch.matchText
        if (matchText != null) {
            matchText = jsTrim(matchText)
            if (matchText.isEmpty()) throw IllegalStateException("نص القاعدة مايبقاش فاضي")
            if (matchText.length > MAX_MATCH_TEXT) throw IllegalStateException("النص أطول من $MAX_MATCH_TEXT حرف")
        }

        deps.rules.saveMany(
            listOf(
                rule.copy(
                    priority = patch.priority ?: rule.priority,
                    matchText = matchText ?: rule.matchText,
                    matchMode = patch.matchMode ?: rule.matchMode,
                    categoryId = patch.categoryId ?: rule.categoryId,
                    enabled = patch.enabled ?: rule.enabled,
                ),
            ),
        )
    }

    /** قفل أو فتح — **مفيش حذف**: المقفولة بتفضل ظاهرة فالمستخدم فاكرها. */
    suspend fun setRuleEnabled(id: Id, enabled: Boolean) = updateRule(id, RulePatch(enabled = enabled))

    /** تثبيت تصنيف تاجر — **أقوى من كل القواعد** (spec/05). `null` بيشيل التثبيت فيرجع للقواعد. */
    suspend fun setMerchantCategory(merchantId: Id, categoryId: Id?) {
        val merchant = deps.merchants.listAll().find { it.id == merchantId } ?: throw IllegalStateException("التاجر ده مش موجود")
        if (categoryId != null) requireCategory(categoryId)
        deps.merchants.saveMany(listOf(merchant.copy(verifiedCategoryId = categoryId)))
    }

    /** الاسم المطبّع **ما بيتغيّرش** مع إعادة التسمية — هو مفتاح المطابقة مع الكشوف الجاية. */
    suspend fun renameMerchant(merchantId: Id, displayName: String) {
        val name = jsTrim(displayName)
        if (name.isEmpty()) throw IllegalStateException("اكتب اسم التاجر")
        val merchant = deps.merchants.listAll().find { it.id == merchantId } ?: throw IllegalStateException("التاجر ده مش موجود")
        deps.merchants.saveMany(listOf(merchant.copy(displayName = name)))
    }

    suspend fun addAlias(merchantId: Id, raw: String) {
        val alias = normalizeText(raw)
        if (alias.isEmpty() || alias.length > 120) throw IllegalStateException("اكتب اسمًا بحد أقصى ١٢٠ حرف.")
        val all = deps.merchants.listAll()
        val merchant = all.find { it.id == merchantId } ?: throw IllegalStateException("اختار تاجرًا موجودًا.")
        if (all.any { it.id != merchantId && (it.normalizedName == alias || it.aliases.orEmpty().contains(alias)) }) {
            throw IllegalStateException("الاسم مربوط بتاجر تاني بالفعل؛ مش هنغيّر الربط القديم ضمنيًا.")
        }
        deps.merchants.saveMany(listOf(merchant.copy(aliases = LinkedHashSet(merchant.aliases.orEmpty() + alias).toList())))
    }
}
