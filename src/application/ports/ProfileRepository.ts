import type { UserProfile } from '../../domain/userProfile'

/** ملف المستخدم الواحد — OVERRIDES §26. null = لسه ما اتحفظش ولا مرة. */
export interface ProfileRepository {
  load(): Promise<UserProfile | null>
  save(profile: UserProfile): Promise<void>
}
