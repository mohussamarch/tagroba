import { doc, getDoc, setDoc, type Firestore } from 'firebase/firestore'
import type { ProfileRepository } from '../../application/ports/ProfileRepository'
import { parseStoredProfile, type UserProfile } from '../../domain/userProfile'

/**
 * ملف المستخدم في `users/{uid}/profile/main` — جوه مسار المستخدم فقواعد الحماية الحالية
 * (`users/{uid}/{document=**}`) بتغطيه من غير تعديل.
 * القراءة بتمر على `parseStoredProfile`: حقل غلط ما يوقعش فتح التطبيق.
 */
export class FirestoreProfileRepository implements ProfileRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  private ref() {
    return doc(this.db, 'users', this.uid, 'profile', 'main')
  }

  async load(): Promise<UserProfile | null> {
    const snapshot = await getDoc(this.ref())
    return snapshot.exists() ? parseStoredProfile(snapshot.data()) : null
  }

  async save(profile: UserProfile): Promise<void> {
    await setDoc(this.ref(), { ...profile })
  }
}
