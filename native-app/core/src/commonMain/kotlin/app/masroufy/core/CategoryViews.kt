package app.masroufy.core

/**
 * عرض التصنيفات — نقل `categoryOptions.ts` (قايمة الاختيار) و`categoryManageView.ts` (شاشة الإدارة)
 * و`groupDistribution.ts` (التوزيع بالمجموعات). فرعي أبوه مش موجود بيتعامل كأساسي في الكل.
 */
data class CategoryOption(val id: Id, val label: String, /** 0 أساسي، 1 فرعي. */ val depth: Int)
data class CategoryOptionGroup(val key: String, val label: String, val options: List<CategoryOption>)

/**
 * قايمة الاختيار بالمجموعات (OVERRIDES §28.1): الظاهر بس، والأساسي المخفي بيخفي فروعه.
 * `keepId` (تصنيف العملية الحالي) بيظهر حتى لو مخفي؛ فرعي أبوه مخفي بيظهر «الأساسي › الفرعي».
 */
fun groupCategoryOptions(categories: List<Category>, keepId: Id? = null): List<CategoryOptionGroup> {
    val ids = categories.map { it.id }.toSet()
    val children = LinkedHashMap<Id, MutableList<Category>>()
    val mains = mutableListOf<Category>()
    for (c in categories) {
        if (c.parentId != null && c.parentId in ids) children.getOrPut(c.parentId) { mutableListOf() }.add(c) else mains.add(c)
    }
    fun shown(c: Category) = c.active || c.id == keepId
    fun optionsFor(list: List<Category>): List<CategoryOption> = buildList {
        for (main in list.sortedWith(categoryOrder)) {
            val kids = children[main.id].orEmpty().sortedWith(categoryOrder)
            if (shown(main)) {
                add(CategoryOption(main.id, main.name, 0))
                for (kid in kids) if (shown(kid)) add(CategoryOption(kid.id, kid.name, 1))
            } else {
                kids.firstOrNull { it.id == keepId }?.let { add(CategoryOption(it.id, "${main.name} › ${it.name}", 0)) }
            }
        }
    }
    val groups = mutableListOf<CategoryOptionGroup>()
    val ungrouped = optionsFor(mains.filter { !isCategoryGroupKey(it.groupKey) })
    if (ungrouped.isNotEmpty()) groups += CategoryOptionGroup("ungrouped", "التصنيفات", ungrouped)
    for (group in CATEGORY_GROUPS) {
        val list = optionsFor(mains.filter { it.groupKey == group.key })
        if (list.isNotEmpty()) groups += CategoryOptionGroup(group.key, group.name, list)
    }
    return groups
}

data class ManagedMain(val category: Category, val subs: List<Category>)
data class ManagedGroup(val key: String, val label: String, val iconKey: String, val mains: List<ManagedMain>)
data class ManagedCategoryView(val groups: List<ManagedGroup>, /** الأساسيات المخفية في قسم لوحدها تحت. */ val hidden: List<ManagedMain>)

private fun isMainIn(ids: Set<Id>, c: Category) = c.parentId == null || c.parentId !in ids
private fun ungroupedCategory(c: Category) = !isCategoryGroupKey(c.groupKey)

/** شاشة الإدارة (OVERRIDES §33.1): المخفي بيظهر كمان عشان يترجع، والأساسي المخفي في قسم تحت. */
fun manageCategoryView(categories: List<Category>): ManagedCategoryView {
    val ids = categories.map { it.id }.toSet()
    val mains = categories.filter { isMainIn(ids, it) }.sortedWith(categoryOrder)
    fun toMain(c: Category) = ManagedMain(c, categories.filter { it.parentId == c.id }.sortedWith(categoryOrder))
    val shown = mains.filter { it.active }
    val groups = mutableListOf<ManagedGroup>()
    val loose = shown.filter(::ungroupedCategory)
    if (loose.isNotEmpty()) groups += ManagedGroup("ungrouped", "التصنيفات", "tag", loose.map(::toMain))
    for (group in CATEGORY_GROUPS) {
        val list = shown.filter { it.groupKey == group.key }
        if (list.isNotEmpty()) groups += ManagedGroup(group.key, group.name, group.iconKey, list.map(::toMain))
    }
    return ManagedCategoryView(groups, mains.filter { !it.active }.map(::toMain))
}

