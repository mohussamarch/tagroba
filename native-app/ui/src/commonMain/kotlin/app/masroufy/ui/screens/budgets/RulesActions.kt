package app.masroufy.ui.screens.budgets

import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.jsTrim
import app.masroufy.ui.text.t
import app.masroufy.usecase.MAX_MATCH_TEXT
import app.masroufy.usecase.RulePatch

/** اللوحة المفتوحة في «القواعد والتجار»: قاعدة (جديدة لو [Rule.row] = null) أو تاجر. */
sealed interface RulesSheet {
    data class Rule(val row: RuleRowUi?) : RulesSheet

    data class Merchant(val row: MerchantRowUi) : RulesSheet
}

/** خانات لوحة القاعدة. [position] = المكان في الترتيب (١ = الأول). */
data class RuleDraft(val text: String, val mode: RuleMatchMode, val categoryId: String?, val group: String?, val position: Int)

/** خانات لوحة التاجر. */
data class MerchantDraft(val name: String, val categoryId: String?, val group: String?, val alias: String)

fun RulesSheet.Rule.startDraft(ui: RulesUi): RuleDraft {
    val r = row
    val firstCategory = ui.picker.firstOrNull()?.categories?.firstOrNull()?.id
    return if (r == null) RuleDraft("", RuleMatchMode.CONTAINS, firstCategory, groupOf(ui.picker, firstCategory), ui.rules.size + 1)
    else {
        val category = r.rule.categoryId.takeIf { !r.missing } ?: firstCategory
        RuleDraft(r.rule.matchText, r.rule.matchMode, category, groupOf(ui.picker, category), r.position)
    }
}

fun RulesSheet.Merchant.startDraft(ui: RulesUi): MerchantDraft {
    val category = row.merchant.verifiedCategoryId ?: ui.picker.firstOrNull()?.categories?.firstOrNull()?.id
    return MerchantDraft(row.name, category, groupOf(ui.picker, category), "")
}

/** أقصى مكان في الترتيب: عدد القواعد (+١ للجديدة). */
fun maxPosition(ui: RulesUi, editing: RuleRowUi?): Int = ui.rules.size + if (editing == null) 1 else 0

/** رسالة تمنع الحفظ قبل حالة الاستخدام: نص فاضي · أطول من الحد · مكرر بنفس طريقة المطابقة · من غير تصنيف. */
fun ruleProblem(ui: RulesUi, editing: RuleRowUi?, d: RuleDraft): String? {
    val text = jsTrim(d.text)
    return when {
        text.isEmpty() -> t(TextKey.RULE_TEXT_REQUIRED)
        text.length > MAX_MATCH_TEXT -> t(TextKey.TEXT_TOO_LONG, "$MAX_MATCH_TEXT")
        d.categoryId == null -> t(TextKey.CATEGORY_THIS_NOT_FOUND)
        else -> ruleDuplicate(ui.rules, editing?.id, text, d.mode)
    }
}

/**
 * حفظ القاعدة عن طريق `ManageRules`: الجديدة في آخر الترتيب ⇒ `addRule` لوحدها (الأولوية بتحسبها حالة الاستخدام). لو المكان اتغير ⇒ القواعد
 * التانية بتاخد ١٠ · ٢٠ · ٣٠ … بالترتيب (`planPriorities` — `updateRule` لكل واحدة اتغيرت) والقاعدة دي في مكانها.
 */
suspend fun saveRule(deps: BudgetsDeps, ui: RulesUi, editing: RuleRowUi?, d: RuleDraft) {
    val categoryId = d.categoryId ?: return
    val sorted = ui.rules.map { it.rule }
    val text = jsTrim(d.text)
    if (editing == null) {
        if (d.position >= sorted.size + 1) {
            deps.rules.addRule(text, d.mode, categoryId)
            return
        }
        val plan = planPriorities(sorted, null, d.position)
        for ((id, priority) in plan.updates) deps.rules.updateRule(id, RulePatch(priority = priority))
        deps.rules.addRule(text, d.mode, categoryId, plan.priority)
        return
    }
    val plan = if (d.position != editing.position) planPriorities(sorted, editing.id, d.position) else null
    plan?.updates?.forEach { (id, priority) -> deps.rules.updateRule(id, RulePatch(priority = priority)) }
    deps.rules.updateRule(editing.id, RulePatch(priority = plan?.priority, matchText = text, matchMode = d.mode, categoryId = categoryId))
}

/** حفظ التاجر: الاسم المعروض لو اتغير (`renameMerchant` — المفتاح المطبّع ثابت) والتصنيف الثابت لو اتغير (`setMerchantCategory`). */
suspend fun saveMerchant(deps: BudgetsDeps, row: MerchantRowUi, d: MerchantDraft) {
    val name = jsTrim(d.name)
    if (name != row.name) deps.rules.renameMerchant(row.id, name)
    if (d.categoryId != null && d.categoryId != row.merchant.verifiedCategoryId) deps.rules.setMerchantCategory(row.id, d.categoryId)
}
