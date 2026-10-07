package app.masroufy.usecase

import app.masroufy.core.ACTUAL_SAVING_MONTHS
import app.masroufy.core.Currency
import app.masroufy.core.DEFAULT_PAYDAY
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.Period
import app.masroufy.core.SavingComparison
import app.masroufy.core.SavingsGoal
import app.masroufy.core.SavingsPlanResult
import app.masroufy.core.SavingsReachResult
import app.masroufy.core.TextKey
import app.masroufy.core.averageSavingMinor
import app.masroufy.core.compareWithActual
import app.masroufy.core.formatMoney
import app.masroufy.core.monthSavingMinor
import app.masroufy.core.periodForDate
import app.masroufy.core.savingsPerMonth
import app.masroufy.core.savingsReach
import app.masroufy.core.uiText
import app.masroufy.core.withEstimatedKinds
import app.masroufy.port.AllocationRepository
import app.masroufy.port.CategoryRepository
import app.masroufy.port.ProfileRepository
import app.masroufy.port.TransactionRepository

/**
 * «حاسبة الادخار» (قرار المالك §69) — حالة استخدام من غير شاشة:
 * - **الاتجاهين** (`core/SavingsCalc.kt`): محتاج كام في الشهر لهدف بتاريخ · هوصل لكام بمبلغ شهري لمدة (من غير أرباح).
 * - **المقارنة باللي بتحوّشه فعلًا** ([LoadActualSaving]): متوسط آخر 3 شهور مالية **مكتملة** (من يوم الراتب) — تعريف «التحويش» في
 *   `core/ActualSaving.kt`. أي شهر مش معروف ⇒ «غير متاح» ومن غير حكم.
 * - **«حوّلها لخطة ادخار» بلمسة** ⇒ `ManageSavingsGoals.create` (§68) بنفس المبلغ والتاريخ، واللي معاك خلاص بيتسجل إيداع يوم البداية
 *   (اختيار Claude) ⇒ «المطلوب في الشهر» في الخطة = رقم الحاسبة بالظبط.
 */

/** تحويش شهر مالي واحد — null = الشهر مش معروف. */
data class MonthSaving(val period: Period, val savedMinor: Halalas?)

/** آخر 3 شهور مالية مكتملة (الأقدم الأول) ومتوسطها — null = «غير متاح». */
data class ActualSaving(val months: List<MonthSaving>, val averageMinor: Halalas?)

data class LoadActualSavingDeps(
    val txns: TransactionRepository,
    val allocations: AllocationRepository,
    val categories: CategoryRepository,
    /** يوم الراتب — null ⇒ الافتراضي (28). */
    val profile: ProfileRepository? = null,
)

class LoadActualSaving(private val deps: LoadActualSavingDeps) {
    /** بعملة البلد [currency] بس. الشهر الحالي (لسه ما خلصش) **مش داخل**. */
    suspend fun load(currency: Currency, today: IsoDate): ActualSaving {
        val payday = deps.profile?.load()?.payday ?: DEFAULT_PAYDAY
        val names = deps.categories.listAll().associate { it.id to it.name }
        val current = periodForDate(today, payday)
        val months = (ACTUAL_SAVING_MONTHS downTo 1).map { back ->
            val p = shiftPeriod(current, -back, payday)
            val rows = deps.txns.listByDateRange(p.start, p.end).filter { it.currency == currency }
            // نفس قاعدة الرئيسية: الواضح بيتحسب بنوعه التقديري (§18)، واللي لسه مش معروف بيخلّي الشهر مش معروف
            val counted = withEstimatedKinds(rows, names).transactions
            MonthSaving(p, monthSavingMinor(counted, deps.allocations.listByTransactionIds(counted.map { it.id })))
        }
        return ActualSaving(months, averageSavingMinor(months.map { it.savedMinor }))
    }
}

data class SavingsCalculatorDeps(
    /** المقارنة باللي بتحوّشه فعلًا — null = مش متوصلة ⇒ «غير متاح». */
    val actual: LoadActualSaving? = null,
    /** «حوّلها لخطة» — null = مش متوصلة. */
    val goals: ManageSavingsGoals? = null,
)

data class SavingsTargetOutcome(val plan: SavingsPlanResult, val actual: ActualSaving?, val comparison: SavingComparison)

data class SavingsReachOutcome(val reach: SavingsReachResult, val actual: ActualSaving?, val comparison: SavingComparison)

class SavingsCalculator(private val deps: SavingsCalculatorDeps) {
    /** عايز أوصل لـ[targetMinor] يوم [targetDate] ومعاك [alreadySavedMinor] ⇒ كام في الشهر، وقدامه اللي بتحوّشه فعلًا. */
    suspend fun perMonth(targetMinor: Halalas, alreadySavedMinor: Halalas, targetDate: IsoDate, currency: Currency, today: IsoDate): SavingsTargetOutcome {
        val plan = savingsPerMonth(targetMinor, alreadySavedMinor, today, targetDate)
        val actual = deps.actual?.load(currency, today)
        return SavingsTargetOutcome(plan, actual, compareWithActual(plan.perMonthMinor, actual?.averageMinor))
    }

    /** بحوّش [monthlyMinor] كل شهر لمدة [months] ⇒ هوصل لكام (من غير أرباح)، وهل ده قد اللي بتحوّشه فعلًا. */
    suspend fun reach(monthlyMinor: Halalas, months: Int, alreadySavedMinor: Halalas, currency: Currency, today: IsoDate): SavingsReachOutcome {
        val reach = savingsReach(monthlyMinor, months, alreadySavedMinor, today)
        val actual = deps.actual?.load(currency, today)
        return SavingsReachOutcome(reach, actual, compareWithActual(monthlyMinor, actual?.averageMinor))
    }

    /** «حوّلها لخطة ادخار» من الاتجاه الأول: نفس المبلغ ونفس التاريخ، والبداية النهارده. [name] null ⇒ «ادخار <المبلغ>». */
    suspend fun turnIntoGoal(plan: SavingsPlanResult, currency: Currency, name: String? = null): SavingsGoal =
        createGoal(plan.targetMinor, plan.alreadySavedMinor, plan.fromDate, plan.targetDate, currency, name)

    /** «حوّلها لخطة ادخار» من الاتجاه التاني: الهدف = اللي هتوصله، والتاريخ = آخر المدة. */
    suspend fun turnIntoGoal(reach: SavingsReachResult, currency: Currency, today: IsoDate, name: String? = null): SavingsGoal =
        createGoal(reach.reachedMinor, reach.alreadySavedMinor, today, reach.endDate, currency, name)

    private suspend fun createGoal(target: Halalas, already: Halalas, start: IsoDate, end: IsoDate, currency: Currency, name: String?): SavingsGoal {
        val goals = checkNotNull(deps.goals) { "ManageSavingsGoals not wired" }
        val goal = goals.create(GoalInput(name ?: uiText(TextKey.CALC_GOAL_NAME_DEFAULT, formatMoney(target, currency)), target, currency, start, end))
        // اللي معاك خلاص = إيداع يوم البداية ⇒ المدّخر يوم البداية معروف، و«المطلوب في الشهر» = رقم الحاسبة
        if (already > 0) goals.recordContribution(goal.id, start, already)
        return goal
    }
}
