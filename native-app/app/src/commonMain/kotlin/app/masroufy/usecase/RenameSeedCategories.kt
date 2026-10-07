package app.masroufy.usecase

import app.masroufy.core.CategoryRename
import app.masroufy.core.planCategoryRename
import app.masroufy.port.CategoryRepository

/** نتيجة التنفيذ: اللي اتغير اسمه، واللي اتساب لأن اسمه اتغير من بعد ما الخطة اتعملت (أو اتمسح). */
data class CategoryRenameResult(val applied: List<CategoryRename>, val skipped: List<CategoryRename>)

/**
 * تغيير أسماء تصنيفات **موجودة** للفصحى (OVERRIDES §66) — على مرحلتين، والتنفيذ **بموافقة المالك الصريحة بس**:
 * 1. [plan] — القايمة (المعرّف · الاسم القديم ← الجديد) من غير أي كتابة. دي اللي بتتعرض على المالك.
 * 2. [apply] — بيغيّر **الاسم بس** (المعرّف والأب واللون والترتيب زي ما هم ⇒ العمليات والقواعد والتجار والميزانيات ما بتتأثرش).
 *    سطر اسمه اتغير من بعد الخطة (أو التصنيف اتمسح) ⇒ **بيتساب** ومش بيتكتب فوقه.
 *
 * ⚠️ المستودع اللي بيتدّي هنا هو اللي بيتكتب فيه. على حساب المالك الحقيقي (السعودية) ما يتشغلش غير بعد ما يشوف القايمة ويوافق —
 * ومش على حساب مصر (المصري بيفضل زي ما هو).
 */
class RenameSeedCategories(private val categories: CategoryRepository) {
    suspend fun plan(): List<CategoryRename> = planCategoryRename(categories.listAll())

    suspend fun apply(plan: List<CategoryRename>): CategoryRenameResult {
        val current = categories.listAll().associateBy { it.id }
        val applied = mutableListOf<CategoryRename>()
        val skipped = mutableListOf<CategoryRename>()
        for (line in plan) {
            val category = current[line.id]
            if (category == null || category.name != line.oldName) {
                skipped += line
                continue
            }
            categories.save(category.copy(name = line.newName))
            applied += line
        }
        return CategoryRenameResult(applied, skipped)
    }
}
