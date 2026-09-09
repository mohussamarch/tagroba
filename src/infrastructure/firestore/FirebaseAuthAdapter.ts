import { Capacitor } from '@capacitor/core'
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signInWithRedirect,
  signOut as fbSignOut,
  type User,
} from 'firebase/auth'
import { auth, authPersistenceReady, googleProvider } from './firebase'
import { AuthError, type AuthPort, type AuthUser } from '../../application/ports/AuthPort'

/** رسائل فايربيز بالإنجليزية — تُترجم لرسائل عربية محددة بجانب الحقل (spec/04). */
const MESSAGES: Record<string, { text: string; field: 'email' | 'password' | 'form' }> = {
  'auth/invalid-email': { text: 'الإيميل مش مكتوب صح', field: 'email' },
  'auth/user-disabled': { text: 'الحساب ده متوقف', field: 'form' },
  'auth/user-not-found': { text: 'مفيش حساب بالإيميل ده', field: 'email' },
  'auth/wrong-password': { text: 'كلمة السر غلط', field: 'password' },
  'auth/invalid-credential': { text: 'الإيميل أو كلمة السر غلط', field: 'form' },
  'auth/email-already-in-use': { text: 'الإيميل ده مسجّل قبل كده — جرّب تسجيل الدخول', field: 'email' },
  'auth/weak-password': { text: 'كلمة السر قصيرة — لازم ٦ حروف على الأقل', field: 'password' },
  'auth/missing-password': { text: 'اكتب كلمة السر', field: 'password' },
  'auth/too-many-requests': { text: 'محاولات كتير. استنى شوية وجرّب تاني', field: 'form' },
  'auth/network-request-failed': { text: 'مفيش إنترنت. اتأكد من الاتصال وجرّب تاني', field: 'form' },
  'auth/popup-closed-by-user': { text: 'قفلت نافذة جوجل قبل ما تكمّل', field: 'form' },
  'auth/popup-blocked': { text: 'المتصفح منع نافذة جوجل. اسمح بالنوافذ المنبثقة', field: 'form' },
  'auth/operation-not-allowed': { text: 'طريقة الدخول دي مش مفعّلة في المشروع', field: 'form' },
  'auth/unauthorized-domain': { text: 'النطاق ده مش مصرّح له في إعدادات فايربيز', field: 'form' },
}

function toAuthError(error: unknown): AuthError {
  const code =
    typeof error === 'object' && error !== null && 'code' in error
      ? String((error as { code: unknown }).code)
      : 'unknown'
  const known = MESSAGES[code]
  if (known) return new AuthError(known.text, code, known.field)
  // خطأ غير معروف: يُعرض بصراحة مع رمزه، ولا يُخفى خلف رسالة عامة كاذبة
  return new AuthError(`حصل خطأ مش متوقع (${code})`, code, 'form')
}

function toAuthUser(user: User): AuthUser {
  return { uid: user.uid, email: user.email, displayName: user.displayName }
}

export class FirebaseAuthAdapter implements AuthPort {
  readonly googleUnavailableReason = Capacitor.isNativePlatform() ? "دخول جوجل لسه قيد التجهيز في نسخة أندرويد. الدخول بالإيميل وكلمة السر متاح." : undefined
  observe(callback: (user: AuthUser | null) => void): () => void {
    return onAuthStateChanged(auth, (user) => callback(user ? toAuthUser(user) : null))
  }

  async signInWithGoogle(): Promise<AuthUser> {
    if (this.googleUnavailableReason) throw new AuthError(this.googleUnavailableReason, "app/native-google-pending", "form")
    await authPersistenceReady
    try {
      const result = await signInWithPopup(auth, googleProvider)
      return toAuthUser(result.user)
    } catch (error) {
      const authError = toAuthError(error)
      // على الجوال المثبت كـ PWA النوافذ المنبثقة كثيرًا ما تُمنع — نتحول للتحويل الكامل
      if (authError.code === 'auth/popup-blocked' || authError.code === 'auth/operation-not-supported-in-this-environment') {
        await signInWithRedirect(auth, googleProvider)
        // signInWithRedirect يغادر الصفحة؛ الوعد لا يُحل هنا
        return new Promise<AuthUser>(() => {})
      }
      throw authError
    }
  }

  async signInWithEmail(email: string, password: string): Promise<AuthUser> {
    await authPersistenceReady
    try {
      const result = await signInWithEmailAndPassword(auth, email.trim(), password)
      return toAuthUser(result.user)
    } catch (error) {
      throw toAuthError(error)
    }
  }

  async registerWithEmail(email: string, password: string): Promise<AuthUser> {
    await authPersistenceReady
    try {
      const result = await createUserWithEmailAndPassword(auth, email.trim(), password)
      return toAuthUser(result.user)
    } catch (error) {
      throw toAuthError(error)
    }
  }

  async sendPasswordReset(email: string): Promise<void> {
    try {
      await sendPasswordResetEmail(auth, email.trim())
    } catch (error) {
      throw toAuthError(error)
    }
  }

  async signOut(): Promise<void> {
    try {
      await fbSignOut(auth)
    } catch (error) {
      throw toAuthError(error)
    }
  }
}
