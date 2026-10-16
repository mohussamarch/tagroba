package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.IsoDate
import app.masroufy.core.ProfileCheck
import app.masroufy.core.SalaryState
import app.masroufy.core.UiKey
import app.masroufy.core.profileAfterDecline
import app.masroufy.core.profileAfterSalary
import app.masroufy.core.salaryState
import app.masroufy.core.uiText
import app.masroufy.core.validSalaryAmount

/**
 * سؤال «المرتب الشهري» في «ملفك» وكارت «كمّل ملفك» في الرئيسية — **مبلغ بالظبط بيتكتب** (قرار المالك 2026-10-16، بدل النطاقات).
 * مكان الرقم ([app.masroufy.core.salaryState]): «المرتب المتوقع» لجهة الشغل الرئيسية لو موجودة (عن طريق [ManageIncomeSources] نفسها) وإلا
 * الملف (عن طريق [ManageProfile] نفسه — `checkProfile`). الاتنين بنفس القاعدة ⇒ رقم واحد بس يتشاف في كل مكان.
 */
data class ProfileSalaryDeps(
    val profile: ManageProfile,
    val incomes: ManageIncomeSources,
    /** عملة البلد الشغالة — المبلغ بيتكتب بيها. */
    val currency: Currency,
    /** النهارده في توقيت البلد (الجهة «شغالة النهارده»). */
    val today: () -> IsoDate,
)

sealed interface SalarySaved {
    data class Ok(val state: SalaryState) : SalarySaved

    /** المبلغ مرفوض (صفر أو سالب أو أكبر من الحد) أو الملف رفض — الجملة جنب الخانة. */
    data class Invalid(val message: String) : SalarySaved
}

class ProfileSalary(private val deps: ProfileSalaryDeps) {
    suspend fun current(): SalaryState = salaryState(deps.profile.load(), deps.incomes.list(), deps.currency, deps.today())

    /** المبلغ اللي اتكتب (بالهللة) ⇒ على الجهة الرئيسية أو على الملف — النطاق القديم و«أفضّل ما أقولش» بيتمسحوا. */
    suspend fun save(amountMinor: Halalas?): SalarySaved {
        if (amountMinor == null || !validSalaryAmount(amountMinor)) return SalarySaved.Invalid(uiText(UiKey.ACC_SALARY_BAD))
        val before = current()
        val employer = before.employer
        if (employer != null) deps.incomes.answerExpectedSalary(employer.id, amountMinor)
        val profile = deps.profile.load()
        val updated = profileAfterSalary(profile, amountMinor, deps.currency, onEmployer = employer != null)
        if (updated != profile) {
            val check = deps.profile.save(updated)
            if (check is ProfileCheck.Invalid) return SalarySaved.Invalid(check.message)
        }
        return SalarySaved.Ok(current())
    }

    /** «أفضّل ما أقولش» ⇒ المبلغ بعملة البلد بيتمسح (من الجهة والملف) والسؤال ما بيرجعش. */
    suspend fun decline(): SalarySaved {
        current().employer?.takeIf { it.expectedMinor != null }?.let { deps.incomes.forgetExpectedSalary(it.id) }
        val check = deps.profile.save(profileAfterDecline(deps.profile.load(), deps.currency))
        if (check is ProfileCheck.Invalid) return SalarySaved.Invalid(check.message)
        return SalarySaved.Ok(current())
    }
}
