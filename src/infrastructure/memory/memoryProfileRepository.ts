import type { ProfileRepository } from '../../application/ports/ProfileRepository'
import type { UserProfile } from '../../domain/userProfile'

/** ملف المستخدم في الذاكرة — للاختبارات ووضع المعاينة. */
export class MemoryProfileRepository implements ProfileRepository {
  private stored: UserProfile | null

  constructor(initial: UserProfile | null = null) {
    this.stored = initial ? structuredClone(initial) : null
  }

  async load(): Promise<UserProfile | null> {
    return this.stored ? structuredClone(this.stored) : null
  }

  async save(profile: UserProfile): Promise<void> {
    this.stored = structuredClone(profile)
  }
}
