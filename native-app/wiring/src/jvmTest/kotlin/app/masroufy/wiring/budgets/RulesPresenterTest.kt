package app.masroufy.wiring.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.ClassificationRule
import app.masroufy.core.Merchant
import app.masroufy.core.RuleMatchMode
import app.masroufy.core.TextKey
import app.masroufy.core.uiText
import app.masroufy.ui.screens.budgets.RuleDraft
import app.masroufy.ui.screens.budgets.RulesSheet
import app.masroufy.ui.screens.budgets.applyPreview
import app.masroufy.ui.screens.budgets.historyRange
import app.masroufy.ui.screens.budgets.loadRules
import app.masroufy.ui.screens.budgets.opsLabel
import app.masroufy.ui.screens.budgets.planPriorities
import app.masroufy.ui.screens.budgets.ruleProblem
import app.masroufy.ui.screens.budgets.saveRule
import app.masroufy.ui.screens.budgets.startDraft
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «القواعد والتجار»: `ManageRules` (القواعد بترتيبها · التجار المؤكدين) و`ReviewHistory.preview` (طبّق على السابق) ⇒ حالة الشاشة. */
class RulesPresenterTest {
    @BeforeTest fun texts() = resetTexts()

    private fun world() = BudgetsWorld(
        transactions = listOf(
            spend("2026-09-30", 4_000, null, merchant = "مطعم البيت الوهمي").copy(categoryConfirmed = false),
            spend("2026-10-02", 2_000, GROCERY.id, merchant = "مطعم مؤكد").copy(categoryConfirmed = true),
            spend("2026-10-03", 1_000, null, merchant = "محل مجهول").copy(categoryConfirmed = false),
        ),
    )

    @Test fun rulesInTheirOrderWithOffAndMissingCategoryNotes() = runBlocking<Unit> {
        val w = world()
        w.deps.rules.addRule("مطعم", RuleMatchMode.CONTAINS, FOOD.id)
        val coffee = w.deps.rules.addRule("كافيه", RuleMatchMode.STARTS_WITH, COFFEE.id)
        w.deps.rules.setRuleEnabled(coffee.id, false)
        w.repos.rules.saveMany(listOf(ClassificationRule("r-gone", 5, "مكتبة", RuleMatchMode.EXACT, "c-gone", enabled = true)))
        val ui = loadRules(w.deps)
        assertEquals(listOf("r-gone", "rule-000001", coffee.id), ui.rules.map { it.id }, "الأولوية الأصغر الأول")
        assertEquals(listOf(1, 2, 3), ui.rules.map { it.position })
        val gone = ui.rules.first()
        assertTrue(gone.missing)
        assertEquals(uiText(UiKey.RULES_MISSING_META), gone.meta)
        assertEquals(uiText(UiKey.RULES_MODE_EXACT), gone.modeLabel)
        val off = ui.rules.last()
        assertEquals(uiText(UiKey.RULES_OFF_META), off.meta, "المقفولة بتفضل ظاهرة")
        assertEquals(uiText(UiKey.RULES_QUOTED, "كافيه"), off.text)
        assertNull(ui.rules[1].meta)
        assertEquals(FOOD.lightColor, ui.rules[1].categoryHex)
    }

    @Test fun onlyRememberedMerchantsShowAndForgottenOnesStayForTheSession() = runBlocking<Unit> {
        val w = world()
        w.repos.merchants.saveMany(
            listOf(
                Merchant("m-1", "مخبز وهمي", "مخبز وهمي", aliases = listOf("مخبز وهمي ٢"), verifiedCategoryId = FOOD.id),
                Merchant("m-2", "محل بلا تصنيف", "محل بلا تصنيف"),
            ),
        )
        val ui = loadRules(w.deps)
        val m = ui.merchants.single()
        assertEquals("m-1", m.id)
        assertEquals(FOOD.name, m.categoryName)
        assertEquals(uiText(UiKey.RULES_ALIASES, "١"), m.meta)
        w.deps.rules.setMerchantCategory("m-1", null)
        val kept = loadRules(w.deps, keep = setOf("m-1")).merchants.single()
        assertNull(kept.categoryName, "«بلا تصنيف ثابت — تُطبَّق القواعد»")
        assertTrue(loadRules(w.deps).merchants.isEmpty())
    }

