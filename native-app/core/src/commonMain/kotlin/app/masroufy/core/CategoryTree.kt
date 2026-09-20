package app.masroufy.core

/**
 * التصنيفات ومجموعاتها — نقل `categoryTree.ts` + `categoryVisibility.ts` + الكيان من `types.ts` (OVERRIDES §28.1).
 * مجموعة ← أساسي ← فرعي. **مكان التصنيف في مجموعة للعرض بس** — المصروف بيتحدد من النوع الاقتصادي.
 */
data class Category(
    val id: Id,
    val parentId: Id?,
    val name: String,
    val iconKey: String,
    val lightColor: String,
    val darkColor: String,
    val active: Boolean,
    val order: Int,
    /** مجموعة الأساسي. الفرعي بياخدها من أبوه؛ الحسابات القديمة من غير مجموعة. */
    val groupKey: String? = null,
    /** مخفي لحد ما ملف المستخدم يأكد المعلومة دي. */
    val requires: String? = null,
    /** اسم ورمز بديلين لو مالوش سيارة: «السيارة» ← «المواصلات». */
    val noCarName: String? = null,
    val noCarIconKey: String? = null,
)

data class CategoryGroup(val key: String, val nameKey: TextKey, val iconKey: String, val kind: String) {
    /** الاسم المعروض باللغة الحالية (Texts.kt). */
    val name: String get() = uiText(nameKey)
}

/** رد المالك «الخمسة اللي اقترحتهم»؛ التحويلات والسحب برا الخمسة. */
val CATEGORY_GROUPS: List<CategoryGroup> = listOf(
    CategoryGroup("food", TextKey.GROUP_FOOD, "chef-hat", "spend"),
    CategoryGroup("home", TextKey.GROUP_HOME, "house-heart", "spend"),
    CategoryGroup("transport", TextKey.GROUP_TRANSPORT, "route", "spend"),
    CategoryGroup("personal", TextKey.GROUP_PERSONAL, "user-round", "spend"),
    CategoryGroup("saving", TextKey.GROUP_SAVING, "piggy-bank", "saving"),
    CategoryGroup("movement", TextKey.GROUP_MOVEMENT, "arrow-left-right", "movement"),
)

/** أسماء الشروط للعرض — بتتقرا وقت العرض عشان تتغير مع اللغة (Texts.kt). */
val REQUIREMENT_LABELS: Map<String, String>
    get() = linkedMapOf(
        "hasCar" to uiText(TextKey.REQ_HAS_CAR),
        "dependents" to uiText(TextKey.REQ_DEPENDENTS),
        "renter" to uiText(TextKey.REQ_RENTER),
        "domesticWorker" to uiText(TextKey.REQ_DOMESTIC_WORKER),
        "business" to uiText(TextKey.REQ_BUSINESS),
    )

private val REQUIREMENT_KEYS = setOf("hasCar", "dependents", "renter", "domesticWorker", "business")

fun isCategoryGroupKey(value: String?): Boolean = CATEGORY_GROUPS.any { it.key == value }
fun isCategoryRequirement(value: String?): Boolean = value != null && value in REQUIREMENT_KEYS

/** ترتيب التصنيفات: الترتيب المحفوظ، وبعده الاسم بترتيب الحروف العربي (زي `localeCompare(…, 'ar')`). */
internal val categoryOrder = Comparator<Category> { a, b -> if (a.order != b.order) a.order.compareTo(b.order) else compareArabic(a.name, b.name) }

/** مقارنة نصين بترتيب الحروف العربي — خاصة بكل جهاز. */
internal expect fun compareArabic(a: String, b: String): Int

/** معلومات الشخص اللي بتظهر تصنيفات — null = ما اتجاوبش ⇒ التصنيف المشروط مخفي. */
data class ProfileFacts(
    val hasCar: Boolean?,
    /** بيعول زوج أو زوجة أو أولاد. */
    val familyDependents: Boolean?,
    val renter: Boolean?,
    val domesticWorker: Boolean?,
    val business: Boolean?,
)

val UNKNOWN_FACTS = ProfileFacts(null, null, null, null, null)

fun factsFromProfile(profile: UserProfile?): ProfileFacts {
    if (profile == null) return UNKNOWN_FACTS
    val kinds = profile.dependentKinds
    val family = when {
        profile.supportsDependents == false -> false
        kinds == null -> null
        else -> "spouse" in kinds || "children" in kinds
    }
    return ProfileFacts(profile.hasCar, family, profile.renter, profile.domesticWorker, profile.business)
}

fun requirementMet(requirement: String, facts: ProfileFacts): Boolean = when (requirement) {
    "hasCar" -> facts.hasCar == true
    "dependents" -> facts.familyDependents == true
    "renter" -> facts.renter == true
    "domesticWorker" -> facts.domesticWorker == true
    "business" -> facts.business == true
    else -> false
}

/** نسخة للعرض: الاسم البديل لو مالوش سيارة، والمشروط غير المتأكد مش ظاهر — **مفيش حاجة متخزنة بتتغير**. */
fun presentCategories(categories: List<Category>, facts: ProfileFacts): List<Category> = categories.map { category ->
    var shown = category
    if (category.noCarName != null && facts.hasCar != true) {
        shown = shown.copy(name = category.noCarName, iconKey = category.noCarIconKey ?: category.iconKey)
    }
    if (category.requires != null && !requirementMet(category.requires, facts)) shown = shown.copy(active = false)
    shown
}
