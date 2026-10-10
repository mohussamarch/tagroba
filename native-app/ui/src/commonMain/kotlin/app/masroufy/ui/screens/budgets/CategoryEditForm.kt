package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.CATEGORY_SWATCHES
import app.masroufy.core.Category
import app.masroufy.core.CategoryColorPair
import app.masroufy.core.CategorySaveInput
import app.masroufy.core.Field
import app.masroufy.core.TextKey
import app.masroufy.core.childColors
import app.masroufy.core.firstFreeSwatch
import app.masroufy.core.jsTrim
import app.masroufy.core.normalizeText
import app.masroufy.core.swatchColors
import app.masroufy.core.swatchKeyOf
import app.masroufy.ui.text.t

/**
 * لوحة «تعديل تصنيف» (`CategoryEditSheet` — جوه «التصنيفات»): الخانات والتحقق قبل `ManageCategories.save` (اللي بيتحقق تاني بنفسه —
 * `planCategorySave`). اللون **مش فلوس**: درجات `CATEGORY_SWATCHES` ودرجة الفرعي من `childColors` في `core` — نفس اللي هيتخزن.
 */
enum class CatEditMode { EDIT, NEW_MAIN, NEW_SUB }

/** اللي بيتعدّل: تصنيف موجود ([id]) أو جديد (رئيسي أو فرعي تحت [parentId]). */
data class CatEditTarget(val mode: CatEditMode, val id: String? = null, val parentId: String? = null)

/** [swatchKey] = null ⇒ «لونه الحالي» (لون قديم مش من الدرجات الـ18) — الحفظ ما بيغيّروش. */
data class CatDraft(val name: String, val iconKey: String?, val parentId: String?, val groupKey: String?, val swatchKey: String?)

data class SwatchUi(val key: String?, val name: String, val colorHex: String)

data class ParentUi(val id: String?, val name: String, val colorHex: String?)

/** كل اللي اللوحة بتعرضه من الخانات (الاسم المكرر · المسار · لون المعاينة · الاختيارات المتاحة). */
data class CatEditView(
    val title: String,
    val path: String,
    val previewHex: String,
    val isMain: Boolean,
    /** رئيسي تحته فروع ⇒ ما يتنقلش تحت تصنيف تاني. */
    val parentLocked: Boolean,
    val parents: List<ParentUi>,
    val swatches: List<SwatchUi>,
    val colorLabel: String,
    val colorNote: String,
    val subNote: String?,
    val nameError: String?,
    val needIcon: Boolean,
    val canSave: Boolean,
    val saveLabel: String,
    /** «أخفِه من الاختيار» — للتصنيف الموجود الظاهر بس. */
    val canHide: Boolean,
    val hideNote: String,
)

private const val DEFAULT_GROUP = "personal"

private fun colorsOf(c: Category) = CategoryColorPair(c.lightColor, c.darkColor)

fun CatEditTarget.startDraft(stored: List<Category>): CatDraft {
    val byId = stored.associateBy { it.id }
    val cur = id?.let { byId[it] }
    return when {
        cur != null -> CatDraft(
            cur.name, cur.iconKey, cur.parentId?.takeIf { it in byId },
            cur.groupKey ?: cur.parentId?.let { byId[it]?.groupKey } ?: DEFAULT_GROUP, swatchKeyOf(cur.lightColor),
        )
        mode == CatEditMode.NEW_SUB -> CatDraft("", null, parentId, parentId?.let { byId[it]?.groupKey } ?: DEFAULT_GROUP, firstFreeSwatch(stored))
        else -> CatDraft("", null, null, DEFAULT_GROUP, firstFreeSwatch(stored))
    }
}

private fun groupName(key: String?): String = CATEGORY_GROUPS.firstOrNull { it.key == key }?.name ?: t(UiKey.CATS_NO_GROUP)

