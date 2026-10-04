package app.masroufy.usecase

import app.masroufy.core.IncomeFollowUp
import app.masroufy.core.ProfileCheck
import app.masroufy.core.UserProfile
import app.masroufy.core.checkProfile
import app.masroufy.core.emptyProfile
import app.masroufy.core.shouldAskCarToWork
import app.masroufy.port.AccountPort
import app.masroufy.port.Clock
import app.masroufy.port.IncomeSourceRepository
import app.masroufy.port.ProfileRepository

/**
 * ManageProfile — نقل `manageProfile.ts`: قسم «الحساب» وأسئلة البداية (OVERRIDES §26).
 * `load` بيرجّع ملف فاضي (يوم الراتب 28) لو لسه ما اتحفظش — مش خطأ. الحفظ بيتأكد الأول، ومفيش كتابة لو فيه حقل غلط.
 * [ManageProfileDeps.incomeSources] (§64): لما الملف يتحوّل لـ«عنده عربية» وعنده شغل شغال ⇒ `saveWithQuestions` بيرجّع
 * «هتروح بيها الشغل؟». null = مصادر الدخل مش متوصلة ⇒ مفيش سؤال.
 */
data class ManageProfileDeps(
    val profiles: ProfileRepository,
    val account: AccountPort,
    val clock: Clock,
    val incomeSources: IncomeSourceRepository? = null,
)

/** نتيجة الحفظ + الأسئلة اللي بتطلع منه (دلوقتي: «هتروح بيها الشغل؟» بس). */
data class ProfileSaved(val check: ProfileCheck, val followUps: List<IncomeFollowUp> = emptyList())

class ManageProfile(private val deps: ManageProfileDeps) {
    suspend fun load(): UserProfile = deps.profiles.load() ?: emptyProfile()

    suspend fun save(input: UserProfile): ProfileCheck = saveWithQuestions(input).check

    /**
     * الحفظ + «هتروح بيها الشغل؟» **لحظة** التحويل لـ«عنده عربية» بس (§64) — الحفظ تاني وهو لسه عنده عربية ما بيسألش تاني.
     * الشاشات اللي بتعدّل الملف تنادي دي.
     */
    suspend fun saveWithQuestions(input: UserProfile): ProfileSaved {
        val before = deps.profiles.load()
        val check = checkProfile(input)
        if (check !is ProfileCheck.Ok) return ProfileSaved(check)
        deps.profiles.save(check.profile)
        val sources = deps.incomeSources?.listAll().orEmpty()
        val ask = shouldAskCarToWork(before, check.profile, sources, deps.clock.nowIso().take(10))
        return ProfileSaved(check, if (ask) listOf(IncomeFollowUp.CarToWork) else emptyList())
    }

    /** الرد على «هتروح بيها الشغل؟» — بيتسجل على الملف (`carToWork`). من غير عربية الرد بيتمسح (`checkProfile`). */
    suspend fun answerCarToWork(yes: Boolean): ProfileCheck = save(load().copy(carToWork = yes))

    /** بيسجل وقت انتهاء أسئلة البداية؛ الحقول الاختيارية ممكن تفضل فاضية. */
    suspend fun completeOnboarding(input: UserProfile): ProfileCheck =
        save(input.copy(onboardedAt = input.onboardedAt ?: deps.clock.nowIso()))

    /** أسئلة البداية بتظهر لأي حساب ما خلصهاش قبل كده، جديد أو قديم (نص المالك، OVERRIDES §26). */
    fun needsOnboarding(profile: UserProfile): Boolean = profile.onboardedAt.isNullOrEmpty()

    fun email(): String? = deps.account.email()

    suspend fun sendPasswordReset() = deps.account.sendPasswordReset()
}
