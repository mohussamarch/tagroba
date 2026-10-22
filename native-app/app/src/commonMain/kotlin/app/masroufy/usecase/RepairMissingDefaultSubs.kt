package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.CountryPack
import app.masroufy.core.normalizeText
import app.masroufy.port.CategoryRepository
import app.masroufy.port.SpaceSeedSourceLoader

/**
 * r06-darahim (PLAN-0.6 §12): الفرعيات الافتراضية الجديدة («مغسلة وكوي» · «هوايات» · «عصير») بتوصل للحسابات **القديمة** كمان —
 * تصليح **آمن التكرار** (مرتين = نفس النتيجة) زي `RepairOpeningDebtCurrency`، بيشتغل في دورة الخلفية.
 * - بيضيف الفرعي الافتراضي بس لو أبوه موجود عند المستخدم، ومفيش تصنيف بنفس المعرّف، ومفيش أخ بنفس الاسم.
 * - الفرعي اللي المالك قفله (`active = false`) لسه موجود بمعرّفه ⇒ ما بيرجعش.
 * - الترتيب بعد آخر تصنيف عنده (بيظهر آخر إخواته). بيرجّع عدد اللي اتضاف (0 في التشغيلة التانية).
 */
class RepairMissingDefaultSubs(
    private val categories: CategoryRepository,
    private val seeds: SpaceSeedSourceLoader,
    private val pack: CountryPack,
) {
    suspend fun run(): Int {
        val mine = categories.listAll()
        if (mine.isEmpty()) return 0
        val missing = missingDefaultSubs(mine, seeds.load(pack).categories)
        missing.forEach { categories.save(it) }
        return missing.size
    }
}

/** الفرعيات الافتراضية اللي ناقصة من [mine] (نقية — للاختبار). */
fun missingDefaultSubs(mine: List<Category>, defaults: List<Category>): List<Category> {
    val ids = mine.mapTo(HashSet()) { it.id }
    val siblingNames = mine.filter { it.parentId != null }.mapTo(HashSet()) { "${it.parentId}|${normalizeText(it.name)}" }
    var order = (mine.maxOfOrNull { it.order } ?: 0) + 1
    return defaults.filter { d ->
        val parent = d.parentId ?: return@filter false
        parent in ids && d.id !in ids && "$parent|${normalizeText(d.name)}" !in siblingNames
    }.map { it.copy(order = order++) }
}