fun catEditView(target: CatEditTarget, draft: CatDraft, stored: List<Category>, visibleMains: List<Category>): CatEditView {
    val byId = stored.associateBy { it.id }
    val cur = target.id?.let { byId[it] }
    val parent = draft.parentId?.let { byId[it] }
    val isMain = parent == null
    val kidsOf = { pid: String -> stored.count { it.parentId == pid && it.id != cur?.id } }
    val hasSubs = cur != null && cur.parentId == null && stored.any { it.parentId == cur.id }
    val currentHex = cur?.takeIf { it.parentId == null && swatchKeyOf(it.lightColor) == null }?.lightColor
    val preview = when {
        parent != null && cur != null && cur.parentId == parent.id -> cur.lightColor
        parent != null -> childColors(colorsOf(parent), kidsOf(parent.id)).lightColor
        draft.swatchKey != null -> swatchColors(draft.swatchKey)?.lightColor ?: currentHex ?: cur?.lightColor.orEmpty()
        else -> currentHex ?: cur?.lightColor.orEmpty()
    }
    val name = jsTrim(draft.name)
    val duplicate = name.isNotEmpty() && stored.any { it.id != cur?.id && normalizeText(it.name) == normalizeText(name) }
    val swatches = CATEGORY_SWATCHES.map { SwatchUi(it.key, it.name, swatchColors(it.key)!!.lightColor) } +
        listOfNotNull(currentHex?.let { SwatchUi(null, t(UiKey.CAT_EDIT_CURRENT_COLOR), it) })
    val chosen = swatches.firstOrNull { it.key == draft.swatchKey && (it.key != null || currentHex != null) }
    return CatEditView(
        title = t(
            when (target.mode) {
                CatEditMode.EDIT -> UiKey.CAT_EDIT_TITLE_EDIT
                CatEditMode.NEW_MAIN -> UiKey.CAT_EDIT_TITLE_MAIN
                CatEditMode.NEW_SUB -> UiKey.CAT_EDIT_TITLE_SUB
            },
        ),
        path = if (parent == null) groupName(draft.groupKey) else t(UiKey.CAT_EDIT_PATH, groupName(parent.groupKey), parent.name),
        previewHex = preview,
        isMain = isMain,
        parentLocked = hasSubs,
        parents = listOf(ParentUi(null, t(UiKey.CAT_EDIT_NO_PARENT), null)) +
            visibleMains.filter { it.id != cur?.id }.map { ParentUi(it.id, it.name, it.lightColor) },
        swatches = swatches,
        colorLabel = t(UiKey.CAT_EDIT_COLOR, chosen?.name.orEmpty()),
        colorNote = t(if (hasSubs) UiKey.CAT_EDIT_SUBS_COLOR else UiKey.CAT_EDIT_MAIN_COLOR),
        subNote = parent?.let { t(UiKey.CAT_EDIT_SUB_NOTE, it.name) },
        nameError = if (duplicate) t(TextKey.CATEGORY_NAME_DUPLICATE) else null,
        needIcon = draft.iconKey == null,
        canSave = name.isNotEmpty() && !duplicate && draft.iconKey != null,
        saveLabel = t(if (target.mode == CatEditMode.EDIT) UiKey.CAT_EDIT_SAVE else UiKey.CAT_EDIT_ADD),
        canHide = cur != null && cur.active,
        hideNote = t(if (hasSubs) UiKey.CAT_EDIT_HIDE_NOTE_SUBS else UiKey.CAT_EDIT_HIDE_NOTE),
    )
}

/** المُدخل لـ`ManageCategories.save`: الرئيسي بمجموعته ولونه · الفرعي من غيرهم (بياخدهم من أبوه). */
fun catSaveInput(target: CatEditTarget, draft: CatDraft, stored: List<Category>): CategorySaveInput {
    val cur = target.id?.let { id -> stored.firstOrNull { it.id == id } }
    val isMain = draft.parentId == null
    return CategorySaveInput(
        id = cur?.id,
        name = jsTrim(draft.name),
        active = cur?.active ?: true,
        iconKey = draft.iconKey,
        parentId = Field.Set(draft.parentId),
        groupKey = if (isMain) Field.Set(draft.groupKey) else Field.Unset,
        swatchKey = if (isMain) draft.swatchKey else null,
    )
}

/** الحفظ غيّر لون رئيسي تحته فروع ⇒ «وتغيّرت درجات فروعه معه». */
fun recolorsSubs(target: CatEditTarget, draft: CatDraft, stored: List<Category>): Boolean {
    val cur = target.id?.let { id -> stored.firstOrNull { it.id == id } } ?: return false
    if (cur.parentId != null || draft.parentId != null || draft.swatchKey == null) return false
    return stored.any { it.parentId == cur.id } && swatchColors(draft.swatchKey)?.lightColor?.uppercase() != cur.lightColor.uppercase()
}

/** الإخفاء/الإظهار من غير ما الاسم المتخزن يتغير (الاسم المعروض ممكن يبقى البديل «من غير سيارة»). */
fun catVisibilityInput(category: Category, active: Boolean): CategorySaveInput =
    CategorySaveInput(id = category.id, name = category.name, active = active)
