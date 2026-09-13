import { checkProfile, emptyProfile, type ProfileCheck, type UserProfile } from '../../domain/userProfile'
import type { AccountPort } from '../ports/AccountPort'
import type { ProfileRepository } from '../ports/ProfileRepository'
import type { Clock } from '../ports/repositories'

/**
 * ManageProfile — قسم «الحساب» وأسئلة البداية (OVERRIDES §26).
 *
 * - `load` بيرجّع ملف فاضي (يوم الراتب 28) لو لسه ما اتحفظش — مش خطأ.
 * - `save` و`completeOnboarding` بيتأكدوا من القيم الأول، ومش بيكتبوا حاجة لو فيه حقل غلط.
 * - `completeOnboarding` بيسجل وقت انتهاء أسئلة البداية؛ الحقول الاختيارية ممكن تفضل فاضية.
 * - الإيميل وتغيير كلمة السر من الحساب الداخل (AccountPort) — الشاشة ما بتكلمش فايربيز (قاعدة 4).
 */
export function makeManageProfile(deps: { profiles: ProfileRepository; account: AccountPort; clock: Clock }) {
  async function load(): Promise<UserProfile> {
    return (await deps.profiles.load()) ?? emptyProfile()
  }

  async function save(input: UserProfile): Promise<ProfileCheck> {
    const check = checkProfile(input)
    if (check.ok) await deps.profiles.save(check.profile)
    return check
  }

  async function completeOnboarding(input: UserProfile): Promise<ProfileCheck> {
    return save({ ...input, onboardedAt: input.onboardedAt ?? deps.clock.nowIso() })
  }

  return {
    load,
    save,
    completeOnboarding,
    email: () => deps.account.email(),
    sendPasswordReset: () => deps.account.sendPasswordReset(),
  }
}
