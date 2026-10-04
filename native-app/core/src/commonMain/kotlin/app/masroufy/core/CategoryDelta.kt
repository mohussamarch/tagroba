package app.masroufy.core

/**
 * شجرة تصنيفات بلد = شجرة السعودية + **فروق بسيطة** (رد المالك §64-٣: «الفرق بين حساب مصر و السعودية شوية كلام وتصنيفات بسيطة»).
 * الفروق **اختيار Claude** ومكتوبة بالاسم في OVERRIDES §64 عشان المالك يزود أو يشيل. الأسماء هنا **بيانات** (بتتخزن في حساب المستخدم
 * وقت إنشاء المساحة — زي شجرة السعودية نفسها)، مش نصوص واجهة.
 */
sealed interface CategoryDeltaEdit {
    /** نفس الفرعي باسم تاني (نفس الرمز والشرط). الاسم القديم بيفضل يوصل للجديد في القواعد. */
    data class RenameSub(val main: String, val from: String, val to: String) : CategoryDeltaEdit

    /** فرعي جديد تحت أساسي — بعد [after] لو اتحدد، وإلا في الآخر. */
    data class AddSub(val main: String, val sub: RawSub, val after: String? = null) : CategoryDeltaEdit
}

/**
 * فروق مصر (§41 «مصر فيها باركنج وسايس» + كلمة مصرية) — اختيار Claude، المالك يقدر يغيّره:
 * - «السيارة › مواقف وسايس» ⇒ «باركنج» + «سايس» لوحده (في مصر حاجتين مختلفتين: الجراج/الموقف بفلوس، والسايس في الشارع).
 * - «اتصالات › جوال» ⇒ «موبايل».
 */
val EGYPT_CATEGORY_DELTA: List<CategoryDeltaEdit> = listOf(
    CategoryDeltaEdit.RenameSub("السيارة", "مواقف وسايس", "باركنج"),
    CategoryDeltaEdit.AddSub("السيارة", RawSub("سايس", "hand-coins", "hasCar"), after = "باركنج"),
    CategoryDeltaEdit.RenameSub("اتصالات", "جوال", "موبايل"),
)

/** الفروق على الشجرة الخام. أساسي أو فرعي مش موجود ⇒ خطأ صريح (لو شجرة السعودية اتغيرت، الفرق ما يتطبقش غلط في صمت). */
fun applyCategoryDelta(raw: RawCategoryTree, edits: List<CategoryDeltaEdit>): RawCategoryTree {
    var groups = raw.groups
    var overrides = raw.ruleWordOverrides
    fun editMain(name: String, change: (RawMain) -> RawMain) {
        var found = false
        groups = groups.map { g -> g.copy(mains = g.mains.map { m -> if (m.name == name) change(m).also { found = true } else m }) }
        if (!found) throw SeedError("فرق التصنيفات: الأساسي «$name» مش موجود")
    }
    for (edit in edits) when (edit) {
        is CategoryDeltaEdit.RenameSub -> {
            editMain(edit.main) { m ->
                if (m.subs.none { it.name == edit.from }) throw SeedError("فرق التصنيفات: «${edit.main} › ${edit.from}» مش موجود")
                m.copy(subs = m.subs.map { if (it.name == edit.from) it.copy(name = edit.to) else it })
            }
            overrides = overrides.mapValues { (_, path) -> if (path == listOf(edit.main, edit.from)) listOf(edit.main, edit.to) else path }
        }
        is CategoryDeltaEdit.AddSub -> editMain(edit.main) { m ->
            if (m.subs.any { it.name == edit.sub.name }) throw SeedError("فرق التصنيفات: «${edit.main} › ${edit.sub.name}» موجود قبل كده")
            val at = edit.after?.let { a -> m.subs.indexOfFirst { it.name == a }.takeIf { it >= 0 }?.plus(1) ?: throw SeedError("فرق التصنيفات: «$a» مش موجود") } ?: m.subs.size
            m.copy(subs = m.subs.take(at) + edit.sub + m.subs.drop(at))
        }
    }
    return RawCategoryTree(groups, overrides)
}

/**
 * شجرة البلد: شجرة السعودية الخام + فروق الحزمة. الاسم القديم للفرعي اللي اتغيّر اسمه بيفضل يوصل للجديد
 * (القواعد والتجار في ملفات المراجع بيشاوروا على التصنيف **بالاسم**).
 */
fun buildCountryCategoryTree(saudiRaw: RawCategoryTree, pack: CountryPack): BuiltCategoryTree {
    val built = buildCategoryTree(applyCategoryDelta(saudiRaw, pack.categoryDelta))
    val renames = pack.categoryDelta.filterIsInstance<CategoryDeltaEdit.RenameSub>()
    if (renames.isEmpty()) return built
    val aliases = LinkedHashMap(built.aliases)
    for (r in renames) {
        val id = built.categories.firstOrNull { c -> c.name == r.to && built.categories.any { it.id == c.parentId && it.name == r.main } }?.id ?: continue
        aliases.getOrPut(normalizeText(r.from)) { id }
    }
    return built.copy(aliases = aliases)
}
