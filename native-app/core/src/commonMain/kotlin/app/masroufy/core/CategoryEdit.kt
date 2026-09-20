package app.masroufy.core

/**
 * حفظ تصنيف من شاشة التصنيفات — نقل `categoryEdit.ts` (OVERRIDES §33.1).
 * مستوى واحد بس؛ الفرعي مجموعته ولونه من أبوه (اللون بيتحسب لما يتعمل أو يتنقل بس)؛
 * تغيير لون الأساسي بيغيّر درجات فرعياته (`recolored`)؛ اللي الشاشة ما بتعدلهوش بيفضل زي ما هو.
 */
class CategoryEditError(message: String) : IllegalArgumentException(message)

/** «مش متبعت» يختلف عن «null» في الحقول الاختيارية — زي `undefined` في جافاسكربت. */
sealed interface Field<out T> {
    data object Unset : Field<Nothing>
    data class Set<T>(val value: T) : Field<T>
}

data class CategorySaveInput(
    val id: Id? = null,
    val name: String,
    val active: Boolean,
    val iconKey: String? = null,
    /** Set(null) = أساسي؛ Unset = مكانه الحالي (والجديد أساسي). */
    val parentId: Field<Id?> = Field.Unset,
    /** Set(null) = بلا مجموعة؛ Unset = الحالية. الفرعي بيتجاهلها. */
    val groupKey: Field<String?> = Field.Unset,
    val swatchKey: String? = null,
)

data class CategorySavePlan(val item: Category, /** فرعيات اتحسبت درجاتها تاني لأن لون أبوها اتغير. */ val recolored: List<Category>)

private val ICON_KEY = Regex("^[a-z0-9-]{1,40}$")

fun planCategorySave(all: List<Category>, input: CategorySaveInput, newId: () -> Id): CategorySavePlan {
    val old = input.id?.let { id -> all.firstOrNull { it.id == id } }
    if (input.id != null && old == null) throw CategoryEditError(uiText(TextKey.CATEGORY_NOT_FOUND))
    val name = JsText.trim(input.name)
    if (name.isEmpty() || name.length > 80) throw CategoryEditError(uiText(TextKey.CATEGORY_NAME_LENGTH))
    if (all.any { it.id != old?.id && normalizeText(it.name) == normalizeText(name) }) throw CategoryEditError(uiText(TextKey.CATEGORY_NAME_DUPLICATE))
    val iconKey = input.iconKey ?: old?.iconKey ?: "tag"
    if (!ICON_KEY.matches(iconKey)) throw CategoryEditError(uiText(TextKey.CATEGORY_ICON_REQUIRED))

    val byId = all.associateBy { it.id }
    val currentParentId = old?.parentId
    val parentId = when (val p = input.parentId) {
        Field.Unset -> currentParentId
        is Field.Set -> p.value
    }
    val moved = parentId != currentParentId
    // فرعي أبوه مش موجود بيتعامل كأساسي
    val parent = parentId?.let { byId[it] }
    if (moved && parentId != null) {
        if (parentId == old?.id) throw CategoryEditError(uiText(TextKey.CATEGORY_SELF_PARENT))
        if (parent == null) throw CategoryEditError(uiText(TextKey.CATEGORY_PARENT_MISSING))
        if (parent.parentId != null && parent.parentId in byId) throw CategoryEditError(uiText(TextKey.CATEGORY_PARENT_NOT_MAIN))
        if (old != null && all.any { it.parentId == old.id }) throw CategoryEditError(uiText(TextKey.CATEGORY_HAS_SUBS))
    }

    val groupKey: String? = when {
        parent != null -> null
        input.groupKey is Field.Set -> (input.groupKey as Field.Set<String?>).value?.also {
            if (!isCategoryGroupKey(it)) throw CategoryEditError(uiText(TextKey.CATEGORY_GROUP_UNKNOWN))
        }
        // فرعي بقى أساسي من غير ما يختار مجموعة ⇒ بيفضل في مجموعة أبوه القديم
        else -> old?.groupKey ?: currentParentId?.let { byId[it]?.groupKey }
    }

    val colors: CategoryColorPair = when {
        parent != null -> if (old != null && !moved) CategoryColorPair(old.lightColor, old.darkColor)
        else childColors(CategoryColorPair(parent.lightColor, parent.darkColor), all.count { it.parentId == parent.id && it.id != old?.id })
        input.swatchKey != null -> swatchColors(input.swatchKey) ?: throw CategoryEditError(uiText(TextKey.CATEGORY_COLOR_REQUIRED))
        old != null -> CategoryColorPair(old.lightColor, old.darkColor)
        else -> swatchColors(firstFreeSwatch(all))!!
    }

    val base = old ?: Category(id = "", parentId = null, name = "", iconKey = "", lightColor = "", darkColor = "", active = true, order = 0)
    val item = base.copy(
        id = old?.id ?: newId(), parentId = parentId, name = name, iconKey = iconKey,
        lightColor = colors.lightColor, darkColor = colors.darkColor, active = input.active,
        order = old?.order ?: (maxOf(0, all.maxOfOrNull { it.order } ?: 0) + 1), groupKey = groupKey,
    )
    val recolored = if (old != null && parent == null && (colors.lightColor != old.lightColor || colors.darkColor != old.darkColor)) {
        all.filter { it.parentId == old.id }.sortedBy { it.order }.mapIndexed { index, kid ->
            val c = childColors(colors, index)
            kid.copy(lightColor = c.lightColor, darkColor = c.darkColor)
        }
    } else emptyList()
    return CategorySavePlan(item, recolored)
}
