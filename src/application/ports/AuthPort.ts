/**
 * واجهة الحسابات (port) — تعريف بلا تنفيذ.
 * application/ يعرف الواجهة؛ التنفيذ في infrastructure/.
 */

export interface AuthUser {
  uid: string
  email: string | null
  displayName: string | null
}

/** خطأ بلغة المستخدم — الشاشة تعرض `message` كما هو. */
export class AuthError extends Error {
  constructor(
    message: string,
    readonly code: string,
    /** اسم الحقل المسؤول، لعرض الخطأ بجانبه (spec/04). */
    readonly field?: 'email' | 'password' | 'form',
  ) {
    super(message)
    this.name = 'AuthError'
  }
}

export interface AuthPort {
  readonly googleUnavailableReason?: string
  /** يراقب حالة الدخول. يرجّع دالة إلغاء الاشتراك. */
  observe(callback: (user: AuthUser | null) => void): () => void
  signInWithGoogle(): Promise<AuthUser>
  signInWithEmail(email: string, password: string): Promise<AuthUser>
  registerWithEmail(email: string, password: string): Promise<AuthUser>
  sendPasswordReset(email: string): Promise<void>
  signOut(): Promise<void>
}
