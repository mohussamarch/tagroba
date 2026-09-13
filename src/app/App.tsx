import { useEffect, useState } from 'react'
import { SignInScreen } from '../presentation/screens/SignInScreen'
import { AppLockContext } from '../presentation/components/AppLockContext'
import { AppLockGate } from '../presentation/components/AppLockGate'
import { AppShell } from './AppShell'
import { appLock } from './appLock'
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

  // القفل بيغطي كل حاجة بما فيها شاشة الفتح، من غير ما يشيل اللي تحته (OVERRIDES §21)
  return (
    <AppLockContext.Provider value={appLock}>
      <AppLockGate lock={appLock}>
        {session.status === 'loading' ? <Splash />
          : session.status === 'out' ? <SignInScreen auth={container.auth} />
          : <AppShell
              key={session.user.uid}
              container={container}
              uid={session.user.uid}
              onSignOut={() => void container.auth.signOut()}
            />}
      </AppLockGate>
    </AppLockContext.Provider>
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