    @Test fun pickerListsVisibleCategoriesByGroupWithSubsAfterTheirParent() = runBlocking<Unit> {
        val ui = loadRules(BudgetsWorld(categories = listOf(FOOD, COFFEE, GROCERY, CAR)).deps)
        assertEquals(listOf("food"), ui.picker.map { it.key }, "السيارة مخفية بشرط ⇒ مش في الاختيار")
        assertEquals(listOf(FOOD.id, COFFEE.id, GROCERY.id), ui.picker.single().categories.map { it.id })
    }

    @Test fun movingARuleRenumbersOnlyWhatChanged() {
        val rules = listOf(rule("a", 10), rule("b", 20), rule("c", 30))
        val toFirst = planPriorities(rules, "c", 1)
        assertEquals(10, toFirst.priority)
        assertEquals(listOf("a" to 20, "b" to 30), toFirst.updates)
        val newSecond = planPriorities(rules, null, 2)
        assertEquals(20, newSecond.priority)
        assertEquals(listOf("b" to 30, "c" to 40), newSecond.updates)
        assertTrue(planPriorities(rules, "b", 2).updates.isEmpty(), "نفس المكان ⇒ ولا كتابة زيادة")
    }

    @Test fun savingANewRuleInTheMiddleAndCatchingDuplicatesFirst() = runBlocking<Unit> {
        val w = world()
        w.deps.rules.addRule("مطعم", RuleMatchMode.CONTAINS, FOOD.id)
        w.deps.rules.addRule("بقالة", RuleMatchMode.CONTAINS, GROCERY.id)
        val ui = loadRules(w.deps)
        val draft = RulesSheet.Rule(null).startDraft(ui)
        assertEquals(3, draft.position, "الجديدة في الآخر")
        val dup = draft.copy(text = " مطعم ", categoryId = FOOD.id)
        assertEquals(uiText(TextKey.RULE_DUPLICATE, "مطعم"), ruleProblem(ui, null, dup))
        assertNull(ruleProblem(ui, null, dup.copy(mode = RuleMatchMode.EXACT)), "نفس النص بطريقة تانية مسموح")
        saveRule(w.deps, ui, null, RuleDraft("كافيه", RuleMatchMode.CONTAINS, COFFEE.id, "food", position = 1))
        assertEquals(listOf("كافيه", "مطعم", "بقالة"), loadRules(w.deps).rules.map { it.rule.matchText })
    }

    @Test fun applyToPreviousShowsCountsAndLeavesConfirmedAlone() = runBlocking<Unit> {
        val w = world()
        w.deps.rules.addRule("مطعم", RuleMatchMode.CONTAINS, FOOD.id)
        val (from, to) = historyRange(TODAY)
        assertEquals(TODAY, to)
        assertEquals("2021-10-05", from, "آخر ١٨٣٠ يوم")
        val preview = w.deps.history.preview(from, to)
        val ui = applyPreview(preview.categoryPlan, w.deps.categories.list())
        assertEquals(1, ui.changed)
        assertEquals(listOf(FOOD.name, uiText(UiKey.RULES_APPLY_CONFIRMED), uiText(UiKey.RULES_APPLY_UNMATCHED)), ui.rows.map { it.label })
        assertEquals(listOf("١", "١", "١"), ui.rows.map { it.count })
        val applied = w.deps.history.applyCategories(preview.rows.map { it.id }, preview.categoryPlan)
        assertEquals(1, applied.changed.size)
        assertEquals(uiText(UiKey.RULES_OPS_ONE), opsLabel(1))
        assertEquals(uiText(UiKey.RULES_OPS_FEW, "٣"), opsLabel(3))
        assertEquals(uiText(UiKey.RULES_OPS_MANY, "١٢"), opsLabel(12))
    }

    private fun rule(id: String, priority: Int) = ClassificationRule(id, priority, id, RuleMatchMode.CONTAINS, FOOD.id, enabled = true)
}
