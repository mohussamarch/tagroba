import { sendPasswordResetEmail } from 'firebase/auth'
import type { AccountPort } from '../../application/ports/AccountPort'
import { auth } from './firebase'
import { toAuthError } from './FirebaseAuthAdapter'

/** الحساب الداخل من Firebase Auth — الإيميل وإيميل تغيير كلمة السر بس. */
export const firebaseAccount: AccountPort = {
  email: () => auth.currentUser?.email ?? null,
  async sendPasswordReset() {
    const email = auth.currentUser?.email
    if (!email) throw new Error('الحساب ده مالوش إيميل نبعتله عليه')
    try {
      await sendPasswordResetEmail(auth, email)
    } catch (error) {
      throw toAuthError(error)
    }
  },
}
