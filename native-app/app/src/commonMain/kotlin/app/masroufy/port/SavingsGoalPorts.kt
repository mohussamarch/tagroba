package app.masroufy.port

import app.masroufy.core.GoalContribution
import app.masroufy.core.Id
import app.masroufy.core.SavingsGoal

/**
 * خطط الادخار وإيداعاتها (المساعد المالي §68) — على مستوى الحساب (الخطة للشخص مش للبلد).
 * `listAll` مسموح: العدد صغير بطبيعته. المسح من لوحة المسح الواحدة بس (r06-links2 — بتراجع).
 */
interface SavingsGoalRepository {
    suspend fun listAll(): List<SavingsGoal>

    suspend fun save(goal: SavingsGoal)

    /** كذا خطة في كتابة واحدة (النجمة ⭐: الجديدة والقديمة مع بعض — يا الاتنين يتكتبوا يا ولا واحدة). */
    suspend fun saveMany(goals: List<SavingsGoal>)

    /** r06-links2 (§118-1): مسح الخطة نفسها من لوحة المسح (بتراجع) — الإيداعات بتتمسح معاها من حالة الاستخدام. */
    suspend fun delete(id: Id): Unit = throw UnsupportedOperationException("delete")
}

interface GoalContributionRepository {
    suspend fun listAll(): List<GoalContribution>

    suspend fun save(contribution: GoalContribution)

    /** إيداع اتسجل بالغلط بيتشال (مش حركة بنكية — سجل يدوي). */
    suspend fun remove(id: Id)
}
