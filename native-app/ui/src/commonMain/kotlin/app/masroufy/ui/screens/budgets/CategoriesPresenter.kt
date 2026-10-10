package app.masroufy.ui.screens.budgets

import app.masroufy.core.UiKey
import app.masroufy.core.CATEGORY_GROUPS
import app.masroufy.core.Category
import app.masroufy.core.ProfileFacts
import app.masroufy.core.TextKey
import app.masroufy.core.arabicCompare
import app.masroufy.core.factsFromProfile
import app.masroufy.core.presentCategories
import app.masroufy.core.sentenceNumber
import app.masroufy.ui.text.t

/**
 * مقدِّم «التصنيفات» (`Categories`): من `ManageCategories.list` + ملفك (`ManageProfile.load` ⇒ `factsFromProfile`) ⇒ مجموعة ← رئيسي ← فرعي
 * باللون والرمز المتخزنين (الفرعي درجة من أبوه — `planCategorySave` بيحسبها وقت الحفظ)، و«المخفية» بسببها: إنت اللي خفيته ولا شرط في ملفك
 * لسه ما اتأكدش (`presentCategories` — مفيش حاجة متخزنة بتتغير). **مفيش فلوس هنا.**
 */
data class SubRowUi(val id: String, val name: String, val iconKey: String, val colorHex: String)

data class MainRowUi(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    /** «٣ فروع» · «بلا فروع». */
    val subsLabel: String,
    val subs: List<SubRowUi>,
)

/** [key] = null للتصنيفات القديمة من غير مجموعة (الحسابات قبل §33.1). */
data class CatGroupUi(val key: String?, val name: String, val iconKey: String, val mains: List<MainRowUi>)

data class HiddenRowUi(val id: String, val name: String, val iconKey: String, val colorHex: String, val why: String, val canShow: Boolean)

data class CategoriesUi(
    val groups: List<CatGroupUi>,
    val hidden: List<HiddenRowUi>,
    /** التصنيفات زي ما هي متخزنة (للوحة التعديل: الأسماء المحجوزة · الأب · المجموعة · اللون). */
    val stored: List<Category>,
    /** الرئيسيات الظاهرة (اختيار «تحت أي تصنيف؟»). */
    val visibleMains: List<Category>,
)

suspend fun loadCategories(deps: BudgetsDeps): CategoriesUi = mapCategories(deps.categories.list(), factsFromProfile(deps.profile.load()))

/** ترتيب التصنيفات: الترتيب المحفوظ ثم الاسم بالحروف العربي (زي `categoryOrder` في `core`). */
private val byOrder = Comparator<Category> { a, b -> if (a.order != b.order) a.order.compareTo(b.order) else arabicCompare(a.name, b.name) }

/** «فرع واحد» · «فرعان» · «٣ فروع» · «١١ فرعًا» — العدّ للعرض بس. */
fun subsLabel(count: Int): String = when {
    count <= 0 -> t(UiKey.CATS_NO_SUBS)
    count == 1 -> t(UiKey.CATS_SUBS_ONE)
    count == 2 -> t(UiKey.CATS_SUBS_TWO)
    count <= 10 -> t(UiKey.CATS_SUBS_FEW, sentenceNumber(count))
    else -> t(UiKey.CATS_SUBS_MANY, sentenceNumber(count))
}

/** سبب الإخفاء بالشرط اللي في ملفك. */
internal fun requirementText(requirement: String?): String = when (requirement) {
    "hasCar" -> t(UiKey.CATS_REQ_HAS_CAR)
    "dependents" -> t(UiKey.CATS_REQ_DEPENDENTS)
    "renter" -> t(UiKey.CATS_REQ_RENTER)
    "domesticWorker" -> t(UiKey.CATS_REQ_DOMESTIC)
    "business" -> t(UiKey.CATS_REQ_BUSINESS)
    else -> t(UiKey.CATS_HIDDEN_BY_YOU)
}

fun mapCategories(stored: List<Category>, facts: ProfileFacts): CategoriesUi {
    val shown = presentCategories(stored, facts)
    val byId = shown.associateBy { it.id }
    val storedById = stored.associateBy { it.id }
    // فرعي أبوه اتشال بيتعامل كرئيسي (زي `planCategorySave`)
    fun isMain(c: Category) = c.parentId == null || c.parentId !in byId
    fun own(c: Category) = c.active
    fun visible(c: Category) = own(c) && (isMain(c) || own(byId.getValue(c.parentId!!)))
    fun kids(id: String) = shown.filter { it.parentId == id }.sortedWith(byOrder)

    val mains = shown.filter(::isMain).sortedWith(byOrder)
    val visibleMains = mains.filter(::visible)
    val known = CATEGORY_GROUPS.map { it.key }.toSet()
    val groups = CATEGORY_GROUPS.map { g -> Triple<String?, String, String>(g.key, g.name, g.iconKey) } +
        Triple(null, t(UiKey.CATS_NO_GROUP), "tag")
    val groupUis = groups.mapNotNull { (key, name, icon) ->
        val inGroup = visibleMains.filter { if (key == null) it.groupKey !in known else it.groupKey == key }
        if (inGroup.isEmpty()) return@mapNotNull null
        CatGroupUi(key, name, icon, inGroup.map { m ->
            val subs = kids(m.id).filter(::visible)
            MainRowUi(m.id, m.name, m.iconKey, m.lightColor, subsLabel(subs.size), subs.map { SubRowUi(it.id, it.name, it.iconKey, it.lightColor) })
        })
    }
    // المخفية: المخفي نفسه وأبوه ظاهر (فرعيات الرئيسي المخفي بتستخبى معاه ومش بتتكرر هنا)
    val hidden = shown.filter { !own(it) && (isMain(it) || own(byId.getValue(it.parentId!!))) }
        .sortedWith(compareBy<Category> { if (isMain(it)) it.order else byId.getValue(it.parentId!!).order }.thenComparator(byOrder::compare))
        .map { c ->
            val byYou = storedById[c.id]?.active == false
            val reason = if (byYou) t(UiKey.CATS_HIDDEN_BY_YOU) else requirementText(c.requires)
            val subsCount = if (isMain(c)) kids(c.id).size else 0
            val why = when {
                !isMain(c) -> t(UiKey.CATS_HIDDEN_UNDER, byId.getValue(c.parentId!!).name, reason)
                subsCount > 0 -> t(UiKey.CATS_HIDDEN_WITH_SUBS, subsLabel(subsCount), reason)
                else -> reason
            }
            HiddenRowUi(c.id, c.name, c.iconKey, c.lightColor, why, canShow = byYou)
        }
    return CategoriesUi(groupUis, hidden, stored, visibleMains)
}
