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

    /** **كل التجار** (رد المالك OVERRIDES §82-18): اللي ليه تصنيف ثابت واللي من غيره — واللي اتنسى تصنيفه بيفضل في القايمة. */
    @Test fun everyMerchantShowsWithOrWithoutAFixedCategory() = runBlocking<Unit> {
        val w = world()
        w.repos.merchants.saveMany(
            listOf(
                Merchant("m-1", "مخبز وهمي", "مخبز وهمي", aliases = listOf("مخبز وهمي 2"), verifiedCategoryId = FOOD.id),
                Merchant("m-2", "محل بلا تصنيف", "محل بلا تصنيف"),
            ),
        )
        val ui = loadRules(w.deps)
        assertEquals(listOf("m-1", "m-2"), ui.merchants.map { it.id })
        val m = ui.merchants.first()
        assertEquals(FOOD.name, m.categoryName)
        assertEquals(uiText(UiKey.RULES_ALIASES, "1"), m.meta)
        assertNull(ui.merchants[1].categoryName, "«بلا تصنيف ثابت — تُطبَّق القواعد»")
        w.deps.rules.setMerchantCategory("m-1", null)
        val after = loadRules(w.deps).merchants
        assertEquals(2, after.size, "اللي اتنسى تصنيفه ما بيختفيش")
        assertTrue(after.all { it.categoryName == null })
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
        // **كل السجل** (رد المالك §82-16) — مش آخر 5 سنين
        val preview = w.deps.history.previewAll(TODAY)
        val ui = applyPreview(preview.categoryPlan, w.deps.categories.list())
        assertEquals(1, ui.changed)
        assertEquals(listOf(FOOD.name, uiText(UiKey.RULES_APPLY_CONFIRMED), uiText(UiKey.RULES_APPLY_UNMATCHED)), ui.rows.map { it.label })
        assertEquals(listOf("1", "1", "1"), ui.rows.map { it.count })
        val applied = w.deps.history.applyCategories(preview.rows.map { it.id }, preview.categoryPlan)
        assertEquals(1, applied.changed.size)
        assertEquals(uiText(UiKey.RULES_OPS_ONE), opsLabel(1))
        assertEquals(uiText(UiKey.RULES_OPS_FEW, "3"), opsLabel(3))
        assertEquals(uiText(UiKey.RULES_OPS_MANY, "12"), opsLabel(12))
    }

    /** «طبّق القواعد على السابق» = **كل حاجة** (رد المالك OVERRIDES §82-16) — عملية من 11 سنة بتدخل، مش آخر 5 سنين بس. */
    @Test fun applyToPreviousCoversTheWholeHistory() = runBlocking<Unit> {
        val old = spend("2015-03-01", 3_000, null, merchant = "مطعم قديم وهمي").copy(categoryConfirmed = false)
        val w = BudgetsWorld(transactions = listOf(old, spend("2026-09-30", 4_000, null, merchant = "مطعم البيت الوهمي").copy(categoryConfirmed = false)))
        w.deps.rules.addRule("مطعم", RuleMatchMode.CONTAINS, FOOD.id)
        val preview = w.deps.history.previewAll(TODAY)
        assertEquals(2, applyPreview(preview.categoryPlan, w.deps.categories.list()).changed)
        assertEquals(listOf("2015-03-01", "2026-09-30"), preview.rows.map { it.occurredAt }, "الأقدم الأول ومن غير تكرار")
        assertEquals(2, w.deps.history.applyCategories(preview.rows.map { it.id }, preview.categoryPlan).changed.size)
    }

    private fun rule(id: String, priority: Int) = ClassificationRule(id, priority, id, RuleMatchMode.CONTAINS, FOOD.id, enabled = true)
}
