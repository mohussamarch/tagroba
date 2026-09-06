import { useEffect, useState } from 'react'
import { SignInScreen } from '../presentation/screens/SignInScreen'
import { useTheme } from '../presentation/theme/useTheme'
import type { AuthUser } from '../application/ports/AuthPort'
import type { Container } from './container'

type Session = { status: 'loading' } | { status: 'out' } | { status: 'in'; user: AuthUser }

interface Props {
  container: Container
}

export function App({ container }: Props) {
  const [session, setSession] = useState<Session>({ status: 'loading' })

  useEffect(() => {
    return container.auth.observe((user) =>
      setSession(user ? { status: 'in', user } : { status: 'out' }),
    )
  }, [container])

  if (session.status === 'loading') return <Splash />
  if (session.status === 'out') return <SignInScreen auth={container.auth} />
  return <SignedIn user={session.user} onSignOut={() => container.auth.signOut()} />
}

function Splash() {
  useTheme() // يطبّق سمة data-theme قبل أول رسم
  return (
    <main
      style={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        color: 'var(--c-muted)',
      }}
    >
      <p role="status">بنفتح مصروفي…</p>
    </main>
  )
}

/**
 * شاشة مؤقتة بعد الدخول — تُستبدل بالشاشة الرئيسية في المرحلة الثانية.
 * موجودة هنا فقط لإثبات معيار المرحلة صفر: دخول وخروج ناجحان.
 */
function SignedIn({ user, onSignOut }: { user: AuthUser; onSignOut: () => void }) {
  const { theme, toggleTheme } = useTheme()
  return (
    <main
      style={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        padding: 'var(--s-page)',
      }}
    >
      <div
        style={{
          background: 'var(--c-surface)',
          border: '1px solid var(--c-border)',
          borderRadius: 'var(--r-card)',
          padding: 'calc(var(--s-card) * 1.5)',
          maxWidth: 380,
          width: '100%',
          display: 'grid',
          gap: 'var(--s-section)',
          textAlign: 'center',
        }}
      >
        <h1 style={{ fontSize: 'var(--t-title)', margin: 0 }}>أهلاً</h1>
        <p style={{ color: 'var(--c-muted)', margin: 0, direction: 'ltr' }}>
          {user.displayName ?? user.email ?? user.uid}
        </p>
        <p style={{ color: 'var(--c-muted)', margin: 0, fontSize: 'var(--t-badge)' }}>
          المرحلة صفر خلصت. الشاشات الحقيقية جاية في الشرائح الجاية.
        </p>
        <button
          type="button"
          onClick={toggleTheme}
          style={{
            minHeight: 'var(--touch-min)',
            borderRadius: 12,
            border: '1px solid var(--c-border)',
            background: 'var(--c-soft)',
            cursor: 'pointer',
          }}
        >
          {theme === 'light' ? 'الوضع الغامق' : 'الوضع الفاتح'}
        </button>
        <button
          type="button"
          onClick={onSignOut}
          style={{
            minHeight: 'var(--touch-min)',
            borderRadius: 12,
            border: 'none',
            background: 'var(--c-text)',
            color: 'var(--c-surface)',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          تسجيل الخروج
        </button>
      </div>
    </main>
  )
}
