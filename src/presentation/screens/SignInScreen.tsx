import { useState, type FormEvent } from 'react'
import { AuthError, type AuthPort } from '../../application/ports/AuthPort'
import { useTheme } from '../theme/useTheme'
import './SignInScreen.css'

type Mode = 'signin' | 'register'

interface Props {
  auth: AuthPort
}

/**
 * شاشة تسجيل الدخول — جوجل + إيميل وكلمة سر (OVERRIDES §3).
 * الألوان من tokens.css فقط، والوضعان فاتح وغامق يشتغلان.
 *
 * الشاشة لا تكلّم فايربيز مباشرة — تنادي AuthPort (ARCHITECTURE.md §3).
 */
export function SignInScreen({ auth }: Props) {
  const { theme, toggleTheme } = useTheme()
  const [mode, setMode] = useState<Mode>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState<'none' | 'google' | 'email' | 'reset'>('none')
  const [error, setError] = useState<AuthError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const working = busy !== 'none'
  const fieldError = (field: 'email' | 'password') => (error?.field === field ? error : null)

  function handleFailure(cause: unknown) {
    setError(
      cause instanceof AuthError
        ? cause
        : new AuthError('حصل خطأ مش متوقع. جرّب تاني', 'unknown', 'form'),
    )
  }

  async function onGoogle() {
    setError(null)
    setNotice(null)
    setBusy('google')
    try {
      await auth.signInWithGoogle()
      // النجاح يغيّر حالة الدخول، وApp يبدّل الشاشة
    } catch (cause) {
      handleFailure(cause)
      setBusy('none')
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setNotice(null)

    if (!email.trim()) {
      setError(new AuthError('اكتب الإيميل', 'app/missing-email', 'email'))
      return
    }
    if (!password) {
      setError(new AuthError('اكتب كلمة السر', 'app/missing-password', 'password'))
      return
    }
    if (mode === 'register' && password.length < 6) {
      setError(new AuthError('كلمة السر لازم ٦ حروف على الأقل', 'app/weak-password', 'password'))
      return
    }

    setBusy('email')
    try {
      if (mode === 'register') await auth.registerWithEmail(email, password)
      else await auth.signInWithEmail(email, password)
    } catch (cause) {
      handleFailure(cause)
      setBusy('none')
    }
  }

  async function onReset() {
    setError(null)
    setNotice(null)
    if (!email.trim()) {
      setError(new AuthError('اكتب الإيميل الأول عشان نبعتلك رابط', 'app/missing-email', 'email'))
      return
    }
    setBusy('reset')
    try {
      await auth.sendPasswordReset(email)
      setNotice('بعتنا رابط تغيير كلمة السر على إيميلك. شوف صندوق الوارد والـSpam')
    } catch (cause) {
      handleFailure(cause)
    } finally {
      setBusy('none')
    }
  }

  return (
    <main className="signin">
      <div className="signin__card">
        <header className="signin__brand">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div className="signin__mark" aria-hidden="true">
              م
            </div>
            <div>
              <h1 className="signin__title">مصروفي</h1>
              <p className="signin__subtitle">
                {mode === 'signin' ? 'سجّل دخولك عشان تكمّل' : 'اعمل حساب جديد'}
              </p>
            </div>
          </div>
          <button
            type="button"
            className="signin__themeBtn"
            onClick={toggleTheme}
            aria-label={theme === 'light' ? 'تحويل للوضع الغامق' : 'تحويل للوضع الفاتح'}
          >
            <span aria-hidden="true">{theme === 'light' ? '🌙' : '☀️'}</span>
          </button>
        </header>

        {auth.googleUnavailableReason ? <p role="note">{auth.googleUnavailableReason}</p> : <button type="button" className="signin__google" onClick={onGoogle} disabled={working}>
          <GoogleMark />
          {busy === 'google' ? 'بنفتح جوجل…' : 'الدخول بحساب جوجل'}
        </button>}

        <div className="signin__divider">أو</div>

        <form onSubmit={onSubmit} noValidate style={{ display: 'grid', gap: 'var(--s-section)' }}>
          <div className="signin__field">
            <label className="signin__label" htmlFor="email">
              الإيميل
            </label>
            <input
              id="email"
              className="signin__input"
              type="email"
              inputMode="email"
              autoComplete="email"
              dir="ltr"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={fieldError('email') ? 'true' : 'false'}
              aria-describedby={fieldError('email') ? 'email-error' : undefined}
              disabled={working}
            />
            {fieldError('email') && (
              <p className="signin__error" id="email-error" role="alert">
                <span aria-hidden="true">⚠</span> {error!.message}
              </p>
            )}
          </div>

          <div className="signin__field">
            <label className="signin__label" htmlFor="password">
              كلمة السر
            </label>
            <input
              id="password"
              className="signin__input"
              type="password"
              autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
              dir="ltr"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={fieldError('password') ? 'true' : 'false'}
              aria-describedby={fieldError('password') ? 'password-error' : undefined}
              disabled={working}
            />
            {fieldError('password') && (
              <p className="signin__error" id="password-error" role="alert">
                <span aria-hidden="true">⚠</span> {error!.message}
              </p>
            )}
          </div>

          {error?.field === 'form' && (
            <p className="signin__error" role="alert">
              <span aria-hidden="true">⚠</span> {error.message}
            </p>
          )}

          {notice && (
            <p className="signin__notice" role="status">
              {notice}
            </p>
          )}

          <button type="submit" className="signin__submit" disabled={working}>
            {busy === 'email'
              ? 'لحظة…'
              : mode === 'signin'
                ? 'تسجيل الدخول'
                : 'إنشاء الحساب'}
          </button>
        </form>

        <div className="signin__links">
          <button
            type="button"
            className="signin__link"
            onClick={() => {
              setMode(mode === 'signin' ? 'register' : 'signin')
              setError(null)
              setNotice(null)
            }}
            disabled={working}
          >
            {mode === 'signin' ? 'معندكش حساب؟ اعمل واحد' : 'عندك حساب؟ سجّل دخولك'}
          </button>
          {mode === 'signin' && (
            <button type="button" className="signin__link" onClick={onReset} disabled={working}>
              {busy === 'reset' ? 'بنبعت…' : 'نسيت كلمة السر'}
            </button>
          )}
        </div>

        <p className="signin__foot">
          بياناتك محفوظة في حسابك وحدك، ومحمية بقواعد السيرفر.
          <br />
          مفيش مشاركة ولا تحليل عبر المستخدمين.
        </p>
      </div>
    </main>
  )
}

function GoogleMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62Z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z"
      />
      <path
        fill="#FBBC05"
        d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33Z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z"
      />
    </svg>
  )
}
