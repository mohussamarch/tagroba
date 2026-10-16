package app.masroufy.usecase

import app.masroufy.core.Currency
import app.masroufy.core.Halalas
import app.masroufy.core.Id
import app.masroufy.core.IsoDate
import app.masroufy.core.PROFILE_SALARY_KEY
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
 * مكان الرقم ([app.masroufy.core.salaryState]): **مبلغ لكل جهة شغل شغالة** (عن طريق [ManageIncomeSources] نفسها)، والملف (عن طريق
 * [ManageProfile] نفسه — `checkProfile`) **بس لو مفيش جهة** (ردود المالك 2026-10-16 18:15). القراية والكتابة بنفس القاعدة.
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

    /** المبلغ مرفوض (صفر أو سالب أو أكبر من الحد · جهة ناقصة) أو الملف رفض — الجملة جنب الخانة. */
    data class Invalid(val message: String) : SalarySaved
}

class ProfileSalary(private val deps: ProfileSalaryDeps) {
    suspend fun current(): SalaryState = salaryState(deps.profile.load(), deps.incomes.list(), deps.currency, deps.today())

    /**
     * الفورم كله: [PROFILE_SALARY_KEY] ⇒ مبلغ الملف (مفيش جهات) · غير كده مبلغ لكل جهة بمعرّفها ([saveEach]).
     */
    suspend fun saveAll(amounts: Map<String, Halalas>): SalarySaved =
        amounts[PROFILE_SALARY_KEY]?.takeIf { amounts.size == 1 }?.let { save(it) } ?: saveEach(amounts)

    /** مبلغ واحد: مفيش جهة ⇒ الملف · جهة واحدة ⇒ عليها · أكتر من جهة ⇒ مرفوض (لازم مبلغ لكل واحدة — [saveEach]). */
    suspend fun save(amountMinor: Halalas?): SalarySaved {
        if (amountMinor == null || !validSalaryAmount(amountMinor)) return SalarySaved.Invalid(uiText(UiKey.ACC_SALARY_BAD))
        val employers = current().employers
        return when (employers.size) {
            0 -> saveOnProfile(amountMinor)
            1 -> saveEach(mapOf(employers.single().id to amountMinor))
            else -> SalarySaved.Invalid(uiText(UiKey.ACC_SALARY_EACH_JOB))
        }
    }

    /** مبلغ **لكل** جهة شغالة (ولا جهة ناقصة ولا زيادة) ⇒ «المرتب المتوقع» لكل واحدة، ونسخة الملف بتتمسح. */
    suspend fun saveEach(amounts: Map<Id, Halalas>): SalarySaved {
        val employers = current().employers
        if (employers.isEmpty() || amounts.keys != employers.map { it.id }.toSet()) return SalarySaved.Invalid(uiText(UiKey.ACC_SALARY_EACH_JOB))
        if (amounts.values.any { !validSalaryAmount(it) }) return SalarySaved.Invalid(uiText(UiKey.ACC_SALARY_BAD))
        for (e in employers) deps.incomes.answerExpectedSalary(e.id, amounts.getValue(e.id))
        val profile = deps.profile.load()
        val updated = profileAfterSalary(profile, amounts.values.first(), deps.currency, onEmployer = true)
        return finish(profile, updated)
    }

    /** «مش عايز أقول» ⇒ المبلغ بعملة البلد بيتمسح (من كل الجهات الشغالة والملف) والسؤال ما بيرجعش. */
    suspend fun decline(): SalarySaved {
        current().employers.filter { it.expectedMinor != null }.forEach { deps.incomes.forgetExpectedSalary(it.id) }
        val profile = deps.profile.load()
        return finish(profile, profileAfterDecline(profile, deps.currency))
    }

    private suspend fun saveOnProfile(amountMinor: Halalas): SalarySaved {
        val profile = deps.profile.load()
        return finish(profile, profileAfterSalary(profile, amountMinor, deps.currency, onEmployer = false))
    }

    private suspend fun finish(before: app.masroufy.core.UserProfile, updated: app.masroufy.core.UserProfile): SalarySaved {
        if (updated != before) {
            val check = deps.profile.save(updated)
            if (check is ProfileCheck.Invalid) return SalarySaved.Invalid(check.message)
        }
        return SalarySaved.Ok(current())
    }
}
