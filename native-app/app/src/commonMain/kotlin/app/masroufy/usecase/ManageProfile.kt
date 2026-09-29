package app.masroufy.usecase

import app.masroufy.core.ProfileCheck
import app.masroufy.core.UserProfile
import app.masroufy.core.checkProfile
import app.masroufy.core.emptyProfile
import app.masroufy.port.AccountPort
import app.masroufy.port.Clock
import app.masroufy.port.ProfileRepository

/**
 * ManageProfile — نقل `manageProfile.ts`: قسم «الحساب» وأسئلة البداية (OVERRIDES §26).
 * `load` بيرجّع ملف فاضي (يوم الراتب 28) لو لسه ما اتحفظش — مش خطأ. الحفظ بيتأكد الأول، ومفيش كتابة لو فيه حقل غلط.
 */
data class ManageProfileDeps(val profiles: ProfileRepository, val account: AccountPort, val clock: Clock)

class ManageProfile(private val deps: ManageProfileDeps) {
    suspend fun load(): UserProfile = deps.profiles.load() ?: emptyProfile()

    suspend fun save(input: UserProfile): ProfileCheck {
        val check = checkProfile(input)
        if (check is ProfileCheck.Ok) deps.profiles.save(check.profile)
        return check
    }

    /** بيسجل وقت انتهاء أسئلة البداية؛ الحقول الاختيارية ممكن تفضل فاضية. */
    suspend fun completeOnboarding(input: UserProfile): ProfileCheck =
        save(input.copy(onboardedAt = input.onboardedAt ?: deps.clock.nowIso()))

    /** أسئلة البداية بتظهر لأي حساب ما خلصهاش قبل كده، جديد أو قديم (نص المالك، OVERRIDES §26). */
    fun needsOnboarding(profile: UserProfile): Boolean = profile.onboardedAt.isNullOrEmpty()

    fun email(): String? = deps.account.email()

    suspend fun sendPasswordReset() = deps.account.sendPasswordReset()
}
