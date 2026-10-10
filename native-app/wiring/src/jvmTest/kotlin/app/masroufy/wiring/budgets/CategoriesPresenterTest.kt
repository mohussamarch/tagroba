package app.masroufy.wiring.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.ArabicVariant
import app.masroufy.core.Category
import app.masroufy.core.CategoryColorPair
import app.masroufy.core.TextKey
import app.masroufy.core.childColors
import app.masroufy.core.swatchColors
import app.masroufy.core.uiText
import app.masroufy.ui.screens.budgets.CatEditMode
import app.masroufy.ui.screens.budgets.CatEditTarget
import app.masroufy.ui.screens.budgets.catEditView
import app.masroufy.ui.screens.budgets.catSaveInput
import app.masroufy.ui.screens.budgets.catVisibilityInput
import app.masroufy.ui.screens.budgets.loadCategories
import app.masroufy.ui.screens.budgets.recolorsSubs
import app.masroufy.ui.screens.budgets.startDraft
import app.masroufy.ui.screens.budgets.subsLabel
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «التصنيفات» + لوحة «تعديل تصنيف»: من `ManageCategories.list` وملفك لحالة الشاشة، والحفظ بيروح لـ`ManageCategories.save`. */
class CategoriesPresenterTest {
    private val gifts = Category("c-gift", null, "هدايا", "gift", "#A55060", "#E09AA8", active = false, order = 4, groupKey = "personal")
    private val legacy = Category("c-old", null, "قديم", "tag", "#4D747C", "#9CC0C8", active = true, order = 5)
    private val all = listOf(FOOD, COFFEE, GROCERY, CAR, gifts, legacy)

    @BeforeTest fun texts() = resetTexts()

    @Test fun groupsMainsSubsAndTheHiddenWithTheirReason() = runBlocking<Unit> {
        val ui = loadCategories(BudgetsWorld(categories = all).deps)
        assertEquals(listOf("food", null), ui.groups.map { it.key }, "المجموعات اللي فيها ظاهر بس، والقديم من غير مجموعة في الآخر")
        val food = ui.groups.first()
        assertEquals(listOf(FOOD.id, GROCERY.id), food.mains.map { it.id })
        assertEquals(listOf(COFFEE.id), food.mains.first().subs.map { it.id })
        assertEquals(uiText(UiKey.CATS_SUBS_ONE), food.mains.first().subsLabel)
        assertEquals(uiText(UiKey.CATS_NO_SUBS), food.mains[1].subsLabel)
        assertEquals(uiText(UiKey.CATS_NO_GROUP), ui.groups.last().name)
        // السيارة مخفية بشرط «عندك سيارة؟» (ملفك ما جاوبش) — ما بتتظهرش بزرار · الهدايا إنت اللي خفيتها ⇒ «إظهار»
        val car = ui.hidden.single { it.id == CAR.id }
        assertEquals(uiText(UiKey.CATS_REQ_HAS_CAR), car.why)
        assertFalse(car.canShow)
        val gift = ui.hidden.single { it.id == gifts.id }
        assertEquals(uiText(UiKey.CATS_HIDDEN_BY_YOU), gift.why)
        assertTrue(gift.canShow)
    }

    @Test fun pluralsOfSubsFollowTheCount() {
        assertEquals(uiText(UiKey.CATS_SUBS_TWO), subsLabel(2))
        assertEquals(uiText(UiKey.CATS_SUBS_FEW, "3"), subsLabel(3))
        assertEquals(uiText(UiKey.CATS_SUBS_MANY, "11"), subsLabel(11))
        val saudi = subsLabel(2)
        resetTexts(ArabicVariant.EGYPTIAN)
        assertNotEquals(saudi, subsLabel(2), "«فرعان» ⇄ «فرعيين»")
    }

