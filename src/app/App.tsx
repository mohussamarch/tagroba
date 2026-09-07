import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { SignInScreen } from '../presentation/screens/SignInScreen'
import { TransactionsScreen } from '../presentation/screens/TransactionsScreen'
import { ImportSheet } from '../presentation/screens/ImportSheet'
import { useTheme } from '../presentation/theme/useTheme'
import type { AuthUser } from '../application/ports/AuthPort'
import type { TransactionsScreenData } from '../application/useCases/loadTransactionsScreen'
import type { Container, UserContainer } from './container'

type Session = { status: 'loading' } | { status: 'out' } | { status: 'in'; user: AuthUser }

export function App({ container }: { container: Container }) {
  const [session, setSession] = useState<Session>({ status: 'loading' })

  useEffect(
    () => container.auth.observe((user) => setSession(user ? { status: 'in', user } : { status: 'out' })),
    [container],
  )

  if (session.status === 'loading') return <Splash />
  if (session.status === 'out') return <SignInScreen auth={container.auth} />
  return (
    <SignedIn
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
    <main style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', color: 'var(--c-muted)' }}>
      <p role="status">بنفتح مصروفي…</p>
    </main>
  )
}

function SignedIn({
  container,
  uid,
  onSignOut,
}: {
  container: Container
  uid: string
  onSignOut: () => void
}) {
  const user: UserContainer = useMemo(() => container.forUser(uid), [container, uid])

  const [data, setData] = useState<TransactionsScreenData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [recovery, setRecovery] = useState<string | null>(null)
  const requestId = useRef(0)
  const cleanedUp = useRef(false)

  const load = useCallback(async () => {
    const id = ++requestId.current
    setLoading(true)
    setError(null)
    try {
      /*
       * تنظيف الدفعات المعلّقة **قبل** أي قراءة، مرة واحدة لكل جلسة.
       * دفعة معلّقة تعني استيرادًا انقطع قبل اعتماده (ARCHITECTURE.md §10.5)،
       * وما تحتها غير معتمد فلا يجوز أن يظهر في الشاشة كأنه بيانات حقيقية.
       */
      if (!cleanedUp.current) {
        cleanedUp.current = true

        /*
         * زرع المراجع الأولية عند أول دخول (ARCHITECTURE.md §10.6).
         * آمن التكرار: لو كانت مزروعة لا يُكتب شيء فوق تعديلات المستخدم.
         * يسبق التصنيف لأن الاستيراد يحتاج القواعد موجودة.
         */
        await user.seedUserReferences()

        const outcomes = await user.resumeStagedBatch.cleanupAll()
        if (outcomes.length > 0) {
          const total = outcomes.reduce((sum, o) => sum + o.deletedTransactions, 0)
          // لا يُخفى ما حدث: المستخدم يعرف أن استيرادًا سابقًا لم يكتمل
          setRecovery(
            `فيه استيراد سابق ماكملش (${outcomes.map((o) => o.fileName).join('، ')}) ` +
              `فشلناه بالكامل عشان مايسيبش بيانات ناقصة. ` +
              `${total > 0 ? `اتشال ${total} سطر. ` : ''}تقدر تستورد الملف تاني.`,
          )
        }
      }

      const today = new Date().toISOString().slice(0, 10)
      const result = await user.loadTransactionsScreen({ today })
      if (id === requestId.current) setData(result)
    } catch (cause) {
      if (id !== requestId.current) return
      // الخطأ يُعرض بنصه، ولا يُخفى خلف رسالة عامة
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      if (id === requestId.current) setLoading(false)
    }
  }, [user])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <TransactionsScreen
        loading={loading}
        error={error}
        data={data}
        recovery={recovery}
        onDismissRecovery={() => setRecovery(null)}
        onSignOut={onSignOut}
        onImport={() => setImportOpen(true)}
        onRetry={() => void load()}
      />
      {importOpen && (
        <ImportSheet
          user={user}
          onClose={() => setImportOpen(false)}
          onImported={() => {
            setImportOpen(false)
            void load()
          }}
        />
      )}
    </>
  )
}
