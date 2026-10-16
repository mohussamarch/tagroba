package app.masroufy.ui.screens.investment.calc

import app.masroufy.core.Currency
import app.masroufy.core.UiKey
import app.masroufy.ui.components.amountLabel
import app.masroufy.ui.text.t
import app.masroufy.usecase.RetirementDefaults
import app.masroufy.usecase.RetirementSalaryFrom

/**
 * مصدر المرتب المقترح في حاسبة التقاعد (رد المالك 2026-10-16 18:15 — OVERRIDES §63): من جهة الشغل · «من البروفايل» لما مفيش جهة
 * عليها مبلغ · ولا ده ولا ده ⇒ «غير متاح». null = لسه بيتحمّل.
 */
internal fun retirementSalaryLine(d: RetirementDefaults?, currency: Currency): String? {
    val defaults = d ?: return null
    val salary = defaults.salaryMinor ?: return t(UiKey.RETCALC_SALARY_NONE)
    return when (defaults.salaryFrom) {
        RetirementSalaryFrom.PROFILE -> t(UiKey.RETCALC_SALARY_FROM_PROFILE, amountLabel(salary, currency))
        RetirementSalaryFrom.EMPLOYER -> t(UiKey.RETCALC_SALARY_FROM_JOB, amountLabel(salary, currency))
        RetirementSalaryFrom.NONE -> t(UiKey.RETCALC_SALARY_NONE)
    }
}
