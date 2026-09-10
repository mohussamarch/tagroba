import { useEffect, useState } from 'react'
import { SignInScreen } from '../presentation/screens/SignInScreen'
import { AppShell } from './AppShell'
import { useTheme } from '../presentation/theme/useTheme'
import type { AuthUser } from '../application/ports/AuthPort'
import type { Container } from './container'

type Session = { status: 'loading' } | { status: 'out' } | { status: 'in'; user: AuthUser }

export function App({ container }: { container: Container }) {
  const [session, setSession] = useState<Session>({ status: 'loading' })

  useEffect(
    () =>
      container.auth.observe((user) =>
        setSession(user ? { status: 'in', user } : { status: 'out' }),
      ),
    [container],
  )

  if (session.status === 'loading') return <Splash />
  if (session.status === 'out') return <SignInScreen auth={container.auth} />
  return (
    <AppShell
      key={session.user.uid}
      container={container}
      uid={session.user.uid}
      onSignOut={() => void container.auth.signOut()}
    />
  )
}

function Splash() {
  useTheme()
  return (
    <main className="appSplash">
      <strong>مصروفي</strong>
      <p role="status">بنفتح مصروفي…</p>
      <div className="appSplash__line" aria-hidden="true"/>
    </main>
  )
}