data class PlaceOption(val id: Id, val label: String)
data class PlaceGroup(val key: String, val label: String, val options: List<PlaceOption>)
data class CategoryPlaceChoices(
    /** مجموعات الأساسي؛ key = null ⇒ «بلا مجموعة». */
    val groups: List<Pair<String?, String>>,
    /** الأساسيات اللي ينفع يبقى فرعي تحتها؛ فاضية لو هو نفسه تحته فرعيات. */
    val parentGroups: List<PlaceGroup>,
    val hasSubs: Boolean,
)

fun categoryPlaceChoices(categories: List<Category>, editingId: Id?): CategoryPlaceChoices {
    val view = manageCategoryView(categories)
    val ids = categories.map { it.id }.toSet()
    val editing = editingId?.let { id -> categories.firstOrNull { it.id == id } }
    val hasSubs = editingId != null && categories.any { it.parentId == editingId }
    val editingLoose = editing != null && isMainIn(ids, editing) && ungroupedCategory(editing)
    val groups = buildList<Pair<String?, String>> {
        if (view.groups.any { it.key == "ungrouped" } || editingLoose) add(null to "بلا مجموعة")
        CATEGORY_GROUPS.forEach { add(it.key to it.name) }
    }
    fun options(mains: List<ManagedMain>) = mains.filter { it.category.id != editingId }.map { PlaceOption(it.category.id, it.category.name) }
    val parentGroups = if (hasSubs) emptyList() else
        (view.groups.map { PlaceGroup(it.key, it.label, options(it.mains)) } + PlaceGroup("hidden", "المخفية", options(view.hidden))).filter { it.options.isNotEmpty() }
    return CategoryPlaceChoices(groups, parentGroups, hasSubs)
}

data class MainSlice(val categoryId: Id, val amountMinor: Halalas, val count: Int, val shareTenthPercent: Long)
data class GroupSlice(val key: String, val amountMinor: Halalas, val count: Int, val shareTenthPercent: Long, val mains: List<MainSlice>)
data class GroupDistribution(val grouped: Boolean, val totalMinor: Halalas, val groups: List<GroupSlice>)

/**
 * التوزيع «من برا» بالمجموعات (OVERRIDES §28.1): فرعي ← أساسي ← مجموعة، **مفيش عملية بتتحسب مرتين**.
 * من غير تصنيف ⇒ «uncategorized»، وأساسي من غير مجموعة ⇒ «ungrouped».
 */
fun groupDistribution(slices: List<CategorySlice>, categories: List<Category>): GroupDistribution {
    val byId = categories.associateBy { it.id }
    fun mainOf(id: Id): Category? {
        var current = byId[id]
        val seen = mutableSetOf<Id>()
        while (current?.parentId != null && current.parentId in byId && current.id !in seen) {
            seen += current.id
            current = byId[current.parentId]
        }
        return current
    }
    fun rank(key: String) = CATEGORY_GROUPS.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: if (key == "ungrouped") 100 else 101

    class Acc(var amount: Long = 0, var count: Int = 0, val mains: LinkedHashMap<Id, Pair<Long, Int>> = LinkedHashMap())
    var total = 0L
    var grouped = false
    val groups = LinkedHashMap<String, Acc>()
    for (slice in slices) {
        total = addMoney(total, slice.amountMinor)
        val main = slice.categoryId?.let(::mainOf)
        val key = when {
            main == null -> "uncategorized"
            isCategoryGroupKey(main.groupKey) -> main.groupKey!!
            else -> "ungrouped"
        }
        if (key != "uncategorized" && key != "ungrouped") grouped = true
        val acc = groups.getOrPut(key) { Acc() }
        acc.amount = addMoney(acc.amount, slice.amountMinor)
        acc.count += slice.count
        if (main != null) {
            val (amount, count) = acc.mains[main.id] ?: (0L to 0)
            acc.mains[main.id] = addMoney(amount, slice.amountMinor) to count + slice.count
        }
    }
    fun share(amount: Long) = if (total == 0L) 0 else rateOfMoney(amount, 1000, total)
    val result = groups.map { (key, g) ->
        GroupSlice(
            key, g.amount, g.count, share(g.amount),
            g.mains.map { (id, m) -> MainSlice(id, m.first, m.second, share(m.first)) }.sortedByDescending { it.amountMinor },
        )
    }.sortedWith(compareByDescending<GroupSlice> { it.amountMinor }.thenBy { rank(it.key) })
    return GroupDistribution(grouped, total, result)
}
