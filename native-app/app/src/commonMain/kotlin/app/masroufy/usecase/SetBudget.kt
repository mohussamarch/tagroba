package app.masroufy.usecase

import app.masroufy.core.Budget
import app.masroufy.core.BudgetError
import app.masroufy.core.CategoryBudget
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.Period
import app.masroufy.port.BudgetRepository
import app.masroufy.port.Clock
import app.masroufy.port.IdGenerator
import app.masroufy.port.UnitOfWork

/**
 * SetBudget — نقل `setBudget.ts`: ضبط السقوف ومسحها.
 *
 * spec/01: «**لا تُخلق ميزانيات من متوسطات دون اختيار المستخدم**» ⇒ مفيش هنا دالة بتعمل سقف من متوسط،
 * وكل مسار بيبدأ بمبلغ المستخدم كاتبه بنفسه. والمسح مطلب مش تحسين (ARCHITECTURE §7).
 */

data class SetBudgetDeps(
    val budgets: BudgetRepository,
    val uow: UnitOfWork,
    val ids: IdGenerator,
    val clock: Clock,
)

/** «كسر» في العتبة أو السقف مالوش حالة هنا: `Int`/`Long` بيمنعوه وقت الترجمة. */
class SetBudget(private val deps: SetBudgetDeps) {
    /** عتبة التنبيه: نسبة بين 1 و100، أو null فمفيش تنبيه. */
    private fun validateThreshold(percent: Int?) {
        if (percent == null) return
        if (percent < 1 || percent > 100) throw BudgetError("عتبة التنبيه لازم تكون رقم صحيح بين 1 و100")
    }

    private fun validateLimit(limitMinor: Halalas) {
        if (limitMinor <= 0) throw BudgetError("السقف لازم يكون أكبر من صفر")
    }

    /** بتعمل ميزانية الفترة لو مش موجودة — من غير أي سقف من عندها. */
    private suspend fun ensureBudget(period: Period): Budget {
        deps.budgets.findByPeriod(period.key)?.let { return it }
        val now = deps.clock.nowIso()
        // معرّف الميزانية = مفتاح الفترة، زي معرّف مستندها في فايربيز وفحص النسخة الشاملة (اتشاف 2026-09-15)
        val budget = Budget(
            id = period.key,
            periodKey = period.key,
            periodStart = period.start,
            periodEnd = period.end,
            totalLimitMinor = null,
            thresholdPercent = null,
            createdAt = now,
            updatedAt = now,
        )
        deps.budgets.save(budget)
        return budget
    }

    /** السقف الإجمالي للفترة بمبلغ **المستخدم كاتبه**. */
    suspend fun setTotalLimit(period: Period, limitMinor: Halalas, thresholdPercent: Int?): Budget {
        validateLimit(limitMinor)
        validateThreshold(thresholdPercent)
        return deps.uow.run {
            val updated = ensureBudget(period).copy(
                totalLimitMinor = limitMinor,
                thresholdPercent = thresholdPercent,
                updatedAt = deps.clock.nowIso(),
            )
            deps.budgets.save(updated)
            updated
        }
    }

    /**
     * بيمسح السقف الإجمالي **وبيسيب سقوف التصنيفات**. المسح بيرجّعه null مش صفر:
     * الصفر سقف بيمنع أي صرف، وnull معناها «مفيش سقف» (spec/06).
     */
    suspend fun clearTotalLimit(period: Period) {
        val budget = deps.budgets.findByPeriod(period.key) ?: return
        deps.budgets.save(budget.copy(totalLimitMinor = null, thresholdPercent = null, updatedAt = deps.clock.nowIso()))
    }

    /** `notifyEnabled` = null ⇒ التنبيه شغال لو فيه عتبة. */
    suspend fun setCategoryLimit(
        period: Period,
        categoryId: Id,
        limitMinor: Halalas,
        notifyEnabled: Boolean? = null,
        thresholdPercent: Int? = null,
    ): CategoryBudget {
        validateLimit(limitMinor)
        validateThreshold(thresholdPercent)
        return deps.uow.run {
            val budget = ensureBudget(period)
            val existing = deps.budgets.listCategoryBudgets(budget.id).find { it.categoryId == categoryId }
            val line = CategoryBudget(
                id = existing?.id ?: deps.ids.next("catbudget"),
                budgetId = budget.id,
                categoryId = categoryId,
                limitMinor = limitMinor,
                notifyEnabled = notifyEnabled ?: (thresholdPercent != null),
                thresholdPercent = thresholdPercent,
            )
            deps.budgets.saveCategoryBudget(line)
            line
        }
    }

    suspend fun clearCategoryLimit(period: Period, categoryId: Id) {
        val budget = deps.budgets.findByPeriod(period.key) ?: return
        val existing = deps.budgets.listCategoryBudgets(budget.id).find { it.categoryId == categoryId }
        if (existing != null) deps.budgets.removeCategoryBudget(existing.id)
    }

    /** بيمسح ميزانية الفترة كلها: الإجمالي وكل سقوف التصنيفات. */
    suspend fun clearAll(period: Period) {
        deps.budgets.remove(period.key)
    }

    /**
     * بينسخ سقوف فترة سابقة للفترة دي — **بطلب صريح من المستخدم**، ومصدرها سقوف هو كاتبها.
     * **الموجود في الفترة الهدف ما يتلمسش** (قرار المالك، OVERRIDES §49): سقف التصنيف الموجود بيفضل، والسقف
     * الإجمالي الموجود بيفضل؛ اللي بيتنسخ هو الناقص بس. بيرجّع عدد سقوف التصنيفات اللي اتضافت فعلًا.
     * (التطبيق الحالي كان بيضيف سقف تاني لنفس التصنيف جنب الموجود.)
     */
    suspend fun copyFrom(sourcePeriodKey: String, target: Period): Int {
        val source = deps.budgets.findByPeriod(sourcePeriodKey) ?: throw BudgetError("مفيش ميزانية للفترة $sourcePeriodKey تتنسخ")
        val sourceLines = deps.budgets.listCategoryBudgets(source.id)
        return deps.uow.run {
            val budget = ensureBudget(target)
            if (budget.totalLimitMinor == null) {
                deps.budgets.save(
                    budget.copy(
                        totalLimitMinor = source.totalLimitMinor,
                        thresholdPercent = source.thresholdPercent,
                        updatedAt = deps.clock.nowIso(),
                    ),
                )
            }
            val present = deps.budgets.listCategoryBudgets(budget.id).map { it.categoryId }.toSet()
            val missing = sourceLines.filter { it.categoryId !in present }
            for (line in missing) {
                deps.budgets.saveCategoryBudget(line.copy(id = deps.ids.next("catbudget"), budgetId = budget.id))
            }
            missing.size
        }
    }
}