    @Test fun editingAMainWithSubsLocksTheParentAndOffersItsCurrentColor() = runBlocking<Unit> {
        val ui = loadCategories(BudgetsWorld(categories = all).deps)
        val target = CatEditTarget(CatEditMode.EDIT, FOOD.id)
        val draft = target.startDraft(ui.stored)
        assertEquals(FOOD.name, draft.name)
        assertNull(draft.swatchKey, "لونه مش من الـ18 درجة ⇒ «لونه الحالي»")
        val view = catEditView(target, draft, ui.stored, ui.visibleMains)
        assertTrue(view.parentLocked)
        assertTrue(view.isMain)
        assertEquals(19, view.swatches.size, "18 درجة + لونه الحالي")
        assertEquals(uiText(UiKey.CAT_EDIT_COLOR, uiText(UiKey.CAT_EDIT_CURRENT_COLOR)), view.colorLabel)
        assertEquals(FOOD.lightColor, view.previewHex)
        assertTrue(view.canSave)
        assertTrue(view.canHide)
        // اسم مكرر ⇒ رسالة حالة الاستخدام والحفظ مقفول
        val dup = catEditView(target, draft.copy(name = " بقالة وسوبرماركت "), ui.stored, ui.visibleMains)
        assertEquals(uiText(TextKey.CATEGORY_NAME_DUPLICATE), dup.nameError)
        assertFalse(dup.canSave)
        // لون جديد لرئيسي تحته فروع ⇒ «وتغيّرت درجات فروعه معه»
        assertTrue(recolorsSubs(target, draft.copy(swatchKey = "blue"), ui.stored))
        assertFalse(recolorsSubs(target, draft, ui.stored))
    }

    @Test fun aNewSubTakesAShadeOfItsParentAndSavesThroughTheUseCase() = runBlocking<Unit> {
        val w = BudgetsWorld(categories = all)
        val ui = loadCategories(w.deps)
        val target = CatEditTarget(CatEditMode.NEW_SUB, parentId = FOOD.id)
        val draft = target.startDraft(ui.stored).copy(name = "حلويات", iconKey = "croissant")
        val view = catEditView(target, draft, ui.stored, ui.visibleMains)
        assertFalse(view.isMain)
        assertEquals(childColors(CategoryColorPair(FOOD.lightColor, FOOD.darkColor), 1).lightColor, view.previewHex, "تاني فرعي ⇒ الدرجة رقم 2")
        assertEquals(uiText(UiKey.CAT_EDIT_PATH, uiText(TextKey.GROUP_FOOD), FOOD.name), view.path)
        val saved = w.deps.categories.save(catSaveInput(target, draft, ui.stored))
        assertEquals(FOOD.id, saved.parentId)
        assertEquals(view.previewHex, saved.lightColor, "المعاينة = اللي اتخزن")
        val after = loadCategories(w.deps)
        assertEquals(listOf(COFFEE.id, saved.id), after.groups.first().mains.first().subs.map { it.id })
    }

    @Test fun aNewMainNeedsANameAndAnIconAndTakesTheChosenSwatch() = runBlocking<Unit> {
        val w = BudgetsWorld(categories = all)
        val ui = loadCategories(w.deps)
        val target = CatEditTarget(CatEditMode.NEW_MAIN)
        val empty = target.startDraft(ui.stored)
        val blank = catEditView(target, empty, ui.stored, ui.visibleMains)
        assertFalse(blank.canSave)
        assertTrue(blank.needIcon)
        val draft = empty.copy(name = "رياضة", iconKey = "dumbbell", groupKey = "personal", swatchKey = "indigo")
        val saved = w.deps.categories.save(catSaveInput(target, draft, ui.stored))
        assertEquals(swatchColors("indigo")!!.lightColor, saved.lightColor)
        assertEquals("personal", saved.groupKey)
        assertTrue(loadCategories(w.deps).groups.any { g -> g.key == "personal" && g.mains.any { it.id == saved.id } })
    }

    @Test fun hidingAndShowingKeepTheStoredName() = runBlocking<Unit> {
        val w = BudgetsWorld(categories = all)
        w.deps.categories.save(catVisibilityInput(GROCERY, active = false))
        val hidden = loadCategories(w.deps)
        assertTrue(hidden.hidden.any { it.id == GROCERY.id && it.canShow })
        assertTrue(hidden.groups.first().mains.none { it.id == GROCERY.id })
        w.deps.categories.save(catVisibilityInput(hidden.stored.single { it.id == GROCERY.id }, active = true))
        val back = loadCategories(w.deps)
        assertTrue(back.groups.first().mains.any { it.id == GROCERY.id && it.name == GROCERY.name })
    }
}
