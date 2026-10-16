package app.masroufy.wiring

import app.masroufy.usecase.ManageIncomeSources
import app.masroufy.usecase.ManageIncomeSourcesDeps
import app.masroufy.usecase.ProfileSalary
import app.masroufy.usecase.ProfileSalaryDeps

/**
 * «المرتب الشهري» (قرار المالك 2026-10-16: **مبلغ بالظبط بيتكتب** بدل النطاقات) — نفس التجميع لـ«ملفك» (`MoreGraph`) و«كمّل ملفك» (`HomeGraph`):
 * ملف الحساب (`c.shell.profile`) + مصادر دخل البلد الشغالة بعملتها ويومها.
 */
internal fun salaryCase(
    c: AreaContext,
    incomes: ManageIncomeSources = ManageIncomeSources(ManageIncomeSourcesDeps(c.repos.incomeSources, c.shell.profile, c.repos.uow, c.env.ids, c.env.clock)),
): ProfileSalary = ProfileSalary(ProfileSalaryDeps(c.shell.profile, incomes, c.space.currency) { c.env.today() })
