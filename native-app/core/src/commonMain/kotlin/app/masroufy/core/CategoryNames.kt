package app.masroufy.core

/**
 * أسماء بذور التصنيفات بالفصحى (OVERRIDES §66 — «غيّر أسماء تصنيفاتي كمان»).
 *
 * - **الحساب السعودي الجديد** بياخد الأسماء الجديدة ([SAUDI_MSA_SEED_NAMES] في `SAUDI_PACK`). **المعرّف ما بيتغيرش** (`cat-…` من الاسم
 *   القديم في شجرة التطبيق الحالي) — التطبيق الحالي وقاعدة التجار المشتركة والقواعد بيشاوروا على التصنيف **بالمعرّف**.
 * - **مصر ما اتغيرتش** (المصري الحالي بالحرف) — فروقها في [EGYPT_CATEGORY_DELTA] بتتطبق على الشجرة الخام بأسمائها القديمة.
 * - **أي كود بيطابق باسم تصنيف** بيقبل الاسمين ([sameCategoryName]) — حساب بالأسماء القديمة (حساب المالك قبل الموافقة) وحساب جديد
 *   بالأسماء الجديدة بيشتغلوا الاتنين.
 * - **تصنيفات المالك الموجودة** ما بتتغيرش غير بموافقته الصريحة: [planCategoryRename] بيطلع القايمة بس، والتنفيذ في `RenameSeedCategories`.
 *
 * الأسماء هنا **بيانات** (بتتخزن في حساب المستخدم وقت الإنشاء) مش نصوص واجهة. الاختيار اختيار Claude — المالك يقدر يغيّره (§66).
 */
data class SeedNameChange(
    /** الأساسي باسمه في شجرة التطبيق الحالي. */
    val main: String,
    /** الفرعي باسمه في الشجرة — `null` = التغيير على الأساسي نفسه. */
    val sub: String?,
    val to: String,
) {
    val from: String get() = sub ?: main
}

/** الأسماء العامية أو المصرية في شجرة التطبيق الحالي ⇒ فصحى مختصرة. باقي الشجرة فصحى أصلًا وما اتلمستش. */
val SAUDI_MSA_SEED_NAMES: List<SeedNameChange> = listOf(
    SeedNameChange("السيارة", "مواقف وسايس", "مواقف"),
    SeedNameChange("السيارة", "وقود", "بنزين"),
    SeedNameChange("تعليم وتدريب", "دورات أونلاين", "دورات إلكترونية"),
    SeedNameChange("مصاريف الشغل", null, "مصاريف الدوام"),
    SeedNameChange("مصاريف الشغل", "أدوات وبرامج للشغل", "أدوات وبرامج الدوام"),
)

/** جدول الأسماء البديلة: الاسم القديم والجديد بيوصلوا لنفس الاسم القانوني (الجديد). مفتاحه بعد `normalizeText`. */
val CATEGORY_NAME_ALIASES: Map<String, String> =
    SAUDI_MSA_SEED_NAMES.associate { normalizeText(it.from) to normalizeText(it.to) }

/** الاسم القانوني للمطابقة: القديم ⇒ الجديد، وأي اسم تاني زي ما هو (بعد `normalizeText`). */
fun canonicalCategoryName(name: String): String = normalizeText(name).let { CATEGORY_NAME_ALIASES[it] ?: it }

/** نفس التصنيف بالاسم — بيقبل الاسم القديم والجديد (§66). */
fun sameCategoryName(a: String?, b: String): Boolean = a != null && canonicalCategoryName(a) == canonicalCategoryName(b)

/**
 * الأسماء الجديدة على شجرة مبنية **من غير ما المعرّف يتغير**، والاسم القديم بيفضل يوصل للتصنيف (القواعد والتجار في ملفات المراجع
 * بيشاوروا بالاسم). التصنيف اللي مش في الشجرة بيتساب (شجر الاختبار ناقصة عن قصد) — و`CategoryNamesTest` بيتأكد إن **كل** الأسماء
 * بتلاقي مكانها في شجرة التطبيق الحقيقية، فلو الشجرة اتغيرت الاختبار بيقع بدل ما الاسم يضيع في صمت.
 */
fun applySeedNames(built: BuiltCategoryTree, changes: List<SeedNameChange>): BuiltCategoryTree {
    if (changes.isEmpty()) return built
    val newNameById = LinkedHashMap<Id, String>()
    for (change in changes) {
        val main = built.categories.firstOrNull { it.parentId == null && it.name == change.main } ?: continue
        val target = if (change.sub == null) main else built.categories.firstOrNull { it.parentId == main.id && it.name == change.sub } ?: continue
        newNameById[target.id] = change.to
    }
    val categories = built.categories.map { c -> newNameById[c.id]?.let { c.copy(name = it) } ?: c }
    val aliases = LinkedHashMap(built.aliases)
    for ((id, name) in newNameById) aliases.getOrPut(normalizeText(name)) { id }
    return built.copy(categories = categories, aliases = aliases)
}

/** سطر في خطة تغيير أسماء تصنيفات موجودة — بيتعرض على المالك (القديم ← الجديد) قبل أي تنفيذ. */
data class CategoryRename(val id: Id, val oldName: String, val newName: String)

/**
 * خطة تغيير أسماء تصنيفات **موجودة** (حساب المالك في السعودية) للفصحى — **دالة نقية، ما بتكتبش حاجة**.
 * التصنيف بيدخل الخطة لو اسمه = اسم بذرة قديم **وفي نفس مكانه** (الفرعي تحت أساسي باسمه القديم أو الجديد). بيتساب لو:
 * اسمه اتغير قبل كده · المستخدم نقله تحت أساسي تاني · أو الاسم الجديد موجود عند أخ ليه (كان هيعمل اسمين متطابقين).
 */
fun planCategoryRename(categories: List<Category>, changes: List<SeedNameChange> = SAUDI_MSA_SEED_NAMES): List<CategoryRename> {
    val byId = categories.associateBy { it.id }
    fun named(c: Category, name: String) = normalizeText(c.name) == normalizeText(name)
    fun siblingsOf(c: Category) = categories.filter { it.id != c.id && it.parentId == c.parentId }
    val mainNames = changes.filter { it.sub == null }.associate { it.main to it.to }
    val plan = mutableListOf<CategoryRename>()
    for (change in changes) {
        val matches = categories.filter { c ->
            if (!named(c, change.from)) return@filter false
            if (change.sub == null) return@filter c.parentId == null
            val parent = c.parentId?.let(byId::get) ?: return@filter false
            parent.parentId == null && (named(parent, change.main) || mainNames[change.main]?.let { named(parent, it) } == true)
        }
        for (c in matches) {
            if (siblingsOf(c).any { named(it, change.to) }) continue
            plan += CategoryRename(c.id, c.name, change.to)
        }
    }
    return plan
}
