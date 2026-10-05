package app.masroufy.port

import app.masroufy.core.GoalContribution
import app.masroufy.core.Id
import app.masroufy.core.SavingsGoal

/**
 * خطط الادخار وإيداعاتها (المساعد المالي §68) — على مستوى الحساب (الخطة للشخص مش للبلد).
 * `listAll` مسموح: العدد صغير بطبيعته. **مفيش مسح للخطة** — الأرشفة بس (فيها فلوس اتسجلت).
 */
interface SavingsGoalRepository {
    suspend fun listAll(): List<SavingsGoal>

    suspend fun save(goal: SavingsGoal)

    /** كذا خطة في كتابة واحدة (النجمة ⭐: الجديدة والقديمة مع بعض — يا الاتنين يتكتبوا يا ولا واحدة). */
    suspend fun saveMany(goals: List<SavingsGoal>)
}

interface GoalContributionRepository {
    suspend fun listAll(): List<GoalContribution>

    suspend fun save(contribution: GoalContribution)

    /** إيداع اتسجل بالغلط بيتشال (مش حركة بنكية — سجل يدوي). */
    suspend fun remove(id: Id)
}
