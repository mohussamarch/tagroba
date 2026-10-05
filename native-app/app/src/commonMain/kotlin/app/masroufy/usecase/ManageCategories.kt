package app.masroufy.usecase

import app.masroufy.core.Category
import app.masroufy.core.CategorySaveInput
import app.masroufy.core.planCategorySave
import app.masroufy.port.CategoryRepository
import app.masroufy.port.IdGenerator

/** إدارة التصنيفات — نقل `manageCategories.ts`. قواعد المكان واللون والرمز في `planCategorySave` (OVERRIDES §33.1). */
data class ManageCategoriesDeps(val categories: CategoryRepository, val ids: IdGenerator)

class ManageCategories(private val deps: ManageCategoriesDeps) {
    suspend fun list(): List<Category> = deps.categories.listAll()

    suspend fun save(input: CategorySaveInput): Category {
        val plan = planCategorySave(deps.categories.listAll(), input) { deps.ids.next("category") }
        // الفرعيات قبل أبوها: لو الحفظ وقف في النص، لون الأساسي لسه القديم فالحفظ تاني بيعيد حسابهم
        for (kid in plan.recolored) deps.categories.save(kid)
        deps.categories.save(plan.item)
        return plan.item
    }
}
